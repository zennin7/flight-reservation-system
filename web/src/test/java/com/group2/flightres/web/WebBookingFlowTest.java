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
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.SeatMapService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebBookingFlowTest {

    private static WebApplication webApp;
    private static int port;
    private static String baseUrl;
    private static MockBookingDao mockBookingDao;
    private static MockSeatDao mockSeatDao;

    // --- Mock DAOs ---

    private static final java.time.ZoneId SAST = java.time.ZoneId.of("Africa/Johannesburg");
    private static final java.time.Instant MOCK_DEP = java.time.Instant.parse("2026-11-02T04:30:00Z");
    private static final java.time.Instant MOCK_ARR = java.time.Instant.parse("2026-11-02T06:40:00Z");
    private static final FlightSearchResult MOCK_FLIGHT = new FlightSearchResult(
            1L, "GRP 204", "JNB", "O. R. Tambo International Airport", "Johannesburg", SAST,
            "CPT", "Cape Town International Airport", "Cape Town", SAST,
            MOCK_DEP, MOCK_ARR, new java.math.BigDecimal("1200"), "Airbus A320", 149, 137, 12
    );

    private static class MockFlightDao implements FlightDao {
        @Override
        public List<FlightSearchResult> searchFlights(String origin, String destination, java.time.LocalDate departureDate) {
            return List.of(MOCK_FLIGHT);
        }

        @Override
        public Optional<FlightSearchResult> findById(long flightId) {
            if (flightId == 1L) {
                return Optional.of(MOCK_FLIGHT);
            }
            return Optional.empty();
        }
    }
    private static class MockSeatDao implements SeatDao {
        private final Map<Long, FlightSeat> seats = new HashMap<>();

        public MockSeatDao() {
            FlightSeat s1 = new FlightSeat();
            s1.setFlightSeatId(101L);
            s1.setFlightId(1L);
            s1.setSeatRow(6);
            s1.setSeatCol('A');
            s1.setSeatClass(SeatClass.ECONOMY);
            s1.setStatus(SeatStatus.AVAILABLE);
            s1.setCalculatedPrice(new BigDecimal("1200"));
            seats.put(101L, s1);
        }

        @Override
        public List<FlightSeat> getSeatMapForFlight(long flightId) {
            return new ArrayList<>(seats.values());
        }

        @Override
        public boolean holdSeat(long flightSeatId) {
            FlightSeat s = seats.get(flightSeatId);
            if (s != null && s.getStatus() == SeatStatus.AVAILABLE) {
                s.setStatus(SeatStatus.HELD);
                s.setHeldUntil(java.time.Instant.now().plusSeconds(300));
                return true;
            }
            return false;
        }

        @Override
        public boolean releaseSeat(long flightSeatId) {
            FlightSeat s = seats.get(flightSeatId);
            if (s != null && s.getStatus() == SeatStatus.HELD) {
                s.setStatus(SeatStatus.AVAILABLE);
                s.setHeldUntil(null);
                return true;
            }
            return false;
        }

        @Override
        public boolean bookSeat(long flightSeatId) {
            FlightSeat s = seats.get(flightSeatId);
            if (s != null) {
                s.setStatus(SeatStatus.BOOKED);
                return true;
            }
            return false;
        }

        @Override
        public int releaseExpiredHolds() {
            return 0;
        }

        @Override
        public Optional<FlightSeat> findSeatById(long flightSeatId) {
            return Optional.ofNullable(seats.get(flightSeatId));
        }
    }

    private static class MockBookingDao implements BookingDao {
        private final Map<String, BookingRecord> store = new HashMap<>();

        @Override
        public BookingRecord createBooking(BookingContext context) {
            String pnr = "WTEST1";
            context.setBookingReference(pnr);

            BookingRecord rec = new BookingRecord();
            rec.setBookingId(999L);
            rec.setBookingReference(pnr);
            rec.setFlightId(context.getFlightId());
            rec.setFlightNumber(context.getFlightNumber() != null ? context.getFlightNumber() : "GRP 204");
            rec.setOrigin("Johannesburg (JNB)");
            rec.setDestination("Cape Town (CPT)");
            rec.setDepartureDate(context.getDepartureDate() != null ? context.getDepartureDate() : "2026-11-02");
            rec.setDepartureTime("06:30");
            rec.setArrivalTime("08:40");
            rec.setDepartureInstant(MOCK_DEP);
            rec.setArrivalInstant(MOCK_ARR);
            rec.setPassengerName(context.getPassengerName());
            rec.setPassengerEmail(context.getPassengerEmail());
            rec.setPassportNumber(context.getPassportNumber());
            rec.setSeatLabel("6A");
            rec.setSeatClass(SeatClass.ECONOMY);
            rec.setStatus("CONFIRMED");
            rec.setTotalAmount(context.getTotalPrice() != null ? context.getTotalPrice() : new BigDecimal("1522.00"));
            rec.setCreatedAt(LocalDateTime.now());

            store.put(pnr, rec);
            return rec;
        }

        @Override
        public Optional<BookingRecord> findByReference(String bookingRef) {
            return Optional.ofNullable(store.get(bookingRef));
        }

        @Override
        public List<BookingRecord> findByUserId(long userId) {
            return new ArrayList<>(store.values());
        }
    }

    @BeforeAll
    static void setUp() {
        MockFlightDao mockFlightDao = new MockFlightDao();
        mockSeatDao = new MockSeatDao();
        mockBookingDao = new MockBookingDao();

        java.time.Clock testClock = java.time.Clock.fixed(java.time.Instant.parse("2026-11-01T00:00:00Z"), java.time.ZoneOffset.UTC);
        FlightService flightService = new FlightService(mockFlightDao, testClock);
        SeatMapService seatMapService = new SeatMapService(mockSeatDao, mockFlightDao, testClock);
        BookingService bookingService = new BookingService(mockBookingDao, mockFlightDao, testClock);
        com.group2.flightres.model.User mockUser = new com.group2.flightres.model.User();
        mockUser.setUserId(7L);
        mockUser.setUsername("throttleuser");
        mockUser.setEmail("throttle@example.com");
        mockUser.setFullName("Throttle User");
        mockUser.setRole(com.group2.flightres.model.UserRole.CUSTOMER);
        mockUser.setActive(true);
        mockUser.setPasswordHash(com.group2.flightres.util.PasswordUtil.hash("Passw0rd!"));
        com.group2.flightres.dao.UserDao mockUserDao = new com.group2.flightres.dao.UserDao() {
            @Override
            public Optional<com.group2.flightres.model.User> findByUsername(String username) {
                return "throttleuser".equals(username) ? Optional.of(mockUser) : Optional.empty();
            }

            @Override
            public Optional<com.group2.flightres.model.User> findByUsernameOrEmail(String identifier) {
                if (identifier == null) return Optional.empty();
                String t = identifier.trim();
                return ("throttleuser".equalsIgnoreCase(t) || "throttle@example.com".equalsIgnoreCase(t))
                        ? Optional.of(mockUser)
                        : Optional.empty();
            }
        };
        AuthService authService = new AuthService(mockUserDao);

        webApp = new WebApplication(flightService, seatMapService, bookingService, authService);
        webApp.start(0);
        port = webApp.getApp().port();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    static void tearDown() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    @Test
    @DisplayName("End-to-end booking flow: View seat map -> Submit booking -> View confirmation e-ticket voucher")
    void testCompleteBookingFlow() throws IOException, InterruptedException {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        // 1. GET /flights/1/seats to inspect seat selection and extract CSRF token
        HttpRequest getSeatsReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/flights/1/seats"))
                .GET()
                .build();

        HttpResponse<String> seatsResp = client.send(getSeatsReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, seatsResp.statusCode());
        String seatsHtml = seatsResp.body();
        assertTrue(seatsHtml.contains("GRP 204"), "Page must display flight number");
        assertTrue(seatsHtml.contains("Passenger &amp; Checkout"), "Page must contain checkout form");

        // Extract CSRF token from HTML form: <input type="hidden" name="csrf_token" value="...">
        Pattern csrfPattern = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"");
        Matcher csrfMatcher = csrfPattern.matcher(seatsHtml);
        assertTrue(csrfMatcher.find(), "CSRF token must be present in the seats form");
        String csrfToken = csrfMatcher.group(1);
        assertNotNull(csrfToken);

        // 2. POST /book to reserve seat
        String postBody = "csrf_token=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8)
                + "&flightId=1"
                + "&flightNumber=" + URLEncoder.encode("GRP 204", StandardCharsets.UTF_8)
                + "&origin=" + URLEncoder.encode("Johannesburg (JNB)", StandardCharsets.UTF_8)
                + "&destination=" + URLEncoder.encode("Cape Town (CPT)", StandardCharsets.UTF_8)
                + "&departureDate=2026-11-02"
                + "&flightSeatId=101"
                + "&passengerName=" + URLEncoder.encode("David Miller", StandardCharsets.UTF_8)
                + "&passengerEmail=" + URLEncoder.encode("david@example.com", StandardCharsets.UTF_8)
                + "&passportNumber=A12345678"
                + "&dateOfBirth=1995-05-15";

        HttpRequest postBookReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/book"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(postBody))
                .build();

        HttpResponse<String> bookResp = client.send(postBookReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, bookResp.statusCode(), "Successful booking must redirect to confirmation");

        String redirectLocation = bookResp.headers().firstValue("Location").orElse("");
        assertTrue(redirectLocation.contains("/confirmation/"), "Must redirect to confirmation voucher");

        String ref = redirectLocation.substring(redirectLocation.lastIndexOf('/') + 1);
        assertEquals("WTEST1", ref);

        // 3. GET /confirmation/WTEST1 to view e-ticket voucher
        HttpRequest confirmReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + redirectLocation))
                .GET()
                .build();

        HttpResponse<String> confirmResp = client.send(confirmReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, confirmResp.statusCode());
        String confirmHtml = confirmResp.body();

        assertTrue(confirmHtml.contains("Booking Confirmed!"), "Confirmation page must show success header");
        assertTrue(confirmHtml.contains("WTEST1"), "Confirmation page must display booking reference");
        assertTrue(confirmHtml.contains("David Miller"), "Confirmation page must display passenger name");
        assertTrue(confirmHtml.contains("GRP 204"), "Confirmation page must display flight number");
        assertTrue(confirmHtml.contains("CONFIRMED"), "Confirmation page must show confirmed status");
        assertTrue(confirmHtml.contains("/confirmation/WTEST1/barcode.png"), "Confirmation page must embed barcode image");
        assertTrue(confirmHtml.contains("/confirmation/WTEST1/boarding-pass.pdf"), "Confirmation page must link boarding-pass PDF");

        // 4. GET /confirmation/WTEST1/barcode.png
        HttpResponse<byte[]> barcodeResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/confirmation/WTEST1/barcode.png")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, barcodeResp.statusCode());
        assertTrue(barcodeResp.headers().firstValue("Content-Type").orElse("").contains("image/png"));
        byte[] png = barcodeResp.body();
        assertTrue(png.length > 8 && png[0] == (byte) 0x89 && png[1] == 0x50 && png[2] == 0x4E && png[3] == 0x47,
                "Barcode endpoint must return valid PNG bytes");

        // 5. GET /confirmation/WTEST1/boarding-pass.pdf
        HttpResponse<byte[]> pdfResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/confirmation/WTEST1/boarding-pass.pdf")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pdfResp.statusCode());
        assertTrue(pdfResp.headers().firstValue("Content-Type").orElse("").contains("application/pdf"));
        byte[] pdf = pdfResp.body();
        assertTrue(pdf.length > 5 && new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"),
                "PDF endpoint must return valid PDF bytes");

        // 6. Unknown reference returns 404 on barcode & PDF
        HttpResponse<byte[]> badBarcode = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/confirmation/UNKNOWN99/barcode.png")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(404, badBarcode.statusCode());
        HttpResponse<byte[]> badPdf = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/confirmation/UNKNOWN99/boarding-pass.pdf")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(404, badPdf.statusCode());
    }

    @Test
    @DisplayName("CSRF rejection on POST /book with missing token creates no booking row")
    void testCsrfProtectionRejectsRequest() throws IOException, InterruptedException {
        int bookingsBefore = mockBookingDao.store.size();
        HttpClient client = HttpClient.newHttpClient();

        String invalidBody = "flightId=1&flightSeatId=101&passengerName=Test&passengerEmail=test@example.com&passportNumber=A12345678";
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/book"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(invalidBody))
                .build();

        HttpResponse<String> resp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, resp.statusCode(), "Missing CSRF token must result in 403 Forbidden");
        assertEquals(bookingsBefore, mockBookingDao.store.size(), "CSRF rejection must not execute booking handler");
    }

    @Test
    @DisplayName("Invalid passenger name re-renders seats form preserving inputs without raw error")
    void testValidationErrorPreservesInputsAndHighlightsField() throws IOException, InterruptedException {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();

        HttpResponse<String> seatsResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m = Pattern.compile("name=\"csrf_token\" value=\"([^\"]+)\"").matcher(seatsResp.body());
        assertTrue(m.find());
        String csrfToken = m.group(1);

        String badBody = "csrf_token=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8)
                + "&flightId=1&flightSeatId=101"
                + "&passengerName=" + URLEncoder.encode("1234!!!", StandardCharsets.UTF_8)
                + "&passengerEmail=" + URLEncoder.encode("keepme@example.com", StandardCharsets.UTF_8)
                + "&passportNumber=" + URLEncoder.encode("AB 12-3456", StandardCharsets.UTF_8)
                + "&dateOfBirth=1995-05-15";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(badBody)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        String html = resp.body();
        assertTrue(html.contains("keepme@example.com"), "Email input must be preserved on error");
        assertTrue(html.contains("AB 12-3456"), "Passport input must be preserved on error");
        assertTrue(html.contains("is-invalid"), "Failed field must be highlighted");
        assertTrue(!html.contains("Booking failed:"), "Raw 'Booking failed:' prefix must not appear");
    }

    @Test
    @DisplayName("Passport with spaces and hyphens is normalized before validation")
    void testPassportNormalizedBeforeValidation() throws IOException, InterruptedException {
        // Ensure seat 101 is available
        mockSeatDao.releaseSeat(101L);
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> seatsResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m = Pattern.compile("name=\"csrf_token\" value=\"([^\"]+)\"").matcher(seatsResp.body());
        assertTrue(m.find());
        String csrfToken = m.group(1);

        String postBody = "csrf_token=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8)
                + "&flightId=1&flightSeatId=101"
                + "&passengerName=" + URLEncoder.encode("Nomsa Dlamini", StandardCharsets.UTF_8)
                + "&passengerEmail=" + URLEncoder.encode("nomsa@example.com", StandardCharsets.UTF_8)
                + "&passportNumber=" + URLEncoder.encode("ab 12-3456", StandardCharsets.UTF_8)
                + "&dateOfBirth=1995-05-15";

        HttpResponse<String> bookResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(postBody)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(302, bookResp.statusCode());
        BookingRecord saved = mockBookingDao.findByReference("WTEST1").orElseThrow();
        assertEquals("AB123456", saved.getPassportNumber());
    }

    @Test
    @DisplayName("Lookup route /confirmation?reference=REF redirects to voucher")
    void testConfirmationLookupRedirect() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/confirmation?reference=WTEST1"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resp.statusCode());
        assertEquals("/confirmation/WTEST1", resp.headers().firstValue("Location").orElse(""));
    }

    @Test
    @DisplayName("Brute-force throttle returns HTTP 429 on 5th failed login and preserves success counter reset")
    void testLoginThrottleReturns429AndSuccessClearsCounter() throws IOException, InterruptedException {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> loginGet = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m = Pattern.compile("name=\"csrf_token\" value=\"([^\"]+)\"").matcher(loginGet.body());
        assertTrue(m.find());
        String csrf = m.group(1);

        // 1. Successful login clears counter
        String okBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + "&username=throttleuser&password=" + URLEncoder.encode("Passw0rd!", StandardCharsets.UTF_8);
        HttpResponse<String> okResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(okBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, okResp.statusCode());

        // Re-fetch CSRF token after session rotation
        HttpResponse<String> loginGet2 = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m2 = Pattern.compile("name=\"csrf_token\" value=\"([^\"]+)\"").matcher(loginGet2.body());
        assertTrue(m2.find());
        String csrf2 = m2.group(1);

        String badBody = "csrf_token=" + URLEncoder.encode(csrf2, StandardCharsets.UTF_8)
                + "&username=throttleuser&password=WrongPassword!";

        for (int i = 1; i <= 4; i++) {
            HttpResponse<String> failResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(badBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, failResp.statusCode(), "Attempt " + i + " should render 200 login error");
            assertTrue(failResp.body().contains("Incorrect username or password."));
        }

        // 5th failure triggers 429 with wait time
        HttpResponse<String> fifthResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(badBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(429, fifthResp.statusCode(), "5th failed attempt must return HTTP 429");
        assertTrue(fifthResp.body().contains("Too many failed login attempts."));
        assertTrue(fifthResp.body().contains("minute"));

        // 6th attempt even with right password stays blocked at 429
        String retryGoodBody = "csrf_token=" + URLEncoder.encode(csrf2, StandardCharsets.UTF_8)
                + "&username=throttleuser&password=" + URLEncoder.encode("Passw0rd!", StandardCharsets.UTF_8);
        HttpResponse<String> sixthResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(retryGoodBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(429, sixthResp.statusCode(), "Locked user must still receive 429 within window");
    }
}
