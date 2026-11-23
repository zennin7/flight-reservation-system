package com.group2.flightres.web;

import com.group2.flightres.dao.AdminDashboardDao;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.CabinLoadBreakdown;
import com.group2.flightres.model.AdminDashboardData.Range;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AdminDashboardService;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BoardingPassVerificationService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.LoginThrottle;
import com.group2.flightres.service.PaymentThrottle;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.RegistrationService;
import com.group2.flightres.service.RegistrationThrottle;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.service.VerifyThrottle;
import com.group2.flightres.util.DestinationPhotoResolver;
import com.group2.flightres.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebBackdropTest {

    private static final Pattern BACKDROP_MODE = Pattern.compile("data-backdrop-mode=\"([^\"]+)\"");
    private static final Pattern BACKDROP_SRC = Pattern.compile("data-backdrop-src=\"([^\"]+)\"");
    private static final Pattern BACKDROP_LIST = Pattern.compile("data-backdrop-list=\"([^\"]*)\"");
    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"");

    private WebApplication webApp;

    @AfterEach
    void tearDown() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    @Test
    @DisplayName("Every page template declares backdrop fragment and every resolver photo exists")
    void everyTemplateDeclaresBackdropAndEveryResolverPhotoExists() throws Exception {
        assertTrue(WebBackdropResolver.ROTATING_PHOTOS.size() <= 6, "Rotation must contain at most 6 images");

        Set<String> allPaths = new HashSet<>(WebBackdropResolver.ROTATING_PHOTOS);
        allPaths.add(WebBackdropResolver.FIXED_AERIAL);
        allPaths.add(WebBackdropResolver.CABIN_ECONOMY);
        allPaths.add(WebBackdropResolver.CABIN_FIRST);
        for (String code : DestinationPhotoResolver.knownAirportCodes()) {
            allPaths.add(WebBackdropResolver.destinationBackdrop(code));
        }
        for (String webPath : allPaths) {
            try (InputStream is = WebBackdropTest.class.getResourceAsStream("/public" + webPath)) {
                assertNotNull(is, "Missing backdrop resource on classpath: /public" + webPath);
                assertTrue(is.readAllBytes().length > 5_000, "Corrupt/empty photo: " + webPath);
            }
        }

        Path tplDir = resolveWebRoot().resolve("src/main/resources/templates");
        List<Path> templates;
        try (Stream<Path> list = Files.list(tplDir)) {
            templates = list.filter(p -> p.getFileName().toString().endsWith(".html")).toList();
        }
        assertTrue(templates.size() >= 14, "Expected all 14 page templates");
        for (Path tpl : templates) {
            String src = Files.readString(tpl, StandardCharsets.UTF_8);
            assertTrue(src.contains("fragments/shell :: backdrop"),
                    "Template missing shared backdrop fragment: " + tpl.getFileName());
            assertTrue(src.contains("/js/backdrop.js"),
                    "Template missing backdrop script: " + tpl.getFileName());
        }
    }

    @Test
    @DisplayName("Every live page declares its assigned backdrop mode and referenced photo returns HTTP 200")
    void livePagesDeclareAssignedBackdropAndReferencedAssetsServe200() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T08:00:00Z"), ZoneOffset.UTC);
        ZoneId sast = ZoneId.of("Africa/Johannesburg");
        FlightSearchResult flight = new FlightSearchResult(
                1L, "SR 305", "JNB", "O. R. Tambo", "Johannesburg", sast,
                "MXP", "Malpensa", "Milan", ZoneId.of("Europe/Rome"),
                Instant.parse("2026-12-10T10:00:00Z"), Instant.parse("2026-12-10T20:40:00Z"),
                new BigDecimal("1180.00"), "Airbus A320", 149, 137, 12);

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setFlightId(1L);
        seat.setSeatRow(6);
        seat.setSeatCol('A');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setCalculatedPrice(new BigDecimal("1180.00"));

        BookingRecord rec = new BookingRecord();
        rec.setBookingReference("BKP001");
        rec.setFlightId(1L);
        rec.setFlightNumber("SR 701");
        rec.setOrigin("Johannesburg (JNB)");
        rec.setDestination("Lake Como (MXP)");
        rec.setOriginCity("Johannesburg");
        rec.setDestinationCity("Lake Como");
        rec.setDepartureDate("2026-12-10");
        rec.setDepartureTime("18:40");
        rec.setArrivalTime("05:20");
        rec.setDepartureInstant(flight.getDeparture());
        rec.setArrivalInstant(flight.getArrival());
        rec.setSeatLabel("3A");
        rec.setSeatClass(SeatClass.FIRST);
        rec.setPassengerName("Thandi Nkosi");
        rec.setStatus("CONFIRMED");
        rec.setTotalAmount(new BigDecimal("4130.00"));
        rec.setUserId(1L);

        User admin = new User();
        admin.setUserId(1L);
        admin.setUsername("admin");
        admin.setFullName("David Zenn");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        FlightDao flightDao = new FlightDao() {
            @Override public List<FlightSearchResult> searchFlights(String o, String d, LocalDate dt) { return List.of(flight); }
            @Override public Optional<FlightSearchResult> findById(long id) { return id == 1L ? Optional.of(flight) : Optional.empty(); }
        };
        SeatDao seatDao = new SeatDao() {
            @Override public List<FlightSeat> getSeatMapForFlight(long flightId) { return List.of(seat); }
            @Override public Optional<Instant> holdSeat(long id, int s, String o) {
                seat.setStatus(SeatStatus.HELD);
                seat.setHeldBy(o);
                seat.setHeldUntil(clock.instant().plusSeconds(s));
                return Optional.of(seat.getHeldUntil());
            }
            @Override public boolean releaseSeat(long id) { seat.setStatus(SeatStatus.AVAILABLE); return true; }
            @Override public boolean bookSeat(long id) { seat.setStatus(SeatStatus.BOOKED); return true; }
            @Override public int releaseExpiredHolds() { return 0; }
            @Override public Optional<FlightSeat> findSeatById(long id) { return Optional.of(seat); }
            @Override public Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
                return Map.of(SeatClass.ECONOMY, BigDecimal.ONE, SeatClass.FIRST, new BigDecimal("3.50"));
            }
        };
        BookingDao bookingDao = new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext c) { throw new UnsupportedOperationException(); }
            @Override public Optional<BookingRecord> findByReference(String ref) { return "BKP001".equals(ref) ? Optional.of(rec) : Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(rec); }
        };
        UserDao userDao = new UserDao() {
            @Override public Optional<User> findByUsername(String u) { return findByUsernameOrEmail(u); }
            @Override public Optional<User> findByUsernameOrEmail(String u) { return "admin".equals(u) ? Optional.of(admin) : Optional.empty(); }
        };
        AdminDashboardDao adminDao = (clk, range, lowPct) -> new AdminDashboardData(
                Range.SEVEN_DAYS, 1L, 0.0, new BigDecimal("4130.00"), 0.0, 1L, 0.0, 0L, 0.0,
                List.of(), new CabinLoadBreakdown(0, 1, 161, 162, 0, 0), List.of(), List.of(), List.of(), List.of());

        PricingService pricingSvc = new PricingService(seatDao, clock) {
            @Override
            public com.group2.flightres.model.PriceQuote quote(long flightId, SeatClass seatClass, Clock clk) {
                BigDecimal mult = seatClass == SeatClass.FIRST ? new BigDecimal("3.50") : BigDecimal.ONE;
                return com.group2.flightres.model.PriceQuote.calculate(
                        flight.getBasePrice(), mult, BigDecimal.ONE, BigDecimal.ONE);
            }
        };
        BookingService bookingSvc = new BookingService(bookingDao, flightDao, pricingSvc, clock);
        webApp = new WebApplication(
                new FlightService(flightDao, Collections::emptyList, pricingSvc, clock),
                new SeatMapService(seatDao, flightDao, clock),
                bookingSvc,
                new AuthService(userDao),
                new LoginThrottle(clock),
                new RegistrationService(),
                new RegistrationThrottle(clock),
                new SimulatedPaymentGateway(),
                new PaymentThrottle(clock),
                new BoardingPassVerificationService(bookingSvc),
                new VerifyThrottle(clock),
                new AdminDashboardService(adminDao, clock));
        webApp.start(0);
        String baseUrl = "http://localhost:" + webApp.getApp().port();

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();

        // Login so authenticated pages also resolve
        String loginHtml = assertBackdrop(http, baseUrl, "/login", "rotate", "/photos/aerial-1.jpg");
        Matcher cm = CSRF_PATTERN.matcher(loginHtml);
        assertTrue(cm.find());
        String csrf = cm.group(1);
        http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                        + "&username=admin&password=" + URLEncoder.encode("Passw0rd!", StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString());

        String homeHtml = assertBackdrop(http, baseUrl, "/", "rotate", "/photos/aerial-1.jpg");
        Matcher lm = BACKDROP_LIST.matcher(homeHtml);
        assertTrue(lm.find());
        String[] list = lm.group(1).split(",");
        assertTrue(list.length >= 2 && list.length <= 6);
        for (String item : list) {
            HttpResponse<byte[]> asset = http.send(HttpRequest.newBuilder(URI.create(baseUrl + item)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, asset.statusCode(), "Rotating photo returned non-200: " + item);
        }

        assertBackdrop(http, baseUrl, "/register", "rotate", "/photos/aerial-1.jpg");
        assertBackdrop(http, baseUrl, "/flights?origin=Johannesburg+%28JNB%29&destination=Dubai+%28DXB%29&date=2026-12-10",
                "destination", "/photos/dest-dubai.jpg");
        assertBackdrop(http, baseUrl, "/flights/1/seats", "cabin", "/photos/cabin-economy-1.jpg");
        assertBackdrop(http, baseUrl, "/my-bookings", "fixed", "/photos/aerial-1.jpg");
        assertBackdrop(http, baseUrl, "/confirmation/BKP001", "destination-full", "/photos/dest-lake-como.jpg");
        assertBackdrop(http, baseUrl, "/about", "fixed", "/photos/aerial-1.jpg");
        assertBackdrop(http, baseUrl, "/contact", "fixed", "/photos/aerial-1.jpg");
        assertBackdrop(http, baseUrl, "/admin", "fixed", "/photos/aerial-1.jpg");

        // Hold seat to check /checkout backdrop
        String seatsPage = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        Matcher sm = CSRF_PATTERN.matcher(seatsPage);
        assertTrue(sm.find());
        String bookPost = "csrf_token=" + URLEncoder.encode(sm.group(1), StandardCharsets.UTF_8)
                + "&flightId=1&flightSeatId=101&passengerName=Thandi+Nkosi&passengerEmail=t%40example.com&passportNumber=A1234567&dateOfBirth=1998-03-14";
        http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/book"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(bookPost)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertBackdrop(http, baseUrl, "/checkout", "fixed", "/photos/aerial-1.jpg");
    }

    private String assertBackdrop(HttpClient http, String baseUrl, String path, String expectedMode, String expectedSrc)
            throws Exception {
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(), "Expected 200 on " + path);
        String html = resp.body();

        Matcher mm = BACKDROP_MODE.matcher(html);
        assertTrue(mm.find(), "Missing data-backdrop-mode on " + path);
        assertEquals(expectedMode, mm.group(1), "Wrong backdrop mode on " + path);

        Matcher ms = BACKDROP_SRC.matcher(html);
        assertTrue(ms.find(), "Missing data-backdrop-src on " + path);
        assertEquals(expectedSrc, ms.group(1), "Wrong backdrop src on " + path);

        HttpResponse<byte[]> imgResp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + expectedSrc)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, imgResp.statusCode(), "Referenced backdrop asset missing on HTTP server: " + expectedSrc);
        return html;
    }

    private static Path resolveWebRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("src/main/resources/templates/home.html"))) {
            return cwd;
        }
        return cwd.resolve("web");
    }
}
