package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingStep;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MainShellController {

    @FXML private HBox stepperBar;
    @FXML private StackPane contentArea;

    private final BookingContext bookingContext = new BookingContext();
    private final Map<BookingStep, StackPane> stepCircles = new EnumMap<>(BookingStep.class);
    private final Map<BookingStep, Label> stepLabels = new EnumMap<>(BookingStep.class);
    private final List<Region> connectors = new ArrayList<>();

    private BookingStep currentStep;

    @FXML
    public void initialize() {
        buildStepper();
        goToStep(BookingStep.SEARCH);
    }

    private void buildStepper() {
        stepperBar.getChildren().clear();
        stepCircles.clear();
        stepLabels.clear();
        connectors.clear();

        BookingStep[] steps = BookingStep.values();
        for (int i = 0; i < steps.length; i++) {
            stepperBar.getChildren().add(buildStepNode(steps[i]));

            if (i < steps.length - 1) {
                Region connector = new Region();
                connector.getStyleClass().add("step-connector");
                HBox.setHgrow(connector, Priority.ALWAYS);
                stepperBar.getChildren().add(connector);
                connectors.add(connector);
            }
        }
    }

    private VBox buildStepNode(BookingStep step) {
        StackPane circle = new StackPane();
        circle.getStyleClass().add("step-circle");

        Label number = new Label(String.valueOf(step.getOrder() + 1));
        number.getStyleClass().add("step-label-text");
        circle.getChildren().add(number);

        Label name = new Label(step.getDisplayName());
        name.getStyleClass().add("step-name-label");

        VBox box = new VBox(6, circle, name);
        box.setAlignment(Pos.CENTER);

        stepCircles.put(step, circle);
        stepLabels.put(step, name);
        return box;
    }

    public void goToStep(BookingStep step) {
        loadStepContent(step);
        updateStepperVisuals(step);
        currentStep = step;
    }

    private void loadStepContent(BookingStep step) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(step.getFxmlPath()));
            Node view = loader.load();
            contentArea.getChildren().setAll(view);

            Object controller = loader.getController();
            if (controller instanceof WizardStepController wizardController) {
                wizardController.init(bookingContext, () -> goToStep(step.next()));
            }
            // NOTE: controllers not yet implementing WizardStepController
            // will simply load without wizard navigation wired in — this
            // is expected until each screen's controller is updated to
            // implement the interface.
        } catch (IOException e) {
            throw new RuntimeException("Failed to load wizard step: " + step, e);
        }
    }

    private void updateStepperVisuals(BookingStep activeStep) {
        for (BookingStep step : BookingStep.values()) {
            StackPane circle = stepCircles.get(step);
            Label label = stepLabels.get(step);

            circle.getStyleClass().removeAll("step-circle-pending", "step-circle-active", "step-circle-completed");
            label.getStyleClass().remove("step-name-label-active");

            if (step.getOrder() < activeStep.getOrder()) {
                circle.getStyleClass().add("step-circle-completed");
            } else if (step == activeStep) {
                circle.getStyleClass().add("step-circle-active");
                label.getStyleClass().add("step-name-label-active");
            } else {
                circle.getStyleClass().add("step-circle-pending");
            }
        }

        for (int i = 0; i < connectors.size(); i++) {
            connectors.get(i).getStyleClass().remove("step-connector-completed");
            if (i < activeStep.getOrder()) {
                connectors.get(i).getStyleClass().add("step-connector-completed");
            }
        }
    }

    public BookingContext getBookingContext() {
        return bookingContext;
    }
}
