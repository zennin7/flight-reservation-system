package com.group2.flightres.controller;

import com.group2.flightres.component.AirportPickerPopup;
import com.group2.flightres.component.GlassTopBar;
import com.group2.flightres.model.Airport;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.Route;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.util.DesktopAsync;
import com.group2.flightres.util.GlassFlightResultsBridge;
import com.group2.flightres.util.Motion;
import com.group2.flightres.util.Navigator;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controller for the Flight Search screen (Screen 02).
 */
public class FlightSearchController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(FlightSearchController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML private GlassTopBar topBar;
    @FXML private HBox fromCell;
    @FXML private HBox toCell;
    @FXML private VBox departureCell;
    @FXML private Button swapButton;
    @FXML private ComboBox<Airport> fromCombo;
    @FXML private ComboBox<Airport> toCombo;
    @FXML private DatePicker departureDatePicker;
    @FXML private ComboBox<String> passengersCombo;
    @FXML private VBox returnCard;
    @FXML private javafx.scene.layout.Pane travellersCard;
    @FXML private Button searchButton;

    @FXML private TableView<FlightSearchResult> flightsTable;
    @FXML private TableColumn<FlightSearchResult, String> flightCol;
    @FXML private TableColumn<FlightSearchResult, String> departsCol;
    @FXML private TableColumn<FlightSearchResult, String> arrivesCol;
    @FXML private TableColumn<FlightSearchResult, String> durationCol;
    @FXML private TableColumn<FlightSearchResult, String> fromPriceCol;
    @FXML private TableColumn<FlightSearchResult, Void> actionCol;

    @FXML private Label resultsCountLabel;

    private final FlightService flightService;
    private BookingContext bookingContext;
    private Runnable onNext;

    private final ObservableList<FlightSearchResult> flightList = FXCollections.observableArrayList();
    private final AirportPickerPopup airportPickerPopup = new AirportPickerPopup();
    private final java.util.concurrent.atomic.AtomicLong searchGeneration = new java.util.concurrent.atomic.AtomicLong(0);
    private List<Airport> cachedAirports = List.of();
    private boolean airportsLoaded = false;

    private record SearchInitData(List<Airport> airports, Optional<Route> defaultRoute, LocalDate initialDate) {}

    public FlightSearchController() {
        this(new FlightService());
    }

    public FlightSearchController(FlightService flightService) {
        this.flightService = flightService != null ? flightService : new FlightService();
    }

    @Override
    public void init(BookingContext context, Runnable onNext) {
        this.bookingContext = context;
        this.onNext = onNext;
        if (topBar != null) {
            topBar.bind(context);
        }
        if (airportsLoaded) {
            applyContextRouteAndSearch();
        }
    }

    @FXML
    public void initialize() {
        initSearchControls();
        initTable();
        loadAirportsAndInitialSearch();
    }

    private void applyContextRouteAndSearch() {
        if (bookingContext != null && !cachedAirports.isEmpty()) {
            if (bookingContext.getOrigin() != null && !bookingContext.getOrigin().isBlank() && fromCombo != null) {
                String orig = bookingContext.getOrigin();
                cachedAirports.stream()
                        .filter(a -> a.code().equalsIgnoreCase(orig)
                                || orig.contains("(" + a.code() + ")")
                                || orig.equalsIgnoreCase(a.city()))
                        .findFirst()
                        .ifPresent(fromCombo::setValue);
            }
            if (bookingContext.getDestination() != null && !bookingContext.getDestination().isBlank() && toCombo != null) {
                String dest = bookingContext.getDestination();
                cachedAirports.stream()
                        .filter(a -> a.code().equalsIgnoreCase(dest)
                                || dest.contains("(" + a.code() + ")")
                                || dest.equalsIgnoreCase(a.city()))
                        .findFirst()
                        .ifPresent(toCombo::setValue);
            }
            if (bookingContext.getDepartureDate() != null && !bookingContext.getDepartureDate().isBlank()
                    && departureDatePicker != null) {
                try {
                    departureDatePicker.setValue(LocalDate.parse(bookingContext.getDepartureDate().trim(), DATE_FORMATTER));
                } catch (Exception ignored) {
                }
            }
        }
        performSearch();
    }

    private void loadAirportsAndInitialSearch() {
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.showLoadingState("Loading routes and flights...");
        }
        DesktopAsync.run("FlightSearch:loadInitialData", () -> {
            List<Airport> airports = flightService.listAirports();
            Optional<Route> defRoute = flightService.defaultRoute();
            LocalDate initialDate = null;
            if (defRoute.isPresent()) {
                Airport origin = defRoute.get().origin();
                initialDate = flightService.nextAvailableDate(defRoute.get())
                        .orElseGet(() -> LocalDate.now(origin.zoneId()).plusDays(1));
            } else if (airports.size() >= 2) {
                initialDate = LocalDate.now(airports.get(0).zoneId()).plusDays(1);
            }
            return new SearchInitData(airports, defRoute, initialDate);
        }, data -> {
            cachedAirports = data.airports();
            airportsLoaded = true;
            if (fromCombo != null) {
                fromCombo.getItems().setAll(cachedAirports);
            }
            if (data.defaultRoute().isPresent()) {
                Airport origin = data.defaultRoute().get().origin();
                Airport dest = data.defaultRoute().get().destination();
                if (fromCombo != null) fromCombo.setValue(origin);
                if (toCombo != null) {
                    toCombo.getItems().setAll(cachedAirports.stream().filter(a -> !a.equals(origin)).toList());
                    toCombo.setValue(dest);
                }
                if (departureDatePicker != null && data.initialDate() != null) {
                    departureDatePicker.setValue(data.initialDate());
                }
            } else if (cachedAirports.size() >= 2) {
                Airport origin = cachedAirports.get(0);
                Airport dest = cachedAirports.get(1);
                if (fromCombo != null) fromCombo.setValue(origin);
                if (toCombo != null) {
                    toCombo.getItems().setAll(cachedAirports.stream().filter(a -> !a.equals(origin)).toList());
                    toCombo.setValue(dest);
                }
                if (departureDatePicker != null && data.initialDate() != null) {
                    departureDatePicker.setValue(data.initialDate());
                }
            }
            applyContextRouteAndSearch();
        }, err -> {
            logger.error("Failed to load initial airports/routes", err);
            GlassFlightResultsBridge b = GlassFlightResultsBridge.getInstance();
            if (b != null) {
                b.showErrorState(this::loadAirportsAndInitialSearch);
            }
        });
    }

    private void initSearchControls() {
        StringConverter<Airport> airportConverter = new StringConverter<>() {
            @Override
            public String toString(Airport airport) {
                return airport != null ? airport.city() + " (" + airport.code() + ")" : "";
            }

            @Override
            public Airport fromString(String string) {
                return null;
            }
        };

        if (fromCombo != null) {
            fromCombo.setConverter(airportConverter);
        }

        if (toCombo != null) {
            toCombo.setConverter(airportConverter);
        }

        if (fromCombo != null && toCombo != null) {
            fromCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !cachedAirports.isEmpty()) {
                    List<Airport> filteredTo = cachedAirports.stream()
                            .filter(a -> !a.equals(newVal))
                            .toList();
                    Airport currentTo = toCombo.getValue();
                    toCombo.getItems().setAll(filteredTo);
                    if (filteredTo.contains(currentTo)) {
                        toCombo.setValue(currentTo);
                    } else if (!filteredTo.isEmpty()) {
                        toCombo.setValue(filteredTo.get(0));
                    }
                }
            });
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

            departureDatePicker.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate item, boolean empty) {
                    super.updateItem(item, empty);
                    ZoneId originZone = (fromCombo != null && fromCombo.getValue() != null)
                            ? fromCombo.getValue().zoneId()
                            : ZoneId.of("Africa/Johannesburg");
                    LocalDate today = LocalDate.now(originZone);
                    if (item != null && item.isBefore(today)) {
                        setDisable(true);
                        setStyle("-fx-opacity: 0.4;");
                    }
                }
            });

            departureDatePicker.setOnAction(e -> performSearch());
        }

        if (passengersCombo != null) {
            passengersCombo.getItems().addAll("1 Adult", "2 Adults", "3 Adults", "4 Adults");
            passengersCombo.setValue("1 Adult");
        }

        if (returnCard != null) {
            Tooltip.install(returnCard, new Tooltip("Single passenger, one-way bookings in this release"));
        }
        if (travellersCard != null) {
            Tooltip.install(travellersCard, new Tooltip("Single passenger, one-way bookings in this release"));
        }

        if (searchButton != null) {
            searchButton.setOnAction(e -> handleSearch());
            Motion.attachHoverLift(searchButton);
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
                Motion.attachHoverLift(selectBtn);
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
        Airport fromAirport = fromCombo != null ? fromCombo.getValue() : null;
        Airport toAirport = toCombo != null ? toCombo.getValue() : null;
        if (fromAirport == null || toAirport == null) {
            return;
        }

        ZoneId originZone = fromAirport.zoneId();
        LocalDate date = (departureDatePicker != null && departureDatePicker.getValue() != null)
                ? departureDatePicker.getValue()
                : LocalDate.now(originZone).plusDays(1);

        long gen = searchGeneration.incrementAndGet();
        flightList.clear();
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.updateLocationLabels();
            bridge.showLoadingState("Searching flights from " + fromAirport.city() + " to " + toAirport.city() + "...");
        }
        if (searchButton != null) {
            searchButton.setDisable(true);
        }

        String fromCode = fromAirport.code();
        String toCode = toAirport.code();

        DesktopAsync.run("FlightSearch:" + fromCode + "->" + toCode + "@" + date,
                () -> flightService.searchFlights(fromCode, toCode, date),
                results -> {
                    if (gen != searchGeneration.get()) return;
                    if (searchButton != null) searchButton.setDisable(false);
                    GlassFlightResultsBridge b = GlassFlightResultsBridge.getInstance();
                    if (results != null && !results.isEmpty()) {
                        flightList.setAll(results);
                        if (resultsCountLabel != null) {
                            resultsCountLabel.setText("Showing " + results.size() + (results.size() == 1 ? " flight" : " flights"));
                            resultsCountLabel.setVisible(true);
                        }
                        if (b != null) {
                            b.updateFilterBounds(results);
                        }
                    } else {
                        flightList.clear();
                        if (resultsCountLabel != null) {
                            resultsCountLabel.setText("");
                            resultsCountLabel.setVisible(false);
                        }
                        if (b != null) {
                            b.updateFilterBounds(List.of());
                            b.showEmptyState(this::handleNextAvailableDate);
                        }
                    }
                },
                err -> {
                    if (gen != searchGeneration.get()) return;
                    if (searchButton != null) searchButton.setDisable(false);
                    logger.error("Could not query flights from database", err);
                    flightList.clear();
                    if (resultsCountLabel != null) {
                        resultsCountLabel.setText("");
                        resultsCountLabel.setVisible(false);
                    }
                    GlassFlightResultsBridge b = GlassFlightResultsBridge.getInstance();
                    if (b != null) {
                        b.updateFilterBounds(List.of());
                        b.showErrorState(this::performSearch);
                    }
                });
    }

    public void handleNextAvailableDate() {
        Airport from = fromCombo != null ? fromCombo.getValue() : null;
        Airport to = toCombo != null ? toCombo.getValue() : null;
        if (from == null || to == null) return;

        LocalDate currentDate = (departureDatePicker != null && departureDatePicker.getValue() != null)
                ? departureDatePicker.getValue()
                : LocalDate.now(from.zoneId());

        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.showLoadingState("Finding next available date...");
        }

        String fromCode = from.code();
        String toCode = to.code();
        LocalDate searchFrom = currentDate.plusDays(1);

        DesktopAsync.run("FlightSearch:nextAvailableDate:" + fromCode + "->" + toCode,
                () -> flightService.nextAvailableDate(fromCode, toCode, searchFrom),
                nextDateOpt -> {
                    if (nextDateOpt.isPresent()) {
                        LocalDate nextDate = nextDateOpt.get();
                        if (departureDatePicker != null) {
                            departureDatePicker.setValue(nextDate);
                        }
                        GlassFlightResultsBridge b = GlassFlightResultsBridge.getInstance();
                        if (b != null) {
                            b.updateLocationLabels();
                        }
                        performSearch();
                    } else {
                        if (resultsCountLabel != null) {
                            resultsCountLabel.setText("");
                            resultsCountLabel.setVisible(false);
                        }
                        GlassFlightResultsBridge b = GlassFlightResultsBridge.getInstance();
                        if (b != null) {
                            b.showNoUpcomingFlightsState();
                        }
                    }
                },
                err -> {
                    logger.error("Failed to find next available date", err);
                    GlassFlightResultsBridge b = GlassFlightResultsBridge.getInstance();
                    if (b != null) {
                        b.showErrorState(this::handleNextAvailableDate);
                    }
                });
    }

    public void setSearchRoute(String fromCode, String toCode, LocalDate date) {
        if (fromCombo != null && fromCode != null) {
            for (Airport a : fromCombo.getItems()) {
                if (a != null && a.code().equalsIgnoreCase(fromCode)) {
                    fromCombo.setValue(a);
                    break;
                }
            }
        }
        if (toCombo != null && toCode != null) {
            for (Airport a : toCombo.getItems()) {
                if (a != null && a.code().equalsIgnoreCase(toCode)) {
                    toCombo.setValue(a);
                    break;
                }
            }
        }
        if (departureDatePicker != null && date != null) {
            departureDatePicker.setValue(date);
        }
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.updateLocationLabels();
        }
        performSearch();
    }

    public void setSearchDate(LocalDate date) {
        if (departureDatePicker != null) {
            departureDatePicker.setValue(date);
        }
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.updateLocationLabels();
        }
        performSearch();
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
            bookingContext.setDepartureInstant(flight.getDeparture());
            bookingContext.setArrivalInstant(flight.getArrival());
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
    private void handleOpenFromPicker() {
        if (fromCell == null || cachedAirports.isEmpty()) return;
        airportPickerPopup.show(fromCell, cachedAirports, fromCombo != null ? fromCombo.getValue() : null, selected -> {
            if (fromCombo != null) {
                fromCombo.setValue(selected);
            }
            GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
            if (bridge != null) bridge.updateLocationLabels();
            performSearch();
        });
    }

    @FXML
    private void handleOpenToPicker() {
        if (toCell == null || cachedAirports.isEmpty()) return;
        Airport currentFrom = fromCombo != null ? fromCombo.getValue() : null;
        List<Airport> destOptions = currentFrom != null
                ? cachedAirports.stream().filter(a -> !a.equals(currentFrom)).toList()
                : cachedAirports;
        airportPickerPopup.show(toCell, destOptions, toCombo != null ? toCombo.getValue() : null, selected -> {
            if (toCombo != null) {
                toCombo.setValue(selected);
            }
            GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
            if (bridge != null) bridge.updateLocationLabels();
            performSearch();
        });
    }

    @FXML
    private void handleOpenDatePicker() {
        if (departureDatePicker != null) {
            departureDatePicker.show();
        }
    }

    @FXML
    public void handleSwapLocations() {
        if (fromCombo != null && toCombo != null) {
            Airport tempFrom = fromCombo.getValue();
            Airport tempTo = toCombo.getValue();
            if (tempFrom != null && tempTo != null) {
                List<Airport> all = !cachedAirports.isEmpty() ? cachedAirports : fromCombo.getItems();
                toCombo.getItems().setAll(all.stream().filter(a -> !a.equals(tempTo)).toList());
                fromCombo.setValue(tempTo);
                toCombo.setValue(tempFrom);
                GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
                if (bridge != null) bridge.updateLocationLabels();
                performSearch();
            }
        }
    }

    public AirportPickerPopup getAirportPickerPopup() { return airportPickerPopup; }
    public ObservableList<FlightSearchResult> getFlightList() { return flightList; }
    public Button getSwapButton() { return swapButton; }
    public ComboBox<Airport> getFromCombo() { return fromCombo; }
    public ComboBox<Airport> getToCombo() { return toCombo; }

    @FXML
    private void handleComingSoonOffers() {
        showFeatureNotice("Offers", "Offers aren't available yet.");
    }

    @FXML
    private void handleComingSoonProfile() {
        showFeatureNotice("User Profile", "User profile management isn't available yet.");
    }

    @FXML
    private void handleComingSoonMyBookings() {
        Navigator.navigateToMyBookings(searchButton != null ? searchButton : fromCombo, bookingContext);
    }

    @FXML
    private void handleNavHome() {
        Navigator.navigateToHome(searchButton != null ? searchButton : fromCombo, bookingContext);
    }

    @FXML
    private void handleNavFlights() {
        performSearch();
    }

    @FXML
    private void handleNavAbout() {
        Navigator.navigate(searchButton != null ? searchButton : fromCombo,
                "/com/group2/flightres/fxml/about.fxml", false, "About", bookingContext);
    }

    @FXML
    private void handleNavContact() {
        Navigator.navigate(searchButton != null ? searchButton : fromCombo,
                "/com/group2/flightres/fxml/contact.fxml", false, "Contact", bookingContext);
    }

    @FXML
    private void handleNavAvatar() {
        if (bookingContext != null && bookingContext.getCurrentUser() != null) {
            Navigator.signOut(searchButton != null ? searchButton : fromCombo, bookingContext);
        } else {
            Navigator.navigateToLogin(searchButton != null ? searchButton : fromCombo, bookingContext);
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
    private void handleResetPrice() {
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.resetPriceFilter();
        }
    }

    @FXML
    private void handleResetDuration() {
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.resetDurationFilter();
        }
    }

    @FXML
    private void handleResetCabin() {
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.resetCabinFilter();
        }
    }

    @FXML
    private void handleCabinRowEconomy() {
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.toggleCabinFilterEconomy();
        }
    }

    @FXML
    private void handleCabinRowFirst() {
        GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
        if (bridge != null) {
            bridge.toggleCabinFilterFirst();
        }
    }

    private void showFeatureNotice(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle("Information");
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
                Motion.screenTransition(view);

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
