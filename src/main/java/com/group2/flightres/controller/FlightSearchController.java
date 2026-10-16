package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSearchResult;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Controller for SkyVoyage Screen 03 Flight Search.
 */
public class FlightSearchController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(FlightSearchController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML private ComboBox<String> fromCombo;
    @FXML private ComboBox<String> toCombo;
    @FXML private DatePicker departureDatePicker;
    @FXML private ComboBox<String> passengersCombo;
    @FXML private Button searchButton;

    @FXML private TableView<FlightSearchResult> flightsTable;
    @FXML private TableColumn<FlightSearchResult, String> flightCol;
    @FXML private TableColumn<FlightSearchResult, String> departsCol;
    @FXML private TableColumn<FlightSearchResult, String> arrivesCol;
    @FXML private TableColumn<FlightSearchResult, String> durationCol;
    @FXML private TableColumn<FlightSearchResult, String> fromPriceCol;
    @FXML private TableColumn<FlightSearchResult, Void> actionCol;

    @FXML private Label resultsCountLabel;

    private BookingContext bookingContext;
    private Runnable onNext;

    private final ObservableList<FlightSearchResult> flightList = FXCollections.observableArrayList();

    @Override
    public void init(BookingContext context, Runnable onNext) {
        this.bookingContext = context;
        this.onNext = onNext;
    }

    @FXML
    public void initialize() {
        initSearchControls();
        initTable();
        loadSampleFlights();
    }

    private void initSearchControls() {
        if (fromCombo != null) {
            fromCombo.getItems().addAll("Johannesburg (JNB)", "Cape Town (CPT)", "Durban (DUR)");
            fromCombo.setValue("Johannesburg (JNB)");
        }
        if (toCombo != null) {
            toCombo.getItems().addAll("Cape Town (CPT)", "Johannesburg (JNB)", "Durban (DUR)");
            toCombo.setValue("Cape Town (CPT)");
        }
        if (departureDatePicker != null) {
            departureDatePicker.setConverter(new StringConverter<>() {
                @Override
                public String toString(LocalDate date) {
                    return date != null ? DATE_FORMATTER.format(date) : "";
                }

                @Override
                public LocalDate fromString(String string) {
                    return (string != null && !string.isBlank()) ? LocalDate.parse(string, DATE_FORMATTER) : null;
                }
            });
            departureDatePicker.setValue(LocalDate.of(2026, 11, 2));
        }
        if (passengersCombo != null) {
            passengersCombo.getItems().addAll("1 Adult", "2 Adults", "3 Adults", "4 Adults");
            passengersCombo.setValue("1 Adult");
        }
        if (searchButton != null) {
            searchButton.setOnAction(e -> handleSearch());
        }
    }

    private void initTable() {
        flightsTable.setItems(flightList);
        flightsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        flightsTable.setFixedCellSize(72);

        flightCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFlightNumber()));
        departsCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDepartureTime()));
        arrivesCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getArrivalTime()));
        durationCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDuration()));
        fromPriceCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPriceFormatted()));

        // Bold flight number and price styling
        flightCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().remove("table-cell-bold");
                } else {
                    setText(item);
                    if (!getStyleClass().contains("table-cell-bold")) {
                        getStyleClass().add("table-cell-bold");
                    }
                }
            }
        });

        fromPriceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().remove("table-cell-bold");
                } else {
                    setText(item);
                    if (!getStyleClass().contains("table-cell-bold")) {
                        getStyleClass().add("table-cell-bold");
                    }
                }
            }
        });

        durationCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().remove("table-cell-muted");
                } else {
                    setText(item);
                    if (!getStyleClass().contains("table-cell-muted")) {
                        getStyleClass().add("table-cell-muted");
                    }
                }
            }
        });

        // Select button cell factory
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button selectBtn = new Button("Select");
            private final HBox container = new HBox(selectBtn);

            {
                selectBtn.getStyleClass().addAll("btn-gold", "flight-select-btn");
                container.setAlignment(Pos.CENTER_RIGHT);
                container.setPadding(new javafx.geometry.Insets(0, 18, 0, 0));
                selectBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        FlightSearchResult flight = getTableView().getItems().get(idx);
                        handleSelectFlight(flight);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(container);
                }
            }
        });

        // Set table height dynamically according to rows + header so no empty space or scrollbar shows
        flightsTable.prefHeightProperty().bind(
                Bindings.size(flightList).multiply(72).add(42)
        );
        flightsTable.minHeightProperty().bind(flightsTable.prefHeightProperty());
        flightsTable.maxHeightProperty().bind(flightsTable.prefHeightProperty());
    }

    private void loadSampleFlights() {
        // Sample rows matching reference PNG: 03_flight_search
        // TODO(wire-real-data): Replace with FlightService query once DAO is created.
        flightList.clear();
        flightList.add(new FlightSearchResult(1L, "GRP 204", "06:30", "08:40", "2h 10m", new BigDecimal("1200"), "R 1,200"));
        flightList.add(new FlightSearchResult(2L, "GRP 318", "09:15", "11:25", "2h 10m", new BigDecimal("1450"), "R 1,450"));
        flightList.add(new FlightSearchResult(3L, "GRP 422", "13:00", "15:10", "2h 10m", new BigDecimal("1090"), "R 1,090"));
        flightList.add(new FlightSearchResult(4L, "GRP 507", "17:45", "19:55", "2h 10m", new BigDecimal("1680"), "R 1,680"));

        if (resultsCountLabel != null) {
            resultsCountLabel.setText("4 flights found. Prices are base fares before seat class and demand pricing.");
        }
    }

    @FXML
    private void handleSearch() {
        logger.info("Search button clicked. Refreshing flights list.");
        loadSampleFlights();
    }

    private void handleSelectFlight(FlightSearchResult flight) {
        logger.info("Flight selected: {} (ID: {})", flight.getFlightNumber(), flight.getFlightId());
        if (bookingContext != null) {
            bookingContext.setFlightId(flight.getFlightId());
            bookingContext.setTotalPrice(flight.getBasePrice());
        }
        if (onNext != null) {
            onNext.run();
        }
    }
}
