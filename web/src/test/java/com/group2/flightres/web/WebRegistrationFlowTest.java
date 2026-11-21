package com.group2.flightres.web;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.exception.DuplicateEmailException;
import com.group2.flightres.exception.DuplicateUsernameException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.EmailService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.LoginThrottle;
import com.group2.flightres.service.RegistrationService;
import com.group2.flightres.service.RegistrationThrottle;
import com.group2.flightres.service.SeatMapService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRegistrationFlowTest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"");
    private WebApplication webApp;

    private static final class InMemoryUserDao implements UserDao {
        private final Map<String, User> byEmailLower = new HashMap<>();
        private final Map<String, User> byUsernameLower = new HashMap<>();
        private final AtomicInteger idSeq = new AtomicInteger(10);

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(byUsernameLower.get(username == null ? "" : username.toLowerCase(Locale.ROOT)));
        }

        @Override
        public Optional<User> findByUsernameOrEmail(String identifier) {
            if (identifier == null) return Optional.empty();
            String key = identifier.trim().toLowerCase(Locale.ROOT);
            if (byEmailLower.containsKey(key)) return Optional.of(byEmailLower.get(key));
            return Optional.ofNullable(byUsernameLower.get(key));
        }

        @Override
        public boolean existsByUsernameIgnoreCase(String username) {
            return username != null && byUsernameLower.containsKey(username.toLowerCase(Locale.ROOT));
        }

        @Override
        public boolean existsByEmailIgnoreCase(String email) {
            return email != null && byEmailLower.containsKey(email.trim().toLowerCase(Locale.ROOT));
        }

        @Override
        public User createCustomer(User user) {
            String emailKey = user.getEmail().toLowerCase(Locale.ROOT);
            if (byEmailLower.containsKey(emailKey)) {
                throw new DuplicateEmailException("An account with this email address already exists.");
            }
            String unameKey = user.getUsername().toLowerCase(Locale.ROOT);
            if (byUsernameLower.containsKey(unameKey)) {
                throw new DuplicateUsernameException("Username collision", null);
            }
            User saved = new User();
            saved.setUserId(idSeq.getAndIncrement());
            saved.setUsername(user.getUsername());
            saved.setPasswordHash(user.getPasswordHash());
            saved.setRole(UserRole.CUSTOMER);
            saved.setFullName(user.getFullName());
            saved.setEmail(user.getEmail());
            saved.setActive(true);
            byEmailLower.put(emailKey, saved);
            byUsernameLower.put(unameKey, saved);
            return saved;
        }

        int count() {
            return byEmailLower.size();
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
        @Override
        public List<FlightSeat> getSeatMapForFlight(long flightId) {
            return Collections.emptyList();
        }

        @Override
        public boolean releaseSeat(long flightSeatId) {
            return false;
        }

        @Override
        public boolean bookSeat(long flightSeatId) {
            return false;
        }

        @Override
        public int releaseExpiredHolds() {
            return 0;
        }

        @Override
        public Optional<FlightSeat> findSeatById(long flightSeatId) {
            return Optional.empty();
        }
    }

    private static final class EmptyBookingDao implements BookingDao {
        @Override
        public BookingRecord createBooking(BookingContext context) {
            throw new UnsupportedOperationException("Not used in registration test");
        }

        @Override
        public Optional<BookingRecord> findByReference(String bookingRef) {
            return Optional.empty();
        }

        @Override
        public List<BookingRecord> findByUserId(long userId) {
            return Collections.emptyList();
        }
    }

    private static final class TestHarness {
        final String baseUrl;
        final HttpClient client;

        TestHarness(WebApplication app, HttpClient client) {
            this.baseUrl = "http://localhost:" + app.getApp().port();
            this.client = client;
        }
    }

    @AfterEach
    void tearDown() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    private TestHarness startApp(InMemoryUserDao dao, List<User> welcomedUsers) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);
        FlightService flightService = new FlightService(new EmptyFlightDao(), clock);
        SeatMapService seatMapService = new SeatMapService(new EmptySeatDao(), new EmptyFlightDao(), clock);
        BookingService bookingService = new BookingService(new EmptyBookingDao(), new EmptyFlightDao(), clock);
        AuthService authService = new AuthService(dao);
        EmailService emailService = (user, baseUrl) -> welcomedUsers.add(user);
        RegistrationService regService = new RegistrationService(dao, emailService);
        RegistrationThrottle regThrottle = new RegistrationThrottle(clock);
        webApp = new WebApplication(flightService, seatMapService, bookingService, authService,
                new LoginThrottle(clock), regService, regThrottle);
        webApp.start(0);

        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new TestHarness(webApp, client);
    }

    @Test
    @DisplayName("Valid registration logs user in immediately, shows one-time welcome flash, and triggers email")
    void validRegistrationFlow() throws IOException, InterruptedException {
        InMemoryUserDao dao = new InMemoryUserDao();
        List<User> welcomed = new ArrayList<>();
        TestHarness h = startApp(dao, welcomed);

        HttpResponse<String> homeLoggedOut = get(h, "/");
        assertEquals(200, homeLoggedOut.statusCode());
        assertTrue(homeLoggedOut.body().contains("href=\"/register\""));
        assertFalse(homeLoggedOut.body().contains("/login#signup"));

        HttpResponse<String> loginPage = get(h, "/login");
        assertTrue(loginPage.body().contains("href=\"/register\""));

        HttpResponse<String> regPage = get(h, "/register");
        assertEquals(200, regPage.statusCode());
        assertTrue(regPage.body().contains("Create your account"));
        assertTrue(regPage.body().contains("Sky Royal is a university project. We store your name and email only to run this demo. No real bookings or payments are made."));
        String csrf = extractCsrf(regPage.body());

        Map<String, String> form = new LinkedHashMap<>();
        form.put("csrf_token", csrf);
        form.put("fullName", "Naledi Sithole");
        form.put("email", "Naledi@Example.com");
        form.put("password", "StrongPass12");
        form.put("confirmPassword", "StrongPass12");
        form.put("consent", "on");
        form.put("role", "ADMIN");
        form.put("is_active", "false");
        form.put("userId", "999");

        HttpResponse<String> regPost = postForm(h, "/register", form);
        assertEquals(302, regPost.statusCode());
        assertEquals("/", regPost.headers().firstValue("Location").orElse(""));

        User stored = dao.findByUsernameOrEmail("naledi@example.com").orElseThrow();
        assertEquals(UserRole.CUSTOMER, stored.getRole());
        assertTrue(stored.isActive());
        assertNotEquals(999L, stored.getUserId());
        assertEquals(1, welcomed.size());

        // First GET / displays logged-in user and exact one-time welcome flash
        HttpResponse<String> homeFirst = get(h, "/");
        assertEquals(200, homeFirst.statusCode());
        assertTrue(homeFirst.body().contains("naledi.sithole"));
        assertTrue(homeFirst.body().contains("Welcome to Sky Royal, Naledi!"));

        // Second GET / does NOT display flash again
        HttpResponse<String> homeSecond = get(h, "/");
        assertEquals(200, homeSecond.statusCode());
        assertTrue(homeSecond.body().contains("naledi.sithole"));
        assertFalse(homeSecond.body().contains("Welcome to Sky Royal, Naledi!"));
    }

    @Test
    @DisplayName("Validation highlights failed field, duplicate email, honeypot, missing CSRF, and IP throttle work cleanly")
    void validationSecurityAndThrottle() throws IOException, InterruptedException {
        InMemoryUserDao dao = new InMemoryUserDao();
        List<User> welcomed = new ArrayList<>();
        TestHarness h = startApp(dao, welcomed);

        // Missing CSRF -> 403
        HttpResponse<String> noCsrf = postForm(h, "/register", Map.of(
                "fullName", "Test User",
                "email", "nocsrf@example.com",
                "password", "StrongPass12",
                "confirmPassword", "StrongPass12",
                "consent", "on"
        ));
        assertEquals(403, noCsrf.statusCode());
        assertEquals(0, dao.count());

        String csrf = extractCsrf(get(h, "/register").body());

        // Honeypot filled -> neutral 200, zero DB rows, zero emails
        HttpResponse<String> bot = postForm(h, "/register", Map.of(
                "csrf_token", csrf,
                "website", "https://spam.example",
                "fullName", "Bot Account",
                "email", "bot@example.com",
                "password", "StrongPass12",
                "confirmPassword", "StrongPass12",
                "consent", "on"
        ));
        assertEquals(200, bot.statusCode());
        assertEquals(0, dao.count());
        assertEquals(0, welcomed.size());

        // Attempt 1: Valid signup
        assertEquals(302, postForm(h, "/register", Map.of(
                "csrf_token", csrf,
                "fullName", "Buhle Dlamini",
                "email", "Buhle@Example.com",
                "password", "SecretPass12",
                "confirmPassword", "SecretPass12",
                "consent", "on"
        )).statusCode());

        // Attempt 2: Duplicate email (lowercase) -> 409, highlights email, keeps typed inputs, never echoes passwords
        String secretPwd = "NeverEchoMe99";
        HttpResponse<String> dup = postForm(h, "/register", Map.of(
                "csrf_token", csrf,
                "fullName", "Buhle Duplicate",
                "email", "buhle@example.com",
                "password", secretPwd,
                "confirmPassword", secretPwd,
                "consent", "on"
        ));
        assertEquals(409, dup.statusCode());
        assertTrue(dup.body().contains("Buhle Duplicate"));
        assertTrue(dup.body().contains("buhle@example.com"));
        assertTrue(dup.body().contains("already exists"));
        assertTrue(dup.body().contains("id=\"uEmail\" name=\"email\""));
        assertTrue(dup.body().contains("aria-invalid=\"true\""));
        assertFalse(dup.body().contains(secretPwd));

        // Attempt 3: Mismatched confirm password -> 400, highlights confirmPassword, never echoes passwords
        HttpResponse<String> mismatch = postForm(h, "/register", Map.of(
                "csrf_token", csrf,
                "fullName", "Valid Name",
                "email", "valid2@example.com",
                "password", "ValidPass123",
                "confirmPassword", "OtherPass123",
                "consent", "on"
        ));
        assertEquals(400, mismatch.statusCode());
        assertTrue(mismatch.body().contains("Valid Name"));
        assertTrue(mismatch.body().contains("Passwords do not match"));
        assertFalse(mismatch.body().contains("ValidPass123"));
        assertFalse(mismatch.body().contains("OtherPass123"));

        // Attempt 4: Unchecked consent -> 400
        HttpResponse<String> noConsent = postForm(h, "/register", Map.of(
                "csrf_token", csrf,
                "fullName", "Fourth Attempt",
                "email", "fourth@example.com",
                "password", "StrongPass12",
                "confirmPassword", "StrongPass12"
        ));
        assertEquals(400, noConsent.statusCode());
        assertTrue(noConsent.body().contains("privacy notice"));

        // Attempt 5: Weak password (under 10 chars) -> 400
        assertEquals(400, postForm(h, "/register", Map.of(
                "csrf_token", csrf,
                "fullName", "Fifth Attempt",
                "email", "fifth@example.com",
                "password", "Short1Ab",
                "confirmPassword", "Short1Ab",
                "consent", "on"
        )).statusCode());

        // Attempt 6 -> 429 throttled
        HttpResponse<String> sixth = postForm(h, "/register", Map.of(
                "csrf_token", csrf,
                "fullName", "Sixth Attempt",
                "email", "sixth@example.com",
                "password", "StrongPass12",
                "confirmPassword", "StrongPass12",
                "consent", "on"
        ));
        assertEquals(429, sixth.statusCode());
        assertTrue(sixth.body().contains("Too many registration attempts"));
        assertFalse(sixth.body().contains("StrongPass12"));
    }

    private static HttpResponse<String> get(TestHarness h, String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(h.baseUrl + path)).GET().build();
        return h.client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> postForm(TestHarness h, String path, Map<String, String> params)
            throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(h.baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                .build();
        return h.client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String extractCsrf(String html) {
        Matcher m = CSRF_PATTERN.matcher(html);
        assertTrue(m.find(), "CSRF token hidden input missing from HTML");
        return m.group(1);
    }
}
