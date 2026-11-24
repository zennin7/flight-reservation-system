package com.group2.flightres.util;

import com.group2.flightres.config.SeatHoldPolicy;
import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.model.BookingContext;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Locale;

/**
 * Controller-bound Glass Bridge for Screen 04/05 (Checkout).
 * Coordinates live credit card preview synchronization, hold countdown timer,
 * trip summary card data binding, and payment tabs.
 */
public class GlassCheckoutBridge {

    private static final Logger logger = LoggerFactory.getLogger(GlassCheckoutBridge.class);

    private final CheckoutController controller;
    private final BookingContext bookingContext;

    private Timeline countdownTimeline;
    private Label holdTimerLabel;
    private Button confirmButton;
    private boolean stopped = false;

    public GlassCheckoutBridge(CheckoutController controller, BookingContext context) {
        this.controller = controller;
        this.bookingContext = context;
    }

    public void wireCardPreview(TextField firstNameField,
                                TextField lastNameField,
                                TextField cardNumberField,
                                TextField expiryField,
                                Label cardPreviewNumber,
                                Label cardPreviewHolder,
                                Label cardPreviewExpiry) {

        // Synchronize cardholder name
        Runnable updateHolder = () -> {
            String first = firstNameField != null ? firstNameField.getText().trim() : "";
            String last = lastNameField != null ? lastNameField.getText().trim() : "";
            String full = (first + " " + last).trim();
            if (cardPreviewHolder != null) {
                cardPreviewHolder.setText(!full.isEmpty() ? full.toUpperCase(Locale.ROOT) : "AMAHLE MOKOENA");
            }
        };

        if (firstNameField != null) {
            firstNameField.textProperty().addListener((obs, o, n) -> updateHolder.run());
        }
        if (lastNameField != null) {
            lastNameField.textProperty().addListener((obs, o, n) -> updateHolder.run());
        }

        // Synchronize card number
        if (cardNumberField != null) {
            cardNumberField.textProperty().addListener((obs, o, n) -> {
                String digits = (n != null) ? n.replaceAll("\\D+", "") : "";
                if (cardPreviewNumber != null) {
                    if (digits.length() >= 4) {
                        String last4 = digits.substring(digits.length() - 4);
                        cardPreviewNumber.setText("•••• •••• •••• " + last4);
                    } else if (!digits.isEmpty()) {
                        cardPreviewNumber.setText("•••• •••• •••• " + digits);
                    } else {
                        cardPreviewNumber.setText("•••• •••• •••• 4242");
                    }
                }
            });
        }

        // Synchronize expiry
        if (expiryField != null) {
            expiryField.textProperty().addListener((obs, o, n) -> {
                if (cardPreviewExpiry != null) {
                    String clean = (n != null && !n.isBlank()) ? n.trim() : "08 / 28";
                    cardPreviewExpiry.setText(clean);
                }
            });
        }

        // Initial preview state
        updateHolder.run();
    }

    public void initCountdownTimer(Label timerLabel, Button confirmBtn, Runnable onExpired) {
        this.holdTimerLabel = timerLabel;
        this.confirmButton = confirmBtn;
        this.stopped = false;

        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        if (bookingContext != null && bookingContext.getHoldExpiresAt() == null) {
            bookingContext.setHoldExpiresAt(Instant.now().plusSeconds(SeatHoldPolicy.HOLD_SECONDS));
        }

        updateTimerDisplay(onExpired);

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTimerDisplay(onExpired)));
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateTimerDisplay(Runnable onExpired) {
        if (stopped || bookingContext == null || (bookingContext.getBookingReference() != null && !bookingContext.getBookingReference().isBlank())) return;
        long remaining = bookingContext.getRemainingHoldSeconds();
        long minutes = remaining / 60;
        long seconds = remaining % 60;

        if (holdTimerLabel != null) {
            holdTimerLabel.setText(String.format("SEAT HOLD %02d:%02d", minutes, seconds));
        }

        if (remaining <= 0) {
            if (countdownTimeline != null) countdownTimeline.stop();
            if (confirmButton != null) confirmButton.setDisable(true);
            if (onExpired != null) {
                onExpired.run();
            }
        }
    }

    public void checkHoldExpiry() {
        if (stopped || bookingContext == null || (bookingContext.getBookingReference() != null && !bookingContext.getBookingReference().isBlank())) {
            return;
        }
        updateTimerDisplay(controller::handleHoldExpired);
    }

    public void stopTimer() {
        this.stopped = true;
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    public boolean isStopped() {
        return stopped;
    }
}
