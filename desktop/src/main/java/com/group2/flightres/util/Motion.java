package com.group2.flightres.util;

import javafx.animation.*;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Central animation and transition helper for Sky Royal JavaFX.
 * Conforms to the timing and easing rules of the javafx-motion skill.
 * Automatically disabled during ScreenshotRunner or when Sky Royal.motion=false.
 */
public final class Motion {

    private static boolean enabled = !Boolean.getBoolean("skyroyal.screenshots");

    public static boolean isEnabled() {
        return enabled && !"false".equalsIgnoreCase(System.getProperty("Sky Royal.motion"));
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Smooth 240ms ease-out screen transition: fades in while translating 10px up to 0.
     */
    public static void screenTransition(Node newRoot) {
        if (!isEnabled() || newRoot == null) return;
        newRoot.setOpacity(0.0);
        newRoot.setTranslateY(10.0);

        FadeTransition fade = new FadeTransition(Duration.millis(240), newRoot);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition translate = new TranslateTransition(Duration.millis(240), newRoot);
        translate.setFromY(10.0);
        translate.setToY(0.0);
        translate.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(fade, translate);
        pt.play();
    }

    /**
     * Tactile seat selection pop: scales from 1.0 -> 1.08 -> 1.0 in 160ms.
     */
    public static void pop(Node node) {
        if (!isEnabled() || node == null) return;
        ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.08);
        st.setToY(1.08);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.setInterpolator(Interpolator.EASE_OUT);
        st.play();
    }

    /**
     * Hover lift effect on card nodes: lifts 2px in 140ms.
     */
    public static void attachHoverLift(Node node) {
        if (!isEnabled() || node == null) return;
        node.setOnMouseEntered(e -> {
            if (!isEnabled()) return;
            TranslateTransition tt = new TranslateTransition(Duration.millis(140), node);
            tt.setToY(-2.0);
            tt.play();
        });
        node.setOnMouseExited(e -> {
            if (!isEnabled()) return;
            TranslateTransition tt = new TranslateTransition(Duration.millis(140), node);
            tt.setToY(0.0);
            tt.play();
        });
    }

    /**
     * Metric counter roll-up on reveal: counts from 0 to target in 650ms.
     */
    public static void countUp(Label label, long targetValue, String prefix, String suffix) {
        if (label == null) return;
        if (!isEnabled() || targetValue <= 0) {
            label.setText(prefix + targetValue + suffix);
            return;
        }

        LongProperty counter = new SimpleLongProperty(0);
        counter.addListener((obs, oldV, newV) ->
                label.setText(prefix + String.format(java.util.Locale.US, "%,d", newV.longValue()) + suffix)
        );

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(counter, 0)),
                new KeyFrame(Duration.millis(650), new KeyValue(counter, targetValue, Interpolator.EASE_OUT))
        );
        timeline.play();
    }

    private Motion() {}
}
