package com.group2.flightres.controller;

import com.group2.flightres.component.GlassTopBar;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.util.AppSession;
import com.group2.flightres.util.JavaFxTestSupport;
import com.group2.flightres.util.Navigator;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P8 Part B security, session persistence, and top-bar navigation tests.
 */
class DesktopSessionAndNavigationTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

    @BeforeAll
    static void initJavaFx() {
        try (java.sql.Connection conn = com.group2.flightres.config.DatabaseConfig.getConnection()) {
            // Warm DB pool off FX thread before starting tests
        } catch (Exception ignored) {}
        JavaFxTestSupport.ensureStartedWithFonts();
    }

    @AfterEach
    void clearSession() {
        AppSession.signOut(null);
        LoginController.clearLastAuthenticatedUser();
    }

    private static User makeUser(long id, String username, String fullName, UserRole role) {
        User u = new User();
        u.setUserId(id);
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmail(username + "@example.com");
        u.setRole(role);
        return u;
    }

    private static BookingSummary makeSummary(long userId, String ref, String routeOrig, String routeDest) {
        return new BookingSummary(
                userId * 100,
                ref,
                userId,
                "SR 204",
                routeOrig,
                routeOrig,
                Instant.parse("2026-11-20T06:30:00Z"),
                ZoneId.of("Africa/Johannesburg"),
                routeDest,
                routeDest,
                Instant.parse("2026-11-20T08:40:00Z"),
                ZoneId.of("Africa/Johannesburg"),
                "UPCOMING",
                "CONFIRMED",
                List.of("7A"),
                new BigDecimal("1450.00"),
                "User " + userId,
                "user" + userId + "@example.com",
                "Airbus A320"
        );
    }

    @Test
    @DisplayName("Part B: Guest sees 'Sign in to see your bookings' with Sign in button and ZERO booking data")
    void guestSeesSignInPromptAndZeroBookings() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicInteger serviceCalls = new AtomicInteger(0);

        BookingService spyService = new BookingService(new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext c) { return null; }
            @Override public Optional<BookingRecord> findByReference(String r) { return Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long u) { return List.of(); }
            @Override public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
                serviceCalls.incrementAndGet();
                return List.of(makeSummary(12L, "LEAK01", "JNB", "GVA"));
            }
        }, new FlightDaoImpl(), FIXED_CLOCK);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                AppSession.signOut(null);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(spyService, FIXED_CLOCK));
                Parent root = loader.load();
                MyBookingsController ctrl = loader.getController();
                stage.setScene(new Scene(root, 1100, 720));

                BookingContext guestCtx = new BookingContext();
                ctrl.init(guestCtx, null);

                assertEquals(0, serviceCalls.get(), "Guest must never trigger a bookings DB query");
                assertTrue(ctrl.getAllBookings().isEmpty(), "Guest must see zero bookings");
                assertTrue(ctrl.getGuestStateContainer().isVisible(), "Guest sign-in screen must be visible");
                assertTrue(ctrl.getGuestSignInButton().isVisible(), "Guest Sign in button must be visible");
                assertFalse(ctrl.getTabsBar().isVisible(), "Tabs bar must be hidden for guest");
            } catch (Throwable t) {
                err.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (err.get() != null) throw new AssertionError(err.get());
    }

    @Test
    @DisplayName("Part B: User A never sees User B's bookings in My Bookings")
    void userANeverSeesUserBsBookings() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();

        Map<Long, List<BookingSummary>> db = Map.of(
                101L, List.of(makeSummary(101L, "USRA01", "JNB", "CPT")),
                202L, List.of(makeSummary(202L, "USRB02", "JNB", "GVA"))
        );

        BookingService isolatedService = new BookingService(new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext c) { return null; }
            @Override public Optional<BookingRecord> findByReference(String r) { return Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long u) { return List.of(); }
            @Override public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
                return db.getOrDefault(userId, List.of());
            }
        }, new FlightDaoImpl(), FIXED_CLOCK);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                AppSession.signOut(null);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(isolatedService, FIXED_CLOCK));
                Parent root = loader.load();
                MyBookingsController ctrl = loader.getController();
                stage.setScene(new Scene(root, 1100, 720));

                // User A
                BookingContext ctxA = new BookingContext();
                ctxA.setCurrentUser(makeUser(101L, "usera", "Alice Alpha", UserRole.CUSTOMER));
                ctrl.init(ctxA, null);

                assertEquals(1, ctrl.getAllBookings().size());
                assertEquals("USRA01", ctrl.getAllBookings().get(0).getBookingReference());
                assertTrue(ctrl.getAllBookings().stream().noneMatch(b -> "USRB02".equals(b.getBookingReference())),
                        "User A must never see User B's booking");

                // Switch to User B
                BookingContext ctxB = new BookingContext();
                ctxB.setCurrentUser(makeUser(202L, "userb", "Bob Beta", UserRole.CUSTOMER));
                ctrl.init(ctxB, null);

                assertEquals(1, ctrl.getAllBookings().size());
                assertEquals("USRB02", ctrl.getAllBookings().get(0).getBookingReference());
                assertTrue(ctrl.getAllBookings().stream().noneMatch(b -> "USRA01".equals(b.getBookingReference())),
                        "User B must never see User A's booking");
            } catch (Throwable t) {
                err.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (err.get() != null) throw new AssertionError(err.get());
    }

    @Test
    @DisplayName("Part B: Selecting a flight navigates to Seat Map and preserves the logged-in user")
    void selectingFlightNavigatesToSeatMapAndKeepsUser() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                User customer = makeUser(42L, "customer", "Demo Customer", UserRole.CUSTOMER);
                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(customer);
                AppSession.setCurrentUser(customer);

                FXMLLoader homeLoader = new FXMLLoader(getClass().getResource(Navigator.HOME_FXML));
                Parent homeRoot = homeLoader.load();
                Scene scene = new Scene(homeRoot, 1280, 760);
                stage.setScene(scene);

                Navigator.navigateToSearch(homeRoot, ctx);
                assertNotNull(scene.getRoot().lookup("#searchButton"), "Should be on flight-search");
                assertNotNull(FlightSearchController.currentInstance);

                FlightSearchResult flight = new FlightSearchResult(
                        1L, "SR 204", "JNB", "O. R. Tambo International Airport", "Johannesburg",
                        ZoneId.of("Africa/Johannesburg"), "CPT", "Cape Town International Airport", "Cape Town",
                        ZoneId.of("Africa/Johannesburg"),
                        Instant.now().plusSeconds(86400), Instant.now().plusSeconds(86400 + 7800),
                        new BigDecimal("1200.00"), "Airbus A320", 40, 32, 8);

                FlightSearchController.currentInstance.selectFlightFromGlassCard(flight);

                assertNotNull(scene.getRoot().lookup("#cabinContainer"), "Selecting a flight must navigate to Seat Map, not Home/Login");
                assertNull(scene.getRoot().lookup("#loginButton"), "Must NOT drop to login screen");
                assertEquals(42L, AppSession.getCurrentUser().getUserId(), "Logged-in user must survive flight selection");

                GlassTopBar topBar = (GlassTopBar) scene.getRoot().lookup("#topBar");
                assertNotNull(topBar);
                assertEquals("Demo Customer", topBar.getAvatarNameLabel().getText(), "Top bar must still show Demo Customer");
            } catch (Throwable t) {
                err.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(60, TimeUnit.SECONDS));
        if (err.get() != null) throw new AssertionError(err.get());
    }

    @Test
    @DisplayName("Part B: Top bar Home, Flights, My bookings, and Admin buttons navigate from every screen keeping user")
    void topBarButtonsNavigateFromEveryScreenKeepingUser() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                User admin = makeUser(1L, "testadmin", "Test Admin", UserRole.ADMIN);
                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(admin);
                AppSession.setCurrentUser(admin);

                FXMLLoader loader = new FXMLLoader(getClass().getResource(Navigator.HOME_FXML));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1280, 760);
                stage.setScene(scene);
                HomeController homeCtrl = loader.getController();
                homeCtrl.init(ctx, () -> Navigator.navigateToSearch(root, ctx));

                // From Home -> Flights -> My bookings -> Home -> Admin
                GlassTopBar bar1 = (GlassTopBar) scene.getRoot().lookup("#topBar");
                assertNotNull(bar1);
                assertTrue(bar1.getAdminBtn().isVisible(), "Admin button must be visible for ADMIN user");
                assertEquals("Test Admin", bar1.getAvatarNameLabel().getText());

                bar1.getFlightsBtn().fire();
                assertNotNull(scene.getRoot().lookup("#searchButton"), "Flights button must open flight-search");
                assertEquals("Test Admin", ((GlassTopBar) scene.getRoot().lookup("#topBar")).getAvatarNameLabel().getText());

                ((GlassTopBar) scene.getRoot().lookup("#topBar")).getMyBookingsBtn().fire();
                assertNotNull(scene.getRoot().lookup("#upcomingTabBtn"), "My bookings button must open my-bookings");
                assertEquals("Test Admin", ((GlassTopBar) scene.getRoot().lookup("#topBar")).getAvatarNameLabel().getText());

                ((GlassTopBar) scene.getRoot().lookup("#topBar")).getHomeBtn().fire();
                assertNotNull(scene.getRoot().lookup("#searchFlightsButton"), "Home button must open home");
                assertEquals("Test Admin", ((GlassTopBar) scene.getRoot().lookup("#topBar")).getAvatarNameLabel().getText());

                ((GlassTopBar) scene.getRoot().lookup("#topBar")).getAdminBtn().fire();
                assertNotNull(scene.getRoot().lookup("#greetingLabel"), "Admin button must open admin-dashboard");
                assertEquals(1L, AppSession.getCurrentUser().getUserId(), "Admin session must survive navigation");

                // Sign out clears all state
                Navigator.signOut(scene.getRoot(), ctx);
                assertNull(AppSession.getCurrentUser(), "Sign out must clear AppSession user");
                assertNull(ctx.getCurrentUser(), "Sign out must clear BookingContext user");
                assertNotNull(scene.getRoot().lookup("#loginButton"), "Sign out must navigate to login screen");
            } catch (Throwable t) {
                err.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(60, TimeUnit.SECONDS));
        if (err.get() != null) throw new AssertionError(err.get());
    }
}
