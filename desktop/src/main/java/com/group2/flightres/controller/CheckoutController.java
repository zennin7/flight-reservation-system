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
        populateData();
    }

    private void populateData() {
        if (bookingContext == null) {
            return;
        }

        if (flightLabel != null) {
            flightLabel.setText(bookingContext.getFlightRouteDisplay());
        }
        if (departsLabel != null) {
            departsLabel.setText(bookingContext.getDepartureDisplay());
        }

        String passenger = bookingContext.getPassengerName();
        if ((passenger == null || passenger.isBlank()) && bookingContext.getCurrentUser() != null) {
            passenger = bookingContext.getCurrentUser().getFullName();
        }
        if (passengerLabel != null) {
            passengerLabel.setText(passenger != null && !passenger.isBlank() ? passenger : "Guest");
        }

        if (seatLabel != null) {
            seatLabel.setText(bookingContext.getSeatDisplay());
        }

        BigDecimal base = bookingContext.getBaseFare();
        if (base == null) base = BigDecimal.ZERO;
        if (bookingContext.getClassMultiplier() != null && bookingContext.getClassMultiplier().compareTo(BigDecimal.ONE) > 0) {
            base = base.multiply(bookingContext.getClassMultiplier());
        }

        if (baseFareLabel != null) {
            baseFareLabel.setText(formatPrice(base));
        }
        if (demandPricingLabel != null) {
            demandPricingLabel.setText(formatPrice(bookingContext.getDemandPricing()));
        }
        if (taxesFeesLabel != null) {
            taxesFeesLabel.setText(formatPrice(bookingContext.getTaxesAndFees()));
        }
        if (totalLabel != null) {
            totalLabel.setText(formatPrice(bookingContext.getTotalPrice()));
        }

        if (fullNameField != null) {
            if (passenger != null && !passenger.isBlank()) {
                fullNameField.setText(passenger);
            }
            fullNameField.textProperty().addListener((obs, oldV, newV) -> {
                if (passengerLabel != null) {
                    passengerLabel.setText(newV.isBlank() ? "Guest" : newV);
                }
            });
        }

        if (emailField != null) {
            String email = bookingContext.getPassengerEmail();
            if ((email == null || email.isBlank()) && bookingContext.getCurrentUser() != null) {
                email = bookingContext.getCurrentUser().getEmail();
            }
            if (email != null && !email.isBlank()) {
                emailField.setText(email);
            }
        }

        if (passportField != null && bookingContext.getPassportNumber() != null) {
            passportField.setText(bookingContext.getPassportNumber());
        }
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

        if (fullName.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please enter the passenger's full name.");
            alert.setHeaderText("Missing Information");
            alert.showAndWait();
            return;
        }

        if (email.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please enter a valid email address for confirmation.");
            alert.setHeaderText("Missing Information");
            alert.showAndWait();
            return;
        }

        try {
            ValidationUtil.validatePassengerName(fullName);
            ValidationUtil.validateEmail(email);
            if (!passport.isEmpty()) {
                ValidationUtil.validatePassportNumber(passport);
            }
        } catch (ValidationException ve) {
            Alert alert = new Alert(Alert.AlertType.WARNING, ve.getMessage());
            alert.setHeaderText("Validation Error");
            alert.showAndWait();
            return;
        }

        if (bookingContext != null) {
            bookingContext.setPassengerName(fullName);
            bookingContext.setPassengerEmail(email);
            bookingContext.setPassportNumber(passport);
        }

        try {
            if (bookingContext != null && bookingContext.getSelectedSeats() != null && !bookingContext.getSelectedSeats().isEmpty()) {
                BookingRecord record = bookingService.createBooking(bookingContext);
                bookingContext.setBookingReference(record.getBookingReference());
                logger.info("Successfully confirmed booking '{}' in database.", record.getBookingReference());
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING, "No seats selected. Please select a seat first.");
                alert.setHeaderText("Missing Seat Selection");
                alert.showAndWait();
                return;
            }
        } catch (SeatUnavailableException sue) {
            logger.warn("Seat hold expired or taken: {}", sue.getMessage());
            Alert alert = new Alert(Alert.AlertType.WARNING, sue.getMessage());
            alert.setHeaderText("Seat Unavailable");
            alert.showAndWait();
            if (onBack != null) {
                onBack.run();
            }
            return;
        } catch (Exception ex) {
            logger.warn("Database booking transaction failed: {}", ex.getMessage());
            // Offline / DB unavailable fallback
            if (bookingContext != null && (bookingContext.getBookingReference() == null || bookingContext.getBookingReference().isBlank())) {
                bookingContext.generateBookingReference();
            }
        }

        logger.info("Proceeding to confirmation for: {} (Ref: {})", fullName,
                (bookingContext != null ? bookingContext.getBookingReference() : "N/A"));

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
