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
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
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

class WebShellAndAdminTest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"");
    private WebApplication webApp;

    private static final class StubUserDao implements UserDao {
        private final User customer;
        private final User admin;

        StubUserDao(String hash) {
            this.customer = new User();
            this.customer.setUserId(11L);
            this.customer.setUsername("customer");
            this.customer.setPasswordHash(hash);
            this.customer.setRole(UserRole.CUSTOMER);
            this.customer.setFullName("Naledi Sithole");
            this.customer.setEmail("customer@example.com");
            this.customer.setActive(true);

            this.admin = new User();
            this.admin.setUserId(12L);
            this.admin.setUsername("testadmin");
            this.admin.setPasswordHash(hash);
            this.admin.setRole(UserRole.ADMIN);
            this.admin.setFullName("David Zenn");
            this.admin.setEmail("testadmin@example.com");
            this.admin.setActive(true);
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return findByUsernameOrEmail(username);
        }

        @Override
        public Optional<User> findByUsernameOrEmail(String identifier) {
            if (identifier == null) return Optional.empty();
            String norm = identifier.trim().toLowerCase(Locale.ROOT);
            if ("customer".equals(norm) || "customer@example.com".equals(norm)) return Optional.of(customer);
            if ("testadmin".equals(norm) || "testadmin@example.com".equals(norm)) return Optional.of(admin);
            return Optional.empty();
        }
    }

    private static final class EmptyFlightDao implements FlightDao {
        @Override
        public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
            return Collections.emptyList();
        }

        @Override
        public Optional<FlightSearchResult> findById(long flightId) {
            return Optional.empty();
        }
    }

    private static final class EmptySeatDao implements SeatDao {
        @Override public List<FlightSeat> getSeatMapForFlight(long flightId) { return Collections.emptyList(); }
        @Override public boolean releaseSeat(long flightSeatId) { return false; }
        @Override public boolean bookSeat(long flightSeatId) { return false; }
        @Override public int releaseExpiredHolds() { return 0; }
        @Override public Optional<FlightSeat> findSeatById(long flightSeatId) { return Optional.empty(); }
    }

    private static final class EmptyBookingDao implements BookingDao {
        @Override public BookingRecord createBooking(BookingContext context) { throw new UnsupportedOperationException(); }
        @Override public Optional<BookingRecord> findByReference(String bookingRef) { return Optional.empty(); }
        @Override public List<BookingRecord> findByUserId(long userId) { return Collections.emptyList(); }
    }

    @AfterEach
    void tearDown() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    private String startServer() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
        String pwdHash = PasswordUtil.hash("Passw0rd!");
        StubUserDao userDao = new StubUserDao(pwdHash);
        FlightDao flightDao = new EmptyFlightDao();
        AirportDao airportDao = Collections::emptyList;
        SeatDao seatDao = new EmptySeatDao();
        BookingDao bookingDao = new EmptyBookingDao();

        FlightService flightService = new FlightService(flightDao, airportDao, new PricingService(seatDao, clock), clock);
        SeatMapService seatMapService = new SeatMapService(seatDao, flightDao, clock);
        BookingService bookingService = new BookingService(bookingDao, flightDao, clock);
        AuthService authService = new AuthService(userDao);
        RegistrationService regService = new RegistrationService(userDao, (u, b) -> {});

        AdminDashboardDao adminDao = (clk, range, lowPct) -> new AdminDashboardData(
                Range.SEVEN_DAYS,
                15L, 4.5,
                new BigDecimal("12450.00"), 8.0,
                28L, 2.0,
                1L, 0.0,
                List.of(),
                new CabinLoadBreakdown(24, 4, 134, 162, 0, 0),
                List.of(),
                List.of(new FlightLoadFactor(
                        1L, "SR 204", "JNB", "CPT",
                        Instant.parse("2026-11-23T04:30:00Z"), ZoneId.of("Africa/Johannesburg"),
                        12, 162, 150, 7.4, "Scheduled")),
                List.of(),
                List.of()
        );

        webApp = new WebApplication(
                flightService, seatMapService, bookingService, authService,
                new LoginThrottle(clock), regService, new RegistrationThrottle(clock),
                new SimulatedPaymentGateway(), new PaymentThrottle(clock),
                new BoardingPassVerificationService(bookingService), new VerifyThrottle(clock),
                new AdminDashboardService(adminDao, clock)
        );
        webApp.start(0);
        return "http://localhost:" + webApp.getApp().port();
    }

    private HttpClient newClient() {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private HttpResponse<String> get(HttpClient client, String baseUrl, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> login(HttpClient client, String baseUrl, String username, String password) throws Exception {
        String loginHtml = get(client, baseUrl, "/login").body();
        Matcher m = CSRF_PATTERN.matcher(loginHtml);
        assertTrue(m.find(), "Missing CSRF token on /login");
        String body = "csrf_token=" + URLEncoder.encode(m.group(1), StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        return client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Logged-out shell uses full-bleed Home, framed inner pages, footer links, and hides Admin")
    void loggedOutShellStructureAndFrameContract() throws Exception {
        String baseUrl = startServer();
        HttpClient client = newClient();

        HttpResponse<String> home = get(client, baseUrl, "/");
        assertEquals(200, home.statusCode());
        String homeHtml = home.body();
        assertTrue(homeHtml.contains("backdrop-aerial"));
        assertFalse(homeHtml.contains("class=\"inner-glass-frame\""), "Home must stay full-bleed");
        assertTrue(homeHtml.contains("nav-pill-link is-active\">Home</a>"));
        assertTrue(homeHtml.contains("href=\"/flights\""));
        assertTrue(homeHtml.contains("href=\"/my-bookings\""));
        assertTrue(homeHtml.contains("href=\"/login\""));
        assertTrue(homeHtml.contains("href=\"/register\""));
        assertFalse(homeHtml.contains("href=\"/admin\""), "Logged-out nav must not expose Admin");
        assertTrue(homeHtml.contains("href=\"/about\""));
        assertTrue(homeHtml.contains("href=\"/contact\""));
        assertTrue(homeHtml.contains("href=\"/confirmation\""));
        assertTrue(homeHtml.contains("Original website: Nikki and Thaledi. Integrated by David."));

        for (String innerPath : List.of("/flights", "/about", "/contact", "/login", "/register")) {
            HttpResponse<String> resp = get(client, baseUrl, innerPath);
            assertEquals(200, resp.statusCode(), "Expected 200 on " + innerPath);
            assertTrue(resp.body().contains("class=\"inner-glass-frame\""),
                    "Inner page " + innerPath + " must sit inside inner-glass-frame");
            assertTrue(resp.body().contains("id=\"mobileNavSheet\""),
                    "Inner page " + innerPath + " must include mobile navigation sheet");
        }

        HttpResponse<String> anonAdmin = get(client, baseUrl, "/admin");
        assertEquals(302, anonAdmin.statusCode());
        assertEquals("/login?next=/admin", anonAdmin.headers().firstValue("Location").orElse(""));
    }

    @Test
    @DisplayName("Customer sees full_name initials pill and username in menu, but is forbidden from /admin")
    void customerAvatarAndAdminForbidden() throws Exception {
        String baseUrl = startServer();
        HttpClient client = newClient();
        HttpResponse<String> loginResp = login(client, baseUrl, "customer", "Passw0rd!");
        assertEquals(302, loginResp.statusCode());

        String homeHtml = get(client, baseUrl, "/").body();
        assertTrue(homeHtml.contains("<span class=\"avatar-initials\">NS</span>"),
                "Customer avatar must show NS initials from Naledi Sithole");
        assertTrue(homeHtml.contains("<span class=\"avatar-name\">Naledi Sithole</span>"));
        assertTrue(homeHtml.contains("<span class=\"avatar-menu-handle\">customer</span>"));
        assertTrue(homeHtml.contains("href=\"/logout\""));
        assertFalse(homeHtml.contains("href=\"/admin\""), "Customer must not see Admin nav item");

        HttpResponse<String> adminResp = get(client, baseUrl, "/admin");
        assertEquals(403, adminResp.statusCode());
        assertTrue(adminResp.body().contains("Administrator access is required"));
        assertTrue(adminResp.body().contains("class=\"inner-glass-frame\""));
    }

    @Test
    @DisplayName("Admin sees Admin nav pill and live /admin dashboard inside glass frame")
    void adminSeesTabAndDashboard() throws Exception {
        String baseUrl = startServer();
        HttpClient client = newClient();
        HttpResponse<String> loginResp = login(client, baseUrl, "testadmin", "Passw0rd!");
        assertEquals(302, loginResp.statusCode());

        HttpResponse<String> adminResp = get(client, baseUrl, "/admin");
        assertEquals(200, adminResp.statusCode());
        String html = adminResp.body();
        assertTrue(html.contains("<span class=\"avatar-initials\">DZ</span>"));
        assertTrue(html.contains("href=\"/admin\" class=\"nav-pill-link is-active\">Admin</a>"));
        assertTrue(html.contains("Operations Dashboard"));
        assertTrue(html.contains("R 12,450.00"));
        assertTrue(html.contains("SR 204"));
        assertTrue(html.contains("class=\"inner-glass-frame\""));
    }
}
