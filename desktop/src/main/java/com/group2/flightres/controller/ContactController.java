package com.group2.flightres.controller;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.model.BookingContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controller for Sky Royal Contact screen.
 */
public class ContactController implements WizardStepController {

    @FXML private Label supportEmailLabel;
    @FXML private Label supportPhoneLabel;
    @FXML private Button backToHomeBtn;
    private Runnable onBack;

    @FXML
    public void initialize() {
        if (supportEmailLabel != null) {
            supportEmailLabel.setText(BrandConfig.SUPPORT_EMAIL);
        }
        if (supportPhoneLabel != null) {
            supportPhoneLabel.setText(BrandConfig.SUPPORT_PHONE);
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
