package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.util.GlassFlightResultsBridge;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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

    private final FlightService flightService = new FlightService();
    private BookingContext bookingContext;
    private Runnable onNext;

    private final ObservableList<FlightSearchResult> flightList = FXCollections.observableArrayList();

    @Override
    public void init(BookingContext context, Runnable onNext) {
        this.bookingContext = context;
        this.onNext = onNext;
        if (bookingContext != null) {
            if (bookingContext.getOrigin() != null && !bookingContext.getOrigin().isBlank() && fromCombo != null) {
                fromCombo.setValue(bookingContext.getOrigin());
            }
            if (bookingContext.getDestination() != null && !bookingContext.getDestination().isBlank() && toCombo != null) {
                toCombo.setValue(bookingContext.getDestination());
            }
        }
        performSearch();
    }

    @FXML
    public void initialize() {
        initSearchControls();
        initTable();
        performSearch();
    }

    private void initSearchControls() {
        // TODO(B2): Populate origin combo options dynamically from database airports
        if (fromCombo != null) {
            fromCombo.getItems().addAll("Johannesburg (JNB)", "Cape Town (CPT)", "Durban (DUR)");
            fromCombo.setValue("Johannesburg (JNB)");
        }
        // TODO(B2): Populate destination combo options dynamically from database airports
        if (toCombo != null) {
            toCombo.getItems().addAll("Cape Town (CPT)", "Johannesburg (JNB)", "Durban (DUR)");
            toCombo.setValue("Cape Town (CPT)");
        }
        // TODO(B2): Dynamically compute initial departure date rather than hardcoding 2026-11-02
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
        // TODO(B2): Connect passenger/traveller counts to dynamic ticket capacity
        if (passengersCombo != null) {
            passengersCombo.getItems().addAll("1 Adult", "2 Adults", "3 Adults", "4 Adults");
            passengersCombo.setValue("1 Adult");
        }
        if (searchButton != null) {
            searchButton.setOnAction(e -> handleSearch());
            com.group2.flightres.util.Motion.attachHoverLift(searchButton);
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

        flightCol.setCellFactory(createStyledCellFactory("table-cell-bold"));
        fromPriceCol.setCellFactory(createStyledCellFactory("table-cell-bold"));
        durationCol.setCellFactory(createStyledCellFactory("table-cell-muted"));
        actionCol.setCellFactory(createActionCellFactory());

        // Set table height dynamically according to rows + header so no empty space or scrollbar shows
        flightsTable.prefHeightProperty().bind(
                Bindings.size(flightList).multiply(72).add(42)
        );
        flightsTable.minHeightProperty().bind(flightsTable.prefHeightProperty());
        flightsTable.maxHeightProperty().bind(flightsTable.prefHeightProperty());
    }

    private static Callback<TableColumn<FlightSearchResult, String>, TableCell<FlightSearchResult, String>> createStyledCellFactory(String styleClass) {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().remove(styleClass);
                } else {
                    setText(item);
                    if (!getStyleClass().contains(styleClass)) {
                        getStyleClass().add(styleClass);
                    }
                }
            }
        };
    }

    private Callback<TableColumn<FlightSearchResult, Void>, TableCell<FlightSearchResult, Void>> createActionCellFactory() {
        return col -> new TableCell<>() {
            private final Button selectBtn = new Button("Select");
            private final HBox container = new HBox(selectBtn);

            {
                selectBtn.getStyleClass().addAll("btn-gold", "flight-select-btn");
                com.group2.flightres.util.Motion.attachHoverLift(selectBtn);
                container.setAlignment(Pos.CENTER_RIGHT);
                container.setPadding(javafx.geometry.Insets.EMPTY);
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
                setGraphic(empty ? null : container);
            }
        };
    }

    private void performSearch() {
        String from = (fromCombo != null && fromCombo.getValue() != null) ? fromCombo.getValue() : "Johannesburg (JNB)";
        String to = (toCombo != null && toCombo.getValue() != null) ? toCombo.getValue() : "Cape Town (CPT)";
        LocalDate date = (departureDatePicker != null) ? departureDatePicker.getValue() : LocalDate.of(2026, 11, 2);

        flightList.clear();
        try {
            List<FlightSearchResult> results = flightService.searchFlights(from, to, date);
            if (results != null && !results.isEmpty()) {
                flightList.addAll(results);
                if (resultsCountLabel != null) {
                    resultsCountLabel.setText(results.size() + " flights found. Prices are base fares before seat class and demand pricing.");
                }
            } else {
                if (resultsCountLabel != null) {
                    String dStr = (date != null) ? date.format(DATE_FORMATTER) : "selected date";
                    resultsCountLabel.setText("No flights found for " + from + " -> " + to + " on " + dStr + ".");
                }
            }
        } catch (Exception e) {
            logger.error("Could not query flights from database", e);
            flightList.clear();
            if (resultsCountLabel != null) {
                resultsCountLabel.setText("Error loading flights");
            }
            GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
            if (bridge != null) {
                bridge.showErrorState(this::performSearch);
            }
        }
    }

    @FXML
    private void handleSearch() {
        logger.info("Search button clicked. Refreshing flights list.");
        performSearch();
    }

    private void handleSelectFlight(FlightSearchResult flight) {
        logger.info("Flight selected: {} (ID: {})", flight.getFlightNumber(), flight.getFlightId());
        if (bookingContext != null) {
            bookingContext.setFlightId(flight.getFlightId());
            bookingContext.setFlightNumber(flight.getFlightNumber());

            // Source route, airports, and flight metadata strictly from the selected FlightSearchResult
            String origCity = (flight.getOriginCity() != null && !flight.getOriginCity().isBlank())
                    ? flight.getOriginCity() : "Johannesburg";
            String origCode = (flight.getOriginCode() != null && !flight.getOriginCode().isBlank())
                    ? flight.getOriginCode() : "JNB";
            String dstCity = (flight.getDestCity() != null && !flight.getDestCity().isBlank())
                    ? flight.getDestCity() : "Cape Town";
            String dstCode = (flight.getDestCode() != null && !flight.getDestCode().isBlank())
                    ? flight.getDestCode() : "CPT";

            bookingContext.setOrigin(origCity + " (" + origCode + ")");
            bookingContext.setDestination(dstCity + " (" + dstCode + ")");
            bookingContext.setOriginCode(origCode);
            bookingContext.setOriginAirport(flight.getOriginAirportName());
            bookingContext.setOriginCity(origCity);
            bookingContext.setDestinationCode(dstCode);
            bookingContext.setDestinationAirport(flight.getDestAirportName());
            bookingContext.setDestinationCity(dstCity);
            bookingContext.setDuration(flight.getDuration());
            bookingContext.setAircraftModel(flight.getAircraftModel());

            if (flight.getDeparture() != null && flight.getOriginZone() != null) {
                DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
                bookingContext.setDepartureDate(flight.getDeparture().atZone(flight.getOriginZone()).format(dFmt));
            } else if (departureDatePicker != null && departureDatePicker.getValue() != null) {
                DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
                bookingContext.setDepartureDate(departureDatePicker.getValue().format(dFmt));
            }

            bookingContext.setDepartureTime(flight.getDepartureTime());
            bookingContext.setArrivalTime(flight.getArrivalTime());
            bookingContext.setBaseFare(flight.getBasePrice());
            bookingContext.recalculateTotals();
        }
        if (onNext != null) {
            onNext.run();
        }
    }

    // ===================================================================
    // Glass Reskin (Phase 2 Screen 02) Additions
    // ===================================================================
    public static FlightSearchController currentInstance;

    {
        currentInstance = this;
    }

    public void selectFlightFromGlassCard(FlightSearchResult flight) {
        handleSelectFlight(flight);
    }

    public void executeSearchFromGlass() {
        performSearch();
    }

    @FXML
    private void handleSwapLocations() {
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.swapLocations();
        } else if (fromCombo != null && toCombo != null) {
            String temp = fromCombo.getValue();
            fromCombo.setValue(toCombo.getValue());
            toCombo.setValue(temp);
            performSearch();
        }
    }

    @FXML
    private void handleComingSoonOffers() {
        showComingSoon("Offers", "Offers aren't available yet.");
    }

    @FXML
    private void handleComingSoonReturnDate() {
        showComingSoon("Return Flights", "Return flight booking isn't available yet.");
    }

    @FXML
    private void handleComingSoonTravellers() {
        showComingSoon("Travellers and Cabin", "Traveller and cabin class selection isn't available yet.");
    }

    @FXML
    private void handleComingSoonProfile() {
        showComingSoon("User Profile", "User profile management isn't available yet.");
    }

    @FXML
    private void handleComingSoonMyBookings() {
        showComingSoon("My Bookings", "My Bookings isn't available yet.");
    }

    @FXML
    private void handleNavHome() {
        navigateTo("/com/group2/flightres/fxml/home.fxml", "Home");
    }

    @FXML
    private void handleNavFlights() {
        performSearch();
    }

    @FXML
    private void handleNavAbout() {
        navigateTo("/com/group2/flightres/fxml/about.fxml", "About");
    }

    @FXML
    private void handleNavContact() {
        navigateTo("/com/group2/flightres/fxml/contact.fxml", "Contact");
    }

    @FXML
    private void handleNavAvatar() {
        if (bookingContext != null && bookingContext.getCurrentUser() != null) {
            bookingContext.setCurrentUser(null);
            navigateTo("/com/group2/flightres/fxml/home.fxml", "Home");
        } else {
            navigateTo("/com/group2/flightres/fxml/login.fxml", "Login / Sign Up");
        }
    }

    @FXML
    private void handleFilterDeparture(javafx.event.ActionEvent event) {
        if (event.getSource() instanceof Button btn) {
            GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
            if (bridge != null) {
                bridge.setDepartureFilter(btn.getText());
            }
        }
    }

    @FXML
    private void handleFilterStops(javafx.event.ActionEvent event) {
        if (event.getSource() instanceof Button btn) {
            GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
            if (bridge != null) {
                bridge.setStopsFilter(btn.getText());
            }
        }
    }

    @FXML
    private void handleResetPrice() {
        // TODO(B2): Dynamic reset based on current search result min/max prices
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.resetPriceFilter();
        }
    }

    @FXML
    private void handleResetDuration() {
        // TODO(B2): Dynamic reset based on current search result min/max durations
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.resetDurationFilter();
        }
    }

    @FXML
    private void handleResetCabin() {
        // TODO(B2): Dynamic reset based on available cabin classes
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.resetCabinFilter();
        }
    }

    @FXML
    private void handleToggleEconomy() {
        // TODO(B2): Connect cabin toggle to real seat class filtering
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.toggleEconomy();
        }
    }

    @FXML
    private void handleToggleFirstClass() {
        // TODO(B2): Connect cabin toggle to real seat class filtering
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.toggleFirstClass();
        }
    }

    private void showComingSoon(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle("Coming soon");
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private void navigateTo(String fxmlPath, String screenName) {
        javafx.scene.Scene scene = null;
        if (searchButton != null && searchButton.getScene() != null) {
            scene = searchButton.getScene();
        } else if (fromCombo != null && fromCombo.getScene() != null) {
            scene = fromCombo.getScene();
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

                Object ctrl = loader.getController();
                if (ctrl instanceof WizardStepController wz) {
                    wz.init(bookingContext, () -> navigateTo("/com/group2/flightres/fxml/home.fxml", "Home"));
                }
                return;
            } catch (Exception e) {
                logger.error("Failed to navigate to screen {} in contentArea", fxmlPath, e);
            }
        }

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(fxmlPath));
            javafx.scene.Parent root = loader.load();
            scene.setRoot(root);
            com.group2.flightres.util.ThemeManager.getInstance().applyGlassTheme(scene);
        } catch (Exception e) {
            logger.error("Failed to navigate to screen {} as scene root", fxmlPath, e);
        }
    }
}
