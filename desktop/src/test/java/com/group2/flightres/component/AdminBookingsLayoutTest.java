// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.component;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.AdminBookingsController;
import com.group2.flightres.model.AdminBookingRow;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollBar;
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
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBookingsLayoutTest {

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

    private static User adminUser() {
        User u = new User();
        u.setUserId(1L);
        u.setEmail("testadmin@example.com");
        u.setFullName("David Admin");
        u.setRole(UserRole.ADMIN);
        return u;
    }

    private static User customerUser() {
        User u = new User();
        u.setUserId(12L);
        u.setEmail("demo.traveller@example.com");
        u.setFullName("Demo Traveller");
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    @ParameterizedTest(name = "Admin Bookings layout check at width {0}")
    @ValueSource(doubles = {1280.0, 1100.0, 900.0})
    @DisplayName("Admin Bookings renders at 1280, 1100 and 900 with zero ellipsis, zero overflow, and zero h-scroll")
    void testLayoutAtWidths(double width) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<List<String>> violationsRef = new AtomicReference<>(new ArrayList<>());

        Platform.runLater(() -> {
            Stage stage = new Stage();
            java.util.List<String> cssWarnings = new ArrayList<>();
            java.util.logging.Handler cssHandler = new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord record) {
                    if (record != null && record.getMessage() != null && record.getMessage().contains("Could not resolve")) {
                        cssWarnings.add(record.getMessage());
                    }
                }
                @Override public void flush() {}
                @Override public void close() {}
            };
            java.util.logging.Logger cssLogger = java.util.logging.Logger.getLogger("javafx.scene.CssStyleHelper");
            cssLogger.addHandler(cssHandler);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-bookings.fxml"));
                Parent root = loader.load();
                AdminBookingsController controller = loader.getController();
                controller.init(adminUser());

                StackPane container = new StackPane(root);
                Scene scene = new Scene(container, width, 800);
                ThemeManager.getInstance().setTheme(ThemeManager.Theme.DARK);
                ThemeManager.getInstance().applyTheme(scene);
                ThemeManager.getInstance().applyGlassTheme(scene);
                stage.setScene(scene);
                stage.show();

                Platform.runLater(() -> Platform.runLater(() -> {
                    try {
                        controller.updateResponsiveLayout(width, 800);
                        container.applyCss();
                        container.layout();

                        Insets padding = controller.getCardMarginWrapper().getPadding();
                        if (width < 1100) {
                            assertEquals(20.0, padding.getLeft(), 0.01, "Compact padding must be 20 at width 900");
                            assertTrue(controller.getMainContainer().getStyleClass().contains("admin-main-card-compact"));
                            assertEquals(1, controller.getBookingsGrid().getColumnConstraints().size(),
                                    "Bookings grid must collapse to 1 column at width 900");
                            assertTrue(controller.getFiltersToggleBtn().isVisible(), "Compact Filters toggle must be visible at 900");
                            assertFalse(controller.getFilterSidebar().isVisible(), "Filter sidebar must start collapsed at 900");
                            Bounds tblBounds = controller.getTableCard().localToScene(controller.getTableCard().getBoundsInLocal());
                            assertTrue(tblBounds.getMinY() < 680.0, "Table must be visible in first screen at 900x680, was y=" + tblBounds.getMinY());
                        } else {
                            assertEquals(56.0, padding.getLeft(), 0.01, "Standard padding must be 56 at width >= 1100");
                            assertTrue(controller.getMainContainer().getStyleClass().contains("admin-main-card"));
                            assertEquals(3, controller.getBookingsGrid().getColumnConstraints().size(),
                                    "Bookings grid must use 3 columns at width >= 1100");
                        }

                        // Table header x == column cell x within 1.5px
                        javafx.scene.layout.HBox firstRow = (javafx.scene.layout.HBox) controller.getRowsContainer().getChildren().get(0);
                        assertHeaderAligned(controller.getHdrRefLabel(), firstRow.getChildren().get(0), "REFERENCE", width);
                        assertHeaderAligned(controller.getHdrRouteLabel(), firstRow.getChildren().get(2), "ROUTE", width);
                        assertHeaderAligned(controller.getHdrSeatLabel(), firstRow.getChildren().get(3), "SEAT", width);
                        assertHeaderAligned(controller.getHdrTotalLabel(), firstRow.getChildren().get(4), "TOTAL", width);
                        assertHeaderAligned(controller.getHdrStatusLabel(), firstRow.getChildren().get(5), "STATUS", width);

                        assertTrue(cssWarnings.isEmpty(), "Unresolved CSS tokens detected: " + String.join("; ", cssWarnings));

                        assertTrue(controller.getOuterScrollPane().isVisible());
                        assertFalse(controller.getRowsContainer().getChildren().isEmpty());
                        assertNotNull(controller.getSelectedRow());
                        assertEquals(controller.getSelectedRow().bookingReference(), controller.getDetailPnrLabel().getText());

                        // No horizontal scrollbar visible
                        for (ScrollBar sb : collectScrollBars(controller.getMainContainer())) {
                            if (sb.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) {
                                assertFalse(sb.isVisible(), "Horizontal scrollbar must not be visible at width " + width);
                            }
                        }

                        // Zero truncated labels & zero overflow outside viewport
                        List<String> violations = new ArrayList<>();
                        Bounds viewportBounds = container.localToScene(container.getBoundsInLocal());
                        List<Labeled> labels = new ArrayList<>();
                        collectLabeledNodes(controller.getMainContainer(), labels);
                        for (Labeled l : labels) {
                            if (l.getText() == null || l.getText().isEmpty() || !isTreeVisible(l)) continue;
                            Text textNode = (Text) l.lookup(".text");
                            if (textNode != null && textNode.getText() != null && !textNode.getText().isEmpty()) {
                                String rendered = textNode.getText();
                                if (rendered.contains("\u2026") || rendered.endsWith("...")) {
                                    violations.add("Truncated label [" + l.getText() + "]: rendered='" + rendered + "'");
                                }
                            }
                            Bounds b = l.localToScene(l.getBoundsInLocal());
                            if (b.getMaxX() > viewportBounds.getMaxX() + 2.0 || b.getMinX() < viewportBounds.getMinX() - 2.0) {
                                violations.add("Overflowing label [" + l.getText() + "] at width " + width);
                            }
                        }

                        // Detail panel content inside detailPanel bounds
                        Bounds detailBounds = controller.getDetailPanel().localToScene(controller.getDetailPanel().getBoundsInLocal());
                        Bounds pnrBounds = controller.getDetailPnrLabel().localToScene(controller.getDetailPnrLabel().getBoundsInLocal());
                        if (pnrBounds.getMaxX() > detailBounds.getMaxX() + 2.0) {
                            violations.add("Detail PNR overflows detailPanel at width " + width);
                        }

                        violationsRef.set(violations);
                    } catch (Throwable t) {
                        errorRef.set(t);
                    } finally {
                        cssLogger.removeHandler(cssHandler);
                        stage.close();
                        latch.countDown();
                    }
                }));
            } catch (Throwable t) {
                errorRef.set(t);
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(45, TimeUnit.SECONDS), "Admin Bookings layout test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("Admin Bookings layout failed at width " + width, errorRef.get());
        }
        assertTrue(violationsRef.get().isEmpty(), "Violations: " + String.join(", ", violationsRef.get()));
    }

    @Test
    @DisplayName("RBAC, States (clear stale counts), 100+ rows pagination, and Flights tab")
    void testStatesRbacAndLargeDataset() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                // 1. Denied
                FXMLLoader deniedLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-bookings.fxml"));
                deniedLoader.load();
                AdminBookingsController deniedCtrl = deniedLoader.getController();
                deniedCtrl.init(customerUser());
                assertTrue(deniedCtrl.getDeniedStateContainer().isVisible(), "CUSTOMER must see denied state");
                assertFalse(deniedCtrl.getOuterScrollPane().isVisible());

                // 2. 100+ rows (120 total, 25 on page 1) -> counts match rendered rows -> empty/error clear counts
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-bookings.fxml"));
                loader.load();
                AdminBookingsController ctrl = loader.getController();

                ZoneId sast = ZoneId.of("Africa/Johannesburg");
                List<AdminBookingRow> page1Rows = new ArrayList<>();
                for (int i = 1; i <= 25; i++) {
                    page1Rows.add(new AdminBookingRow(
                            i, "PNR" + String.format("%03d", i), "Amahle Mokoena", "amahle@example.com", "Amahle Mokoena",
                            1L, "ALT 204", "JNB", "Johannesburg", "CPT", "Cape Town",
                            Instant.now().plusSeconds(7200), sast, Instant.now().plusSeconds(14400), sast,
                            "9E", SeatClass.ECONOMY, new BigDecimal("1522.00"), "CONFIRMED"
                    ));
                }
                ctrl.populateRows(new AdminBookingsPage(page1Rows, 120, new BigDecimal("182640.00"), 1, 25));
                assertEquals(25, ctrl.getRowsContainer().getChildren().size());
                assertEquals("120 bookings", ctrl.getTotalCountLabel().getText());
                assertEquals("Showing 1 to 25", ctrl.getShowingRangeLabel().getText());
                assertEquals("Page 1 of 5", ctrl.getPaginationLabel().getText());

                ctrl.showEmptyState();
                assertTrue(ctrl.getEmptyStateContainer().isVisible());
                assertEquals("0 bookings", ctrl.getTotalCountLabel().getText(), "Empty state must clear stale count");
                assertEquals("Showing 0 to 0", ctrl.getShowingRangeLabel().getText());

                ctrl.showErrorState("DB error");
                assertTrue(ctrl.getErrorStateContainer().isVisible());
                assertEquals("0 bookings", ctrl.getTotalCountLabel().getText(), "Error state must clear stale count");
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("States/RBAC/100+ rows UI test failed", errorRef.get());
        }
    }

    private static void assertHeaderAligned(Node header, Node cell, String col, double width) {
        double hx = header.localToScene(header.getBoundsInLocal()).getMinX();
        double cx = cell.localToScene(cell.getBoundsInLocal()).getMinX();
        assertTrue(Math.abs(hx - cx) <= 1.5,
                "Header " + col + " minX=" + hx + " must align with cell minX=" + cx + " at width " + width);
    }

    private static List<ScrollBar> collectScrollBars(Parent root) {
        List<ScrollBar> out = new ArrayList<>();
        Set<Node> nodes = root.lookupAll(".scroll-bar");
        for (Node n : nodes) {
            if (n instanceof ScrollBar sb) out.add(sb);
        }
        return out;
    }

    private static void collectLabeledNodes(Parent parent, List<Labeled> out) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Labeled l) {
                out.add(l);
            }
            if (child instanceof Parent p) {
                collectLabeledNodes(p, out);
            }
        }
    }

    private static boolean isTreeVisible(Node node) {
        Node cur = node;
        while (cur != null) {
            if (!cur.isVisible() || !cur.isManaged()) return false;
            cur = cur.getParent();
        }
        return true;
    }
}
