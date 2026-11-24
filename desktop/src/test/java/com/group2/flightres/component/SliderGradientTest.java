package com.group2.flightres.component;

import com.group2.flightres.util.GlassFlightResultsBridge;
import com.group2.flightres.util.JavaFxTestSupport;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderGradientTest {

    @BeforeAll
    static void initJavaFX() {
        JavaFxTestSupport.ensureStarted();
    }

    @Test
    @DisplayName("Slider dynamic gradient follows value at 0%, 50%, and 100%")
    void testSliderGradientDynamicFill() {
        StackPane track = new StackPane();
        track.getStyleClass().add("track");

        Slider testSlider = new Slider(0, 100, 0) {
            @Override
            public javafx.scene.Node lookup(String selector) {
                if (".track".equals(selector)) {
                    return track;
                }
                return super.lookup(selector);
            }
        };

        // Test 0%
        testSlider.setValue(0);
        GlassFlightResultsBridge.updateSliderGradient(testSlider);
        String style0 = track.getStyle();
        assertTrue(style0.contains("-glass-accent 0.00%"), "At 0%, accent stop must be 0.00%: " + style0);
        assertTrue(style0.contains("rgba(255, 255, 255, 0.12) 0.00%"), "At 0%, dim track stop must start at 0.00%: " + style0);

        // Test 50%
        testSlider.setValue(50);
        GlassFlightResultsBridge.updateSliderGradient(testSlider);
        String style50 = track.getStyle();
        assertTrue(style50.contains("-glass-accent 50.00%"), "At 50%, accent stop must be 50.00%: " + style50);
        assertTrue(style50.contains("rgba(255, 255, 255, 0.12) 50.00%"), "At 50%, dim track stop must start at 50.00%: " + style50);

        // Test 100%
        testSlider.setValue(100);
        GlassFlightResultsBridge.updateSliderGradient(testSlider);
        String style100 = track.getStyle();
        assertTrue(style100.contains("-glass-accent 100.00%"), "At 100%, accent stop must be 100.00%: " + style100);
        assertTrue(style100.contains("rgba(255, 255, 255, 0.12) 100.00%"), "At 100%, dim track stop must start at 100.00%: " + style100);
    }
}
