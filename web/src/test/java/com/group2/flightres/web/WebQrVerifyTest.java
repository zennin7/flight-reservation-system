package com.group2.flightres.web;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
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
import com.group2.flightres.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebQrVerifyTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final Instant DEP_INSTANT = Instant.parse("2026-11-20T06:30:00Z");
    private static final Instant ARR_INSTANT = Instant.parse("2026-11-20T08:40:00Z");
    private static final FlightSearchResult FLIGHT = new FlightSearchResult(
            1L, "GRP 204", "JNB", "O. R. Tambo International Airport", "Johannesburg", SAST,
            "CPT", "Cape Town International Airport", "Cape Town", SAST,
            DEP_INSTANT, ARR_INSTANT, new BigDecimal("1200.00"), "Airbus A320", 149, 137, 12
    );

    private WebApplication webApp;

    @AfterEach
    void tearDown() {
        if (webApp != null) webApp.stop();
    }

    @Test
    @DisplayName("Owner-only /qr.png decodes to public /verify URL; unknown vs bad sig return identical 404; cancelled shows Not valid; 31st request -> 429")
    void qrAndVerifyEndpointsEnforceOwnershipSecurityAndThrottle() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
        Map<String, BookingRecord> bookings = new ConcurrentHashMap<>();

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setFlightId(1L);
        seat.setSeatRow(6);
        seat.setSeatCol('A');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setCalculatedPrice(new BigDecimal("1200.00"));

        FlightDao flightDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String o, String d, java.time.LocalDate dt) {
                return List.of(FLIGHT);
            }

            @Override
            public Optional<FlightSearchResult> findById(long flightId) {
                return flightId == 1L ? Optional.of(FLIGHT) : Optional.empty();
            }
        };

        SeatDao seatDao = new SeatDao() {
            @Override
            public List<FlightSeat> getSeatMapForFlight(long flightId) {
                return List.of(seat);
            }

            @Override
            public Optional<Instant> holdSeat(long id, int secs, String owner) {
                seat.setStatus(SeatStatus.HELD);
                seat.setHeldBy(owner);
                seat.setHeldUntil(fixedClock.instant().plusSeconds(secs));
                return Optional.of(seat.getHeldUntil());
            }

            @Override
            public boolean releaseSeat(long id) {
                seat.setStatus(SeatStatus.AVAILABLE);
                return true;
            }

            @Override
            public boolean bookSeat(long id) {
                seat.setStatus(SeatStatus.BOOKED);
                return true;
            }

            @Override
            public int releaseExpiredHolds() {
                return 0;
            }

            @Override
            public Optional<FlightSeat> findSeatById(long id) {
                return Optional.of(seat);
            }

            @Override
            public Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
                return Map.of(SeatClass.ECONOMY, BigDecimal.ONE, SeatClass.FIRST, new BigDecimal("3.50"));
            }
        };

        BookingDao bookingDao = new BookingDao() {
            @Override
            public BookingRecord createBooking(BookingContext ctx) {
                String pnr = "QRTEST1";
                ctx.setBookingReference(pnr);
                BookingRecord rec = new BookingRecord();
                rec.setBookingId(42L);
                rec.setBookingReference(pnr);
                rec.setFlightId(1L);
                rec.setFlightNumber("GRP 204");
                rec.setOrigin("Johannesburg (JNB)");
                rec.setDestination("Cape Town (CPT)");
                rec.setDepartureDate("2026-11-20");
                rec.setDepartureTime("08:30");
                rec.setArrivalTime("10:40");
                rec.setSeatLabel("6A");
                rec.setSeatClass(SeatClass.ECONOMY);
                rec.setPassengerName("Naledi Sithole");
                rec.setPassengerEmail(ctx.getPassengerEmail());
                rec.setStatus("CONFIRMED");
                rec.setTotalAmount(ctx.getTotalPrice());
                rec.setUserId(1L);
                bookings.put(pnr, rec);
                return rec;
            }

            @Override
            public Optional<BookingRecord> findByReference(String ref) {
                return Optional.ofNullable(bookings.get(ref));
            }

            @Override
            public List<BookingRecord> findByUserId(long userId) {
                return List.of();
            }
        };

        User owner = new User();
        owner.setUserId(1L);
        owner.setUsername("naledi");
        owner.setFullName("Naledi Sithole");
        owner.setEmail("naledi@example.com");
        owner.setRole(UserRole.CUSTOMER);
        owner.setActive(true);
        owner.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        User otherUser = new User();
        otherUser.setUserId(2L);
        otherUser.setUsername("other");
        otherUser.setFullName("Other User");
        otherUser.setEmail("other@example.com");
        otherUser.setRole(UserRole.CUSTOMER);
        otherUser.setActive(true);
        otherUser.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        AuthService authService = new AuthService(new UserDao() {
            @Override
            public Optional<User> findByUsername(String username) {
                return findByUsernameOrEmail(username);
            }

            @Override
            public Optional<User> findByUsernameOrEmail(String id) {
                if ("naledi".equalsIgnoreCase(id)) return Optional.of(owner);
                if ("other".equalsIgnoreCase(id)) return Optional.of(otherUser);
                return Optional.empty();
            }
        });

        BookingService bookingService = new BookingService(bookingDao, flightDao, new PricingService(seatDao, fixedClock), fixedClock);
        BoardingPassVerificationService verifySvc = new BoardingPassVerificationService(
                bookingService, "web-qr-verify-test-secret-32-bytes".getBytes(StandardCharsets.UTF_8));
        webApp = new WebApplication(
                new FlightService(flightDao, fixedClock),
                new SeatMapService(seatDao, flightDao, fixedClock),
                bookingService,
                authService,
                new LoginThrottle(fixedClock),
                new RegistrationService(),
                new RegistrationThrottle(fixedClock),
                new SimulatedPaymentGateway(),
                new PaymentThrottle(fixedClock),
                verifySvc,
                new VerifyThrottle(fixedClock));
        webApp.start(0);
        String baseUrl = "http://localhost:" + webApp.getApp().port();

        // Owner books ticket
        HttpClient ownerClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();

        String seatsHtml = ownerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        Matcher cm = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"").matcher(seatsHtml);
        assertTrue(cm.find());
        String csrf = cm.group(1);

        ownerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/book"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                                        + "&flightId=1&flightSeatId=101&passengerName=Naledi+Sithole"
                                        + "&passengerEmail=naledi%40example.com&passportNumber=A1234567&dateOfBirth=1994-05-05")).build(),
                HttpResponse.BodyHandlers.ofString());

        HttpResponse<String> payResp = ownerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/pay"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "csrf_token=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                                        + "&cardNumber=4242+4242+4242+4242&cardholderName=Naledi+Sithole&expiry=12%2F29&cvv=123")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, payResp.statusCode());

        // Confirmation page embeds QR for owner
        HttpResponse<String> confirmResp = ownerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/confirmation/QRTEST1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(confirmResp.body().contains("/confirmation/QRTEST1/qr.png"));

        // Owner downloads /qr.png (200) and decodes via ZXing
        HttpResponse<byte[]> qrResp = ownerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/confirmation/QRTEST1/qr.png")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, qrResp.statusCode());
        BufferedImage qrImg = ImageIO.read(new ByteArrayInputStream(qrResp.body()));
        assertTrue(qrImg.getWidth() >= 320);
        LuminanceSource src = new BufferedImageLuminanceSource(qrImg);
        Result decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(src)));
        String verifyUrl = decoded.getText();
        assertTrue(verifyUrl.contains("/verify/QRTEST1?sig="));

        // Non-owner cannot fetch /qr.png -> 403
        HttpClient strangerClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<byte[]> forbiddenQr = strangerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/confirmation/QRTEST1/qr.png")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(403, forbiddenQr.statusCode());

        // Public /verify (no login!) returns VALID + masked name + security headers
        HttpResponse<String> validVerify = strangerClient.send(
                HttpRequest.newBuilder().uri(URI.create(verifyUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, validVerify.statusCode());
        assertTrue(validVerify.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertTrue(validVerify.headers().firstValue("X-Robots-Tag").orElse("").contains("noindex"));
        assertTrue(validVerify.body().contains("VALID FOR TRAVEL"));
        assertTrue(validVerify.body().contains("N***** S*****"));
        assertFalse(validVerify.body().contains("Naledi Sithole"));

        // Unknown PNR vs tampered signature return 404 with BYTE-IDENTICAL bodies
        String tamperedUrl = verifyUrl.substring(0, verifyUrl.length() - 2) + (verifyUrl.endsWith("AA") ? "BB" : "AA");
        HttpResponse<String> badSigResp = strangerClient.send(
                HttpRequest.newBuilder().uri(URI.create(tamperedUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> unknownPnrResp = strangerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/verify/NOPE999?sig=invalid")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, badSigResp.statusCode());
        assertEquals(404, unknownPnrResp.statusCode());
        assertEquals(badSigResp.body(), unknownPnrResp.body(), "Unknown PNR and bad signature must return byte-identical 404 body");

        // Cancelled pass shows CANCELLED + Not valid for travel
        bookings.get("QRTEST1").setStatus("CANCELLED");
        HttpResponse<String> cancelledVerify = strangerClient.send(
                HttpRequest.newBuilder().uri(URI.create(verifyUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancelledVerify.statusCode());
        assertTrue(cancelledVerify.body().contains("Not valid for travel"));

        // We used 4 verify requests so far; send 26 more to reach 30, then 31st -> 429
        for (int i = 5; i <= 30; i++) {
            HttpResponse<String> r = strangerClient.send(
                    HttpRequest.newBuilder().uri(URI.create(verifyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r.statusCode());
        }
        HttpResponse<String> thirtyFirst = strangerClient.send(
                HttpRequest.newBuilder().uri(URI.create(verifyUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(429, thirtyFirst.statusCode());
    }
}
