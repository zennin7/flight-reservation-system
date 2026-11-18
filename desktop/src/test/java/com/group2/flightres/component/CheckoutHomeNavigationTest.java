package com.group2.flightres.component;

import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.util.FontLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutHomeNavigationTest {

    @BeforeAll
    static void initJavaFX() {
        System.setProperty("skyroyal.testing", "true");
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
    @DisplayName("Gate 4d: Checkout Home navigation opens the real home screen (home.fxml)")
    void testCheckoutNavHomeOpensHomeScreen() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
                Parent checkoutView = loader.load();
                assertNotNull(checkoutView);

                CheckoutController controller = loader.getController();
                assertNotNull(controller);

                BookingContext ctx = new BookingContext();
                ctx.setFlightId(1L);
                ctx.setFlightNumber("ALT 204");
                controller.init(ctx, () -> {});

                // Construct scene with #contentArea container as in AppWindow
                StackPane contentArea = new StackPane();
                contentArea.setId("contentArea");
                contentArea.getChildren().add(checkoutView);

                Scene scene = new Scene(contentArea, 1280, 800);
                assertNotNull(scene);

                // Invoke handleNavHome via reflection
                Method navHomeMethod = CheckoutController.class.getDeclaredMethod("handleNavHome");
                navHomeMethod.setAccessible(true);
                navHomeMethod.invoke(controller);

                // Verify contentArea now contains the loaded home.fxml view
                assertFalse(contentArea.getChildren().isEmpty(), "contentArea must contain loaded view");
                Node loadedView = contentArea.getChildren().get(0);
                assertNotNull(loadedView, "Loaded home screen must not be null");

                // Verify real home screen elements are present
                Node searchBtn = loadedView.lookup("#searchFlightsButton");
                Node fromCombo = loadedView.lookup("#fromCombo");
                Node toCombo = loadedView.lookup("#toCombo");

                assertNotNull(searchBtn, "Home screen must contain #searchFlightsButton");
                assertNotNull(fromCombo, "Home screen must contain #fromCombo");
                assertNotNull(toCombo, "Home screen must contain #toCombo");

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
