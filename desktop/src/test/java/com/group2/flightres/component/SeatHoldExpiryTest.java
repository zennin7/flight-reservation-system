package com.group2.flightres.component;

import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.util.FontLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatHoldExpiryTest {

    @BeforeAll
    static void initJavaFX() {
        System.setProperty("altitude.testing", "true");
                try (java.sql.Connection conn = com.group2.flightres.config.DatabaseConfig.getConnection()) {
            // Warm database connection pool
        } catch (Exception ignored) {}
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
        Platform.setImplicitExit(false);
        try {
            FontLoader.loadFonts();
        } catch (Throwable ignored) {}
    }

    @Test
    @DisplayName("Gate 4a: When held_until passes, seat released, selection cleared, and hold expired alert shown")
    void testSeatHoldExpiryHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/seat-map.fxml"));
                Parent root = loader.load();
                assertNotNull(root);

                SeatMapController controller = loader.getController();
                assertNotNull(controller);

                BookingContext ctx = new BookingContext();
                long testFlightId = 53L;
                try (java.sql.Connection conn = com.group2.flightres.config.DatabaseConfig.getConnection();
                     java.sql.PreparedStatement ps = conn.prepareStatement(
                             "SELECT flight_id FROM flights WHERE departure_time > NOW() + INTERVAL '2 hours' AND flight_number = 'ALT 204' LIMIT 1")) {
                    var rs = ps.executeQuery();
                    if (rs.next()) testFlightId = rs.getLong(1);
                } catch (Exception ignored) {}
                ctx.setFlightId(testFlightId);
                ctx.setFlightNumber("ALT 204");
                controller.init(ctx, () -> {});

                // Select a seat
                controller.selectSeatByLabel("7C");

                assertFalse(controller.getSelectedSeatsList().isEmpty(), "Seat 7C should be selected");
                assertFalse(ctx.getSelectedSeats().isEmpty(), "Context should have selected seat");
                assertNotNull(ctx.getHoldExpiresAt(), "Context should track hold expiry instant");

                // Trigger hold expiration
                controller.handleHoldExpired();

                // Assertions per Gate 4a:
                // (i) seat selection is cleared
                assertTrue(controller.getSelectedSeatsList().isEmpty(), "Controller selection must be cleared on hold expiry");
                assertTrue(ctx.getSelectedSeats().isEmpty(), "BookingContext selected seats must be cleared on hold expiry");
                assertNull(ctx.getHoldExpiresAt(), "BookingContext hold expiry must be reset to null");
                assertEquals(0, BigDecimal.ZERO.compareTo(ctx.getTotalPrice()), "Total price must be reset to 0");

                // (ii) honest hold expired alert is recorded/shown
                assertNotNull(controller.getLastAlertMessage(), "Alert message must be set");
                assertTrue(controller.getLastAlertMessage().contains("expired") || controller.getLastAlertMessage().contains("released"),
                        "User must see honest hold expired alert: " + controller.getLastAlertMessage());

            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Timed out waiting for JavaFX thread");
        if (errorRef.get() != null) {
            throw new RuntimeException("Test failed", errorRef.get());
        }
    }
}
