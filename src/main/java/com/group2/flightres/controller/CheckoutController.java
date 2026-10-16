package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for SkyVoyage Screen 05 Checkout.
 */
public class CheckoutController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    @FXML private Label flightLabel;
    @FXML private Label departsLabel;
    @FXML private Label passengerLabel;
    @FXML private Label seatLabel;
    @FXML private Label baseFareLabel;
    @FXML private Label demandPricingLabel;
    @FXML private Label taxesFeesLabel;
    @FXML private Label totalLabel;

    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField passportField;

    @FXML private Button confirmBookingButton;
    @FXML private Button backButton;

    private BookingContext bookingContext;
    private Runnable onNext;
    private Runnable onBack;

    @Override
    public void init(BookingContext context, Runnable onNext) {
        init(context, onNext, null);
    }

    @Override
    public void init(BookingContext context, Runnable onNext, Runnable onBack) {
        this.bookingContext = context;
        this.onNext = onNext;
        this.onBack = onBack;
        populateData();
    }

    @FXML
    public void initialize() {
        populateData();
    }

    private void populateData() {
        // Sample data matching 05_checkout reference PNG
        // TODO(wire-real-data): bind from bookingContext and current user profile
        if (flightLabel != null) flightLabel.setText("GRP 204 · JNB to CPT");
        if (departsLabel != null) departsLabel.setText("2 Nov 2026 · 06:30");
        if (passengerLabel != null) passengerLabel.setText("David M.");
        if (seatLabel != null) seatLabel.setText("7E · Economy");

        if (baseFareLabel != null) baseFareLabel.setText("R 1,200");
        if (demandPricingLabel != null) demandPricingLabel.setText("R 180");
        if (taxesFeesLabel != null) taxesFeesLabel.setText("R 142");
        if (totalLabel != null) totalLabel.setText("R 1,522");

        if (fullNameField != null) fullNameField.setText("David M.");
        if (emailField != null) emailField.setText("david@example.com");
        if (passportField != null) {
            passportField.setText("");
            passportField.setPromptText("A1234567");
        }
    }

    @FXML
    private void handleConfirm() {
        logger.info("Confirming booking for: {}", (fullNameField != null ? fullNameField.getText() : "Unknown"));
        if (onNext != null) {
            onNext.run();
        }
    }

    @FXML
    private void handleBack() {
        logger.info("Going back to Seat Selection step.");
        if (onBack != null) {
            onBack.run();
        }
    }
}
