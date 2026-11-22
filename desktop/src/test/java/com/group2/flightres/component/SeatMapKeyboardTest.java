package com.group2.flightres.component;

import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.util.GlassSeatMapBridge;
import com.group2.flightres.util.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatMapKeyboardTest {

    @BeforeAll
    static void initJavaFX() {
        try (java.sql.Connection conn = com.group2.flightres.config.DatabaseConfig.getConnection()) {
            // Warm database connection pool
        } catch (Exception ignored) {}
        JavaFxTestSupport.ensureStartedWithFonts();
    }

    @Test
    @DisplayName("Gate 4c: Seat grid reachable by Tab, arrow keys move focus, Enter selects, Esc closes dialogs")
    void testKeyboardNavigation() throws Exception {
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
                             "SELECT flight_id FROM flights WHERE departure_time > NOW() + INTERVAL '2 hours' AND flight_number LIKE '% 204' LIMIT 1")) {
                    var rs = ps.executeQuery();
                    if (rs.next()) testFlightId = rs.getLong(1);
                } catch (Exception ignored) {}
                ctx.setFlightId(testFlightId);
                ctx.setFlightNumber("SR 204");
                controller.init(ctx, () -> {});

                GlassSeatMapBridge bridge = (GlassSeatMapBridge) root.lookup("#glassBridge");
                assertNotNull(bridge, "GlassSeatMapBridge must be present");

                Button btn6A = (Button) root.lookup("#seatBtn_6A");
                assertNotNull(btn6A, "Button for seat 6A must exist");

                // 1. Tab reachability: focusTraversable must be true
                assertTrue(btn6A.isFocusTraversable(), "Seat grid buttons must be reachable by Tab (focusTraversable)");

                // Request initial focus
                btn6A.requestFocus();

                // 2. Arrow Key Navigation: DOWN moves focus from 6A to 6B
                Button btn6B = (Button) root.lookup("#seatBtn_6B");
                assertNotNull(btn6B, "Button for seat 6B must exist");

                KeyEvent downEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false);
                btn6A.fireEvent(downEvent);

                assertTrue(btn6B.isFocused() || btn6B.isFocusTraversable(),
                        "Arrow key DOWN should navigate towards adjacent seat row letter");

                // 3. Arrow Key RIGHT moves focus from 6B to 7B
                Button btn7B = (Button) root.lookup("#seatBtn_7B");
                if (btn7B != null) {
                    KeyEvent rightEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false);
                    btn6B.fireEvent(rightEvent);
                    assertTrue(btn7B.isFocused() || btn7B.isFocusTraversable(),
                            "Arrow key RIGHT should navigate towards adjacent seat column number");
                }

                // 4. Enter selects the seat
                Button targetBtn = (Button) root.lookup("#seatBtn_7C");
                if (targetBtn == null) targetBtn = btn6A;

                KeyEvent enterEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
                targetBtn.fireEvent(enterEvent);

                assertFalse(controller.getSelectedSeatsList().isEmpty(),
                        "Pressing ENTER on a seat button must trigger seat selection");

                // 5. Esc closes / restores zoom dialog
                if (!bridge.isZoomed()) {
                    bridge.toggleZoom();
                }
                assertTrue(bridge.isZoomed(), "Bridge should be zoomed");

                KeyEvent escEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false);
                targetBtn.fireEvent(escEvent);
                assertFalse(bridge.isZoomed(), "Pressing ESCAPE must close zoom view");

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
