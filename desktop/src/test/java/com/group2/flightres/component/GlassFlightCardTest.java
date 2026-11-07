package com.group2.flightres.component;

import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.util.GlassFlightResultsBridge;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
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
        FlightSearchResult flight = new FlightSearchResult(
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

        AtomicReference<Node> cardRef = new AtomicReference<>();
        AtomicReference<List<String>> truncationsRef = new AtomicReference<>(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                GlassFlightResultsBridge bridge = new GlassFlightResultsBridge();
                Node card = bridge.buildFlightResultCard(flight, true); // expanded = true
                cardRef.set(card);

                // Place in layout container at 824px results width within 1280x800 scene
                VBox container = new VBox(card);
                container.setPrefWidth(824);
                Scene scene = new Scene(container, 1280, 800);
                String cssUrl = getClass().getResource("/com/group2/flightres/css/glass.css").toExternalForm();
                scene.getStylesheets().add(cssUrl);
                container.applyCss();
                container.layout();

                // Reusing Check 16 truncation detection technique from GlassConfirmationVerifier
                List<Labeled> labeledNodes = new ArrayList<>();
                collectLabeledNodes(card, labeledNodes);

                List<String> truncations = new ArrayList<>();
                for (Labeled l : labeledNodes) {
                    if (l.getText() == null || l.getText().isEmpty()) continue;
                    Text textNode = (Text) l.lookup(".text");
                    if (textNode != null && textNode.getText() != null && !textNode.getText().isEmpty()) {
                        String rendered = textNode.getText();
                        String expected = l.getText();
                        if (rendered.contains("\u2026") || rendered.endsWith("...") || !rendered.equals(expected)) {
                            truncations.add("Truncated label [" + l.getText() + "]: rendered='" + rendered + "' expected='" + expected + "'");
                        }
                    }
                }
                truncationsRef.set(truncations);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for JavaFX thread");
        assertTrue(truncationsRef.get().isEmpty(), "No label in expanded card should be truncated:\n" + String.join("\n", truncationsRef.get()));
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
