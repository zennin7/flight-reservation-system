package com.group2.flightres.controller;

import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.exception.ValidationException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.util.ValidationUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Locale;

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

    private final BookingService bookingService = new BookingService();
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
        if (confirmBookingButton != null) {
            com.group2.flightres.util.Motion.attachHoverLift(confirmBookingButton);
        }
        if (backButton != null) {
            com.group2.flightres.util.Motion.attachHoverLift(backButton);
        }
        populateData();
    }

    private void populateData() {
        if (bookingContext == null) {
            return;
        }
        populateFlightAndPassengerLabels();
        populatePriceSummary();
        populateInputFields();
    }

    private void populateFlightAndPassengerLabels() {
        if (flightLabel != null) {
            flightLabel.setText(bookingContext.getFlightRouteDisplay());
        }
        if (departsLabel != null) {
            departsLabel.setText(bookingContext.getDepartureDisplay());
        }
        String passenger = resolvePassengerName();
        if (passengerLabel != null) {
            passengerLabel.setText(!passenger.isBlank() ? passenger : "Guest");
        }
        if (seatLabel != null) {
            seatLabel.setText(bookingContext.getSeatDisplay());
        }
    }

    private void populatePriceSummary() {
        BigDecimal base = bookingContext.getBaseFare() != null ? bookingContext.getBaseFare() : BigDecimal.ZERO;
        if (bookingContext.getClassMultiplier() != null && bookingContext.getClassMultiplier().compareTo(BigDecimal.ONE) > 0) {
            base = base.multiply(bookingContext.getClassMultiplier());
        }
        if (baseFareLabel != null) baseFareLabel.setText(formatPrice(base));
        if (demandPricingLabel != null) demandPricingLabel.setText(formatPrice(bookingContext.getDemandPricing()));
        if (taxesFeesLabel != null) taxesFeesLabel.setText(formatPrice(bookingContext.getTaxesAndFees()));
        if (totalLabel != null) totalLabel.setText(formatPrice(bookingContext.getTotalPrice()));
    }

    private void populateInputFields() {
        String passenger = resolvePassengerName();
        if (fullNameField != null) {
            if (!passenger.isBlank()) {
                fullNameField.setText(passenger);
            }
            fullNameField.textProperty().addListener((obs, oldV, newV) -> {
                if (passengerLabel != null) {
                    passengerLabel.setText(newV.isBlank() ? "Guest" : newV);
                }
            });
        }
        if (emailField != null) {
            String email = resolvePassengerEmail();
            if (!email.isBlank()) {
                emailField.setText(email);
            }
        }
        if (passportField != null && bookingContext.getPassportNumber() != null) {
            passportField.setText(bookingContext.getPassportNumber());
        }
    }

    private String resolvePassengerName() {
        String name = bookingContext.getPassengerName();
        if ((name == null || name.isBlank()) && bookingContext.getCurrentUser() != null) {
            name = bookingContext.getCurrentUser().getFullName();
        }
        return name != null ? name : "";
    }

    private String resolvePassengerEmail() {
        String email = bookingContext.getPassengerEmail();
        if ((email == null || email.isBlank()) && bookingContext.getCurrentUser() != null) {
            email = bookingContext.getCurrentUser().getEmail();
        }
        return email != null ? email : "";
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "R 0";
        return String.format(Locale.US, "R %,d", price.longValue());
    }

    @FXML
    private void handleConfirm() {
        String fullName = (fullNameField != null && fullNameField.getText() != null)
                ? fullNameField.getText().trim() : "";
        String email = (emailField != null && emailField.getText() != null)
                ? emailField.getText().trim() : "";
        String passport = (passportField != null && passportField.getText() != null)
                ? passportField.getText().trim() : "";

        if (!validateInputs(fullName, email, passport)) {
            return;
        }

        if (bookingContext != null) {
            bookingContext.setPassengerName(fullName);
            bookingContext.setPassengerEmail(email);
            bookingContext.setPassportNumber(passport);
        }

        if (bookingContext == null || bookingContext.getSelectedSeats() == null || bookingContext.getSelectedSeats().isEmpty()) {
            showAlert("Missing Seat Selection", "No seats selected. Please select a seat first.");
            return;
        }

        try {
            BookingRecord record = bookingService.createBooking(bookingContext);
            bookingContext.setBookingReference(record.getBookingReference());
            logger.info("Successfully confirmed booking '{}' in database.", record.getBookingReference());
        } catch (SeatUnavailableException sue) {
            logger.warn("Seat hold expired or taken: {}", sue.getMessage());
            showAlert("Seat Unavailable", sue.getMessage());
            if (onBack != null) {
                onBack.run();
            }
            return;
        } catch (Exception ex) {
            logger.warn("Database booking transaction failed: {}", ex.getMessage());
            if (bookingContext.getBookingReference() == null || bookingContext.getBookingReference().isBlank()) {
                bookingContext.generateBookingReference();
            }
        }

        logger.info("Proceeding to confirmation for: {} (Ref: {})", fullName,
                (bookingContext != null ? bookingContext.getBookingReference() : "N/A"));

        if (onNext != null) {
            onNext.run();
        }
    }

    private boolean validateInputs(String fullName, String email, String passport) {
        if (fullName.isEmpty()) {
            showAlert("Missing Information", "Please enter the passenger's full name.");
            return false;
        }
        if (email.isEmpty()) {
            showAlert("Missing Information", "Please enter a valid email address for confirmation.");
            return false;
        }
        try {
            ValidationUtil.validatePassengerName(fullName);
            ValidationUtil.validateEmail(email);
            if (!passport.isEmpty()) {
                ValidationUtil.validatePassportNumber(passport);
            }
            return true;
        } catch (ValidationException ve) {
            showAlert("Validation Error", ve.getMessage());
            return false;
        }
    }

    private void showAlert(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    @FXML
    private void handleBack() {
        logger.info("Going back to Seat Selection step.");
        if (onBack != null) {
            onBack.run();
        }
    }
}
