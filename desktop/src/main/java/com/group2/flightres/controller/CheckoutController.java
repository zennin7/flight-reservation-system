package com.group2.flightres.controller;

import com.group2.flightres.component.StepperBar;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.exception.ValidationException;
import com.group2.flightres.config.BaggagePolicy;
import com.group2.flightres.exception.PriceChangedException;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingStep;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PaymentService;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.util.DestinationPhotos;
import com.group2.flightres.util.GlassCheckoutBridge;
import com.group2.flightres.util.Navigator;
import com.group2.flightres.util.ValidationUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Controller for Sky Royal Screen 04/05 Glass Checkout.
 */
public class CheckoutController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    // Existing baseline fx:ids
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

    // Glass reskin fx:ids
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField dobField;
    @FXML private TextField cardNumberField;
    @FXML private TextField expiryField;
    @FXML private PasswordField cvvField;

    @FXML private Label cardPreviewNumber;
    @FXML private Label cardPreviewHolder;
    @FXML private Label cardPreviewExpiry;

    @FXML private Button cardTabButton;
    @FXML private Label baggageAllowanceLabel;
    @FXML private VBox errorContainer;
    @FXML private Label errorMessageLabel;
    @FXML private Button errorBackButton;

    @FXML private Label holdTimerLabel;
    @FXML private Label confirmTotalLabel;
    @FXML private Label orderSummaryBadge;

    @FXML private Label tripDateBadge;
    @FXML private Label tripOriginDestLabel;
    @FXML private Label tripDepTimeLabel;
    @FXML private Label tripArrTimeLabel;
    @FXML private Label tripDurationLabel;
    @FXML private Label tripFlightLabel;
    @FXML private Label tripSeatLabel;

    @FXML private StepperBar stepperBar;
    @FXML private ImageView tripHeroImageView;
    @FXML private Label tripOriginSubLabel;
    @FXML private Label tripDestSubLabel;

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final SeatMapService seatMapService;
    private BookingContext bookingContext;
    private Runnable onNext;
    private Runnable onBack;

    private GlassCheckoutBridge glassBridge;

    public CheckoutController() {
        this(new BookingService(), new SimulatedPaymentGateway(), new SeatMapService(new SeatDaoImpl()));
    }

    public CheckoutController(BookingService bookingService, PaymentService paymentService) {
        this(bookingService, paymentService, new SeatMapService(new SeatDaoImpl()));
    }

    public CheckoutController(BookingService bookingService, PaymentService paymentService, SeatMapService seatMapService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.seatMapService = seatMapService;
    }

    @Override
    public void init(BookingContext context, Runnable onNext) {
        init(context, onNext, null);
    }

    @Override
    public void init(BookingContext context, Runnable onNext, Runnable onBack) {
        this.bookingContext = context;
        this.onNext = onNext;
        this.onBack = onBack;

        if (stepperBar != null) {
            stepperBar.setActiveStep(BookingStep.CHECKOUT);
        }

        populateData();

        if (glassBridge != null) {
            glassBridge.stopTimer();
        }
        glassBridge = new GlassCheckoutBridge(this, bookingContext);
        glassBridge.wireCardPreview(firstNameField, lastNameField, cardNumberField, expiryField,
                cardPreviewNumber, cardPreviewHolder, cardPreviewExpiry);
        glassBridge.initCountdownTimer(holdTimerLabel, confirmBookingButton, this::handleHoldExpired);
    }

    @FXML
    public void initialize() {
        if (stepperBar != null) {
            stepperBar.setActiveStep(BookingStep.CHECKOUT);
        }
        populateData();
    }

    private void populateData() {
        if (bookingContext == null) {
            return;
        }
        populateTripCardLabels();
        populatePriceSummary();
        populateInputFields();
    }

    private void populateTripCardLabels() {
        // Date badge
        String depDate = bookingContext.getDepartureDate();
        if (tripDateBadge != null) {
            tripDateBadge.setText(formatDateBadge(depDate));
        }

        // Origin and Destination
        String dest = bookingContext.getDestination();
        if (tripOriginDestLabel != null) {
            tripOriginDestLabel.setText(resolveDestinationCity(dest));
        }

        // Sub-labels for Origin and Destination
        if (tripOriginSubLabel != null) {
            tripOriginSubLabel.setText(bookingContext.getOrigin() != null ? bookingContext.getOrigin() : "");
        }
        if (tripDestSubLabel != null) {
            tripDestSubLabel.setText(bookingContext.getDestination() != null ? bookingContext.getDestination() : "");
        }

        // Times
        String depTime = bookingContext.getDepartureTime();
        if (tripDepTimeLabel != null) {
            tripDepTimeLabel.setText((depTime != null && !depTime.isBlank()) ? depTime : "");
        }
        String arrTime = bookingContext.getArrivalTime();
        if (tripArrTimeLabel != null) {
            tripArrTimeLabel.setText((arrTime != null && !arrTime.isBlank()) ? arrTime : "");
        }

        // Trip Hero Photo
        if (tripHeroImageView != null) {
            String destCode = extractAirportCode(dest);
            DestinationPhotos.getImage(destCode).ifPresent(tripHeroImageView::setImage);
        }

        // Flight number
        String flightNo = bookingContext.getFlightNumber();
        if (tripFlightLabel != null) {
            tripFlightLabel.setText((flightNo != null && !flightNo.isBlank()) ? flightNo : "");
        }

        // Seat label
        String seatDisp = bookingContext.getSeatDisplay();
        if (tripSeatLabel != null) {
            tripSeatLabel.setText((seatDisp != null && !seatDisp.isBlank() && !"None".equals(seatDisp))
                    ? seatDisp : "");
        }
        if (orderSummaryBadge != null) {
            orderSummaryBadge.setText((seatDisp != null && !seatDisp.isBlank() && !"None".equals(seatDisp))
                    ? "Seat " + seatDisp : "");
        }

        // Baseline preserved labels
        if (flightLabel != null) flightLabel.setText(bookingContext.getFlightRouteDisplay());
        if (departsLabel != null) departsLabel.setText(bookingContext.getDepartureDisplay());
        if (seatLabel != null) seatLabel.setText(seatDisp);
    }

    private void populatePriceSummary() {
        if (bookingContext == null || bookingContext.getPriceQuotes() == null || bookingContext.getPriceQuotes().isEmpty()
                || bookingContext.getTotalPrice() == null || bookingContext.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            showNoQuoteErrorState("No price quote available for this booking. Please return to seat selection.");
            return;
        }

        if (errorContainer != null) {
            errorContainer.setVisible(false);
            errorContainer.setManaged(false);
        }
        if (confirmBookingButton != null) {
            confirmBookingButton.setDisable(false);
        }

        BigDecimal base = bookingContext.getPriceQuotes().stream()
                .map(PriceQuote::classFare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal demand = bookingContext.getPriceQuotes().stream()
                .map(PriceQuote::demandAdjustment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal timing = bookingContext.getPriceQuotes().stream()
                .map(PriceQuote::timingAdjustment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = bookingContext.getTotalPrice();

        if (baseFareLabel != null) baseFareLabel.setText(formatExactPrice(base));
        if (demandPricingLabel != null) demandPricingLabel.setText(formatExactPrice(demand));
        if (taxesFeesLabel != null) taxesFeesLabel.setText(formatExactPrice(timing));
        if (totalLabel != null) totalLabel.setText(formatTotalText(total));
        if (confirmTotalLabel != null) confirmTotalLabel.setText(formatTotalText(total));
    }

    private void showNoQuoteErrorState(String message) {
        if (errorContainer != null) {
            errorContainer.setVisible(true);
            errorContainer.setManaged(true);
        }
        if (errorMessageLabel != null) {
            errorMessageLabel.setText(message);
        }
        if (confirmBookingButton != null) {
            confirmBookingButton.setDisable(true);
        }
        if (baseFareLabel != null) baseFareLabel.setText("-");
        if (demandPricingLabel != null) demandPricingLabel.setText("-");
        if (taxesFeesLabel != null) taxesFeesLabel.setText("-");
        if (totalLabel != null) totalLabel.setText("-");
        if (confirmTotalLabel != null) confirmTotalLabel.setText("-");
    }

    private void populateInputFields() {
        // Only prefill if a logged-in user exists
        if (bookingContext.getCurrentUser() != null) {
            String full = bookingContext.getCurrentUser().getFullName();
            if (full != null && !full.isBlank()) {
                String[] parts = full.trim().split("\\s+", 2);
                if (firstNameField != null) firstNameField.setText(parts[0]);
                if (lastNameField != null && parts.length > 1) lastNameField.setText(parts[1]);
                if (fullNameField != null) fullNameField.setText(full);
            }
            String email = bookingContext.getCurrentUser().getEmail();
            if (emailField != null && email != null) {
                emailField.setText(email);
            }
        } else {
            // Keep fields empty by default per specification
            if (firstNameField != null) firstNameField.setText("");
            if (lastNameField != null) lastNameField.setText("");
            if (fullNameField != null) fullNameField.setText("");
            if (emailField != null) emailField.setText("");
            if (passportField != null) passportField.setText("");
            if (dobField != null) dobField.setText("");
            if (cardNumberField != null) cardNumberField.setText("");
            if (expiryField != null) expiryField.setText("");
            if (cvvField != null) cvvField.setText("");
        }

        // Live sync between firstName/lastName and fullNameField / passengerLabel
        if (firstNameField != null) {
            firstNameField.textProperty().addListener((obs, o, n) -> syncFullName());
        }
        if (lastNameField != null) {
            lastNameField.textProperty().addListener((obs, o, n) -> syncFullName());
        }
    }

    private void syncFullName() {
        String first = firstNameField != null ? firstNameField.getText().trim() : "";
        String last = lastNameField != null ? lastNameField.getText().trim() : "";
        String full = (first + " " + last).trim();
        if (fullNameField != null) {
            fullNameField.setText(full);
        }
        if (passengerLabel != null) {
            passengerLabel.setText(!full.isEmpty() ? full : "Guest");
        }
    }

    private String formatExactPrice(BigDecimal price) {
        if (price == null) return "R 0.00";
        return String.format(Locale.US, "R %,.2f", price.doubleValue());
    }

    private String formatTotalText(BigDecimal price) {
        if (price == null) return "R 0";
        return String.format(Locale.US, "R %,d", price.longValue());
    }

    private String formatDateBadge(String depDate) {
        if (depDate == null || depDate.isBlank()) {
            return "Sat, 14 Nov 2026";
        }
        return com.group2.flightres.util.FlightDateFormatter.format(depDate);
    }

    private String resolveDestinationCity(String dest) {
        if (bookingContext != null && bookingContext.getDestinationCity() != null && !bookingContext.getDestinationCity().isBlank()) {
            return bookingContext.getDestinationCity();
        }
        if (dest == null || dest.isBlank()) {
            return "";
        }
        String clean = dest.trim();
        int paren = clean.indexOf('(');
        if (paren > 0) {
            return clean.substring(0, paren).trim();
        }
        return clean;
    }

    public void clearCardFields() {
        if (cardNumberField != null) cardNumberField.clear();
        if (expiryField != null) expiryField.clear();
        if (cvvField != null) cvvField.clear();
    }

    private void refundIfApproved(PaymentResult payResult) {
        if (payResult != null && payResult.getTransactionReference() != null) {
            try {
                paymentService.refund(payResult.getTransactionReference());
                logger.info("Triggered simulated refund for transaction {}", payResult.getTransactionReference());
            } catch (Exception rex) {
                logger.error("Failed to refund after booking failure: {}", rex.getMessage(), rex);
            }
        }
    }

    @FXML
    public void handleConfirm() {
        if (bookingContext == null || bookingContext.getFlightId() == null
                || bookingContext.getSelectedSeats() == null || bookingContext.getSelectedSeats().isEmpty()) {
            showAlert("Incomplete Booking", "No flight or seat selected. Please return to flight search.");
            return;
        }
        // 1. Resolve passenger details
        String first = (firstNameField != null && firstNameField.getText() != null)
                ? firstNameField.getText().trim() : "";
        String last = (lastNameField != null && lastNameField.getText() != null)
                ? lastNameField.getText().trim() : "";
        String fullName = (first + " " + last).trim();
        if (fullName.isEmpty() && fullNameField != null && fullNameField.getText() != null) {
            fullName = fullNameField.getText().trim();
        }

        String email = (emailField != null && emailField.getText() != null)
                ? emailField.getText().trim() : "";
        String passport = (passportField != null && passportField.getText() != null)
                ? passportField.getText().trim() : "";
        String dobStr = (dobField != null && dobField.getText() != null)
                ? dobField.getText().trim() : "";

        // 2. Validate passenger fields
        if (fullName.isEmpty()) {
            showAlert("Missing Information", "Please enter the passenger's full name.");
            return;
        }
        if (email.isEmpty()) {
            showAlert("Missing Information", "Please enter a valid email address for confirmation.");
            return;
        }

        LocalDate parsedDob = null;
        try {
            ValidationUtil.validatePassengerName(fullName);
            ValidationUtil.validateEmail(email);
            if (!passport.isEmpty()) {
                ValidationUtil.validatePassportNumber(passport);
            }
            if (!dobStr.isEmpty()) {
                parsedDob = parseDateOfBirth(dobStr);
                ValidationUtil.validateDateOfBirth(parsedDob);
            }
        } catch (ValidationException ve) {
            showAlert("Validation Error", ve.getMessage());
            return;
        }

        if (bookingContext != null) {
            bookingContext.setPassengerName(fullName);
            bookingContext.setPassengerEmail(email);
            if (!passport.isEmpty()) {
                bookingContext.setPassportNumber(passport);
            }
            if (parsedDob != null) {
                bookingContext.setDateOfBirth(parsedDob);
            }
        }

        // 3. Validate seat selection
        if (bookingContext == null || bookingContext.getSelectedSeats() == null || bookingContext.getSelectedSeats().isEmpty()) {
            showAlert("Missing Seat Selection", "No seats selected. Please select a seat first.");
            return;
        }

        // 4. Validate and process payment card
        String cardNum = cardNumberField != null ? cardNumberField.getText() : "";
        String expiry = expiryField != null ? expiryField.getText() : "";
        String cvv = cvvField != null ? cvvField.getText() : "";

        if (bookingContext.getTotalPrice() == null || bookingContext.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            showAlert("Missing Quote", "No price quote available for this booking. Please return to seat selection.");
            return;
        }
        BigDecimal amount = bookingContext.getTotalPrice();

        PaymentResult payResult = paymentService.processPayment(cardNum, expiry, cvv, amount);
        if (!payResult.isApproved()) {
            logger.warn("Payment declined: {}", payResult.getErrorMessage());
            clearCardFields();
            showAlert("Payment Declined", payResult.getErrorMessage());
            return; // Seat remains HELD, no booking created
        }

        // Store PaymentResult in context (PCI compliant: carries brand, last4, ref, never PAN or CVV)
        bookingContext.setPaymentResult(payResult);

        // 5. Check if hold expired
        if (bookingContext.getRemainingHoldSeconds() <= 0) {
            refundIfApproved(payResult);
            clearCardFields();
            handleHoldExpired();
            return;
        }

        // 6. Execute atomic booking transaction
        try {
            BookingRecord record = bookingService.createBooking(bookingContext);
            bookingContext.setBookingReference(record.getBookingReference());
            logger.info("Successfully confirmed booking '{}' in database.", record.getBookingReference());
        } catch (PriceChangedException pce) {
            logger.warn("Price changed during checkout: {}", pce.getMessage());
            refundIfApproved(payResult);
            clearCardFields();
            showAlert("Fare Updated", String.format(Locale.US,
                    "The fare changed from R %,d to R %,d. The price summary has been refreshed.",
                    pce.getOldTotal().longValue(), pce.getNewTotal().longValue()));
            bookingContext.setTotalPrice(pce.getNewTotal());
            populatePriceSummary();
            return;
        } catch (SeatUnavailableException sue) {
            logger.warn("Seat hold expired or taken: {}", sue.getMessage());
            refundIfApproved(payResult);
            clearCardFields();
            showAlert("Seat Unavailable", sue.getMessage());
            if (onBack != null) {
                onBack.run();
            }
            return;
        } catch (Exception ex) {
            logger.error("Database booking transaction failed: {}", ex.getMessage(), ex);
            refundIfApproved(payResult);
            clearCardFields();
            showAlert("Booking Failed", "We couldn't complete your booking. You have not been charged.");
            return; // Stay on screen, NEVER fabricate a booking reference or navigate forward
        }

        // Clear card fields after payment attempt
        clearCardFields();

        // 7. Stop timer and proceed to confirmation
        if (glassBridge != null) {
            glassBridge.stopTimer();
        }

        logger.info("Proceeding to confirmation for: {} (Ref: {})", fullName,
                (bookingContext != null ? bookingContext.getBookingReference() : "N/A"));

        if (onNext != null) {
            onNext.run();
        }
    }

    public void handleHoldExpired() {
        Runnable r = () -> {
            if (confirmBookingButton != null) {
                confirmBookingButton.setDisable(true);
            }
            long holdMinutes = com.group2.flightres.config.SeatHoldPolicy.HOLD_SECONDS / 60;
            showAlert("Seat Hold Expired", "Your " + holdMinutes + "-minute seat hold has expired. Please select a seat again.");
            if (onBack != null) {
                onBack.run();
            }
        };
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    public static LocalDate parseDateOfBirth(String dobStr) {
        if (dobStr == null || dobStr.isBlank()) return null;
        String s = dobStr.trim();
        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy")
        };
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        throw new ValidationException("dateOfBirth", "Invalid date of birth format. Use e.g. 12 May 1998 or YYYY-MM-DD.");
    }

    private String lastAlertMessage;
    public String getLastAlertMessage() { return lastAlertMessage; }

    private void showAlert(String header, String message) {
        this.lastAlertMessage = message;
        logger.warn("Checkout Alert [{}]: {}", header, message);
        if (!Boolean.getBoolean("skyroyal.testing") && !Boolean.getBoolean("skyroyal.screenshots")) {
            Alert alert = new Alert(Alert.AlertType.WARNING, message);
            alert.setHeaderText(header);
            alert.showAndWait();
        }
    }

    // --- Tab and Navigation Handlers ---

    @FXML
    private void handleCardTab() {
        if (cardTabButton != null) cardTabButton.getStyleClass().add("tab-btn-active");
    }

    public void releaseHeldSeats() {
        if (seatMapService != null && bookingContext != null && bookingContext.getSelectedSeats() != null) {
            for (FlightSeat seat : bookingContext.getSelectedSeats()) {
                seatMapService.deselectSeat(seat);
            }
        }
    }

    @FXML
    private void handleNavHome() {
        releaseHeldSeats();
        navigateTo("/com/group2/flightres/fxml/home.fxml", "Home");
    }

    private void navigateTo(String fxmlPath, String screenName) {
        javafx.scene.Scene scene = null;
        if (confirmBookingButton != null && confirmBookingButton.getScene() != null) {
            scene = confirmBookingButton.getScene();
        }
        if (scene == null) return;

        javafx.scene.layout.StackPane contentArea =
                (javafx.scene.layout.StackPane) scene.lookup("#contentArea");
        if (contentArea != null) {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(fxmlPath));
                javafx.scene.Node view = loader.load();
                contentArea.getChildren().setAll(view);
                com.group2.flightres.util.Motion.screenTransition(view);

                javafx.scene.layout.VBox stepperWrapper =
                        (javafx.scene.layout.VBox) scene.lookup("#stepperWrapper");
                if (stepperWrapper != null) {
                    stepperWrapper.setVisible(false);
                    stepperWrapper.setManaged(false);
                }

                com.group2.flightres.component.NavBar nav =
                        (com.group2.flightres.component.NavBar) scene.lookup("#navBar");
                if (nav != null) {
                    nav.setActiveLink(screenName);
                }
            } catch (Exception e) {
                logger.error("Failed to navigate to {}", fxmlPath, e);
            }
        }
    }

    @FXML
    private void handleNavFlights() {
        handleBack();
    }

    @FXML
    private void handleComingSoonMyBookings() {
        Navigator.navigateToMyBookings(confirmBookingButton != null ? confirmBookingButton : backButton, bookingContext);
    }

    @FXML
    private void handleComingSoonOffers() {
        showAlert("Coming soon", "Offers aren't available yet.");
    }

    @FXML
    private void handleNavAbout() {
        showAlert("Coming soon", "About page isn't available yet.");
    }

    @FXML
    private void handleNavContact() {
        showAlert("Coming soon", "Contact page isn't available yet.");
    }

    @FXML
    private void handleNavAvatar() {
        showAlert("Coming soon", "User profile isn't available yet.");
    }

    @FXML
    public void handleBack() {
        logger.info("Going back to Seat Selection step.");
        clearCardFields();
        if (glassBridge != null) {
            glassBridge.stopTimer();
        }
        if (onBack != null) {
            onBack.run();
        }
    }

    // Getters for verification / testing
    public BookingContext getBookingContext() { return bookingContext; }
    public GlassCheckoutBridge getGlassBridge() { return glassBridge; }
    public TextField getFirstNameField() { return firstNameField; }
    public TextField getLastNameField() { return lastNameField; }
    public TextField getPassportField() { return passportField; }
    public TextField getDobField() { return dobField; }
    public TextField getEmailField() { return emailField; }
    public TextField getCardNumberField() { return cardNumberField; }
    public TextField getExpiryField() { return expiryField; }
    public PasswordField getCvvField() { return cvvField; }
    public Button getConfirmBookingButton() { return confirmBookingButton; }
    public Label getHoldTimerLabel() { return holdTimerLabel; }
    public Label getTripOriginDestLabel() { return tripOriginDestLabel; }

    private String extractAirportCode(String dest) {
        if (dest == null || dest.isBlank()) return "";
        int open = dest.lastIndexOf('(');
        int close = dest.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return dest.substring(open + 1, close).trim().toUpperCase();
        }
        return dest.trim().toUpperCase();
    }

    public ImageView getTripHeroImageView() { return tripHeroImageView; }
}
