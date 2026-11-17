package com.group2.flightres.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class BookingConfirmationController {

    @FXML
    private Label bookingReference;

    @FXML
    private Label flightNumber;

    @FXML
    private Label passengerName;

    @FXML
    private Label seatNumber;

    @FXML
    private Label departureDate;

    @FXML
    private Label departureTime;

    @FXML
    private void downloadBoardingPass() {
        System.out.println("Boarding pass download selected.");
    }

    @FXML
    private void backToHome() {
        System.out.println("Back to Home selected.");
    }
}