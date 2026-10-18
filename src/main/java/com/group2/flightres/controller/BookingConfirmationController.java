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
    }

    @FXML
    private void handleBackToHome() {
        logger.info("Back to Home clicked.");
        if (onHome != null) {
            onHome.run();
        }
    }
}
