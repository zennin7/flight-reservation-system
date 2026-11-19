@FXML
private ComboBox<String> originCombo;

private TableView<Flight> resultsTable;

private TableColumn<Flight, String> flightNumberCol;

  
  
package com.group2.flightres.controller; 

import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.Flight;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FlightSearchController {

    @FXML
    private ComboBox<String> originCombo;

    @FXML
    private ComboBox<String> destinationCombo;

    @FXML
    private DatePicker departureDatePicker;

    @FXML
    private Button searchButton;

    @FXML
    private TableView<Flight> resultsTable;

    @FXML
    private TableColumn<Flight, String> flightNumberCol;

    @FXML
    private TableColumn<Flight, String> departureCol;

    @FXML
    private TableColumn<Flight, String> arrivalCol;

    @FXML
    private TableColumn<Flight, String> priceCol;

    private final FlightDao flightDao = new FlightDaoImpl();

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM, HH:mm");

    @FXML
    public void initialize() {

        // Temporary airport options
        originCombo.setItems(
                FXCollections.observableArrayList(
                        "JNB", "CPT", "DUR", "LHR", "JFK"
                )
        );

        destinationCombo.setItems(
                FXCollections.observableArrayList(
                        "JNB", "CPT", "DUR", "LHR", "JFK"
                )
        );

        // Connect Flight properties to table columns

        flightNumberCol.setCellValueFactory(
               cell -> cell.getValue().getFlightNumber()
                )
        );

        departureCol.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        cell.getValue().getDepartureTime().format(FMT)
                )
        );

        arrivalCol.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        cell.getValue().getArrivalTime().format(FMT)
                )
        );

        priceCol.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        "R " + cell.getValue().getBasePrice()
                                .setScale(2, RoundingMode.HALF_UP)
                )
        );
    }

    @FXML
    private void handleSearch() {

        String origin = originCombo.getValue();

        String destination = destinationCombo.getValue();

        LocalDate date = departureDatePicker.getValue();

        // Validate user selections

        if (origin == null ||
            destination == null ||
            date == null) {

            showMessage(
                    Alert.AlertType.WARNING,
                    "Missing Information",
                    "Please select an origin, destination and date."
            );

            return;
        }

        if (origin.equals(destination)) {

            showMessage(
                    Alert.AlertType.WARNING,
                    "Invalid Route",
                    "Origin and destination cannot be the same."
            );

            return;
        }

        try {

            // Ask DAO to retrieve matching flights

            List<Flight> results =
                    flightDao.searchFlights(origin, destination, date);

            // Display results in the TableView

            resultsTable.setItems(
                    FXCollections.observableArrayList(results)
            );

            if (results.isEmpty()) {

                showMessage(
                        Alert.AlertType.INFORMATION,
                        "No Flights Found",
                        "No flights were found for your search."
                );
            }

        } catch (RuntimeException e) {

            showMessage(
                    Alert.AlertType.ERROR,
                    "Search Error",
                    "Unable to retrieve flights. Please try again."
            );

            e.printStackTrace();
        }
    }

    private void showMessage(
            Alert.AlertType type,
            String title,
            String message) {

        Alert alert = new Alert(type);

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }

    @FXML
    private void handleFlightSelection(MouseEvent event) {

        if (event.getClickCount() == 2) {

            Flight selectedFlight =
                    resultsTable.getSelectionModel().getSelectedItem();

            if (selectedFlight == null) {
                return;
            }

            long flightId = selectedFlight.getFlightId();

            
    }
    SceneManager.switchScene("seat-map.fxml", flightId);

    
}
