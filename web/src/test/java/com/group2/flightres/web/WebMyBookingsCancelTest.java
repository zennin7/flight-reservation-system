package com.group2.flightres.web;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.CancelResult;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BoardingPassVerificationService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.CancellationPolicy;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebMyBookingsCancelTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private WebApplication webApp;

    @AfterEach
    void tearDown() {
        if (webApp != null) webApp.stop();
    }

    private static final class TestBookingStore implements BookingDao {
        final Map<String, BookingRecord> byRef = new ConcurrentHashMap<>();
        final Map<Long, List<BookingSummary>> customSummaries = new ConcurrentHashMap<>();
        final AtomicBoolean simulateListError = new AtomicBoolean(false);
        final FlightSeat seat48h = new FlightSeat();
        final FlightSeat seat12h = new FlightSeat();

        TestBookingStore() {
            seat48h.setFlightSeatId(201L);
            seat48h.setFlightId(10L);
            seat48h.setSeatRow(6);
            seat48h.setSeatCol('A');
            seat48h.setSeatClass(SeatClass.ECONOMY);
            seat48h.setStatus(SeatStatus.BOOKED);

            seat12h.setFlightSeatId(202L);
            seat12h.setFlightId(11L);
            seat12h.setSeatRow(7);
            seat12h.setSeatCol('B');
            seat12h.setSeatClass(SeatClass.ECONOMY);
            seat12h.setStatus(SeatStatus.BOOKED);

            // User 1: one cancellable (>48h), one non-cancellable (<12h), one past (-72h)
            byRef.put("OK48HR", makeRec(1L, "OK48HR", 1L, 10L, NOW.plusSeconds(48 * 3600L), "CONFIRMED", "6A"));
            byRef.put("SOON12", makeRec(2L, "SOON12", 1L, 11L, NOW.plusSeconds(12 * 3600L), "CONFIRMED", "7B"));
            byRef.put("PAST01", makeRec(4L, "PAST01", 1L, 12L, NOW.minusSeconds(72 * 3600L), "CONFIRMED", "5C"));
            // User 2: another user's booking
            byRef.put("USER2A", makeRec(3L, "USER2A", 2L, 10L, NOW.plusSeconds(72 * 3600L), "CONFIRMED", "8C"));
        }

        private static BookingRecord makeRec(long id, String ref, long userId, long flightId, Instant dep, String status, String seat) {
            BookingRecord r = new BookingRecord();
            r.setBookingId(id);
            r.setBookingReference(ref);
            r.setUserId(userId);
            r.setFlightId(flightId);
            r.setFlightNumber("GRP 301");
            r.setOrigin("Johannesburg (JNB)");
            r.setDestination("Cape Town (CPT)");
            r.setDepartureDate("2026-11-12");
            r.setDepartureTime("10:00");
            r.setArrivalTime("12:10");
            r.setDepartureInstant(dep);
            r.setArrivalInstant(dep.plusSeconds(7800));
            r.setSeatLabel(seat);
            r.setSeatClass(SeatClass.ECONOMY);
            r.setPassengerName("Alice Mokoena");
            r.setPassengerEmail("alice@example.com");
            r.setStatus(status);
            r.setTotalAmount(new BigDecimal("1522.00"));
            return r;
        }

        @Override
        public BookingRecord createBooking(BookingContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BookingRecord> findByReference(String bookingRef) {
            return Optional.ofNullable(byRef.get(bookingRef));
        }

        @Override
        public List<BookingRecord> findByUserId(long userId) {
            return byRef.values().stream().filter(b -> b.getUserId() == userId).toList();
        }

        @Override
        public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
            if (simulateListError.get()) {
                throw new RuntimeException("Simulated DB read error");
            }
            if (customSummaries.containsKey(userId)) {
                return customSummaries.get(userId);
            }
            List<BookingSummary> out = new ArrayList<>();
            for (BookingRecord b : findByUserId(userId)) {
                String summaryStatus = "CANCELLED".equalsIgnoreCase(b.getStatus())
                        ? "CANCELLED"
                        : (b.getDepartureInstant().isAfter(clock.instant()) ? "UPCOMING" : "COMPLETED");
                out.add(new BookingSummary(
                        b.getBookingId(), b.getBookingReference(), b.getUserId(), b.getFlightNumber(),
                        "JNB", "Johannesburg", b.getDepartureInstant(), SAST,
                        "CPT", "Cape Town", b.getArrivalInstant(), SAST,
                        summaryStatus, b.getStatus(), List.of(b.getSeatLabel()),
                        b.getTotalAmount(), b.getPassengerName(), b.getPassengerEmail(), "Airbus A320"
                ));
            }
            return out;
        }

        @Override
        public CancelResult cancelBookingForUser(long userId, String bookingReference, CancellationPolicy policy, PaymentService paymentService) {
            BookingRecord rec = byRef.get(bookingReference);
            if (rec == null) throw new NoSuchElementException("Not found");
            if (rec.getUserId() != userId) throw new SecurityException("Not owner");
            if ("CANCELLED".equalsIgnoreCase(rec.getStatus())) {
                return new CancelResult(false, true, bookingReference, BigDecimal.ZERO, "REF-TXN-" + bookingReference, "Already cancelled");
            }
            var decision = policy.evaluate(rec);
            if (!decision.allowed()) {
                throw new IllegalStateException(decision.reason());
            }
            String refundRef;
            try {
                refundRef = paymentService.refundWithReference("TXN-" + bookingReference);
            } catch (RuntimeException ex) {
                throw new com.group2.flightres.exception.RefundFailedException("Refund failed", ex);
            }
            rec.setStatus("CANCELLED");
            if ("OK48HR".equals(bookingReference)) {
                seat48h.setStatus(SeatStatus.AVAILABLE);
            }
            return new CancelResult(true, false, bookingReference, decision.refundAmount(), refundRef, "Cancelled");
        }
    }

    private record Env(String baseUrl, TestBookingStore store, AtomicBoolean failNextRefund,
                       AtomicInteger refundCalls, BoardingPassVerificationService verifySvc) {}

    private Env startApp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        TestBookingStore store = new TestBookingStore();
        AtomicBoolean failRefund = new AtomicBoolean(false);
        AtomicInteger refundCalls = new AtomicInteger(0);

        FlightDao flightDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String o, String d, java.time.LocalDate dt) {
                return List.of();
            }

            @Override
            public Optional<FlightSearchResult> findById(long id) {
                return Optional.empty();
            }
        };
        SeatDao seatDao = new SeatDao() {
            @Override
            public List<FlightSeat> getSeatMapForFlight(long flightId) { return List.of(); }

            @Override
            public boolean releaseSeat(long flightSeatId) { return false; }

            @Override
            public boolean bookSeat(long flightSeatId) { return false; }

            @Override
            public int releaseExpiredHolds() { return 0; }

            @Override
            public Optional<FlightSeat> findSeatById(long id) { return Optional.empty(); }

            @Override
            public Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
                return Map.of(SeatClass.ECONOMY, BigDecimal.ONE, SeatClass.FIRST, new BigDecimal("3.50"));
            }
        };

        PaymentService gateway = new SimulatedPaymentGateway() {
            @Override
            public void refund(String reference) {
                if (failRefund.get()) {
                    throw new IllegalStateException("Refund outage");
                }
                refundCalls.incrementAndGet();
                super.refund(reference);
            }
        };

        User alice = new User();
        alice.setUserId(1L);
        alice.setUsername("alice");
        alice.setFullName("Alice Mokoena");
        alice.setEmail("alice@example.com");
        alice.setRole(UserRole.CUSTOMER);
        alice.setActive(true);
        alice.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        User emptyUser = new User();
        emptyUser.setUserId(99L);
        emptyUser.setUsername("emptyuser");
        emptyUser.setFullName("Empty User");
        emptyUser.setEmail("empty@example.com");
        emptyUser.setRole(UserRole.CUSTOMER);
        emptyUser.setActive(true);
        emptyUser.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        AuthService authService = new AuthService(new UserDao() {
            @Override
            public Optional<User> findByUsername(String u) {
                return findByUsernameOrEmail(u);
            }

            @Override
            public Optional<User> findByUsernameOrEmail(String id) {
                if ("alice".equalsIgnoreCase(id)) return Optional.of(alice);
                if ("emptyuser".equalsIgnoreCase(id)) return Optional.of(emptyUser);
                return Optional.empty();
            }
        });

        BookingService bookingService = new BookingService(store, flightDao, new PricingService(seatDao, clock), clock);
        BoardingPassVerificationService verifySvc = new BoardingPassVerificationService(
                bookingService, "cancel-test-secret-32-bytes-key!!".getBytes(StandardCharsets.UTF_8));
        webApp = new WebApplication(
                new FlightService(flightDao, clock),
                new SeatMapService(seatDao, flightDao, clock),
                bookingService,
                authService,
                new LoginThrottle(clock),
                new RegistrationService(),
                new RegistrationThrottle(clock),
                gateway,
                new PaymentThrottle(clock),
                verifySvc,
                new VerifyThrottle(clock));
        webApp.start(0);
        return new Env("http://localhost:" + webApp.getApp().port(), store, failRefund, refundCalls, verifySvc);
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static String extractCsrf(String html) {
        Matcher m = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"").matcher(html);
        assertTrue(m.find(), "CSRF token missing");
        return m.group(1);
    }

    private static String loginViaNext(HttpClient client, String baseUrl, String nextPath, String username) throws Exception {
        HttpResponse<String> getLogin = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login?next=" + URLEncoder.encode(nextPath, StandardCharsets.UTF_8))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrf = extractCsrf(getLogin.body());
        String postBody = "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + "&next=" + URLEncoder.encode(nextPath, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode("Passw0rd!", StandardCharsets.UTF_8);
        HttpResponse<String> postResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(postBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, postResp.statusCode());
        return postResp.headers().firstValue("Location").orElse("");
    }

    @Test
    @DisplayName("My Bookings requires login, supports empty/error/105-row states, enforces service ownership and cancel/refund flow")
    void fullMyBookingsAndCancelLifecycle() throws Exception {
        Env env = startApp();
        HttpClient alice = newClient();

        // 1. Anonymous GET /my-bookings redirects to login?next=/my-bookings
        HttpResponse<String> anonResp = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/my-bookings")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, anonResp.statusCode());
        assertEquals("/login?next=/my-bookings", anonResp.headers().firstValue("Location").orElse(""));

        // Login with unsafe next=//evil.com falls back to /
        HttpClient checkRedirect = newClient();
        assertEquals("/", loginViaNext(checkRedirect, env.baseUrl(), "//evil.com", "alice"));

        // Login with /my-bookings lands on /my-bookings
        assertEquals("/my-bookings", loginViaNext(alice, env.baseUrl(), "/my-bookings", "alice"));

        // 2. Populated view shows both OK48HR (with Cancel) and SOON12 (no Cancel) plus board-5 live preview hooks
        HttpResponse<String> myResp = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/my-bookings")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, myResp.statusCode());
        assertTrue(myResp.body().contains("Upcoming Flights"));
        assertTrue(myResp.body().contains("Past Flights"));
        assertTrue(myResp.body().contains("BOARDING PASS"));
        assertTrue(myResp.body().contains("Need another trip?"));
        assertTrue(myResp.body().contains("OK48HR"));
        assertTrue(myResp.body().contains("/bookings/OK48HR/calendar.ics"));
        assertTrue(myResp.body().contains("PAST01"));
        assertTrue(myResp.body().contains("/bookings/OK48HR/cancel"));
        assertTrue(myResp.body().contains("SOON12"));
        assertFalse(myResp.body().contains("/bookings/SOON12/cancel"), "<24h booking must not offer Cancel CTA");

        // 2b. Owner-only iCalendar (.ics) endpoint enforcement
        HttpResponse<String> anonIcs = newClient().send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/OK48HR/calendar.ics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, anonIcs.statusCode());
        assertTrue(anonIcs.headers().firstValue("Location").orElse("").contains("/login"));

        HttpResponse<String> missingIcs = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/NOPE99/calendar.ics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missingIcs.statusCode());

        HttpResponse<String> otherIcs = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/USER2A/calendar.ics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, otherIcs.statusCode());

        HttpResponse<String> okIcs = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/OK48HR/calendar.ics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, okIcs.statusCode());
        assertTrue(okIcs.headers().firstValue("Content-Type").orElse("").contains("text/calendar"));
        assertTrue(okIcs.headers().firstValue("Content-Disposition").orElse("").contains("sky-royal-OK48HR.ics"));
        assertTrue(okIcs.body().contains("BEGIN:VCALENDAR"));
        assertTrue(okIcs.body().contains("OK48HR"));
        assertTrue(okIcs.body().contains("END:VCALENDAR"));

        // 3. Another user's booking returns 403 via service ownership
        HttpResponse<String> otherGet = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/USER2A/cancel")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, otherGet.statusCode());

        // 4. Cancel <24h (SOON12) returns 409 blocked
        HttpResponse<String> cancel12Get = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/SOON12/cancel")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrf = extractCsrf(alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/OK48HR/cancel")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
        assertTrue(cancel12Get.body().contains("24 hours"));

        HttpResponse<String> cancel12Post = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/SOON12/cancel"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, cancel12Post.statusCode());

        // 5. Refund outage rolls back and keeps OK48HR confirmed with 502 error message
        env.failNextRefund().set(true);
        HttpResponse<String> failedRefundPost = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/OK48HR/cancel"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(502, failedRefundPost.statusCode());
        assertTrue(failedRefundPost.body().contains("remain active"));
        assertEquals("CONFIRMED", env.store().byRef.get("OK48HR").getStatus());
        assertEquals(SeatStatus.BOOKED, env.store().seat48h.getStatus());

        // 6. Valid cancel >=24h cancels, frees seat, refunds, returns 410 on PDF, shows CANCELLED banner and /verify CANCELLED
        env.failNextRefund().set(false);
        HttpResponse<String> okCancelPost = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/OK48HR/cancel"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, okCancelPost.statusCode());
        assertEquals("/my-bookings", okCancelPost.headers().firstValue("Location").orElse(""));
        assertEquals("CANCELLED", env.store().byRef.get("OK48HR").getStatus());
        assertEquals(SeatStatus.AVAILABLE, env.store().seat48h.getStatus());
        assertEquals(1, env.refundCalls().get());

        // PDF & Calendar return 410 after cancel
        HttpResponse<byte[]> cancelledPdf = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/confirmation/OK48HR/boarding-pass.pdf")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(410, cancelledPdf.statusCode());

        HttpResponse<String> cancelledIcs = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/OK48HR/calendar.ics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(410, cancelledIcs.statusCode());

        // Confirmation page shows CANCELLED banner and hides PDF
        HttpResponse<String> cancelledConf = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/confirmation/OK48HR")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancelledConf.statusCode());
        assertTrue(cancelledConf.body().contains("Booking Cancelled"));
        assertFalse(cancelledConf.body().contains("Download PDF"));

        // Verify endpoint shows CANCELLED
        String verifyUrl = env.verifySvc().buildVerificationUrl(env.baseUrl(), env.store().byRef.get("OK48HR"));
        HttpResponse<String> verifyCancelled = newClient().send(
                HttpRequest.newBuilder().uri(URI.create(verifyUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, verifyCancelled.statusCode());
        assertTrue(verifyCancelled.body().contains("Not valid for travel"));

        // My Bookings now lists OK48HR under Cancelled & Refunded
        HttpResponse<String> afterCancelMy = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/my-bookings")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, afterCancelMy.statusCode());
        assertTrue(afterCancelMy.body().contains("Cancelled &amp; Refunded"));
        assertTrue(afterCancelMy.body().contains("OK48HR"));

        // Second cancel shows already cancelled and never refunds twice
        HttpResponse<String> secondCancel = alice.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/bookings/OK48HR/cancel"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, secondCancel.statusCode());
        assertTrue(secondCancel.body().toLowerCase().contains("already cancelled"));
        assertEquals(1, env.refundCalls().get());

        // 7. Empty state (user 99)
        HttpClient emptyClient = newClient();
        loginViaNext(emptyClient, env.baseUrl(), "/my-bookings", "emptyuser");
        HttpResponse<String> emptyResp = emptyClient.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/my-bookings")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, emptyResp.statusCode());
        assertTrue(emptyResp.body().contains("do not have any bookings yet"));

        // 8. 105-row state renders cleanly
        List<BookingSummary> bigList = new ArrayList<>();
        for (int i = 1; i <= 105; i++) {
            bigList.add(new BookingSummary(
                    i, String.format("BK%04d", i), 99L, "GRP 500",
                    "JNB", "Johannesburg", NOW.plusSeconds(72L * 3600L + i * 60L), SAST,
                    "CPT", "Cape Town", NOW.plusSeconds(74L * 3600L + i * 60L), SAST,
                    "UPCOMING", "CONFIRMED", List.of("6A"), new BigDecimal("1400.00"),
                    "Empty User", "empty@example.com", "Airbus A320"
            ));
        }
        env.store().customSummaries.put(99L, bigList);
        HttpResponse<String> largeResp = emptyClient.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/my-bookings")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, largeResp.statusCode());
        assertTrue(largeResp.body().contains("BK0105"));
        assertTrue(largeResp.body().contains("105 total booking(s)"));

        // 9. Error state renders clean banner without 500 stack trace
        env.store().simulateListError.set(true);
        HttpResponse<String> errResp = emptyClient.send(
                HttpRequest.newBuilder().uri(URI.create(env.baseUrl() + "/my-bookings")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, errResp.statusCode());
        assertTrue(errResp.body().contains("Could not load bookings"));
    }
}
