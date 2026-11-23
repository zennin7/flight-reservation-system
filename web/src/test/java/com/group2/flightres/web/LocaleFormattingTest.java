package com.group2.flightres.web;

import com.group2.flightres.dao.AdminDashboardDao;
import com.group2.flightres.dao.AirportDao;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.CabinLoadBreakdown;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.AdminDashboardData.Range;
import com.group2.flightres.model.Airport;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.RouteSummary;
import com.group2.flightres.model.SeatClass;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleFormattingTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"");
    private WebApplication webApp;

    @AfterEach
    void tearDown() {
        if (webApp != null) webApp.stop();
    }

    @Test
    @DisplayName("Admin dashboard, Flights search, Home, and Confirmation use locale-independent dot decimals even under en-ZA and fr-FR JVM locales")
    void allPagesFormatMoneyAndPercentagesWithDotDecimalsUnderCommaLocales() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T08:00:00Z"), ZoneOffset.UTC);
        User admin = new User();
        admin.setUserId(1L);
        admin.setUsername("admin");
        admin.setFullName("David Zenn");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        UserDao userDao = new UserDao() {
            @Override public Optional<User> findByUsername(String u) { return findByUsernameOrEmail(u); }
            @Override public Optional<User> findByUsernameOrEmail(String id) {
                return "admin".equals(id) ? Optional.of(admin) : Optional.empty();
            }
        };

        FlightSearchResult fl = new FlightSearchResult(
                10L, "SR 305", "JNB", "O. R. Tambo", "Johannesburg", SAST,
                "CPT", "Cape Town Intl", "Cape Town", SAST,
                Instant.parse("2026-11-24T07:15:00Z"), Instant.parse("2026-11-24T09:20:00Z"),
                new BigDecimal("1180.00"), "Airbus A320", 149, 137, 12);

        FlightDao flightDao = new FlightDao() {
            @Override public List<FlightSearchResult> searchFlights(String o, String d, LocalDate dt) { return List.of(fl); }
            @Override public Optional<FlightSearchResult> findById(long id) { return Optional.of(fl); }
            @Override public List<RouteSummary> findRouteSummaries(Instant minDep) {
                return List.of(new RouteSummary("JNB", "Johannesburg", "South Africa", "CPT", "Cape Town", "South Africa", 125, new BigDecimal("1180.00"), 12));
            }
        };
        SeatDao seatDao = new SeatDao() {
            @Override public List<FlightSeat> getSeatMapForFlight(long flightId) { return List.of(); }
            @Override public boolean releaseSeat(long flightSeatId) { return false; }
            @Override public boolean bookSeat(long flightSeatId) { return false; }
            @Override public int releaseExpiredHolds() { return 0; }
            @Override public Optional<FlightSeat> findSeatById(long flightSeatId) { return Optional.empty(); }
            @Override public Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
                return Map.of(SeatClass.ECONOMY, BigDecimal.ONE, SeatClass.FIRST, new BigDecimal("3.50"));
            }
        };

        BookingRecord rec = new BookingRecord();
        rec.setBookingReference("LOC001");
        rec.setFlightId(10L);
        rec.setFlightNumber("SR 305");
        rec.setOrigin("Johannesburg (JNB)");
        rec.setDestination("Cape Town (CPT)");
        rec.setDepartureDate("2026-11-24");
        rec.setDepartureTime("09:15");
        rec.setArrivalTime("11:20");
        rec.setDepartureInstant(fl.getDeparture());
        rec.setArrivalInstant(fl.getArrival());
        rec.setSeatLabel("9A");
        rec.setSeatClass(SeatClass.ECONOMY);
        rec.setPassengerName("Thandi Nkosi");
        rec.setStatus("CONFIRMED");
        rec.setTotalAmount(new BigDecimal("1180.00"));
        rec.setUserId(1L);

        BookingDao bookingDao = new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext c) { throw new UnsupportedOperationException(); }
            @Override public Optional<BookingRecord> findByReference(String ref) { return "LOC001".equals(ref) ? Optional.of(rec) : Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(rec); }
        };

        AdminDashboardDao adminDao = (clk, range, lowPct) -> new AdminDashboardData(
                Range.SEVEN_DAYS, 10L, 0.0, new BigDecimal("1180.00"), 0.0, 1L, 0.0, 0L, 0.0,
                List.of(), new CabinLoadBreakdown(1, 0, 161, 162, 0, 0),
                List.of(),
                List.of(
                        new FlightLoadFactor(10L, "SR 305", "JNB", "CPT", fl.getDeparture(), SAST, 0, 162, 162, 0.0, "Scheduled"),
                        new FlightLoadFactor(11L, "SR 301", "JNB", "CPT", fl.getDeparture(), SAST, 12, 162, 150, 7.4, "Scheduled")
                ),
                List.of(), List.of()
        );

        FlightService flightSvc = new FlightService(flightDao, Collections::emptyList, new PricingService(seatDao, clock), clock);
        webApp = new WebApplication(
                flightSvc, new SeatMapService(seatDao, flightDao, clock), new BookingService(bookingDao, flightDao, clock),
                new AuthService(userDao), new LoginThrottle(clock), new RegistrationService(), new RegistrationThrottle(clock),
                new SimulatedPaymentGateway(), new PaymentThrottle(clock),
                new BoardingPassVerificationService(new BookingService(bookingDao, flightDao, clock)),
                new VerifyThrottle(clock), new AdminDashboardService(adminDao, clock));
        webApp.start(0);
        String baseUrl = "http://localhost:" + webApp.getApp().port();

        Locale previous = Locale.getDefault();
        try {
            for (Locale commaLocale : new Locale[]{Locale.forLanguageTag("en-ZA"), Locale.FRANCE}) {
                Locale.setDefault(commaLocale);
                CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                HttpClient http = HttpClient.newBuilder().cookieHandler(cm).followRedirects(HttpClient.Redirect.NEVER).build();

                // Login as admin
                String loginHtml = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/login")).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                Matcher m = CSRF_PATTERN.matcher(loginHtml);
                assertTrue(m.find());
                String body = "csrf_token=" + URLEncoder.encode(m.group(1), StandardCharsets.UTF_8) + "&username=admin&password=" + URLEncoder.encode("Passw0rd!", StandardCharsets.UTF_8);
                assertEquals(302, http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode());

                String adminHtml = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/admin")).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                assertTrue(adminHtml.contains("R 1,180.00"), "Admin revenue must use dot decimals");
                assertTrue(adminHtml.contains("0.0%"), "Admin 0.0% load factor must use dot decimal under " + commaLocale);
                assertTrue(adminHtml.contains("7.4%"), "Admin 7.4% load factor must use dot decimal under " + commaLocale);
                assertFalse(adminHtml.contains("0,0%"), "Must never render comma-decimal 0,0% under " + commaLocale);
                assertFalse(adminHtml.contains("7,4%"), "Must never render comma-decimal 7,4% under " + commaLocale);

                String flightsHtml = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=JNB&destination=CPT&date=2026-11-24")).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                assertTrue(flightsHtml.contains("R 1,180.00"), "Flights fare must format as R 1,180.00 under " + commaLocale);
                assertTrue(flightsHtml.contains("Tue 24 Nov"), "Flights departure must show friendly date Tue 24 Nov");
                assertTrue(flightsHtml.contains("Search flights"), "Flights search button must contain Search flights label");
                assertTrue(flightsHtml.contains("value=\"ECONOMY\" class=\"filter-cabin-check\" checked"),
                        "Economy cabin filter should be pre-checked by default");

                String confHtml = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/confirmation/LOC001")).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                assertTrue(confHtml.contains("R 1,180.00"), "Confirmation total must format as R 1,180.00 under " + commaLocale);
            }
        } finally {
            Locale.setDefault(previous);
        }
    }
}
