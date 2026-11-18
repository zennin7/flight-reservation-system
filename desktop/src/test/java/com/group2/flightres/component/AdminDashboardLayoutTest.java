// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.component;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.AdminDashboardController;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.CabinLoadBreakdown;
import com.group2.flightres.model.AdminDashboardData.DailySeriesPoint;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.AdminDashboardData.Range;
import com.group2.flightres.model.AdminDashboardData.RecentBookingItem;
import com.group2.flightres.model.AdminDashboardData.TopDestinationItem;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.FontLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.GridPane;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDashboardLayoutTest {

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

    @ParameterizedTest(name = "Admin Dashboard layout check at width {0}")
    @ValueSource(doubles = {1280.0, 1100.0, 900.0})
    @DisplayName("Admin Dashboard renders at 1280, 1100 and 900 with 2x2 KPI reflow, zero ellipsis, and zero h-scroll")
    void testLayoutAtWidths(double width) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<List<String>> violationsRef = new AtomicReference<>(new ArrayList<>());

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-dashboard.fxml"));
                Parent root = loader.load();
                AdminDashboardController controller = loader.getController();
                controller.init(adminUser());

                StackPane container = new StackPane(root);
                Scene scene = new Scene(container, width, 800);
                scene.getStylesheets().add(getClass().getResource("/com/group2/flightres/css/glass.css").toExternalForm());
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
                            assertEquals(2, controller.getKpiGrid().getColumnConstraints().size(),
                                    "KPI grid must reflow to 2 columns (2x2) below 1100px");
                        } else {
                            assertEquals(56.0, padding.getLeft(), 0.01, "Standard padding must be 56 at width >= 1100");
                            assertTrue(controller.getMainContainer().getStyleClass().contains("admin-main-card"));
                            assertEquals(4, controller.getKpiGrid().getColumnConstraints().size(),
                                    "KPI grid must be 4 columns at width >= 1100px");
                        }

                        assertTrue(controller.getDashboardScrollPane().isVisible());
                        assertTrue(controller.getGreetingLabel().getText().contains("David"));

                        // Check no visible horizontal scrollbar
                        Set<Node> hBars = controller.getDashboardScrollPane().lookupAll(".scroll-bar");
                        for (Node n : hBars) {
                            if (n instanceof ScrollBar sb && sb.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) {
                                assertFalse(sb.isVisible(), "Horizontal scrollbar must not be visible at width " + width);
                            }
                        }

                        // Check zero truncated labels and bounds inside viewport
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
                        violationsRef.set(violations);
                    } catch (Throwable t) {
                        errorRef.set(t);
                    } finally {
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

        assertTrue(latch.await(45, TimeUnit.SECONDS), "Admin Dashboard layout test timed out");
        if (errorRef.get() != null) {
            throw new AssertionError("Admin Dashboard layout failed at width " + width, errorRef.get());
        }
        assertTrue(violationsRef.get().isEmpty(), "Violations: " + String.join(", ", violationsRef.get()));
    }

    @Test
    @DisplayName("RBAC & States: CUSTOMER denied, Empty and Error clear stale numbers, Populated renders charts & lists")
    void testStatesAndRbacUi() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                // 1. Denied
                FXMLLoader deniedLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-dashboard.fxml"));
                deniedLoader.load();
                AdminDashboardController deniedCtrl = deniedLoader.getController();
                deniedCtrl.init(customerUser());
                assertTrue(deniedCtrl.getDeniedStateContainer().isVisible(), "CUSTOMER must see denied state");
                assertFalse(deniedCtrl.getDashboardScrollPane().isVisible());

                // 2. Populated -> then Empty -> then Error (must clear stale numbers)
                FXMLLoader stateLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-dashboard.fxml"));
                stateLoader.load();
                AdminDashboardController ctrl = stateLoader.getController();

                ZoneId sast = ZoneId.of("Africa/Johannesburg");
                Instant dep = Instant.now().plusSeconds(7200);
                AdminDashboardData data = new AdminDashboardData(
                        Range.SEVEN_DAYS,
                        7, 16.7, new BigDecimal("28175.75"), 12.5, 9, 10.0, 1, null,
                        List.of(new DailySeriesPoint(LocalDate.now(sast), "Mon", 7, new BigDecimal("28175.75"), 9, 1)),
                        new CabinLoadBreakdown(7, 2, 171, 180, 3, 1),
                        List.of(new FlightLoadFactor(1, "ALT 101", "JNB", "CPT", dep, sast, 90, 100, 10, 90.0, "Scheduled")),
                        List.of(new FlightLoadFactor(1, "ALT 101", "JNB", "CPT", dep, sast, 90, 100, 10, 90.0, "Scheduled")),
                        List.of(new TopDestinationItem("CPT", "Cape Town", 5)),
                        List.of(new RecentBookingItem("9RQHX7", "Amahle Mokoena", "JNB -> CPT", "9E", new BigDecimal("3557.75")))
                );
                ctrl.populateData(data);
                assertTrue(ctrl.getDashboardScrollPane().isVisible());
                assertEquals("R 28,175.75", ctrl.getKpiRevenueLabel().getText());
                assertEquals("3", ctrl.getSeatsHeldLabel().getText());
                assertEquals("1", ctrl.getFlightsLowFreeLabel().getText());
                assertEquals("1 of 1", ctrl.getTodayFlightsCountLabel().getText());
                assertEquals(1, ctrl.getTodayFlightsList().getChildren().size());
                assertEquals(1, ctrl.getTopDestinationsList().getChildren().size());
                assertEquals(1, ctrl.getRecentBookingsList().getChildren().size());
                assertEquals(2, ctrl.getBookingsRevenueChart().getData().size());
                assertEquals(3, ctrl.getCabinDonutChart().getData().size());

                ctrl.showEmptyState();
                assertTrue(ctrl.getEmptyStateContainer().isVisible());
                assertFalse(ctrl.getDashboardScrollPane().isVisible());
                assertEquals("R 0.00", ctrl.getKpiRevenueLabel().getText(), "Empty state must clear stale revenue");
                assertEquals("0", ctrl.getKpiBookingsLabel().getText());

                ctrl.populateData(data);
                ctrl.showErrorState("DB unreachable");
                assertTrue(ctrl.getErrorStateContainer().isVisible());
                assertFalse(ctrl.getDashboardScrollPane().isVisible());
                assertEquals("R 0.00", ctrl.getKpiRevenueLabel().getText(), "Error state must clear stale revenue");

                // 3. Initials uniqueness, cabin 0.2% formatting, labelled chart axis, NavBar links
                assertEquals("DT", com.group2.flightres.util.NameInitials.of("Demo Traveller"));
                assertEquals("D2", com.group2.flightres.util.NameInitials.of("Demo Traveller II"));
                assertEquals("D3", com.group2.flightres.util.NameInitials.of("Demo Traveller III"));
                assertEquals("D4", com.group2.flightres.util.NameInitials.of("Demo Traveller IV"));
                assertEquals("AM", com.group2.flightres.util.NameInitials.of("Ms Amahle Mokoena"));

                assertEquals("0.2%", AdminDashboardController.formatCabinPercent(0.2));
                assertEquals("0%", AdminDashboardController.formatCabinPercent(0.0));
                assertEquals("68%", AdminDashboardController.formatCabinPercent(68.4));
                assertEquals("% of period peak", ctrl.getBookingsRevenueChart().getYAxis().getLabel());

                NavBar nav = new NavBar();
                nav.setAdminMode(true);
                List<String> navTexts = new ArrayList<>();
                collectLabeledNodes(nav, new ArrayList<>());
                for (Node n : nav.lookupAll(".nav-link")) {
                    if (n instanceof Labeled l) navTexts.add(l.getText());
                }
                assertEquals(List.of("Dashboard", "Flights", "Bookings"), navTexts,
                        "Admin NavBar must only expose real implemented destinations");
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("State/RBAC UI test failed", errorRef.get());
        }
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
