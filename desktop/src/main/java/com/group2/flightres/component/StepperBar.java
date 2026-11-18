package com.group2.flightres.component;

import com.group2.flightres.model.BookingStep;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable Sky Royal Stepper component showing the 4 booking steps:
 * 1 Search -> 2 Select Seat -> 3 Checkout -> 4 Confirmed.
 * - Done: Green circle with white checkmark
 * - Active: Gold circle with navy/white step number
 * - Pending: Grey circle with white step number
 * Connector lines: 64x2 with 16 margin (green if done, translucent if pending).
 */
public class StepperBar extends HBox {

    private static final String CHECKMARK = "\u2713";

    private static class StepView {
        final int order;
        final String title;
        final StackPane circle;
        final Label circleText;
        final Label stepTitleLabel;
        final HBox stepBox;

        StepView(int order, String title) {
            this.order = order;
            this.title = title;

            this.circle = new StackPane();
            this.circle.getStyleClass().add("step-circle");

            this.circleText = new Label(String.valueOf(order + 1));
            this.circleText.getStyleClass().add("step-circle-text");
            this.circle.getChildren().add(circleText);

            this.stepTitleLabel = new Label(title);
            this.stepTitleLabel.getStyleClass().add("step-label");

            this.stepBox = new HBox(9, circle, stepTitleLabel);
            this.stepBox.getStyleClass().add("step-item");
            this.stepBox.setAlignment(Pos.CENTER);
        }
    }

    private final List<StepView> stepViews = new ArrayList<>();
    private final List<Region> lineViews = new ArrayList<>();
    private int currentStepOrder = 0;
    private boolean allCompleted = false;

    public StepperBar() {
        initUI();
    }

    private void initUI() {
        getStyleClass().add("stepper-container");
        setAlignment(Pos.CENTER);

        BookingStep[] steps = BookingStep.values();
        for (int i = 0; i < steps.length; i++) {
            StepView stepView = new StepView(steps[i].getOrder(), steps[i].getDisplayName());
            stepViews.add(stepView);
            getChildren().add(stepView.stepBox);

            if (i < steps.length - 1) {
                Region line = new Region();
                line.getStyleClass().addAll("step-arrow", "step-line");
                HBox.setMargin(line, new Insets(0, 16, 0, 16));
                lineViews.add(line);
                getChildren().add(line);
            }
        }

        updateVisuals();
    }

    public void setActiveStep(BookingStep step) {
        if (step != null) {
            this.currentStepOrder = step.getOrder();
            this.allCompleted = (step == BookingStep.CONFIRMATION);
            updateVisuals();
        }
    }

    public void setStepOrder(int order, boolean completed) {
        this.currentStepOrder = order;
        this.allCompleted = completed;
        updateVisuals();
    }

    public void setAllCompleted(boolean completed) {
        this.allCompleted = completed;
        updateVisuals();
    }

    private void updateVisuals() {
        for (StepView step : stepViews) {
            step.circle.getStyleClass().removeAll("step-circle-done", "step-circle-active", "step-circle-pending");
            step.stepTitleLabel.getStyleClass().removeAll("step-label-done", "step-label-active", "step-label-pending");

            if (allCompleted || step.order < currentStepOrder) {
                // Done
                step.circle.getStyleClass().add("step-circle-done");
                step.circleText.setText(CHECKMARK);
                step.stepTitleLabel.getStyleClass().add("step-label-done");
            } else if (step.order == currentStepOrder) {
                // Active
                step.circle.getStyleClass().add("step-circle-active");
                step.circleText.setText(String.valueOf(step.order + 1));
                step.stepTitleLabel.getStyleClass().add("step-label-active");
            } else {
                // Pending
                step.circle.getStyleClass().add("step-circle-pending");
                step.circleText.setText(String.valueOf(step.order + 1));
                step.stepTitleLabel.getStyleClass().add("step-label-pending");
            }
        }

        for (int i = 0; i < lineViews.size(); i++) {
            Region line = lineViews.get(i);
            line.getStyleClass().removeAll("step-line-done", "step-line-pending");
            if (allCompleted || i < currentStepOrder) {
                line.getStyleClass().add("step-line-done");
            } else {
                line.getStyleClass().add("step-line-pending");
            }
        }
    }
}
