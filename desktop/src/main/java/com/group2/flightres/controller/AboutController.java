package com.group2.flightres.controller;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.model.BookingContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controller for Sky Royal About screen.
 */
public class AboutController implements WizardStepController {

    @FXML private Label aboutTitleLabel;
    @FXML private Label projectNameLabel;
    @FXML private Button backToHomeBtn;
    private Runnable onBack;

    @FXML
    public void initialize() {
        if (aboutTitleLabel != null) {
            aboutTitleLabel.setText("About " + BrandConfig.APP_NAME);
        }
        if (projectNameLabel != null) {
            projectNameLabel.setText(BrandConfig.APP_NAME + " Flight Reservation System");
        }
    }

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
