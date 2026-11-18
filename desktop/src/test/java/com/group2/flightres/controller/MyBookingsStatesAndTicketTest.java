package com.group2.flightres.controller;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.util.FontLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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

class MyBookingsStatesAndTicketTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

    @BeforeAll
    static void initJavaFX() {
        System.setProperty("altitude.testing", "true");
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Warm database connection pool for flight-search navigation target
        } catch (Exception ignored) {}
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
        Platform.setImplicitExit(false);
        try {
            FontLoader.loadFonts();
        } catch (Throwable ignored) {}
    }

    private static User createUser(long userId, String email, String name) {
        User u = new User();
        u.setUserId(userId);
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    private static BookingSummary createSampleSummary(long userId, String ref) {
        Instant dep = Instant.parse("2026-11-20T06:30:00Z");
        Instant arr = Instant.parse("2026-11-20T08:40:00Z");
        return new BookingSummary(
                501L,
                ref,
                userId,
                "ALT 204",
                "JNB",
                "Johannesburg",
                dep,
                ZoneId.of("Africa/Johannesburg"),
                "CPT",
                "Cape Town",
                arr,
                ZoneId.of("Africa/Johannesburg"),
                "UPCOMING",
                "CONFIRMED",
                List.of("7A"),
                new BigDecimal("1560.00"),
                "Demo Traveller",
                "demo.traveller@example.com",
                "Airbus A320"
        );
    }

    private static BookingRecord createSampleRecord(long ownerUserId, String ref) {
        BookingRecord rec = new BookingRecord();
        rec.setBookingId(501L);
        rec.setBookingReference(ref);
        rec.setUserId(ownerUserId);
        rec.setFlightId(101L);
        rec.setFlightNumber("ALT 204");
        rec.setOrigin("Johannesburg (JNB)");
        rec.setOriginCity("Johannesburg");
        rec.setDestination("Cape Town (CPT)");
        rec.setDestinationCity("Cape Town");
        rec.setDepartureInstant(Instant.parse("2026-11-20T06:30:00Z"));
        rec.setArrivalInstant(Instant.parse("2026-11-20T08:40:00Z"));
        rec.setDepartureDate("2026-11-20");
        rec.setDepartureTime("08:30");
        rec.setArrivalTime("10:40");
        rec.setStatus("CONFIRMED");
        rec.setTotalAmount(new BigDecimal("1560.00"));
        rec.setPassengerName("Demo Traveller");
        rec.setPassengerEmail("demo.traveller@example.com");
        rec.setPassportNumber("DT987654");
        rec.setSeatLabel("7A");
        rec.setSeatClass(SeatClass.ECONOMY);
        return rec;
    }

    @Test
    @DisplayName("Empty state Search flights button navigates to flight-search via Navigator")
    void testEmptyStateSearchFlightsNavigatesViaNavigator() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        BookingService emptyService = new BookingService(new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext context) { return null; }
            @Override public Optional<BookingRecord> findByReference(String bookingRef) { return Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(); }
            @Override public List<BookingSummary> listSummariesForUser(long userId, Clock clock) { return List.of(); }
        }, new FlightDaoImpl(), FIXED_CLOCK);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(emptyService, FIXED_CLOCK));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                Scene scene = new Scene(root, 1100, 720);
                stage.setScene(scene);

                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(createUser(12L, "demo.traveller@example.com", "Demo Traveller"));
                controller.init(ctx, null);

                assertTrue(controller.getEmptyStateContainer().isVisible(), "Empty state must be visible when no bookings exist");
                assertTrue(controller.getSearchFlightsButton().isVisible(), "Search flights button must be visible in empty state");

                // Click "Search flights" -> navigates via Navigator.navigateToSearch
                controller.getSearchFlightsButton().fire();

                Node searchBtn = scene.getRoot().lookup("#searchButton");
                Node fromCombo = scene.getRoot().lookup("#fromCombo");
                assertNotNull(searchBtn, "Scene root must navigate to flight-search (#searchButton present)");
                assertNotNull(fromCombo, "Scene root must navigate to flight-search (#fromCombo present)");
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Empty state navigation test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("Empty state Search flights test failed", errorRef.get());
        }
    }

    @Test
    @DisplayName("Error state Retry button invokes loadBookings again and recovers to populated state")
    void testErrorStateRetryReloadsBookings() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicInteger listCallCount = new AtomicInteger(0);

        BookingService flakyService = new BookingService(new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext context) { return null; }
            @Override public Optional<BookingRecord> findByReference(String bookingRef) { return Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(); }
            @Override public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
                int call = listCallCount.incrementAndGet();
                if (call == 1) {
                    throw new RuntimeException("Simulated transient connection failure");
                }
                return List.of(createSampleSummary(userId, "RTY123"));
            }
        }, new FlightDaoImpl(), FIXED_CLOCK);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(flakyService, FIXED_CLOCK));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                Scene scene = new Scene(root, 1100, 720);
                stage.setScene(scene);

                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(createUser(12L, "demo.traveller@example.com", "Demo Traveller"));
                controller.init(ctx, () -> {});

                assertEquals(1, listCallCount.get(), "Initial load must call listBookingsForUser once");
                assertTrue(controller.getErrorStateContainer().isVisible(), "Error state must be visible after load failure");
                assertFalse(controller.getTabsBar().isVisible(), "Tab bar with counts must be hidden in error state");
                assertFalse(controller.getUpcomingTabBtn().getText().matches(".*\\d+.*"), "Error state must not display stale numeric tab counts");
                assertTrue(controller.getRetryButton().isVisible(), "Retry button must be visible in error state");

                // Click Retry -> invokes loadBookings() again
                controller.getRetryButton().fire();

                assertEquals(2, listCallCount.get(), "Clicking Retry must call loadBookings / listBookingsForUser a second time");
                assertFalse(controller.getErrorStateContainer().isVisible(), "Error state must hide after successful retry");
                assertTrue(controller.getTabsBar().isVisible(), "Tab bar must reappear after successful retry");
                assertEquals("Upcoming (1)", controller.getUpcomingTabBtn().getText());
                assertEquals(1, controller.getCardsContainer().getChildren().size(), "Booking card must render after retry");
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Error retry test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("Error state Retry test failed", errorRef.get());
        }
    }

    @Test
    @DisplayName("View ticket loads booking via getBookingForUser(userId, pnr) and opens confirmation")
    void testViewTicketLoadsOwnedBookingAndNavigatesToConfirmation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<Long> queriedUserId = new AtomicReference<>();
        AtomicReference<String> queriedPnr = new AtomicReference<>();

        BookingService spyService = new BookingService(new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext context) { return null; }
            @Override public Optional<BookingRecord> findByReference(String bookingRef) {
                if ("TK8899".equalsIgnoreCase(bookingRef)) {
                    return Optional.of(createSampleRecord(12L, "TK8899"));
                }
                return Optional.empty();
            }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(); }
            @Override public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
                return List.of(createSampleSummary(userId, "TK8899"));
            }
        }, new FlightDaoImpl(), FIXED_CLOCK) {
            @Override
            public BookingRecord getBookingForUser(long userId, String bookingReference) {
                queriedUserId.set(userId);
                queriedPnr.set(bookingReference);
                return super.getBookingForUser(userId, bookingReference);
            }
        };

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(spyService, FIXED_CLOCK));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                Scene scene = new Scene(root, 1280, 800);
                stage.setScene(scene);
                stage.show();

                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(createUser(12L, "demo.traveller@example.com", "Demo Traveller"));
                controller.init(ctx, () -> {});

                assertEquals(1, controller.getCardsContainer().getChildren().size());
                Button viewTicketBtn = (Button) controller.getCardsContainer().getChildren().get(0).lookup(".btn-gold");
                assertNotNull(viewTicketBtn, "View ticket button must be present on booking card");
                viewTicketBtn.fire();

                assertEquals(12L, queriedUserId.get(), "getBookingForUser must be called with active userId 12");
                assertEquals("TK8899", queriedPnr.get(), "getBookingForUser must be called with card PNR TK8899");

                scene.getRoot().applyCss();
                scene.getRoot().layout();

                Label bookingRefLabel = (Label) scene.getRoot().lookup("#bookingRefLabel");
                assertNotNull(bookingRefLabel, "View ticket must navigate to booking-confirmation.fxml");
                assertEquals("TK8899", bookingRefLabel.getText(), "Confirmation screen must show requested PNR");
                assertNotNull(scene.getRoot().lookup("#downloadPdfButton"), "Confirmation PDF export button must be present");
                assertNotNull(scene.getRoot().lookup("#addToCalendarButton"), "Confirmation ICS export button must be present");
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "View ticket owned booking test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("View ticket owned booking test failed", errorRef.get());
        }
    }

    @Test
    @DisplayName("View ticket refuses another user's PNR via getBookingForUser ownership check")
    void testViewTicketRefusesAnotherUsersPnr() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        // Card attempts to reference OTHER1 (owned by user 888), while session user is 12
        BookingService strictService = new BookingService(new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext context) { return null; }
            @Override public Optional<BookingRecord> findByReference(String bookingRef) {
                if ("OTHER1".equalsIgnoreCase(bookingRef)) {
                    return Optional.of(createSampleRecord(888L, "OTHER1"));
                }
                return Optional.empty();
            }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(); }
            @Override public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
                return List.of(createSampleSummary(888L, "OTHER1"));
            }
        }, new FlightDaoImpl(), FIXED_CLOCK);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(strictService, FIXED_CLOCK));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                Scene scene = new Scene(root, 1280, 800);
                stage.setScene(scene);

                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(createUser(12L, "demo.traveller@example.com", "Demo Traveller"));
                controller.init(ctx, () -> {});

                Button viewTicketBtn = (Button) controller.getCardsContainer().getChildren().get(0).lookup(".btn-gold");
                assertNotNull(viewTicketBtn);
                viewTicketBtn.fire();

                // Must refuse access and stay off confirmation screen
                assertNull(scene.getRoot().lookup("#bookingRefLabel"), "Unauthorized user must NOT navigate to booking confirmation");
                assertTrue(controller.getErrorStateContainer().isVisible(), "Refusal must show visible error state");
                assertTrue(controller.getErrorMessageLabel().getText().contains("not authorized"),
                        "Error message must explain ownership refusal: " + controller.getErrorMessageLabel().getText());
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "View ticket ownership refusal test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("View ticket ownership refusal test failed", errorRef.get());
        }
    }

    @Test
    @DisplayName("Step 0A: Tab pill counts match rendered cards for every tab (Upcoming, Past, Cancelled)")
    void testTabCountsMatchRenderedCardsForEveryTab() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        BookingSummary up1 = createSampleSummary(12L, "UP0001");
        BookingSummary up2 = createSampleSummary(12L, "UP0002");
        BookingSummary past1 = new BookingSummary(
                502L, "PA0001", 12L, "ALT 205",
                "CPT", "Cape Town", Instant.parse("2026-11-01T06:00:00Z"), ZoneId.of("Africa/Johannesburg"),
                "JNB", "Johannesburg", Instant.parse("2026-11-01T08:00:00Z"), ZoneId.of("Africa/Johannesburg"),
                "COMPLETED", "CONFIRMED", List.of("8B"), new BigDecimal("1400.00"),
                "Demo Traveller", "demo.traveller@example.com", "Airbus A320");
        BookingSummary can1 = new BookingSummary(
                503L, "CA0001", 12L, "ALT 602",
                "JNB", "Johannesburg", Instant.parse("2026-11-25T06:00:00Z"), ZoneId.of("Africa/Johannesburg"),
                "DUR", "Durban", Instant.parse("2026-11-25T07:25:00Z"), ZoneId.of("Africa/Johannesburg"),
                "CANCELLED", "CANCELLED", List.of("9C"), new BigDecimal("1100.00"),
                "Demo Traveller", "demo.traveller@example.com", "Airbus A320");

        BookingService mixedService = new BookingService(new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext context) { return null; }
            @Override public Optional<BookingRecord> findByReference(String bookingRef) { return Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(); }
            @Override public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
                return List.of(up1, up2, past1, can1);
            }
        }, new FlightDaoImpl(), FIXED_CLOCK);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(mixedService, FIXED_CLOCK));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(createUser(12L, "demo.traveller@example.com", "Demo Traveller"));
                controller.init(ctx, () -> {});

                assertEquals("Upcoming (2)", controller.getUpcomingTabBtn().getText());
                assertEquals("Past (1)", controller.getPastTabBtn().getText());
                assertEquals("Cancelled (1)", controller.getCancelledTabBtn().getText());

                controller.setActiveTab(MyBookingsController.Tab.UPCOMING);
                assertEquals(2, controller.getCardsContainer().getChildren().size());

                controller.setActiveTab(MyBookingsController.Tab.PAST);
                assertEquals(1, controller.getCardsContainer().getChildren().size());

                controller.setActiveTab(MyBookingsController.Tab.CANCELLED);
                assertEquals(1, controller.getCardsContainer().getChildren().size());
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Tab counts parity test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("Tab counts parity test failed", errorRef.get());
        }
    }

    @Test
    @DisplayName("Step 0A: Real DB user with Upcoming empty (0) and Past rows (N) shows Upcoming (0), Past (N), and empty message")
    void testUpcomingEmptyButPastHasRows() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        // Use real BookingService against DB with a future clock (2027-01-01) so all user 12 bookings are in the past
        Clock futureClock = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(new BookingService(), futureClock));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(createUser(12L, "demo.traveller@example.com", "Demo Traveller"));
                controller.init(ctx, () -> {});

                int totalForUser12 = controller.getAllBookings().size();
                assertTrue(totalForUser12 >= 4, "Demo user 12 must have real DB bookings");

                assertEquals("Upcoming (0)", controller.getUpcomingTabBtn().getText());
                assertEquals("Past (" + totalForUser12 + ")", controller.getPastTabBtn().getText());
                assertTrue(controller.getEmptyStateContainer().isVisible(), "Empty message must show when Upcoming is 0");
                Label emptyTitle = (Label) controller.getEmptyStateContainer().lookup("#emptyTitleLabel");
                assertNotNull(emptyTitle);
                assertEquals("No upcoming trips", emptyTitle.getText());
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Upcoming empty / Past populated test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("Upcoming empty / Past populated test failed", errorRef.get());
        }
    }
}
