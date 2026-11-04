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

    // --- Mock DAOs ---

    private static class MockFlightDao implements FlightDao {
        @Override
        public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
            return List.of(new FlightSearchResult(1L, "GRP 204", "06:30", "08:40", "2h 10m",
                    new BigDecimal("1200"), "R 1,200"));
        }

        @Override
        public Optional<FlightSearchResult> findById(long flightId) {
            if (flightId == 1L) {
                return Optional.of(new FlightSearchResult(1L, "GRP 204", "06:30", "08:40", "2h 10m",
                        new BigDecimal("1200"), "R 1,200"));
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
            rec.setDepartureTime("06:30");
            rec.setArrivalTime("08:40");
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
        MockSeatDao mockSeatDao = new MockSeatDao();
        MockBookingDao mockBookingDao = new MockBookingDao();

        FlightService flightService = new FlightService(mockFlightDao);
        SeatMapService seatMapService = new SeatMapService(mockSeatDao);
        BookingService bookingService = new BookingService(mockBookingDao);
        AuthService authService = new AuthService();

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
                + "&passportNumber=A12345678";

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
    }

    @Test
    @DisplayName("CSRF rejection on POST /book with missing token")
    void testCsrfProtectionRejectsRequest() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        String invalidBody = "flightId=1&flightSeatId=101&passengerName=Test";
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/book"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(invalidBody))
                .build();

        HttpResponse<String> resp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, resp.statusCode(), "Missing CSRF token must result in 403 Forbidden");
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
}
