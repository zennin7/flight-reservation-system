package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for SkyVoyage Screen 06 Booking Confirmation.
 */
public class BookingConfirmationController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(BookingConfirmationController.class);

    @FXML private Label confirmationEmailLabel;
    @FXML private Label bookingRefLabel;
    @FXML private Label flightNumberLabel;
    @FXML private Label seatNumberLabel;
    @FXML private Label totalPaidLabel;
    @FXML private Button downloadPdfButton;
    @FXML private Button backToHomeButton;

    private BookingContext bookingContext;
    private Runnable onHome;

    @Override
    public void init(BookingContext context, Runnable onHome) {
        this.bookingContext = context;
        this.onHome = onHome;
        populateData();
    }

    @FXML
    public void initialize() {
        populateData();
    }

    private void populateData() {
        // Sample data matching 06_confirmation reference PNG
        // TODO(wire-real-data): bind from real booking transaction
        if (confirmationEmailLabel != null) {
            confirmationEmailLabel.setText("A confirmation has been sent to david@example.com");
        }
        if (bookingRefLabel != null) bookingRefLabel.setText("K7P2QX");
        if (flightNumberLabel != null) flightNumberLabel.setText("GRP 204");
        if (seatNumberLabel != null) seatNumberLabel.setText("7E");
        if (totalPaidLabel != null) totalPaidLabel.setText("R 1,522");
    }

    @FXML
    private void handleDownloadPdf() {
        logger.info("Download Boarding Pass (PDF) clicked.");
    }

    @FXML
    private void handleBackToHome() {
        logger.info("Back to Home clicked.");
        if (onHome != null) {
            onHome.run();
        }
    }
}
