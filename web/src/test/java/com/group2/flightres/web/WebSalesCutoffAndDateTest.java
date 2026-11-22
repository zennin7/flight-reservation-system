package com.group2.flightres.web;

import com.group2.flightres.config.PricingPolicy;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BoardingPassVerificationService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.LoginThrottle;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PaymentService;
import com.group2.flightres.service.PaymentThrottle;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.RegistrationService;
import com.group2.flightres.service.RegistrationThrottle;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.service.VerifyThrottle;
import com.group2.flightres.util.FlightTimes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSalesCutoffAndDateTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");

    private WebApplication webApp;

    @AfterEach
    void tearDown() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration d) {
            this.instant = this.instant.plus(d);
        }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return instant; }
    }

    private static final class InMemorySeatDao implements SeatDao {
        private final Map<Long, FlightSeat> seats = new ConcurrentHashMap<>();
        private final Clock clock;

        InMemorySeatDao(Clock clock) {
            this.clock = clock;
        }

        void addSeat(long flightId, long seatId, String rowCol, SeatClass cls) {
            FlightSeat s = new FlightSeat();
            s.setFlightSeatId(seatId);
            s.setFlightId(flightId);
            int row = Integer.parseInt(rowCol.substring(0, rowCol.length() - 1));
            char col = rowCol.charAt(rowCol.length() - 1);
            s.setSeatRow(row);
            s.setSeatCol(col);
            s.setSeatClass(cls);
            s.setStatus(SeatStatus.AVAILABLE);
            s.setCalculatedPrice(new BigDecimal("1500.00"));
            seats.put(seatId, s);
        }

        FlightSeat get(long seatId) { return seats.get(seatId); }

        @Override
        public List<FlightSeat> getSeatMapForFlight(long flightId) {
            return seats.values().stream()
                    .filter(s -> s.getFlightId() == flightId)
                    .toList();
        }

        @Override
        public Optional<Instant> holdSeat(long flightSeatId, int holdSeconds, String holdOwner) {
            FlightSeat s = seats.get(flightSeatId);
            if (s == null || s.getStatus() == SeatStatus.BOOKED) {
                return Optional.empty();
            }
            Instant until = clock.instant().plusSeconds(holdSeconds);
            s.setStatus(SeatStatus.HELD);
            s.setHeldUntil(until);
            s.setHeldBy(holdOwner);
            return Optional.of(until);
        }

        @Override
        public boolean releaseSeat(long flightSeatId) {
            return releaseSeat(flightSeatId, null);
        }

        @Override
        public boolean releaseSeat(long flightSeatId, String holdOwner) {
            FlightSeat s = seats.get(flightSeatId);
            if (s == null || s.getStatus() != SeatStatus.HELD) {
                return false;
            }
            if (holdOwner != null && s.getHeldBy() != null && !holdOwner.equals(s.getHeldBy())) {
                return false;
            }
            s.setStatus(SeatStatus.AVAILABLE);
            s.setHeldUntil(null);
            s.setHeldBy(null);
            return true;
        }

        @Override
        public boolean bookSeat(long flightSeatId) {
            FlightSeat s = seats.get(flightSeatId);
            if (s == null) return false;
            s.setStatus(SeatStatus.BOOKED);
            s.setHeldUntil(null);
            s.setHeldBy(null);
            return true;
        }

        @Override
        public int releaseExpiredHolds() { return 0; }

        @Override
        public Optional<FlightSeat> findSeatById(long flightSeatId) {
            return Optional.ofNullable(seats.get(flightSeatId));
        }

        @Override
        public Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
            return Map.of(SeatClass.ECONOMY, BigDecimal.ONE, SeatClass.FIRST, new BigDecimal("3.50"));
        }
    }

    private record Harness(WebApplication app, int port, InMemorySeatDao seatDao,
                           Map<String, BookingRecord> bookingsByRef,
                           AtomicBoolean gatewayCharged, MutableClock clock) {}

    private Harness startHarness(Instant startNow, List<FlightSearchResult> flights) {
        MutableClock clock = new MutableClock(startNow);
        InMemorySeatDao seatDao = new InMemorySeatDao(clock);
        Map<Long, FlightSearchResult> flightMap = new ConcurrentHashMap<>();
        long nextSeat = 1001L;
        for (FlightSearchResult f : flights) {
            flightMap.put(f.getFlightId(), f);
            seatDao.addSeat(f.getFlightId(), nextSeat++, "6A", SeatClass.ECONOMY);
        }
        FlightDao flightDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
                return new ArrayList<>(flightMap.values());
            }

            @Override
            public Optional<FlightSearchResult> findById(long flightId) {
                return Optional.ofNullable(flightMap.get(flightId));
            }
        };

        Map<String, BookingRecord> store = new ConcurrentHashMap<>();
        AtomicInteger pnrSeq = new AtomicInteger(1);
        BookingDao bookingDao = new BookingDao() {
            @Override
            public BookingRecord createBooking(BookingContext context) {
                FlightSearchResult f = flightMap.get(context.getFlightId());
                String pnr = "SCUT0" + pnrSeq.getAndIncrement();
                context.setBookingReference(pnr);
                seatDao.bookSeat(context.getSelectedSeats().get(0).getFlightSeatId());
                BookingRecord r = new BookingRecord();
                r.setBookingId(200L + pnrSeq.get());
                r.setBookingReference(pnr);
                r.setFlightId(f.getFlightId());
                r.setFlightNumber(f.getFlightNumber());
                r.setOrigin(context.getOrigin());
                r.setDestination(context.getDestination());
                r.setDepartureInstant(f.getDeparture());
                r.setArrivalInstant(f.getArrival());
                r.setDepartureDate(FlightTimes.formatLocalDate(f.getDeparture(), f.getOriginZone()));
                r.setDepartureTime(FlightTimes.formatLocalTime(f.getDeparture(), f.getOriginZone()));
                r.setArrivalTime(FlightTimes.formatLocalTime(f.getArrival(), f.getDestZone()));
                r.setPassengerName(context.getPassengerName());
                r.setPassengerEmail(context.getPassengerEmail());
                r.setPassportNumber(context.getPassportNumber());
                r.setSeatLabel("6A");
                r.setSeatClass(SeatClass.ECONOMY);
                r.setStatus("CONFIRMED");
                r.setTotalAmount(context.getTotalPrice());
                r.setUserId(42L);
                store.put(pnr, r);
                return r;
            }

            @Override
            public Optional<BookingRecord> findByReference(String bookingRef) {
                return Optional.ofNullable(store.get(bookingRef));
            }

            @Override
            public List<BookingRecord> findByUserId(long userId) {
                return new ArrayList<>(store.values());
            }
        };

        PricingService pricingService = new PricingService(seatDao, clock) {
            @Override
            public PriceQuote quote(long flightId, SeatClass seatClass, Clock c) {
                FlightSearchResult f = flightMap.get(flightId);
                BigDecimal timing = PricingPolicy.getTimingMultiplier(c.instant(), f.getDeparture());
                return PriceQuote.calculate(f.getBasePrice(), BigDecimal.ONE, BigDecimal.ONE, timing);
            }
        };
        FlightService flightService = new FlightService(flightDao, clock);
        SeatMapService seatMapService = new SeatMapService(seatDao, flightDao, clock);
        BookingService bookingService = new BookingService(bookingDao, flightDao, pricingService, clock);
        UserDao emptyUserDao = new UserDao() {
            @Override
            public Optional<User> findByUsername(String username) { return Optional.empty(); }

            @Override
            public Optional<User> findByUsernameOrEmail(String identifier) { return Optional.empty(); }
        };
        AuthService authService = new AuthService(emptyUserDao);
        RegistrationService regService = new RegistrationService();

        AtomicBoolean gatewayCharged = new AtomicBoolean(false);
        PaymentService realGateway = new SimulatedPaymentGateway();
        PaymentService trackingGateway = new PaymentService() {
            @Override
            public PaymentResult processPayment(String cardNumber, String expiry, String cvv, BigDecimal amount) {
                gatewayCharged.set(true);
                return realGateway.processPayment(cardNumber, expiry, cvv, amount);
            }

            @Override
            public void refund(String reference) {
                realGateway.refund(reference);
            }
        };

        webApp = new WebApplication(
                flightService, seatMapService, bookingService, authService,
                new LoginThrottle(clock), regService, new RegistrationThrottle(clock),
                trackingGateway, new PaymentThrottle(clock),
                new BoardingPassVerificationService(bookingService, "test-secret".getBytes(StandardCharsets.UTF_8)),
                new VerifyThrottle(clock));
        webApp.start(0);
        int port = webApp.getApp().port();
        return new Harness(webApp, port, seatDao, store, gatewayCharged, clock);
    }

    private static String extractCsrf(String html) {
        Matcher m = Pattern.compile("name=\"csrf_token\" value=\"([^\"]+)\"").matcher(html);
        assertTrue(m.find(), "Expected csrf_token input in HTML");
        return m.group(1);
    }

    @Test
    @DisplayName("Past flight or flight departing under 60m cannot be held on /book or charged on /pay")
    void pastAndWithinCutoffFlightsAreBlockedOnBookAndPay() throws Exception {
        Instant now = Instant.parse("2026-11-22T12:00:00Z");
        FlightSearchResult pastFlight = new FlightSearchResult(
                10L, "SR 101", "JNB", "O. R. Tambo", "Johannesburg", SAST,
                "CPT", "Cape Town Intl", "Cape Town", SAST,
                now.minus(Duration.ofHours(2)), now, new BigDecimal("1200.00"), "Airbus A320", 10);
        FlightSearchResult within60mFlight = new FlightSearchResult(
                11L, "SR 102", "JNB", "O. R. Tambo", "Johannesburg", SAST,
                "CPT", "Cape Town Intl", "Cape Town", SAST,
                now.plus(Duration.ofMinutes(30)), now.plus(Duration.ofHours(2)), new BigDecimal("1200.00"), "Airbus A320", 10);
        FlightSearchResult borderlineOpenFlight = new FlightSearchResult(
                12L, "SR 103", "JNB", "O. R. Tambo", "Johannesburg", SAST,
                "CPT", "Cape Town Intl", "Cape Town", SAST,
                now.plus(Duration.ofMinutes(62)), now.plus(Duration.ofHours(3)), new BigDecimal("1200.00"), "Airbus A320", 10);

        Harness h = startHarness(now, List.of(pastFlight, within60mFlight, borderlineOpenFlight));
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        String base = "http://127.0.0.1:" + h.port();

        HttpResponse<String> seatsResp = client.send(
                HttpRequest.newBuilder(URI.create(base + "/flights/12/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrf = extractCsrf(seatsResp.body());

        // 1. Past flight rejected with 409 and seat stays AVAILABLE
        HttpResponse<String> pastBook = client.send(
                HttpRequest.newBuilder(URI.create(base + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                                        + "&flightId=10&flightSeatId=1001"
                                        + "&passengerName=Naledi+Sithole&passengerEmail=naledi@example.com"
                                        + "&passportNumber=A1234567&dateOfBirth=1994-05-15"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, pastBook.statusCode());
        assertTrue(pastBook.body().toLowerCase().contains("closed"));
        assertEquals(SeatStatus.AVAILABLE, h.seatDao().get(1001L).getStatus());

        // 2. <60m flight rejected with 409 and seat stays AVAILABLE
        HttpResponse<String> soonBook = client.send(
                HttpRequest.newBuilder(URI.create(base + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                                        + "&flightId=11&flightSeatId=1002"
                                        + "&passengerName=Naledi+Sithole&passengerEmail=naledi@example.com"
                                        + "&passportNumber=A1234567&dateOfBirth=1994-05-15"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, soonBook.statusCode());
        assertEquals(SeatStatus.AVAILABLE, h.seatDao().get(1002L).getStatus());

        // 3. Flight departing in 62m can be held, then advancing 3m crosses 60m cutoff -> /pay returns 409 without charging
        HttpResponse<String> openBook = client.send(
                HttpRequest.newBuilder(URI.create(base + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                                        + "&flightId=12&flightSeatId=1003"
                                        + "&passengerName=Naledi+Sithole&passengerEmail=naledi@example.com"
                                        + "&passportNumber=A1234567&dateOfBirth=1994-05-15"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, openBook.statusCode());
        assertEquals(SeatStatus.HELD, h.seatDao().get(1003L).getStatus());

        h.clock().advance(Duration.ofMinutes(3));
        HttpResponse<String> latePay = client.send(
                HttpRequest.newBuilder(URI.create(base + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                                        + "&cardNumber=" + URLEncoder.encode("4242 4242 4242 4242", StandardCharsets.UTF_8)
                                        + "&cardholderName=Naledi+Sithole&expiry=12/28&cvv=123"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, latePay.statusCode());
        assertFalse(h.gatewayCharged().get(), "Gateway must never be charged after sales cutoff");
        assertTrue(h.bookingsByRef().isEmpty(), "No booking may be created after sales cutoff");
        assertEquals(SeatStatus.AVAILABLE, h.seatDao().get(1003L).getStatus(), "Seat hold should be released");
    }

    @Test
    @DisplayName("Checkout and confirmation render full date and time derived from flights.departure_time in origin timezone")
    void checkoutAndConfirmationShowFullFlightTimezoneDateAndTime() throws Exception {
        Instant now = Instant.parse("2026-11-22T12:00:00Z");
        // 21:30Z on Dec 1 == 01:30+04 on Dec 2 in Asia/Dubai
        Instant dubaiDep = Instant.parse("2026-12-01T21:30:00Z");
        Instant jnbArr = Instant.parse("2026-12-02T03:00:00Z");
        FlightSearchResult dxbFlight = new FlightSearchResult(
                13L, "SR 801", "DXB", "Dubai International Airport", "Dubai", DUBAI,
                "JNB", "O. R. Tambo", "Johannesburg", SAST,
                dubaiDep, jnbArr, new BigDecimal("8450.00"), "Airbus A320", 10);

        Harness h = startHarness(now, List.of(dxbFlight));
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        String base = "http://127.0.0.1:" + h.port();

        HttpResponse<String> seatsResp = client.send(
                HttpRequest.newBuilder(URI.create(base + "/flights/13/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrf = extractCsrf(seatsResp.body());

        // Even if caller injects stale departureDate=2026-11-02, controller must compute from flight.departure in DXB timezone
        HttpResponse<String> bookResp = client.send(
                HttpRequest.newBuilder(URI.create(base + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                                        + "&flightId=13&flightSeatId=1001&departureDate=2026-11-02"
                                        + "&passengerName=Naledi+Sithole&passengerEmail=naledi@example.com"
                                        + "&passportNumber=A1234567&dateOfBirth=1994-05-15"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, bookResp.statusCode());

        HttpResponse<String> checkoutResp = client.send(
                HttpRequest.newBuilder(URI.create(base + "/checkout")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, checkoutResp.statusCode());
        assertTrue(checkoutResp.body().contains("2026-12-02 · 01:30"),
                "Checkout must render full local date and time in Asia/Dubai");
        assertFalse(checkoutResp.body().contains("2026-11-02"),
                "Checkout must never render stale fallback date 2026-11-02");

        HttpResponse<String> payResp = client.send(
                HttpRequest.newBuilder(URI.create(base + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                                        + "&cardNumber=" + URLEncoder.encode("4242 4242 4242 4242", StandardCharsets.UTF_8)
                                        + "&cardholderName=Naledi+Sithole&expiry=12/28&cvv=123"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, payResp.statusCode());
        String loc = payResp.headers().firstValue("Location").orElse("");
        assertTrue(loc.startsWith("/confirmation/"));

        HttpResponse<String> confResp = client.send(
                HttpRequest.newBuilder(URI.create(base + loc)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, confResp.statusCode());
        assertTrue(confResp.body().contains("2026-12-02 · 01:30"),
                "Confirmation boarding pass must display full departure date and time in origin timezone");
    }
}
