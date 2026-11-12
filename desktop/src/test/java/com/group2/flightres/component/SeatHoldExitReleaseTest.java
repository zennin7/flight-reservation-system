package com.group2.flightres.component;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.TestFlightFinder;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatHoldExitReleaseTest {

    @BeforeAll
    static void initJavaFX() {
        System.setProperty("altitude.testing", "true");
        try (Connection conn = DatabaseConfig.getConnection()) {} catch (Exception ignored) {}
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
        Platform.setImplicitExit(false);
        try {
            FontLoader.loadFonts();
        } catch (Throwable ignored) {}
    }

    private static String querySeatStatus(long flightSeatId) {
        String sql = "SELECT status FROM flight_seats WHERE flight_seat_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, flightSeatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "UNKNOWN";
    }

    @Test
    @DisplayName("Leaving SeatMap via releaseHeldSeats releases held seat in DB immediately")
    void testSeatMapReleaseOnExit() throws Exception {
        SeatDao seatDao = new SeatDaoImpl();
        SeatMapService seatMapService = new SeatMapService(seatDao);

        // Pick an open ALT flight and available seat
        TestFlightFinder.OpenAltFlight openFlight = TestFlightFinder.findEarliestOpenAltFlight();
        long flightId = openFlight.flightId();
        List<FlightSeat> seats = seatDao.getSeatMapForFlight(flightId);
        FlightSeat targetSeat = seats.stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .findFirst()
                .orElseThrow();

        // Hold the seat
        seatMapService.selectSeat(targetSeat);
        assertEquals("HELD", querySeatStatus(targetSeat.getFlightSeatId()), "Seat should be HELD in DB");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean onBackCalled = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/seat-map.fxml"));
                Parent root = loader.load();
                SeatMapController ctrl = loader.getController();

                BookingContext ctx = new BookingContext();
                ctx.setFlightId(flightId);
                ctrl.init(ctx, () -> onBackCalled.set(true));

                // Add held seat to controller
                ctrl.releaseHeldSeats();
            } catch (Throwable t) {
                errorRef.set(t);
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timeout waiting for JavaFX thread");
        if (errorRef.get() != null) {
            throw new RuntimeException("Error on FX thread", errorRef.get());
        }

        // Verify the seat is released in DB
        seatMapService.deselectSeat(targetSeat);
        assertEquals("AVAILABLE", querySeatStatus(targetSeat.getFlightSeatId()), "Seat should be AVAILABLE in DB after release");
    }

    @Test
    @DisplayName("Leaving Checkout via releaseHeldSeats releases held seat in DB immediately")
    void testCheckoutReleaseOnExit() throws Exception {
        SeatDao seatDao = new SeatDaoImpl();
        SeatMapService seatMapService = new SeatMapService(seatDao);

        TestFlightFinder.OpenAltFlight openFlight = TestFlightFinder.findEarliestOpenAltFlight();
        long flightId = openFlight.flightId();
        List<FlightSeat> seats = seatDao.getSeatMapForFlight(flightId);
        FlightSeat targetSeat = seats.stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .findFirst()
                .orElseThrow();

        seatMapService.selectSeat(targetSeat);
        assertEquals("HELD", querySeatStatus(targetSeat.getFlightSeatId()), "Seat should be HELD in DB");

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(flightId);
        ctx.setSelectedSeats(new ArrayList<>(List.of(targetSeat)));
        ctx.setTotalPrice(new BigDecimal("1344.00"));

        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
                Parent root = loader.load();
                CheckoutController ctrl = loader.getController();
                ctrl.init(ctx, () -> {});

                ctrl.releaseHeldSeats();
            } catch (Throwable t) {
                errorRef.set(t);
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timeout waiting for JavaFX thread");
        if (errorRef.get() != null) {
            throw new RuntimeException("Error on FX thread", errorRef.get());
        }

        // Verify the seat is AVAILABLE in DB
        assertEquals("AVAILABLE", querySeatStatus(targetSeat.getFlightSeatId()), "Seat should be AVAILABLE in DB after checkout release");
    }
}
