package com.group2.flightres.component;

import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.util.FontLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatMapAndCheckoutLayoutTest {

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

    private static BookingContext createTestContext() {
        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("ALT 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureInstant(Instant.parse("2026-11-11T04:30:00Z"));
        ctx.setArrivalInstant(Instant.parse("2026-11-11T06:40:00Z"));
        ctx.setAircraftModel("Airbus A320");
        ctx.setDuration("2h 10m");

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setFlightId(1L);
        seat.setSeatRow(7);
        seat.setSeatCol('C');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.HELD);
        seat.setCalculatedPrice(new BigDecimal("1344.00"));
        ctx.setSelectedSeats(List.of(seat));

        PriceQuote quote = PriceQuote.calculate(new BigDecimal("1200.00"), BigDecimal.ONE, 0.0, 24.0);
        ctx.setPriceQuotes(List.of(quote));
        ctx.setBaseFare(quote.baseFare());
        ctx.setTotalPrice(quote.total());
        return ctx;
    }

    @ParameterizedTest(name = "Seat Map Layout Check at width={0}")
    @ValueSource(doubles = {1100.0, 900.0})
    @DisplayName("Gate 4b: Seat Map layout verification at 1100 and 900 wide (no ellipsis, width >= prefWidth - 0.5, maxX within bounds)")
    void testSeatMapLayout(double width) throws Exception {
        verifyLayout("/com/group2/flightres/fxml/seat-map.fxml", width, 750.0, true);
    }

    @ParameterizedTest(name = "Checkout Layout Check at width={0}")
    @ValueSource(doubles = {1100.0, 900.0})
    @DisplayName("Gate 4b: Checkout layout verification at 1100 and 900 wide (no ellipsis, width >= prefWidth - 0.5, maxX within bounds)")
    void testCheckoutLayout(double width) throws Exception {
        verifyLayout("/com/group2/flightres/fxml/checkout.fxml", width, 750.0, false);
    }

    private void verifyLayout(String fxmlPath, double width, double height, boolean isSeatMap) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> violationsRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                Parent root = loader.load();
                assertNotNull(root);

                BookingContext ctx = createTestContext();
                if (isSeatMap) {
                    SeatMapController ctrl = loader.getController();
                    ctrl.init(ctx, () -> {});
                    ctrl.selectSeatByLabel("7C");
                } else {
                    CheckoutController ctrl = loader.getController();
                    ctrl.init(ctx, () -> {});
                }

                StackPane container = new StackPane(root);
                Scene scene = new Scene(container, width, height);
                stage.setScene(scene);
                stage.setWidth(width);
                stage.setHeight(height);
                stage.show();

                // Wait two layout pulses for CSS and layout passes to settle
                Platform.runLater(() -> {
                    Platform.runLater(() -> {
                        try {
                            container.applyCss();
                            container.layout();

                            List<Labeled> labeledNodes = new ArrayList<>();
                            collectLabeledNodes(root, labeledNodes);

                            Bounds rootBounds = root.localToScene(root.getBoundsInLocal());
                            double rootMaxX = rootBounds.getMaxX();

                            List<String> violations = new ArrayList<>();
                            for (Labeled l : labeledNodes) {
                                if (l.getText() == null || l.getText().isEmpty()) continue;
                                if (!isTreeVisible(l)) continue;

                                // 1. Ellipsis check on underlying Text node
                                Text textNode = (Text) l.lookup(".text");
                                if (textNode != null && textNode.getText() != null && !textNode.getText().isEmpty()) {
                                    String rendered = textNode.getText();
                                    String expected = l.getText();
                                    if (rendered.contains("\u2026") || rendered.endsWith("...") || !rendered.equals(expected)) {
                                        violations.add("Truncated label [" + l.getText() + "]: rendered='" + rendered + "' expected='" + expected + "'");
                                    }
                                }

                                // 2. Assertion (i): label width >= label.prefWidth(-1) - 0.5
                                double prefW = l.prefWidth(-1);
                                double actualW = l.getWidth();
                                if (actualW < prefW - 0.5) {
                                    violations.add("Label [" + l.getText() + "] width " + String.format(Locale.US, "%.1f", actualW)
                                            + " < prefWidth " + String.format(Locale.US, "%.1f", prefW) + " - 0.5");
                                }

                                // 3. Assertion (ii): label's scene bounds maxX <= root scene bounds maxX + 0.5
                                Bounds labelBounds = l.localToScene(l.getBoundsInLocal());
                                double labelMaxX = labelBounds.getMaxX();
                                if (labelMaxX > rootMaxX + 0.5) {
                                    violations.add("Label [" + l.getText() + "] scene maxX " + String.format(Locale.US, "%.1f", labelMaxX)
                                            + " > root maxX " + String.format(Locale.US, "%.1f", rootMaxX) + " + 0.5");
                                }
                            }
                            violationsRef.set(violations);
                        } catch (Throwable t) {
                            errorRef.set(t);
                        } finally {
                            stage.hide();
                            latch.countDown();
                        }
                    });
                });
            } catch (Throwable t) {
                errorRef.set(t);
                stage.hide();
                latch.countDown();
            }
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Timed out waiting for JavaFX layout pass");
        if (errorRef.get() != null) {
            throw new RuntimeException("Error during layout check of " + fxmlPath, errorRef.get());
        }
        List<String> violations = violationsRef.get();
        assertTrue(violations != null && violations.isEmpty(),
                "Violations in " + fxmlPath + " at width " + width + ":\n" + (violations != null ? String.join("\n", violations) : "null"));
    }

    private static boolean isTreeVisible(Node node) {
        Node curr = node;
        while (curr != null) {
            if (!curr.isVisible() || !curr.isManaged()) {
                return false;
            }
            curr = curr.getParent();
        }
        return true;
    }

    private static void collectLabeledNodes(Node node, List<Labeled> list) {
        if (node instanceof Labeled l) {
            list.add(l);
        }
        if (node instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                collectLabeledNodes(c, list);
            }
        }
    }
}
