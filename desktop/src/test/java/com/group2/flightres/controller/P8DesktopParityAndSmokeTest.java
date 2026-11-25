package com.group2.flightres.controller;

import com.group2.flightres.component.AirportPickerPopup;
import com.group2.flightres.component.GlassTopBar;
import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.model.Airport;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.util.AppSession;
import com.group2.flightres.util.DestinationPhotos;
import com.group2.flightres.util.DesktopAsync;
import com.group2.flightres.util.GlassFlightResultsBridge;
import com.group2.flightres.util.JavaFxTestSupport;
import com.group2.flightres.util.Navigator;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automated P8 verification covering Parts C, D, and F (full smoke flow) against throwaway DB sr_p8.
 */
class P8DesktopParityAndSmokeTest {

    @BeforeAll
    static void init() {
        try (Connection c = DatabaseConfig.getConnection()) {
            // Warm connection pool
        } catch (Exception ignored) {}
        JavaFxTestSupport.ensureStartedWithFonts();
    }

    @AfterEach
    void cleanup() {
        AppSession.signOut(null);
        LoginController.clearLastAuthenticatedUser();
    }

    @Test
    @DisplayName("Part C: DesktopAsync runs work off JavaFX thread and DestinationPhotos caps cache at 6")
    void asyncRunsOffFxThreadAndImageCacheCappedAtSix() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ranOffFx = new AtomicBoolean(false);
        AtomicBoolean callbackOnFx = new AtomicBoolean(false);

        Platform.runLater(() -> DesktopAsync.run("Test:offFx",
                () -> {
                    ranOffFx.set(!Platform.isFxApplicationThread());
                    return "ok";
                },
                res -> {
                    callbackOnFx.set(Platform.isFxApplicationThread());
                    latch.countDown();
                },
                err -> latch.countDown()));

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        assertTrue(ranOffFx.get(), "Background task must execute off the JavaFX Application Thread");
        assertTrue(callbackOnFx.get(), "Success callback must execute on the JavaFX Application Thread");

        // Load >6 photos and confirm LRU cache never exceeds 6
        for (String path : DestinationPhotos.ROTATING_PHOTOS) {
            DestinationPhotos.loadResource(path);
        }
        assertTrue(DestinationPhotos.getCacheSize() <= DestinationPhotos.MAX_IMAGE_CACHE,
                "Image cache must stay <= 6, got " + DestinationPhotos.getCacheSize());
    }

    @Test
    @DisplayName("Part D: JNB->CPT has >=8 flights across all 4 time chips, every destination has multiple flights for next 7 days")
    void flightSearchParityAndDestinationCoverage() {
        FlightService flightService = new FlightService();
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(1);

        List<FlightSearchResult> jnbCpt = flightService.searchFlights("JNB", "CPT", tomorrow);
        assertTrue(jnbCpt.size() >= 8,
                "JNB->CPT must return at least 8 flights, got " + jnbCpt.size()
                        + " (run scripts\\reseed.cmd to refresh the rolling 14-day schedule)");

        GlassFlightResultsBridge bridge = new GlassFlightResultsBridge();
        for (String chip : List.of("Before 6 AM", "6 AM to 12 PM", "12 PM to 6 PM", "After 6 PM")) {
            bridge.setDepartureFilter(chip);
            long count = jnbCpt.stream().filter(bridge::matchesDeparture).count();
            assertTrue(count >= 1,
                    "Time chip '" + chip + "' must match >=1 flight on JNB->CPT, got " + count
                            + " (run scripts\\reseed.cmd to refresh the rolling 14-day schedule)");
        }

        // Every destination in airports list (except JNB) must return multiple flights from JNB for each of next 7 days
        List<Airport> airports = flightService.listAirports();
        assertTrue(airports.size() >= 10, "Expected full destination list");
        for (Airport dest : airports) {
            if ("JNB".equalsIgnoreCase(dest.code())) continue;
            for (int dayOffset = 1; dayOffset <= 7; dayOffset++) {
                LocalDate d = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(dayOffset);
                List<FlightSearchResult> res = flightService.searchFlights("JNB", dest.code(), d);
                assertTrue(res.size() >= 2,
                        "Destination " + dest.code() + " on " + d + " must return >=2 flights from JNB, got " + res.size()
                                + " (run scripts\\reseed.cmd to refresh the rolling 14-day schedule)");
            }
        }

        // Next available date jumps to a date with flights
        LocalDate pastOrEmpty = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        Optional<LocalDate> nextDate = flightService.nextAvailableDate("JNB", "CPT", pastOrEmpty);
        assertTrue(nextDate.isPresent(), "Next available date must be present for JNB->CPT");
        assertFalse(flightService.searchFlights("JNB", "CPT", nextDate.get()).isEmpty(),
                "Next available date must have open flights");
    }

    @Test
    @DisplayName("Part D: AirportPickerPopup filters by city/name/code and commits selection cleanly")
    void airportPickerTypeToFilterAndCommit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();

        List<Airport> airports = List.of(
                new Airport("JNB", "O. R. Tambo International Airport", "Johannesburg", "South Africa", ZoneId.of("Africa/Johannesburg")),
                new Airport("CPT", "Cape Town International Airport", "Cape Town", "South Africa", ZoneId.of("Africa/Johannesburg")),
                new Airport("DUR", "King Shaka International Airport", "Durban", "South Africa", ZoneId.of("Africa/Johannesburg")),
                new Airport("DXB", "Dubai International Airport", "Dubai", "UAE", ZoneId.of("Asia/Dubai"))
        );

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                Button anchor = new Button("From");
                stage.setScene(new Scene(new javafx.scene.layout.StackPane(anchor), 400, 300));
                stage.show();

                AtomicReference<Airport> chosen = new AtomicReference<>();
                AirportPickerPopup picker = new AirportPickerPopup();
                picker.show(anchor, airports, airports.get(0), chosen::set);

                assertTrue(picker.isShowing(), "Airport picker popup must be visible");
                assertEquals(4, picker.getListView().getItems().size());

                picker.applyFilter("durb");
                assertEquals(1, picker.getListView().getItems().size());
                assertEquals("DUR", picker.getListView().getItems().get(0).code());

                picker.commitSelection();
                assertNotNull(chosen.get());
                assertEquals("DUR", chosen.get().code());
                assertFalse(picker.isShowing(), "Picker must close after committing selection");
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
    @DisplayName("Part F Smoke Test: login -> JNB-CPT >=8 flights -> seat -> checkout 4242 -> confirmation -> My bookings -> sign out -> guest -> sign in again")
    void endToEndSmokeFlow() throws Exception {
        AuthService authService = new AuthService();
        FlightService flightService = new FlightService();
        SeatMapService seatMapService = new SeatMapService();
        PricingService pricingService = new PricingService();
        BookingService bookingService = new BookingService();
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

        // 1. Login as customer
        User customer = authService.authenticate("customer", "Passw0rd!");
        assertNotNull(customer);
        AppSession.setCurrentUser(customer);

        // 2. Search JNB -> CPT tomorrow (>= 8 flights)
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(1);
        List<FlightSearchResult> flights = flightService.searchFlights("JNB", "CPT", tomorrow);
        assertTrue(flights.size() >= 8,
                "Expected >=8 JNB->CPT flights, got " + flights.size()
                        + " (run scripts\\reseed.cmd to refresh the rolling 14-day schedule)");

        // 3. Pick a flight & hold an available economy seat
        FlightSearchResult chosen = flights.get(0);
        List<FlightSeat> seats = seatMapService.loadSeatMap(chosen.getFlightId());
        FlightSeat available = seats.stream()
                .filter(s -> s.getSeatClass() == SeatClass.ECONOMY && s.getStatus() == SeatStatus.AVAILABLE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No available economy seat on flight " + chosen.getFlightId()));

        String holdOwner = "p8-smoke-" + UUID.randomUUID();
        seatMapService.selectSeat(available, holdOwner);
        String createdRef = null;
        try {
            PriceQuote quote = pricingService.quote(chosen.getFlightId(), SeatClass.ECONOMY, Clock.systemUTC());

            // 4. Checkout with test card 4242 4242 4242 4242
            PaymentResult pay = gateway.processPayment("4242 4242 4242 4242", "12/30", "123", quote.total());
            assertTrue(pay.isApproved(), "Test card 4242 must be approved");

            BookingContext ctx = new BookingContext();
            ctx.setCurrentUser(customer);
            ctx.setFlightId(chosen.getFlightId());
            ctx.setFlightNumber(chosen.getFlightNumber());
            ctx.setOrigin(chosen.getOriginCity() + " (" + chosen.getOriginCode() + ")");
            ctx.setDestination(chosen.getDestCity() + " (" + chosen.getDestCode() + ")");
            ctx.setOriginCity(chosen.getOriginCity());
            ctx.setDestinationCity(chosen.getDestCity());
            ctx.setDepartureInstant(chosen.getDeparture());
            ctx.setArrivalInstant(chosen.getArrival());
            ctx.setSelectedSeats(List.of(available));
            ctx.setPriceQuotes(List.of(quote));
            ctx.setBaseFare(quote.baseFare());
            ctx.setTotalPrice(quote.total());
            ctx.setPassengerName("Demo Customer");
            ctx.setPassengerEmail("customer@example.com");
            ctx.setPassportNumber("ZA998877");
            ctx.setDateOfBirth(LocalDate.of(1992, 5, 15));
            ctx.setHoldOwner(holdOwner);
            ctx.setPaymentResult(pay);
            ctx.setIdempotencyKey("p8-smoke-" + UUID.randomUUID());

            var record = bookingService.createBooking(ctx);
            createdRef = record.getBookingReference();
            assertNotNull(createdRef);
            final String bookingRef = createdRef;

            // 5. Verify My Bookings UI for customer shows ONLY customer's bookings, then sign out -> guest sees none -> sign in as admin
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> err = new AtomicReference<>();

            Platform.runLater(() -> {
                Stage stage = new Stage();
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource(Navigator.MY_BOOKINGS_FXML));
                    Parent root = loader.load();
                    MyBookingsController mbCtrl = loader.getController();
                    Scene scene = new Scene(root, 1280, 760);
                    stage.setScene(scene);

                    mbCtrl.init(ctx, () -> Navigator.navigateToSearch(root, ctx));
                    assertFalse(mbCtrl.getAllBookings().isEmpty(), "Customer must see their new booking");
                    assertTrue(mbCtrl.getAllBookings().stream().allMatch(b -> b.getUserId() == customer.getUserId()),
                            "My bookings must ONLY contain bookings owned by the logged-in user");
                    assertTrue(mbCtrl.getAllBookings().stream().anyMatch(b -> bookingRef.equals(b.getBookingReference())),
                            "Newly created booking must appear in My bookings");

                    // Sign out -> guest sees no bookings
                    Navigator.signOut(root, ctx);
                    assertNull(AppSession.getCurrentUser());
                    Navigator.navigateToMyBookings(scene.getRoot(), new BookingContext());
                    assertNotNull(scene.getRoot().lookup("#guestSignInButton"));
                    assertTrue(scene.getRoot().lookup("#guestStateContainer").isVisible(),
                            "After sign out, guest must see sign-in prompt and zero bookings");

                    // Sign in again as admin -> Admin button visible
                    User admin = authService.authenticate("testadmin", "Passw0rd!");
                    BookingContext adminCtx = new BookingContext();
                    adminCtx.setCurrentUser(admin);
                    AppSession.setCurrentUser(admin);
                    Navigator.navigateToHome(scene.getRoot(), adminCtx);
                    GlassTopBar bar = (GlassTopBar) scene.getRoot().lookup("#topBar");
                    assertNotNull(bar);
                    assertTrue(bar.getAdminBtn().isVisible(), "Admin button must be visible after signing in as admin");
                } catch (Throwable t) {
                    err.set(t);
                } finally {
                    stage.close();
                    latch.countDown();
                }
            });

            assertTrue(latch.await(25, TimeUnit.SECONDS));
            if (err.get() != null) throw new AssertionError(err.get());
        } finally {
            // Clean up smoke-test booking / release seat so repeated verify runs do not consume seats
            if (createdRef != null) {
                try {
                    bookingService.cancelBookingForUser(customer.getUserId(), createdRef, gateway);
                } catch (Exception ignored) {}
            } else {
                try {
                    seatMapService.deselectSeat(available, holdOwner);
                } catch (Exception ignored) {}
            }
        }
    }
}
