package com.group2.flightres.component;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.MyBookingsController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.util.JavaFxTestSupport;
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
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Warm database connection pool
        } catch (Exception ignored) {}
        JavaFxTestSupport.ensureStartedWithFonts();
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
                    "SR " + (200 + i),
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

                            // 3. Bounds, prefWidth, and ellipsis checks on 2-column web layout
                            List<String> violations = new ArrayList<>();
                            for (Node card : controller.getCardsContainer().getChildren()) {
                                Bounds cardBounds = card.localToScene(card.getBoundsInLocal());
                                double cardMaxX = cardBounds.getMaxX();
                                if (cardMaxX > width + 0.5) {
                                    violations.add("Card maxX " + cardMaxX + " exceeds width " + width);
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

                            // 4. Verify live white pass preview card at desktop widths (>= 1000) and hidden at compact (900)
                            Node passPreview = root.lookup("#passPreviewCard");
                            assertNotNull(passPreview, "#passPreviewCard must exist in 2-column My Bookings layout");
                            if (width >= 1000) {
                                assertTrue(passPreview.isVisible(), "#passPreviewCard must be visible at width " + width);
                                Bounds previewBounds = passPreview.localToScene(passPreview.getBoundsInLocal());
                                if (previewBounds.getMaxX() > width + 0.5) {
                                    violations.add("Pass preview maxX " + previewBounds.getMaxX() + " exceeds width " + width);
                                }
                                javafx.scene.image.ImageView qrView = (javafx.scene.image.ImageView) passPreview.lookup("#previewQrImageView");
                                assertNotNull(qrView, "#previewQrImageView must exist in pass preview");
                                assertNotNull(qrView.getImage(), "Live pass preview must render a real QR code image");
                                assertNotNull(passPreview.lookup("#previewDownloadPdfBtn"), "Download PDF button must exist");
                                assertNotNull(passPreview.lookup("#previewAddCalendarBtn"), "Add to calendar button must exist");
                                assertNotNull(passPreview.lookup("#previewViewPassBtn"), "View pass button must exist");
                            } else {
                                assertFalse(passPreview.isVisible(), "#passPreviewCard must collapse cleanly at width " + width);
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

                // Build a booking row card and verify keyboard focus + accessible text, plus preview action buttons
                BookingSummary summary = createMockSummaries(1).get(0);
                Node card = controller.buildBookingCard(summary);
                assertTrue(card.isFocusTraversable(), "Selectable booking row must be keyboard focusable");
                assertNotNull(card.getAccessibleText(), "Selectable booking row must have accessible text");
                assertTrue(card.getAccessibleText().contains(summary.getBookingReference()),
                        "Booking row accessible text must include PNR");

                Button viewPassBtn = (Button) root.lookup("#previewViewPassBtn");
                assertNotNull(viewPassBtn, "#previewViewPassBtn must exist in pass preview");
                assertTrue(viewPassBtn.isFocusTraversable(), "View pass button must be focusTraversable");
                Button downloadPdfBtn = (Button) root.lookup("#previewDownloadPdfBtn");
                assertNotNull(downloadPdfBtn, "#previewDownloadPdfBtn must exist in pass preview");
                assertTrue(downloadPdfBtn.isFocusTraversable(), "Download PDF button must be focusTraversable");
                Button addCalBtn = (Button) root.lookup("#previewAddCalendarBtn");
                assertNotNull(addCalBtn, "#previewAddCalendarBtn must exist in pass preview");
                assertTrue(addCalBtn.isFocusTraversable(), "Add to calendar button must be focusTraversable");
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
