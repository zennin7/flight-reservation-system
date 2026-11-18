package com.group2.flightres.component;

import com.group2.flightres.util.GlassSeatMapBridge;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatMapZoomTest {

    @BeforeAll
    static void initJavaFX() {
        System.setProperty("altitude.testing", "true");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
    }

    @Test
    @DisplayName("Seat map zoom toggles scale between 1.0 and 1.25 on zoomContainer")
    void testSeatMapZoomToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                StackPane root = new StackPane();
                VBox zoomContainer = new VBox();
                zoomContainer.setId("zoomContainer");
                zoomContainer.setScaleX(1.0);
                zoomContainer.setScaleY(1.0);
                root.getChildren().add(zoomContainer);

                GlassSeatMapBridge bridge = new GlassSeatMapBridge(zoomContainer);

                // Initial state
                assertFalse(bridge.isZoomed(), "Initially not zoomed");
                assertEquals(1.0, zoomContainer.getScaleX(), 0.001);
                assertEquals(1.0, zoomContainer.getScaleY(), 0.001);

                // First toggle -> zoomed in
                bridge.toggleZoom();
                assertTrue(bridge.isZoomed(), "Should be zoomed in after first toggle");
                assertEquals(1.25, zoomContainer.getScaleX(), 0.001);
                assertEquals(1.25, zoomContainer.getScaleY(), 0.001);

                // Second toggle -> restored to 1.0
                bridge.toggleZoom();
                assertFalse(bridge.isZoomed(), "Should be zoomed out after second toggle");
                assertEquals(1.0, zoomContainer.getScaleX(), 0.001);
                assertEquals(1.0, zoomContainer.getScaleY(), 0.001);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX thread should complete in 5s");
    }
}
