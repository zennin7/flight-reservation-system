package com.group2.flightres.web;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AuthService;
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
import com.group2.flightres.util.PasswordUtil;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPaymentFlowTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final Instant DEP_INSTANT = Instant.parse("2026-11-20T06:30:00Z");
    private static final Instant ARR_INSTANT = Instant.parse("2026-11-20T08:40:00Z");
    private static final FlightSearchResult TEST_FLIGHT = new FlightSearchResult(
            1L, "GRP 204", "JNB", "O. R. Tambo International Airport", "Johannesburg", SAST,
            "CPT", "Cape Town International Airport", "Cape Town", SAST,
            DEP_INSTANT, ARR_INSTANT, new BigDecimal("1200.00"), "Airbus A320", 149, 137, 12
    );

    private WebApplication webApp;

    @AfterEach
    void tearDown() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant start) {
            this.current = start;
        }

        void advance(Duration d) {
            this.current = this.current.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static final class InMemorySeatDao implements SeatDao {
        private final Map<Long, FlightSeat> seats = new ConcurrentHashMap<>();
        private final Clock clock;

        InMemorySeatDao(Clock clock) {
            this.clock = clock;
            FlightSeat seat = new FlightSeat();
            seat.setFlightSeatId(101L);
            seat.setFlightId(1L);
            seat.setSeatRow(6);
            seat.setSeatCol('A');
            seat.setSeatClass(SeatClass.ECONOMY);
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setCalculatedPrice(new BigDecimal("1200.00"));
            seats.put(101L, seat);
        }

        @Override
        public List<FlightSeat> getSeatMapForFlight(long flightId) {
            releaseExpiredHolds();
            return new ArrayList<>(seats.values());
        }

        @Override
        public Optional<Instant> holdSeat(long flightSeatId, int holdSeconds, String holdOwner) {
            releaseExpiredHolds();
            FlightSeat s = seats.get(flightSeatId);
            if (s == null) return Optional.empty();
            synchronized (s) {
                if (s.getStatus() == SeatStatus.AVAILABLE
                        || (s.getStatus() == SeatStatus.HELD && (holdOwner == null || holdOwner.equals(s.getHeldBy())))) {
                    Instant exp = clock.instant().plusSeconds(holdSeconds);
                    s.setStatus(SeatStatus.HELD);
                    s.setHeldUntil(exp);
                    s.setHeldBy(holdOwner);
                    return Optional.of(exp);
                }
                return Optional.empty();
            }
        }

        @Override
        public Optional<Instant> holdSeat(long flightSeatId, int holdSeconds) {
            return holdSeat(flightSeatId, holdSeconds, null);
        }

        @Override
        public boolean releaseSeat(long flightSeatId) {
            return releaseSeat(flightSeatId, null);
        }

        @Override
        public boolean releaseSeat(long flightSeatId, String holdOwner) {
            FlightSeat s = seats.get(flightSeatId);
            if (s == null) return false;
            synchronized (s) {
                if (s.getStatus() == SeatStatus.HELD
                        && (holdOwner == null || s.getHeldBy() == null || holdOwner.equals(s.getHeldBy()))) {
                    s.setStatus(SeatStatus.AVAILABLE);
                    s.setHeldUntil(null);
                    s.setHeldBy(null);
                    return true;
                }
                return false;
            }
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
        public int releaseExpiredHolds() {
            int count = 0;
            Instant now = clock.instant();
            for (FlightSeat s : seats.values()) {
                synchronized (s) {
                    if (s.getStatus() == SeatStatus.HELD && s.getHeldUntil() != null && !s.getHeldUntil().isAfter(now)) {
                        s.setStatus(SeatStatus.AVAILABLE);
                        s.setHeldUntil(null);
                        s.setHeldBy(null);
                        count++;
                    }
                }
            }
            return count;
        }

        @Override
        public Optional<FlightSeat> findSeatById(long flightSeatId) {
            return Optional.ofNullable(seats.get(flightSeatId));
        }

        @Override
        public Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
            return Map.of(SeatClass.ECONOMY, BigDecimal.ONE, SeatClass.FIRST, new BigDecimal("3.50"));
        }
    }

    private static final class InMemoryBookingDao implements BookingDao {
        final Map<String, BookingRecord> byRef = new ConcurrentHashMap<>();
        final Map<String, BookingRecord> byIdem = new ConcurrentHashMap<>();
        final List<BookingContext> createdContexts = new ArrayList<>();
        final AtomicBoolean failNextPersist = new AtomicBoolean(false);
        final AtomicInteger counter = new AtomicInteger(1);
        final InMemorySeatDao seatDao;

        InMemoryBookingDao(InMemorySeatDao seatDao) {
            this.seatDao = seatDao;
        }

        @Override
        public synchronized BookingRecord createBooking(BookingContext context) {
            if (context.getIdempotencyKey() != null && byIdem.containsKey(context.getIdempotencyKey())) {
                return byIdem.get(context.getIdempotencyKey());
            }
            if (failNextPersist.get()) {
                throw new RuntimeException("Simulated DB write failure");
            }
            String pnr = "PAYT0" + counter.getAndIncrement();
            context.setBookingReference(pnr);
            seatDao.bookSeat(context.getSelectedSeats().get(0).getFlightSeatId());

            BookingRecord rec = new BookingRecord();
            rec.setBookingId(100L + counter.get());
            rec.setBookingReference(pnr);
            rec.setFlightId(context.getFlightId());
            rec.setFlightNumber("GRP 204");
            rec.setOrigin(context.getOrigin());
            rec.setDestination(context.getDestination());
            rec.setDepartureDate(context.getDepartureDate());
            rec.setDepartureTime("08:30");
            rec.setArrivalTime("10:40");
            rec.setDepartureInstant(DEP_INSTANT);
            rec.setArrivalInstant(ARR_INSTANT);
            rec.setPassengerName(context.getPassengerName());
            rec.setPassengerEmail(context.getPassengerEmail());
            rec.setPassportNumber(context.getPassportNumber());
            rec.setSeatLabel("6A");
            rec.setSeatClass(SeatClass.ECONOMY);
            rec.setStatus("CONFIRMED");
            rec.setTotalAmount(context.getTotalPrice());
            rec.setUserId(context.getCurrentUser() != null ? context.getCurrentUser().getUserId() : 50L);

            createdContexts.add(context);
            byRef.put(pnr, rec);
            if (context.getIdempotencyKey() != null) {
                byIdem.put(context.getIdempotencyKey(), rec);
            }
            return rec;
        }

        @Override
        public Optional<BookingRecord> findByReference(String bookingRef) {
            return Optional.ofNullable(byRef.get(bookingRef));
        }

        @Override
        public Optional<BookingRecord> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(byIdem.get(idempotencyKey));
        }

        @Override
        public List<BookingRecord> findByUserId(long userId) {
            return new ArrayList<>(byRef.values());
        }
    }

    private record Harness(String baseUrl, MutableClock clock, InMemorySeatDao seatDao,
                           InMemoryBookingDao bookingDao, PricingService pricingService) {}

    private Harness startApp(PaymentService paymentService, PaymentThrottle paymentThrottle) {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
        FlightDao flightDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String o, String d, java.time.LocalDate date) {
                return List.of(TEST_FLIGHT);
            }

            @Override
            public Optional<FlightSearchResult> findById(long flightId) {
                return flightId == 1L ? Optional.of(TEST_FLIGHT) : Optional.empty();
            }
        };
        InMemorySeatDao seatDao = new InMemorySeatDao(clock);
        InMemoryBookingDao bookingDao = new InMemoryBookingDao(seatDao);
        PricingService pricingService = new PricingService(seatDao, clock);
        FlightService flightService = new FlightService(flightDao, clock);
        SeatMapService seatMapService = new SeatMapService(seatDao, flightDao, clock);
        BookingService bookingService = new BookingService(bookingDao, flightDao, pricingService, clock);

        User user1 = new User();
        user1.setUserId(1L);
        user1.setUsername("alice");
        user1.setEmail("alice@example.com");
        user1.setFullName("Alice Mokoena");
        user1.setRole(UserRole.CUSTOMER);
        user1.setActive(true);
        user1.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        User user2 = new User();
        user2.setUserId(2L);
        user2.setUsername("bob");
        user2.setEmail("bob@example.com");
        user2.setFullName("Bob Nkosi");
        user2.setRole(UserRole.CUSTOMER);
        user2.setActive(true);
        user2.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        AuthService authService = new AuthService(new com.group2.flightres.dao.UserDao() {
            @Override
            public Optional<User> findByUsername(String username) {
                return findByUsernameOrEmail(username);
            }

            @Override
            public Optional<User> findByUsernameOrEmail(String identifier) {
                if ("alice".equalsIgnoreCase(identifier)) return Optional.of(user1);
                if ("bob".equalsIgnoreCase(identifier)) return Optional.of(user2);
                return Optional.empty();
            }
        });

        webApp = new WebApplication(
                flightService, seatMapService, bookingService, authService,
                new LoginThrottle(clock), new RegistrationService(), new RegistrationThrottle(clock),
                paymentService, paymentThrottle != null ? paymentThrottle : new PaymentThrottle(clock));
        webApp.start(0);
        return new Harness("http://localhost:" + webApp.getApp().port(), clock, seatDao, bookingDao, pricingService);
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static String extractCsrf(String html) {
        Matcher m = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"").matcher(html);
        assertTrue(m.find(), "CSRF token missing");
        return m.group(1);
    }

    private static final String DEFAULT_PAX_PARAMS =
            "&passengerName=" + URLEncoder.encode("Thabo Khumalo", StandardCharsets.UTF_8)
                    + "&passengerEmail=" + URLEncoder.encode("thabo@example.com", StandardCharsets.UTF_8)
                    + "&passportNumber=Z9876543"
                    + "&dateOfBirth=1992-04-12";

    private static String holdSeatAndReachCheckout(HttpClient client, String baseUrl, String extraParams) throws Exception {
        HttpResponse<String> seats = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, seats.statusCode());
        String csrf = extractCsrf(seats.body());

        String body = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + "&flightId=1&flightSeatId=101"
                + (extraParams != null ? extraParams : "");

        HttpResponse<String> bookResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, bookResp.statusCode());
        assertEquals("/checkout", bookResp.headers().firstValue("Location").orElse(""));
        return csrf;
    }

    private static void loginUser(HttpClient client, String baseUrl, String username, String password) throws Exception {
        HttpResponse<String> loginGet = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrf = extractCsrf(loginGet.body());
        String body = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resp.statusCode());
    }

    @Test
    @DisplayName("Invalid passenger input on POST /pay returns 400 and never charges gateway")
    void invalidPassengerOnPayReturns400AndNeverChargesGateway() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger(0);
        SimulatedPaymentGateway inner = new SimulatedPaymentGateway();
        PaymentService spy = new PaymentService() {
            @Override
            public PaymentResult processPayment(String cardNumber, String expiry, String cvv, BigDecimal amount) {
                gatewayCalls.incrementAndGet();
                return inner.processPayment(cardNumber, expiry, cvv, amount);
            }

            @Override
            public void refund(String reference) {
                inner.refund(reference);
            }
        };
        Harness h = startApp(spy, null);
        HttpClient client = newClient();
        String csrf = holdSeatAndReachCheckout(client, h.baseUrl(), "");

        String badPayBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + "&passengerName=1234"
                + "&passengerEmail=" + URLEncoder.encode("valid.traveller@example.com", StandardCharsets.UTF_8)
                + "&passportNumber=" + URLEncoder.encode("ZA 99-8877", StandardCharsets.UTF_8)
                + "&dateOfBirth=1992-04-12"
                + "&cardNumber=" + URLEncoder.encode("4242 4242 4242 4242", StandardCharsets.UTF_8)
                + "&cardholderName=" + URLEncoder.encode("Thabo Khumalo", StandardCharsets.UTF_8)
                + "&expiry=" + URLEncoder.encode("12/29", StandardCharsets.UTF_8)
                + "&cvv=739";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(badPayBody)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode());
        assertEquals(0, gatewayCalls.get(), "Gateway must not be charged when passenger validation fails");
        assertEquals(0, h.bookingDao().byRef.size());
        assertTrue(resp.body().contains("valid.traveller@example.com"));
        assertTrue(resp.body().contains("ZA 99-8877"));
        assertTrue(resp.body().contains("is-invalid"));
        assertFalse(resp.body().contains("4242424242424242"));
    }

    @Test
    @DisplayName("Tampered price is ignored; approved payment stores brand/last4 only and sets no-store")
    void tamperedPriceIgnoredAndApprovedCardSucceedsWithoutLeakingPanOrCvv() throws Exception {
        Harness h = startApp(new SimulatedPaymentGateway(), null);
        HttpClient client = newClient();
        String csrf = holdSeatAndReachCheckout(client, h.baseUrl(), "&price=1.00&totalPrice=1.00");

        BigDecimal serverTotal = h.pricingService().quote(1L, SeatClass.ECONOMY, h.clock()).total();
        String payBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + DEFAULT_PAX_PARAMS
                + "&cardNumber=" + URLEncoder.encode("4242 4242 4242 4242", StandardCharsets.UTF_8)
                + "&cardholderName=" + URLEncoder.encode("Thabo Khumalo", StandardCharsets.UTF_8)
                + "&expiry=" + URLEncoder.encode("12/29", StandardCharsets.UTF_8)
                + "&cvv=739"
                + "&price=1.00&totalPrice=1.00&amount=1.00";

        HttpResponse<String> payResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(payBody)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(302, payResp.statusCode());
        assertTrue(payResp.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(1, h.bookingDao().byRef.size());
        BookingContext recorded = h.bookingDao().createdContexts.get(0);
        assertEquals(0, serverTotal.compareTo(recorded.getTotalPrice()));
        assertEquals("VISA", recorded.getCardBrand());
        assertEquals("4242", recorded.getCardLast4());
        assertNotNull(recorded.getPaymentResult().getTransactionReference());
    }

    @Test
    @DisplayName("Declined card keeps hold up to 3 attempts, clears sensitive inputs, then releases seat back to seat map")
    void declinedCardClearsFieldsKeepsHoldAndReleasesOnThirdDecline() throws Exception {
        Harness h = startApp(new SimulatedPaymentGateway(), null);
        HttpClient client = newClient();
        String csrf = holdSeatAndReachCheckout(client, h.baseUrl(), "");

        String declineBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + DEFAULT_PAX_PARAMS
                + "&cardNumber=" + URLEncoder.encode("4000 0000 0000 0002", StandardCharsets.UTF_8)
                + "&cardholderName=" + URLEncoder.encode("Thabo Khumalo", StandardCharsets.UTF_8)
                + "&expiry=" + URLEncoder.encode("12/29", StandardCharsets.UTF_8)
                + "&cvv=739";

        for (int attempt = 1; attempt <= 2; attempt++) {
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(declineBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(402, resp.statusCode(), "Attempt " + attempt + " must return 402");
            assertEquals(0, h.bookingDao().byRef.size());
            assertEquals(SeatStatus.HELD, h.seatDao().findSeatById(101L).orElseThrow().getStatus());
            assertFalse(resp.body().contains("4000000000000002"), "Card number must never be echoed");
            assertFalse(resp.body().contains("value=\"4000"), "Card input must remain blank");
            assertFalse(resp.body().contains("739"), "CVV must never be echoed");
        }

        HttpResponse<String> thirdResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(declineBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, thirdResp.statusCode());
        assertEquals("/flights/1/seats", thirdResp.headers().firstValue("Location").orElse(""));
        assertEquals(SeatStatus.AVAILABLE, h.seatDao().findSeatById(101L).orElseThrow().getStatus());

        HttpResponse<String> seatFlashPage = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(seatFlashPage.body().contains("declined 3 times"));
    }

    @Test
    @DisplayName("Concurrent double POST /pay creates strictly 1 booking and 1 gateway charge")
    void concurrentDoublePayCreatesOneBookingAndOneCharge() throws Exception {
        AtomicInteger chargeCalls = new AtomicInteger(0);
        SimulatedPaymentGateway spyGateway = new SimulatedPaymentGateway() {
            @Override
            public PaymentResult processPayment(String cardNumber, String expiry, String cvv, BigDecimal amount) {
                chargeCalls.incrementAndGet();
                try { Thread.sleep(80); } catch (InterruptedException ignored) {}
                return super.processPayment(cardNumber, expiry, cvv, amount);
            }
        };

        Harness h = startApp(spyGateway, null);
        HttpClient client = newClient();
        String csrf = holdSeatAndReachCheckout(client, h.baseUrl(), "");

        String payBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + DEFAULT_PAX_PARAMS
                + "&cardNumber=4242+4242+4242+4242&cardholderName=Thabo+Khumalo&expiry=12%2F29&cvv=123";

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> f1 = pool.submit(() -> {
                ready.countDown();
                start.await();
                return client.send(
                        HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(payBody)).build(),
                        HttpResponse.BodyHandlers.ofString());
            });
            Future<HttpResponse<String>> f2 = pool.submit(() -> {
                ready.countDown();
                start.await();
                return client.send(
                        HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(payBody)).build(),
                        HttpResponse.BodyHandlers.ofString());
            });

            ready.await();
            start.countDown();
            HttpResponse<String> r1 = f1.get();
            HttpResponse<String> r2 = f2.get();

            assertEquals(302, r1.statusCode());
            assertEquals(302, r2.statusCode());
            assertEquals(r1.headers().firstValue("Location"), r2.headers().firstValue("Location"));
            assertEquals(1, chargeCalls.get(), "Gateway must only be charged once");
            assertEquals(1, h.bookingDao().byRef.size(), "Strictly one booking must be persisted");
            assertEquals(1, h.bookingDao().byIdem.size(), "Strictly one idempotency payment must exist");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Expired hold returns 410 and creates no booking")
    void expiredHoldIsRejectedAtCheckoutAndPay() throws Exception {
        Harness h = startApp(new SimulatedPaymentGateway(), null);
        HttpClient client = newClient();
        String csrf = holdSeatAndReachCheckout(client, h.baseUrl(), "");

        h.clock().advance(Duration.ofMinutes(6));

        HttpResponse<String> checkoutResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/checkout")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(410, checkoutResp.statusCode());
        assertTrue(checkoutResp.body().contains("expired"));

        String payBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + DEFAULT_PAX_PARAMS
                + "&cardNumber=4242+4242+4242+4242&cardholderName=Thabo&expiry=12%2F29&cvv=123";
        HttpResponse<String> payResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(payBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(410, payResp.statusCode());
        assertEquals(0, h.bookingDao().byRef.size());
    }

    @Test
    @DisplayName("Another user's hold is rejected at booking and at payment")
    void anotherUsersHoldRejected() throws Exception {
        Harness h = startApp(new SimulatedPaymentGateway(), null);

        HttpClient clientAlice = newClient();
        loginUser(clientAlice, h.baseUrl(), "alice", "Passw0rd!");
        String aliceCsrf = holdSeatAndReachCheckout(clientAlice, h.baseUrl(), "");

        // Another user cannot select the held seat via POST /book
        HttpClient clientBob = newClient();
        loginUser(clientBob, h.baseUrl(), "bob", "Passw0rd!");
        HttpResponse<String> bobSeats = clientBob.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String bobCsrf = extractCsrf(bobSeats.body());
        String bobBookBody = "csrf_token=" + URLEncoder.encode(bobCsrf, StandardCharsets.UTF_8)
                + "&flightId=1&flightSeatId=101&passengerName=Bob+Nkosi&passengerEmail=bob@example.com&passportNumber=B9988776&dateOfBirth=1990-01-01";
        HttpResponse<String> bobBookResp = clientBob.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(bobBookBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bobBookResp.statusCode());
        assertTrue(bobBookResp.body().contains("taken by another user"));

        // Switching user identity inside Alice's browser session rejects /pay with 403 without creating a booking
        loginUser(clientAlice, h.baseUrl(), "bob", "Passw0rd!");
        String payBody = "csrf_token=" + URLEncoder.encode(aliceCsrf, StandardCharsets.UTF_8)
                + DEFAULT_PAX_PARAMS
                + "&cardNumber=4242+4242+4242+4242&cardholderName=Bob&expiry=12%2F29&cvv=123";
        HttpResponse<String> hijackedPay = clientAlice.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(payBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, hijackedPay.statusCode());
        assertEquals(0, h.bookingDao().byRef.size());
    }

    @Test
    @DisplayName("Gateway exception returns 502 and DB failure triggers compensating refund")
    void gatewayErrorAndDbFailureCompensatingRefund() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> lastRefunded = new java.util.concurrent.atomic.AtomicReference<>();
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
        PaymentService throwingOnce = new PaymentService() {
            boolean shouldThrow = true;

            @Override
            public PaymentResult processPayment(String cardNumber, String expiry, String cvv, BigDecimal amount) {
                if (shouldThrow) {
                    shouldThrow = false;
                    throw new IllegalStateException("Gateway socket timeout");
                }
                return gateway.processPayment(cardNumber, expiry, cvv, amount);
            }

            @Override
            public void refund(String reference) {
                lastRefunded.set(reference);
                gateway.refund(reference);
            }
        };

        Harness h = startApp(throwingOnce, null);
        HttpClient client = newClient();
        String csrf = holdSeatAndReachCheckout(client, h.baseUrl(), "");

        String payBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + DEFAULT_PAX_PARAMS
                + "&cardNumber=4242+4242+4242+4242&cardholderName=Thabo&expiry=12%2F29&cvv=123";

        // 1. Gateway exception -> 502 with clean message and no stack trace
        HttpResponse<String> gwErrResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(payBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(502, gwErrResp.statusCode());
        assertFalse(gwErrResp.body().contains("IllegalStateException"));
        assertTrue(gwErrResp.body().contains("not charged"));

        // 2. Approved payment followed by DB error -> refunds transaction automatically
        h.bookingDao().failNextPersist.set(true);
        HttpResponse<String> dbErrResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(payBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(500, dbErrResp.statusCode());
        assertTrue(dbErrResp.body().contains("reversed"));
        assertNotNull(lastRefunded.get(), "Failed persistence after approved charge must trigger compensating refund");
        assertTrue(gateway.isRefunded(lastRefunded.get()));
        assertEquals(0, h.bookingDao().byRef.size());
    }

    @Test
    @DisplayName("POST /pay rate limit returns HTTP 429 on 11th attempt")
    void payThrottleReturns429OnEleventhAttempt() throws Exception {
        Harness h = startApp(new SimulatedPaymentGateway(), null);
        HttpClient client = newClient();
        String csrf = holdSeatAndReachCheckout(client, h.baseUrl(), "");

        String declineBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + DEFAULT_PAX_PARAMS
                + "&cardNumber=4000+0000+0000+0002&cardholderName=Test&expiry=12%2F29&cvv=123";

        for (int i = 1; i <= 10; i++) {
            client.send(
                    HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(declineBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> eleventh = client.send(
                HttpRequest.newBuilder().uri(URI.create(h.baseUrl() + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(declineBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(429, eleventh.statusCode());
        assertTrue(eleventh.body().contains("Too many payment attempts"));
    }
}
