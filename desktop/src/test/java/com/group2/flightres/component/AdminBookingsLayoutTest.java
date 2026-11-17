// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.component;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.AdminBookingsController;
import com.group2.flightres.model.AdminBookingRow;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AdminBookingsService;
import com.group2.flightres.util.FontLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
        System.setProperty("altitude.testing", "true");
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
        u.setFullName("Test Admin");
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
    @ValueSource(doubles = {1100.0, 900.0})
    @DisplayName("Admin Bookings renders at 1100 and 900 without clipping or truncation")
    void testLayoutAtWidths(double width) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<List<String>> violationsRef = new AtomicReference<>(new ArrayList<>());

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-bookings.fxml"));
                Parent root = loader.load();
                AdminBookingsController controller = loader.getController();
                controller.init(adminUser());

                StackPane container = new StackPane(root);
                Scene scene = new Scene(container, width, 760);
                stage.setScene(scene);
                stage.setWidth(width);
                stage.setHeight(760);
                stage.show();

                Platform.runLater(() -> Platform.runLater(() -> {
                    try {
                        controller.updateResponsiveLayout(width, 760);
                        container.applyCss();
                        container.layout();

                        Insets padding = controller.getCardMarginWrapper().getPadding();
                        if (width < 1100) {
                            assertEquals(20.0, padding.getLeft(), 0.01, "Compact padding must be 20 at width 900");
                            assertTrue(controller.getMainContainer().getStyleClass().contains("admin-main-card-compact"));
                        } else {
                            assertEquals(56.0, padding.getLeft(), 0.01, "Standard padding must be 56 at width 1100");
                            assertTrue(controller.getMainContainer().getStyleClass().contains("admin-main-card"));
                        }

                        assertTrue(controller.getSplitBody().isVisible());
                        assertFalse(controller.getRowsContainer().getChildren().isEmpty());
                        assertNotNull(controller.getSelectedRow());
                        assertEquals(controller.getSelectedRow().bookingReference(), controller.getSelectedPnrLabel().getText());

                        List<String> violations = new ArrayList<>();
                        List<Labeled> labels = new ArrayList<>();
                        collectLabeledNodes(controller.getRowsContainer(), labels);
                        for (Labeled l : labels) {
                            if (l.getText() == null || l.getText().isEmpty() || !isTreeVisible(l)) continue;
                            Text textNode = (Text) l.lookup(".text");
                            if (textNode != null && textNode.getText() != null && !textNode.getText().isEmpty()) {
                                String rendered = textNode.getText();
                                if (rendered.contains("\u2026") || rendered.endsWith("...")) {
                                    violations.add("Truncated label [" + l.getText() + "]: rendered='" + rendered + "'");
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
    @DisplayName("RBAC & States: CUSTOMER denied, Empty result shows empty state, DAO failure shows error state")
    void testStatesAndRbacUi() throws Exception {
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
                assertFalse(deniedCtrl.getAdminNavLinksBox().isVisible(), "Admin nav links must be hidden for CUSTOMER");

                // 2. Empty
                AdminBookingsService emptyService = new AdminBookingsService(
                        (s, st, p, sz) -> new AdminBookingsPage(List.of(), 0L, BigDecimal.ZERO, p, sz));
                FXMLLoader emptyLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-bookings.fxml"));
                emptyLoader.setControllerFactory(c -> new AdminBookingsController(emptyService));
                emptyLoader.load();
                AdminBookingsController emptyCtrl = emptyLoader.getController();
                emptyCtrl.init(adminUser());
                assertTrue(emptyCtrl.getEmptyStateContainer().isVisible(), "Empty search must show empty state");

                // 3. Error
                AdminBookingsService errService = new AdminBookingsService((s, st, p, sz) -> {
                    throw new IllegalStateException("DB down");
                });
                FXMLLoader errLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-bookings.fxml"));
                errLoader.setControllerFactory(c -> new AdminBookingsController(errService));
                errLoader.load();
                AdminBookingsController errCtrl = errLoader.getController();
                errCtrl.init(adminUser());
                assertTrue(errCtrl.getErrorStateContainer().isVisible(), "DAO failure must show error state");
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("Admin Bookings states/RBAC UI test failed", errorRef.get());
        }
    }

    @Test
    @DisplayName("100+ booking rows UI build performance")
    void test100PlusBookingsRowsPerformance() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-bookings.fxml"));
                loader.load();
                AdminBookingsController ctrl = loader.getController();

                List<AdminBookingRow> rows = new ArrayList<>();
                Instant dep = Instant.parse("2026-11-20T06:30:00Z");
                Instant arr = dep.plusSeconds(7800);
                ZoneId jhb = ZoneId.of("Africa/Johannesburg");
                for (int i = 0; i < 120; i++) {
                    rows.add(new AdminBookingRow(100L + i, "PNR" + String.format("%03d", i),
                            "Passenger " + i, "p" + i + "@example.com", "Passenger " + i,
                            10L, "ALT 204", "JNB", "Johannesburg", "CPT", "Cape Town",
                            dep, jhb, arr, jhb, "7A", SeatClass.ECONOMY, new BigDecimal("1560.00"), "CONFIRMED"));
                }

                long start = System.currentTimeMillis();
                ctrl.getRowsContainer().getChildren().clear();
                for (AdminBookingRow row : rows) {
                    ctrl.getRowsContainer().getChildren().add(ctrl.buildBookingTableRow(row));
                }
                long elapsed = System.currentTimeMillis() - start;

                assertEquals(120, ctrl.getRowsContainer().getChildren().size());
                assertTrue(elapsed < 5000, "120 booking rows took " + elapsed + " ms");
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("100+ booking rows test failed", errorRef.get());
        }
    }

    private static void collectLabeledNodes(Node node, List<Labeled> list) {
        if (node instanceof Labeled l) list.add(l);
        if (node instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) collectLabeledNodes(c, list);
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
