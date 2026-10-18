package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Controller for SkyVoyage About screen.
 */
public class AboutController implements WizardStepController {

    @FXML private Button backToHomeBtn;
    private Runnable onBack;

    @Override
    public void init(BookingContext context, Runnable onNext) {
        this.onBack = onNext;
    }

    @FXML
    private void handleBackToHome() {
        if (onBack != null) {
            onBack.run();
        }
    }
}
