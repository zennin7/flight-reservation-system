package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the SkyVoyage 01 Home screen.
 */
public class HomeController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @FXML private ComboBox<String> fromCombo;
    @FXML private ComboBox<String> toCombo;
    @FXML private DatePicker departureDatePicker;
    @FXML private DatePicker returnDatePicker;
    @FXML private ComboBox<String> passengersCombo;
    @FXML private Button searchFlightsButton;

    private BookingContext bookingContext;
    private Runnable onNext;

    @Override
    public void init(BookingContext context, Runnable onNext) {
        this.bookingContext = context;
        this.onNext = onNext;
    }

    @FXML
    public void initialize() {
        if (fromCombo != null) {
            fromCombo.getItems().addAll("Johannesburg (JNB)", "Cape Town (CPT)", "Durban (DUR)");
            fromCombo.setValue("Johannesburg (JNB)");
        }
        if (toCombo != null) {
            toCombo.getItems().addAll("Cape Town (CPT)", "Johannesburg (JNB)", "Durban (DUR)");
            toCombo.setValue("Cape Town (CPT)");
        }
        if (passengersCombo != null) {
            passengersCombo.getItems().addAll("1 Adult", "2 Adults", "3 Adults", "4 Adults");
            passengersCombo.setValue("1 Adult");
        }
        if (searchFlightsButton != null) {
            searchFlightsButton.setOnAction(e -> handleSearch());
            com.group2.flightres.util.Motion.attachHoverLift(searchFlightsButton);
        }
    }

    @FXML
    private void handleSearch() {
        logger.info("Search Flights clicked from Home screen.");
        if (bookingContext != null) {
            if (fromCombo != null && fromCombo.getValue() != null) {
                bookingContext.setOrigin(fromCombo.getValue());
            }
            if (toCombo != null && toCombo.getValue() != null) {
                bookingContext.setDestination(toCombo.getValue());
            }
        }
        if (onNext != null) {
            onNext.run();
        }
    }
}
