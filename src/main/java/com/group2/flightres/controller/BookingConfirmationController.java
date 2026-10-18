package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Locale;

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
        if (bookingContext == null) {
            return;
        }

        String email = bookingContext.getPassengerEmail();
        if (confirmationEmailLabel != null) {
            if (email != null && !email.isBlank()) {
                confirmationEmailLabel.setText("A confirmation has been sent to " + email);
            } else {
                confirmationEmailLabel.setText("A confirmation has been sent to your email");
            }
        }

        if (bookingRefLabel != null) {
            String ref = bookingContext.getBookingReference();
            bookingRefLabel.setText(ref != null && !ref.isBlank() ? ref : "—");
        }

        if (flightNumberLabel != null) {
            String fn = bookingContext.getFlightNumber();
            flightNumberLabel.setText(fn != null && !fn.isBlank() ? fn : "—");
        }

        if (seatNumberLabel != null) {
            String seat = bookingContext.getSeatLabel();
            seatNumberLabel.setText(seat != null && !seat.isBlank() ? seat : "—");
        }

        if (totalPaidLabel != null) {
            totalPaidLabel.setText(formatPrice(bookingContext.getTotalPrice()));
        }
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "R 0";
        return String.format(Locale.US, "R %,d", price.longValue());
    }

    @FXML
    private void handleDownloadPdf() {
        logger.info("Download Boarding Pass (PDF) clicked.");
        javafx.stage.Window window = (downloadPdfButton != null && downloadPdfButton.getScene() != null)
                ? downloadPdfButton.getScene().getWindow() : null;

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save Boarding Pass");
        String ref = (bookingContext != null && bookingContext.getBookingReference() != null && !bookingContext.getBookingReference().isBlank())
                ? bookingContext.getBookingReference() : "REF";
        fileChooser.setInitialFileName("BoardingPass_" + ref + ".pdf");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));

        java.io.File targetFile = fileChooser.showSaveDialog(window);
        if (targetFile == null) {
            logger.info("User cancelled saving boarding pass.");
            return;
        }

        try {
            com.group2.flightres.util.BoardingPassPdfGenerator.generate(bookingContext, targetFile);

            javafx.scene.control.Alert successAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            successAlert.setTitle("Boarding Pass Downloaded");
            successAlert.setHeaderText("Success!");
            successAlert.setContentText("Boarding pass successfully saved to:\n" + targetFile.getAbsolutePath());
            successAlert.show();

            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    java.awt.Desktop.getDesktop().open(targetFile);
                }
            } catch (Exception ex) {
                logger.warn("Could not automatically open PDF file via Desktop: {}", ex.getMessage());
            }

        } catch (Exception e) {
            logger.error("Failed to generate boarding pass PDF", e);
            javafx.scene.control.Alert errorAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            errorAlert.setTitle("Download Failed");
            errorAlert.setHeaderText("Error Generating PDF");
            errorAlert.setContentText("Could not create boarding pass: " + e.getMessage());
            errorAlert.showAndWait();
        }
    }

    @FXML
    private void handleBackToHome() {
        logger.info("Back to Home clicked.");
        if (onHome != null) {
            onHome.run();
        }
    }
}
