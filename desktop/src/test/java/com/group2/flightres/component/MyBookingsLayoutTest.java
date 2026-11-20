package com.group2.flightres.component;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.MyBookingsController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.util.FontLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyBookingsLayoutTest {

    @BeforeAll
    static void initJavaFX() {
        System.setProperty("skyroyal.testing", "true");
        try (Connection conn = DatabaseConfig.getConnection()) {
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

    private static List<BookingSummary> createMockSummaries(int count) {
        List<BookingSummary> list = new ArrayList<>();
        Instant baseTime = Instant.parse("2026-11-20T10:00:00Z");
        for (int i = 0; i < count; i++) {
            Instant dep = baseTime.plusSeconds(i * 3600L);
            Instant arr = dep.plusSeconds(7200L);
            list.add(new BookingSummary(
                    100L + i,
                    "PNR" + String.format("%03d", i),
                    12L,
                    "ALT " + (200 + i),
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
                    List.of((i % 20 + 1) + "A"),
                    new BigDecimal("1500.00"),
                    "Demo Traveller",
                    "demo.traveller@example.com",
                    "Airbus A320"
            ));
        }
        return list;
    }

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest(name = "Layout responsive check at width {0}")
    @ValueSource(doubles = {1280.0, 1100.0, 900.0})
    @DisplayName("My Bookings layout renders at 1280, 1100 and 900 without clipping, truncation, or tall connectors")
    void testLayoutAtWidths(double width) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<List<String>> violationsRef = new AtomicReference<>(new ArrayList<>());

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                BookingService stubService = new BookingService() {
                    @Override
                    public List<BookingSummary> listBookingsForUser(long userId, Clock clock) {
                        return createMockSummaries(6);
                    }
                };
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                loader.setControllerFactory(cls -> new MyBookingsController(stubService, FIXED_CLOCK));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                User user = new User();
                user.setUserId(12L);
                user.setEmail("demo.traveller@example.com");
                user.setFullName("Demo Traveller");
                user.setRole(UserRole.CUSTOMER);

                BookingContext ctx = new BookingContext();
                ctx.setCurrentUser(user);

                controller.init(ctx, () -> {});

                StackPane container = new StackPane(root);
                Scene scene = new Scene(container, width, 700);
                stage.setScene(scene);
                stage.setWidth(width);
                stage.setHeight(700);
                stage.show();

                Platform.runLater(() -> {
                    Platform.runLater(() -> {
                        try {
                            controller.updateResponsiveLayout(width, 700);
                            container.applyCss();
                            container.layout();

                            // 1. Verify responsive 900px compact breakpoint padding
                            Insets padding = controller.getCardMarginWrapper().getPadding();
                            if (width < 1100) {
                                assertEquals(20.0, padding.getLeft(), 0.01, "Left padding must be 20 at width 900");
                                assertEquals(20.0, padding.getRight(), 0.01, "Right padding must be 20 at width 900");
                            } else {
                                assertEquals(56.0, padding.getLeft(), 0.01, "Left padding must be 56 at width 1100");
                                assertEquals(56.0, padding.getRight(), 0.01, "Right padding must be 56 at width 1100");
                            }

                            // 2. Verify populated cards container
                            assertNotNull(controller.getCardsContainer());
                            assertFalse(controller.getAllBookings().isEmpty(), "Bookings must be loaded for demo user 12");
                            assertFalse(controller.getCardsContainer().getChildren().isEmpty(), "Cards must be rendered");

                            // 3. Bounds, prefWidth, and ellipsis checks (same rigour as GlassFlightCardTest)
                            List<String> violations = new ArrayList<>();
                            for (Node card : controller.getCardsContainer().getChildren()) {
                                Bounds cardBounds = card.localToScene(card.getBoundsInLocal());
                                double cardMaxX = cardBounds.getMaxX();
                                if (cardMaxX > width + 0.5) {
                                    violations.add("Card maxX " + cardMaxX + " exceeds width " + width);
                                }

                                // Verify route connector nodes stay thin (<= 2px height)
                                var connectors = card.lookupAll(".route-connector-line");
                                if (connectors.isEmpty()) {
                                    violations.add("Expected .route-connector-line nodes on booking card");
                                }
                                for (Node conn : connectors) {
                                    double connHeight = conn.getBoundsInParent().getHeight();
                                    if (connHeight <= 0.0 || connHeight > 2.0) {
                                        violations.add("Route connector height must be <= 2.0px, was " + connHeight);
                                    }
                                }

                                List<Labeled> labeledNodes = new ArrayList<>();
                                collectLabeledNodes(card, labeledNodes);
                                for (Labeled l : labeledNodes) {
                                    if (l.getText() == null || l.getText().isEmpty() || !isTreeVisible(l)) continue;

                                    Text textNode = (Text) l.lookup(".text");
                                    if (textNode != null && textNode.getText() != null && !textNode.getText().isEmpty()) {
                                        String rendered = textNode.getText();
                                        String expected = l.getText();
                                        if (rendered.contains("\u2026") || rendered.endsWith("...") || !rendered.equals(expected)) {
                                            violations.add("Truncated label [" + expected + "]: rendered='" + rendered + "'");
                                        }
                                    }

                                    double prefW = l.prefWidth(-1);
                                    double actualW = l.getWidth();
                                    if (actualW < prefW - 0.5) {
                                        violations.add("Label [" + l.getText() + "] width " + String.format(Locale.US, "%.1f", actualW)
                                                + " < prefWidth " + String.format(Locale.US, "%.1f", prefW) + " - 0.5");
                                    }

                                    Bounds labelBounds = l.localToScene(l.getBoundsInLocal());
                                    if (labelBounds.getMaxX() > cardMaxX + 0.5) {
                                        violations.add("Label [" + l.getText() + "] maxX " + String.format(Locale.US, "%.1f", labelBounds.getMaxX())
                                                + " > card maxX " + String.format(Locale.US, "%.1f", cardMaxX) + " + 0.5");
                                    }
                                }
                            }
                            violationsRef.set(violations);
                        } catch (Throwable t) {
                            errorRef.set(t);
                        } finally {
                            stage.close();
                            latch.countDown();
                        }
                    });
                });
            } catch (Throwable t) {
                errorRef.set(t);
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Layout test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("Layout test failed at width " + width, errorRef.get());
        }
        List<String> violations = violationsRef.get();
        assertTrue(violations.isEmpty(), "Layout violations at width " + width + ":\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("List Performance (R8d): 100+ booking rows render without lag")
    void testListPerformanceWith100Rows() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                List<BookingSummary> largeList = createMockSummaries(120);

                long startTime = System.currentTimeMillis();
                controller.getCardsContainer().getChildren().clear();
                for (BookingSummary summary : largeList) {
                    Node card = controller.buildBookingCard(summary);
                    controller.getCardsContainer().getChildren().add(card);
                }
                long elapsed = System.currentTimeMillis() - startTime;

                assertEquals(120, controller.getCardsContainer().getChildren().size());
                assertTrue(elapsed < 5000, "120 booking cards should build in under 5 seconds, took: " + elapsed + " ms");
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("100+ rows test failed", errorRef.get());
        }
    }

    @Test
    @DisplayName("Keyboard Navigation (R8c): Tab buttons and actions are accessible")
    void testKeyboardAndAccessibility() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                Parent root = loader.load();
                MyBookingsController controller = loader.getController();

                // Buttons should be focusTraversable
                assertTrue(controller.getUpcomingTabBtn().isFocusTraversable());
                assertTrue(controller.getPastTabBtn().isFocusTraversable());
                assertTrue(controller.getCancelledTabBtn().isFocusTraversable());

                // Build a card and verify accessible text on "View ticket" button
                BookingSummary summary = createMockSummaries(1).get(0);
                Node card = controller.buildBookingCard(summary);
                Button viewTicket = (Button) card.lookup(".btn-gold");
                assertNotNull(viewTicket);
                assertNotNull(viewTicket.getAccessibleText());
                assertTrue(viewTicket.getAccessibleText().contains(summary.getBookingReference()));
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("Keyboard/accessibility test failed", errorRef.get());
        }
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

    private static boolean isTreeVisible(Node node) {
        Node cur = node;
        while (cur != null) {
            if (!cur.isVisible()) return false;
            cur = cur.getParent();
        }
        return true;
    }
}
