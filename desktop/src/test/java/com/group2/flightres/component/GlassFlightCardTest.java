package com.group2.flightres.component;

import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.GlassFlightResultsBridge;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlassFlightCardTest {

    @BeforeAll
    static void initJavaFX() {
        System.setProperty("altitude.testing", "true");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
        Platform.setImplicitExit(false);
        try {
            FontLoader.loadFonts();
        } catch (Throwable ignored) {}
    }

    @Test
    @DisplayName("Glass card: Synthetic flight to DXB dynamically binds all labels with NO hard-coded defaults")
    void testSyntheticFlightCardBinding() throws Exception {
        FlightSearchResult dxbFlight = new FlightSearchResult(
                99L,
                "DXB 777",
                "DOH",
                "Hamad International Airport",
                "Doha",
                ZoneId.of("Asia/Qatar"),
                "DXB",
                "Dubai International Airport",
                "Dubai",
                ZoneId.of("Asia/Dubai"),
                Instant.parse("2026-11-02T10:00:00Z"),
                Instant.parse("2026-11-02T11:15:00Z"),
                new BigDecimal("3500"),
                "Boeing 777-300ER",
                45
        );

        AtomicReference<Node> cardRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                GlassFlightResultsBridge bridge = new GlassFlightResultsBridge();
                Node card = bridge.buildFlightResultCard(dxbFlight, true);
                cardRef.set(card);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for JavaFX thread");
        Node card = cardRef.get();
        assertNotNull(card, "Card node should be created");

        List<String> labelTexts = new ArrayList<>();
        collectLabelTexts(card, labelTexts);

        // Verify NO hard-coded placeholder strings from old card
        for (String text : labelTexts) {
            assertFalse(text.contains("O. R. Tambo"), "Must not contain hardcoded 'O. R. Tambo', found: " + text);
            assertFalse(text.contains("Cape Town"), "Must not contain hardcoded 'Cape Town', found: " + text);
            assertFalse(text.contains("JNB"), "Must not contain hardcoded 'JNB', found: " + text);
            assertFalse(text.contains("CPT"), "Must not contain hardcoded 'CPT', found: " + text);
            assertFalse(text.contains("Airbus A320"), "Must not contain hardcoded 'Airbus A320', found: " + text);
            assertFalse(text.contains("Via Durban"), "Must not contain 'Via Durban', found: " + text);
            assertFalse(text.contains("Free meal"), "Must not contain 'Free meal', found: " + text);
            assertFalse(text.contains("Snack"), "Must not contain 'Snack', found: " + text);
            assertFalse(text.contains("18 seats left"), "Must not contain '18 seats left', found: " + text);
        }

        // Verify expected dynamic values are present
        boolean foundOrigin = labelTexts.stream().anyMatch(t -> t.contains("DOH, Hamad Intl"));
        boolean foundDest = labelTexts.stream().anyMatch(t -> t.contains("DXB, Dubai Intl"));
        boolean foundMeta = labelTexts.stream().anyMatch(t -> t.contains("DXB 777 · Boeing 777-300ER"));
        boolean foundDuration = labelTexts.stream().anyMatch(t -> t.contains("1h 15m · Direct"));
        boolean foundTag = labelTexts.stream().anyMatch(t -> t.contains("Direct · 45 seats available"));

        assertTrue(foundOrigin, "Card must display dynamic origin: DOH, Hamad Intl");
        assertTrue(foundDest, "Card must display dynamic destination: DXB, Dubai Intl");
        assertTrue(foundMeta, "Card must display dynamic metadata: DXB 777 · Boeing 777-300ER");
        assertTrue(foundDuration, "Card must display dynamic duration: 1h 15m · Direct");
        assertTrue(foundTag, "Card must display dynamic availability: Direct · 45 seats available");
    }

    @Test
    @DisplayName("Glass card: Expanded flight information leg labels do not truncate at 1280x800")
    void testExpandedFlightInfoNoTruncation() throws Exception {
        FlightSearchResult flight = createTestFlight();
        verifyCardAtWidth(flight, 1280, 800, 824);
    }

    @Test
    @DisplayName("Glass card: Expanded flight information leg labels do not truncate or overflow at 1100x700 and 900x680")
    void testExpandedFlightInfoResponsiveWidths() throws Exception {
        FlightSearchResult flight = createTestFlight();
        verifyCardAtWidth(flight, 1100, 700, 644);
        verifyCardAtWidth(flight, 900, 680, 444);
    }

    private FlightSearchResult createTestFlight() {
        return new FlightSearchResult(
                2L,
                "GRP 310",
                "JNB",
                "O. R. Tambo International Airport",
                "Johannesburg",
                ZoneId.of("Africa/Johannesburg"),
                "CPT",
                "Cape Town International Airport",
                "Cape Town",
                ZoneId.of("Africa/Johannesburg"),
                Instant.parse("2026-11-02T07:15:00Z"),
                Instant.parse("2026-11-02T09:25:00Z"),
                new BigDecimal("1450"),
                "Airbus A320",
                147
        );
    }

    private void verifyCardAtWidth(FlightSearchResult flight, double sceneWidth, double sceneHeight, double resultsWidth) throws Exception {
        AtomicReference<List<String>> violationsRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                GlassFlightResultsBridge bridge = new GlassFlightResultsBridge();
                Node card = bridge.buildFlightResultCard(flight, true); // expanded = true

                VBox container = new VBox(card);
                container.setPrefWidth(resultsWidth);
                container.setMaxWidth(resultsWidth);
                container.setMinWidth(resultsWidth);

                Scene scene = new Scene(container, sceneWidth, sceneHeight);
                String cssUrl = getClass().getResource("/com/group2/flightres/css/glass.css").toExternalForm();
                scene.getStylesheets().add(cssUrl);

                stage.setScene(scene);
                stage.setWidth(sceneWidth);
                stage.setHeight(sceneHeight);
                stage.show();

                // Wait for layout pulse via Platform.runLater twice
                Platform.runLater(() -> {
                    Platform.runLater(() -> {
                        try {
                            container.applyCss();
                            container.layout();

                            List<Labeled> labeledNodes = new ArrayList<>();
                            collectLabeledNodes(card, labeledNodes);

                            Bounds cardBounds = card.localToScene(card.getBoundsInLocal());
                            double cardMaxX = cardBounds.getMaxX();

                            List<String> violations = new ArrayList<>();
                            for (Labeled l : labeledNodes) {
                                if (l.getText() == null || l.getText().isEmpty()) continue;
                                if (!l.isVisible()) continue;

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

                                // 3. Assertion (ii): label's scene bounds maxX <= card's scene bounds maxX + 0.5
                                Bounds labelBounds = l.localToScene(l.getBoundsInLocal());
                                double labelMaxX = labelBounds.getMaxX();
                                if (labelMaxX > cardMaxX + 0.5) {
                                    violations.add("Label [" + l.getText() + "] scene maxX " + String.format(Locale.US, "%.1f", labelMaxX)
                                            + " > card maxX " + String.format(Locale.US, "%.1f", cardMaxX) + " + 0.5");
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

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for JavaFX pulse");
        if (errorRef.get() != null) {
            throw new RuntimeException("Error during card layout verification", errorRef.get());
        }
        List<String> violations = violationsRef.get();
        assertTrue(violations.isEmpty(), "Violations at sceneWidth " + sceneWidth + " (resultsWidth " + resultsWidth + "):\n" + String.join("\n", violations));
    }

    private void collectLabelTexts(Node node, List<String> list) {
        if (node instanceof Label lbl && lbl.getText() != null) {
            list.add(lbl.getText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectLabelTexts(child, list);
            }
        }
    }

    // Reused helper from GlassConfirmationVerifier Check 16
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
