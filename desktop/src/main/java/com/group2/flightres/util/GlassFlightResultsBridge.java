package com.group2.flightres.util;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.controller.FlightSearchController;
import com.group2.flightres.model.FlightSearchResult;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Custom TableView bridge for the Glass Flight Search screen.
 * Maintains compatibility with FlightSearchController.initTable() while dynamically
 * rendering the interactive glass result cards, active filters, search bar synchronization,
 * and expandable details tabs to match design-glass/flight-search.png and flight-search.html.
 */
public class GlassFlightResultsBridge extends TableView<FlightSearchResult> {

    private static final Logger logger = LoggerFactory.getLogger(GlassFlightResultsBridge.class);
    private static GlassFlightResultsBridge activeInstance;

    private static final String SVG_PLANE_TINY =
            "M21 16v-2l-8-5V3.5a1.5 1.5 0 00-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5z";
    private static final String SVG_CHEVRON_DOWN = "M6 9l6 6 6-6";
    private static final String SVG_CHEVRON_UP = "M18 15l-6-6-6 6";

    // References to UI containers
    private VBox cardsContainer;
    private Label resultsTitleLabel;
    private Label resultsCounterLabel;
    private ComboBox<String> fromComboBox;
    private ComboBox<String> toComboBox;
    private DatePicker departureDatePickerNode;

    private Label fromCityLabel;
    private Label fromSubLabel;
    private Label toCityLabel;
    private Label toSubLabel;
    private Label depDateLabel;
    private Label depDayLabel;

    // Interactive filter state - default NO filter active
    private String selectedDepartureFilter = null;
    private String selectedStopsFilter = null;
    // TODO(B2): Dynamically compute slider min/max from search results
    private double maxPriceFilter = 4850.0;
    // TODO(B2): Dynamically compute duration min/max from search results
    private double maxDurationMinutes = 400.0;
    private boolean economySelected = true;
    private boolean firstClassSelected = true;
    private Long expandedFlightId = 2L; // expand 2nd card by default matching mockup
    private String activeDetailsTab = "Flight information";

    // UI Pill and control references for visual toggle updates
    private final Map<String, Button> departurePillMap = new HashMap<>();
    private final Map<String, Button> stopsPillMap = new HashMap<>();
    private Slider priceSlider;
    private Slider durationSlider;
    private StackPane economyCheckCircle;
    private StackPane firstClassCheckCircle;

    public GlassFlightResultsBridge() {
        activeInstance = this;
        setVisible(false);
        setManaged(false);
        setOpacity(0.0);
        setMaxWidth(0);
        setMaxHeight(0);
        itemsProperty().addListener((obs, oldItems, newItems) -> {
            if (newItems != null) {
                newItems.addListener((ListChangeListener<FlightSearchResult>) change -> {
                    resetFiltersToDefault();
                    Platform.runLater(this::refreshCardsView);
                });
                resetFiltersToDefault();
                Platform.runLater(this::refreshCardsView);
            }
        });
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(this::initUiBindings);
            }
        });
    }

    public static GlassFlightResultsBridge getInstance() {
        return activeInstance;
    }

    /**
     * Discovers FXML nodes in parent scene and wires bindings.
     */
    public void initUiBindings() {
        Node rootNode = getScene() != null ? getScene().getRoot() : null;
        if (rootNode == null) {
            Node curr = this;
            while (curr.getParent() != null) {
                curr = curr.getParent();
            }
            rootNode = curr;
        }
        if (!(rootNode instanceof Pane)) return;
        Pane root = (Pane) rootNode;

        cardsContainer = (VBox) root.lookup("#resultsCardsContainer");
        resultsTitleLabel = (Label) root.lookup("#resultsTitleLabel");
        resultsCounterLabel = (Label) root.lookup("#resultsCountLabel");

        fromComboBox = (ComboBox<String>) root.lookup("#fromCombo");
        toComboBox = (ComboBox<String>) root.lookup("#toCombo");
        departureDatePickerNode = (DatePicker) root.lookup("#departureDatePicker");

        fromCityLabel = (Label) root.lookup("#fromCityDisplay");
        fromSubLabel = (Label) root.lookup("#fromSubDisplay");
        toCityLabel = (Label) root.lookup("#toCityDisplay");
        toSubLabel = (Label) root.lookup("#toSubDisplay");
        depDateLabel = (Label) root.lookup("#depDateDisplay");
        depDayLabel = (Label) root.lookup("#depDayDisplay");

        if (fromComboBox != null) {
            fromComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateLocationLabels());
        }
        if (toComboBox != null) {
            toComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateLocationLabels());
        }
        if (departureDatePickerNode != null) {
            departureDatePickerNode.valueProperty().addListener((obs, oldVal, newVal) -> updateLocationLabels());
        }

        Slider pSlider = (Slider) root.lookup("#priceSlider");
        if (pSlider != null) {
            this.priceSlider = pSlider;
            pSlider.valueProperty().addListener((obs, oldVal, newVal) -> setMaxPriceFilter(newVal.doubleValue()));
        }

        Slider dSlider = (Slider) root.lookup("#durationSlider");
        if (dSlider != null) {
            this.durationSlider = dSlider;
            dSlider.valueProperty().addListener((obs, oldVal, newVal) -> setMaxDurationFilter(newVal.doubleValue()));
        }

        updateLocationLabels();
        refreshCardsView();
    }

    public void registerDeparturePill(String key, Button button) {
        departurePillMap.put(key, button);
    }

    public void registerStopsPill(String key, Button button) {
        stopsPillMap.put(key, button);
    }

    public void registerPriceSlider(Slider slider) {
        this.priceSlider = slider;
    }

    public void registerDurationSlider(Slider slider) {
        this.durationSlider = slider;
    }

    public void registerEconomyCheck(StackPane checkCircle) {
        this.economyCheckCircle = checkCircle;
    }

    public void registerFirstClassCheck(StackPane checkCircle) {
        this.firstClassCheckCircle = checkCircle;
    }

    /**
     * Synchronizes From / To / Departure date display labels.
     */
    public void updateLocationLabels() {
        // TODO(B2): Bind location display labels dynamically to database airport entities
        if (fromComboBox != null && fromCityLabel != null) {
            String val = fromComboBox.getValue();
            if (val == null || val.isBlank()) val = "Johannesburg (JNB)";
            if (val.contains("Johannesburg")) {
                fromCityLabel.setText("Johannesburg");
                if (fromSubLabel != null) fromSubLabel.setText("JNB, O.R. Tambo International");
            } else if (val.contains("Cape Town")) {
                fromCityLabel.setText("Cape Town");
                if (fromSubLabel != null) fromSubLabel.setText("CPT, Cape Town International");
            } else if (val.contains("Durban")) {
                fromCityLabel.setText("Durban");
                if (fromSubLabel != null) fromSubLabel.setText("DUR, King Shaka International");
            } else {
                fromCityLabel.setText(val);
                if (fromSubLabel != null) fromSubLabel.setText(val);
            }
        }

        // TODO(B2): Bind destination display labels dynamically to database airport entities
        if (toComboBox != null && toCityLabel != null) {
            String val = toComboBox.getValue();
            if (val == null || val.isBlank()) val = "Cape Town (CPT)";
            if (val.contains("Cape Town")) {
                toCityLabel.setText("Cape Town");
                if (toSubLabel != null) toSubLabel.setText("CPT, Cape Town International");
            } else if (val.contains("Johannesburg")) {
                toCityLabel.setText("Johannesburg");
                if (toSubLabel != null) toSubLabel.setText("JNB, O.R. Tambo International");
            } else if (val.contains("Durban")) {
                toCityLabel.setText("Durban");
                if (toSubLabel != null) toSubLabel.setText("DUR, King Shaka International");
            } else {
                toCityLabel.setText(val);
                if (toSubLabel != null) toSubLabel.setText(val);
            }
        }

        // TODO(B2): Synchronize departure date labels with interactive calendar picker
        if (departureDatePickerNode != null && depDateLabel != null) {
            LocalDate date = departureDatePickerNode.getValue();
            if (date != null) {
                DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("d MMM ''yy", Locale.ENGLISH);
                DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
                depDateLabel.setText(date.format(dFmt));
                if (depDayLabel != null) depDayLabel.setText(date.format(dayFmt));
            } else {
                depDateLabel.setText("2 Nov '26");
                if (depDayLabel != null) depDayLabel.setText("Monday");
            }
        }
    }

    /**
     * Swaps From and To locations (UI only).
     */
    public void swapLocations() {
        if (fromComboBox != null && toComboBox != null) {
            String temp = fromComboBox.getValue();
            fromComboBox.setValue(toComboBox.getValue());
            toComboBox.setValue(temp);
            updateLocationLabels();
            if (FlightSearchController.currentInstance != null) {
                FlightSearchController.currentInstance.executeSearchFromGlass();
            }
        }
    }

    // --- Interactive Filter Handlers ---

    public void setDepartureFilter(String filter) {
        if (filter != null && filter.equals(this.selectedDepartureFilter)) {
            this.selectedDepartureFilter = null; // Toggle off
        } else {
            this.selectedDepartureFilter = filter;
        }
        updatePillStyles();
        refreshCardsView();
    }

    public void setStopsFilter(String filter) {
        if (filter != null && filter.equals(this.selectedStopsFilter)) {
            this.selectedStopsFilter = null; // Toggle off
        } else {
            this.selectedStopsFilter = filter;
        }
        updatePillStyles();
        refreshCardsView();
    }

    public void setMaxPriceFilter(double price) {
        this.maxPriceFilter = price;
        refreshCardsView();
    }

    public void resetPriceFilter() {
        // TODO(B2): Dynamic price reset based on result set bounds
        this.maxPriceFilter = 4850.0;
        if (priceSlider != null) priceSlider.setValue(4850.0);
        refreshCardsView();
    }

    public void setMaxDurationFilter(double minutes) {
        this.maxDurationMinutes = minutes;
        refreshCardsView();
    }

    public void resetDurationFilter() {
        // TODO(B2): Dynamic duration reset based on result set bounds
        this.maxDurationMinutes = 400.0;
        if (durationSlider != null) durationSlider.setValue(400.0);
        refreshCardsView();
    }

    public void toggleEconomy() {
        // TODO(B2): Connect cabin toggle to real seat class filtering
        this.economySelected = !this.economySelected;
        updateCheckStyles();
        refreshCardsView();
    }

    public void toggleFirstClass() {
        // TODO(B2): Connect cabin toggle to real seat class filtering
        this.firstClassSelected = !this.firstClassSelected;
        updateCheckStyles();
        refreshCardsView();
    }

    public void resetCabinFilter() {
        // TODO(B2): Dynamic cabin reset
        this.economySelected = true;
        this.firstClassSelected = true;
        updateCheckStyles();
        refreshCardsView();
    }

    public void resetFiltersToDefault() {
        // TODO(B2): Dynamically compute initial filter bounds from DB results
        this.selectedDepartureFilter = null;
        this.selectedStopsFilter = null;
        this.maxPriceFilter = 4850.0;
        this.maxDurationMinutes = 400.0;
        this.economySelected = true;
        this.firstClassSelected = true;
        if (priceSlider != null) priceSlider.setValue(4850.0);
        if (durationSlider != null) durationSlider.setValue(400.0);
        updatePillStyles();
        updateCheckStyles();
    }

    private void updatePillStyles() {
        for (Map.Entry<String, Button> entry : departurePillMap.entrySet()) {
            boolean active = entry.getKey().equals(selectedDepartureFilter);
            entry.getValue().getStyleClass().remove("pill-button-on");
            if (active) entry.getValue().getStyleClass().add("pill-button-on");
        }
        for (Map.Entry<String, Button> entry : stopsPillMap.entrySet()) {
            boolean active = entry.getKey().equals(selectedStopsFilter);
            entry.getValue().getStyleClass().remove("pill-button-on");
            if (active) entry.getValue().getStyleClass().add("pill-button-on");
        }
    }

    private void updateCheckStyles() {
        if (economyCheckCircle != null) {
            economyCheckCircle.getStyleClass().remove("check-circle-on");
            if (economySelected) economyCheckCircle.getStyleClass().add("check-circle-on");
            economyCheckCircle.getChildren().forEach(n -> n.setVisible(economySelected));
        }
        if (firstClassCheckCircle != null) {
            firstClassCheckCircle.getStyleClass().remove("check-circle-on");
            if (firstClassSelected) firstClassCheckCircle.getStyleClass().add("check-circle-on");
            firstClassCheckCircle.getChildren().forEach(n -> n.setVisible(firstClassSelected));
        }
    }

    /**
     * Displays an error card in the results area when a search query fails,
     * including a retry button.
     */
    public void showErrorState(Runnable retryAction) {
        if (cardsContainer == null) {
            initUiBindings();
        }
        if (resultsCounterLabel != null) {
            resultsCounterLabel.setText("Unable to load flights");
        }
        if (cardsContainer == null) return;

        cardsContainer.getChildren().clear();

        VBox errorBox = new VBox(14);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.getStyleClass().addAll("inner", "flight-search-error-card");

        Label msgLabel = new Label("We couldn't load flights. Please check your connection and try again.");
        msgLabel.getStyleClass().add("flight-search-error-text");
        msgLabel.setWrapText(true);

        Button retryBtn = new Button("Retry");
        retryBtn.getStyleClass().addAll("btn-gold", "search-retry-btn");
        if (retryAction != null) {
            retryBtn.setOnAction(e -> retryAction.run());
        }
        Motion.attachHoverLift(retryBtn);

        errorBox.getChildren().addAll(msgLabel, retryBtn);
        cardsContainer.getChildren().add(errorBox);
    }

    /**
     * Renders result cards according to in-memory filter criteria.
     */
    public void refreshCardsView() {
        if (cardsContainer == null) {
            Node rootNode = getScene() != null ? getScene().getRoot() : null;
            if (rootNode == null) {
                Node curr = this;
                while (curr.getParent() != null) curr = curr.getParent();
                rootNode = curr;
            }
            if (rootNode != null) {
                cardsContainer = (VBox) rootNode.lookup("#resultsCardsContainer");
                resultsTitleLabel = (Label) rootNode.lookup("#resultsTitleLabel");
                resultsCounterLabel = (Label) rootNode.lookup("#resultsCountLabel");
            }
            if (cardsContainer == null) return;
        }

        cardsContainer.getChildren().clear();

        ObservableList<FlightSearchResult> allFlights = getItems();
        if (allFlights == null || allFlights.isEmpty()) {
            if (resultsCounterLabel != null) {
                resultsCounterLabel.setText("No flights found");
            }
            Label emptyLbl = new Label("No flights found for selected route and date.");
            emptyLbl.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 14px; -fx-text-fill: rgba(255,255,255,0.58); -fx-padding: 30px;");
            cardsContainer.getChildren().add(emptyLbl);
            return;
        }

        // Update results header using first flight or display labels
        FlightSearchResult firstFlight = allFlights.get(0);
        String originCity = (firstFlight.getOriginCity() != null && !firstFlight.getOriginCity().isBlank())
                ? firstFlight.getOriginCity()
                : ((fromCityLabel != null && !fromCityLabel.getText().isBlank()) ? fromCityLabel.getText() : "Johannesburg");
        String destCity = (firstFlight.getDestCity() != null && !firstFlight.getDestCity().isBlank())
                ? firstFlight.getDestCity()
                : ((toCityLabel != null && !toCityLabel.getText().isBlank()) ? toCityLabel.getText() : "Cape Town");

        if (resultsTitleLabel != null) {
            resultsTitleLabel.setText("Flights from " + originCity + " to " + destCity);
        }

        // Apply in-memory filters
        List<FlightSearchResult> filtered = new ArrayList<>();
        for (FlightSearchResult flight : allFlights) {
            if (!matchesDeparture(flight)) continue;
            if (!matchesStops(flight)) continue;
            if (!matchesPrice(flight)) continue;
            if (!matchesDuration(flight)) continue;
            filtered.add(flight);
        }

        int count = filtered.size();
        if (resultsCounterLabel != null) {
            resultsCounterLabel.setText("Showing " + count + " flights");
        }

        if (filtered.isEmpty()) {
            Label noMatch = new Label("No flights match the selected filters.");
            noMatch.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 14px; -fx-text-fill: rgba(255,255,255,0.58); -fx-padding: 30px;");
            cardsContainer.getChildren().add(noMatch);
            return;
        }

        for (FlightSearchResult flight : filtered) {
            boolean isExpanded = flight.getFlightId() != null && flight.getFlightId().equals(expandedFlightId);
            Node cardNode = buildFlightResultCard(flight, isExpanded);
            cardsContainer.getChildren().add(cardNode);
        }
    }

    private boolean matchesDeparture(FlightSearchResult flight) {
        if (selectedDepartureFilter == null) return true;
        String depTime = flight.getDepartureTime();
        if (depTime == null || depTime.length() < 5) return true;
        try {
            int hour = Integer.parseInt(depTime.substring(0, 2));
            return switch (selectedDepartureFilter) {
                case "Before 6 AM" -> hour < 6;
                case "6 AM to 12 PM" -> hour >= 6 && hour < 12;
                case "12 PM to 6 PM" -> hour >= 12 && hour < 18;
                case "After 6 PM" -> hour >= 18;
                default -> true;
            };
        } catch (Exception e) {
            return true;
        }
    }

    private boolean matchesStops(FlightSearchResult flight) {
        if (selectedStopsFilter == null) return true;
        // All scheduled flights in the system are nonstop direct flights
        return "Direct".equals(selectedStopsFilter);
    }

    private boolean matchesPrice(FlightSearchResult flight) {
        if (flight.getBasePrice() == null) return true;
        return flight.getBasePrice().doubleValue() <= maxPriceFilter;
    }

    private boolean matchesDuration(FlightSearchResult flight) {
        String dur = flight.getDuration();
        if (dur == null) return true;
        try {
            int h = 0, m = 0;
            if (dur.contains("h")) {
                String[] parts = dur.split("h");
                h = Integer.parseInt(parts[0].trim());
                if (parts.length > 1 && parts[1].contains("m")) {
                    m = Integer.parseInt(parts[1].replace("m", "").trim());
                }
            }
            int totalMins = h * 60 + m;
            return totalMins <= maxDurationMinutes;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Builds a single flight result card bound strictly to FlightSearchResult data.
     */
    public Node buildFlightResultCard(FlightSearchResult flight, boolean isExpanded) {
        VBox card = new VBox();
        card.getStyleClass().addAll("inner", "flight-result-card");
        card.setPadding(new Insets(16, 22, isExpanded ? 16 : 14, 22));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(824);

        // 1. Header Row: Logo circle + Brand + flight number / aircraft
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        StackPane logoCircle = new StackPane();
        logoCircle.getStyleClass().add("airline-logo-circle");
        SVGPath planeIconMini = new SVGPath();
        planeIconMini.setContent(SVG_PLANE_TINY);
        planeIconMini.setStyle("-fx-fill: #0b1220; -fx-scale-x: 0.65; -fx-scale-y: 0.65;");
        logoCircle.getChildren().add(planeIconMini);

        Label brandLabel = new Label(BrandConfig.APP_NAME);
        brandLabel.getStyleClass().add("airline-brand-text");

        String aircraft = (flight.getAircraftModel() != null && !flight.getAircraftModel().isBlank())
                ? flight.getAircraftModel() : "Aircraft";
        String flightMeta = flight.getFlightNumber() + " · " + aircraft;
        Label metaLabel = new Label(flightMeta);
        metaLabel.getStyleClass().add("flight-number-muted");

        headerRow.getChildren().addAll(logoCircle, brandLabel, metaLabel);

        // 2. Flight Path Row
        HBox pathRow = new HBox(20);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        pathRow.setPadding(new Insets(12, 0, 0, 0));
        pathRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathRow, Priority.ALWAYS);

        // Origin Block (width 190)
        VBox originBlock = new VBox(3);
        originBlock.setPrefWidth(190);
        originBlock.setMinWidth(190);
        originBlock.setMaxWidth(190);
        String origShort = AirportNames.shortName(flight.getOriginAirportName());
        String originTitle = (flight.getOriginCode() != null ? flight.getOriginCode() : "")
                + (!origShort.isBlank() ? ", " + origShort : "");
        Label originName = new Label(originTitle);
        originName.getStyleClass().add("airport-code-muted");
        Label originTime = new Label(flight.getDepartureTime());
        originTime.getStyleClass().add("flight-time-bold");
        originBlock.getChildren().addAll(originName, originTime);

        // Center flight path graphic (flex-grow)
        HBox flightPath = new HBox(8);
        flightPath.setAlignment(Pos.CENTER);
        flightPath.setMaxWidth(Double.MAX_VALUE);
        flightPath.setPrefWidth(220);
        HBox.setHgrow(flightPath, Priority.ALWAYS);

        Region dot = new Region();
        dot.getStyleClass().add("flight-path-dot");

        Region dashLine1 = new Region();
        dashLine1.getStyleClass().add("flight-path-line");
        dashLine1.setMinWidth(10);
        dashLine1.setPrefWidth(60);
        dashLine1.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dashLine1, Priority.ALWAYS);

        String durText = flight.getDuration() + " · Direct";
        Label durLabel = new Label(durText);
        durLabel.getStyleClass().add("flight-duration-pill");
        durLabel.setMinWidth(Region.USE_PREF_SIZE);

        Region dashLine2 = new Region();
        dashLine2.getStyleClass().add("flight-path-line");
        dashLine2.setMinWidth(10);
        dashLine2.setPrefWidth(60);
        dashLine2.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dashLine2, Priority.ALWAYS);

        SVGPath planeIconGold = new SVGPath();
        planeIconGold.setContent(SVG_PLANE_TINY);
        planeIconGold.setStyle("-fx-fill: #e8c46a; -fx-rotate: 90; -fx-scale-x: 0.75; -fx-scale-y: 0.75;");

        flightPath.getChildren().addAll(dot, dashLine1, durLabel, dashLine2, planeIconGold);

        // Destination Block (width 190)
        VBox destBlock = new VBox(3);
        destBlock.setPrefWidth(190);
        destBlock.setMinWidth(190);
        destBlock.setMaxWidth(190);
        String destShort = AirportNames.shortName(flight.getDestAirportName());
        String destTitle = (flight.getDestCode() != null ? flight.getDestCode() : "")
                + (!destShort.isBlank() ? ", " + destShort : "");
        Label destName = new Label(destTitle);
        destName.getStyleClass().add("airport-code-muted");
        Label destTime = new Label(flight.getArrivalTime());
        destTime.getStyleClass().add("flight-time-bold");
        destBlock.getChildren().addAll(destName, destTime);

        // Price Block (width 120, right aligned)
        Label priceLabel = new Label(flight.getPriceFormatted());
        priceLabel.getStyleClass().add("flight-price-bold");
        priceLabel.setPrefWidth(120);
        priceLabel.setMinWidth(120);
        priceLabel.setMaxWidth(120);
        priceLabel.setAlignment(Pos.CENTER_RIGHT);

        pathRow.getChildren().addAll(originBlock, flightPath, destBlock, priceLabel);

        // 3. Divider Line
        Region divider = new Region();
        divider.getStyleClass().add("card-divider-line");
        VBox.setMargin(divider, new Insets(12, 0, 10, 0));

        // 4. Footer Row: tags + "Flight details" expander
        HBox footerRow = new HBox();
        footerRow.setAlignment(Pos.CENTER_LEFT);

        int seats = flight.getAvailableSeats();
        String tagText = (seats <= 20)
                ? "Only " + seats + " seats left"
                : "Direct · " + seats + " seats available";
        Label tags = new Label(tagText);
        tags.getStyleClass().add("card-tag-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button detailsBtn = new Button("Flight details");
        detailsBtn.getStyleClass().add("flight-details-btn");

        SVGPath chevron = new SVGPath();
        chevron.setContent(isExpanded ? SVG_CHEVRON_UP : SVG_CHEVRON_DOWN);
        chevron.setStyle("-fx-stroke: #e8c46a; -fx-stroke-width: 3; -fx-stroke-linecap: round; -fx-fill: transparent; -fx-scale-x: 0.6; -fx-scale-y: 0.6;");
        detailsBtn.setGraphic(chevron);
        detailsBtn.setContentDisplay(ContentDisplay.RIGHT);

        detailsBtn.setOnAction(e -> {
            e.consume();
            if (isExpanded) {
                expandedFlightId = null;
            } else {
                expandedFlightId = flight.getFlightId();
            }
            refreshCardsView();
        });

        footerRow.getChildren().addAll(tags, spacer, detailsBtn);

        card.getChildren().addAll(headerRow, pathRow, divider, footerRow);

        // 5. Expanded details box if active
        if (isExpanded) {
            Node expandedBox = buildExpandedDetailsBox(flight);
            card.getChildren().add(expandedBox);
        }

        // Clicking the card row selects the flight and advances to the seat map
        card.setOnMouseClicked(e -> {
            if (FlightSearchController.currentInstance != null) {
                FlightSearchController.currentInstance.selectFlightFromGlassCard(flight);
            }
        });
        card.setStyle(card.getStyle() + "; -fx-cursor: hand;");

        return card;
    }

    /**
     * Builds the expanded flight details box with 4 tabs and leg row.
     */
    private Node buildExpandedDetailsBox(FlightSearchResult flight) {
        VBox box = new VBox(8);
        box.getStyleClass().add("expanded-details-box");
        VBox.setMargin(box, new Insets(12, 0, 0, 0));

        // Tab bar: Flight information, Date details, Baggage rules, Cancellation rules
        HBox tabBar = new HBox(4);
        tabBar.getStyleClass().add("tab-bar-container");

        String[] tabs = {"Flight information", "Date details", "Baggage rules", "Cancellation rules"};
        for (String tabName : tabs) {
            Button tabBtn = new Button(tabName);
            tabBtn.getStyleClass().add("details-tab-btn");
            HBox.setHgrow(tabBtn, Priority.ALWAYS);
            tabBtn.setMaxWidth(Double.MAX_VALUE);

            if (tabName.equals(activeDetailsTab)) {
                tabBtn.getStyleClass().add("details-tab-btn-on");
            }

            tabBtn.setOnAction(e -> {
                e.consume();
                activeDetailsTab = tabName;
                refreshCardsView();
            });

            tabBar.getChildren().add(tabBtn);
        }

        box.getChildren().add(tabBar);

        // Tab content
        VBox contentBox = new VBox(6);
        contentBox.setPadding(new Insets(4, 8, 4, 8));

        switch (activeDetailsTab) {
            case "Flight information" -> {
                String origZoneAbbr = FlightTimes.zoneAbbreviation(flight.getOriginZone(), flight.getDeparture());
                String destZoneAbbr = FlightTimes.zoneAbbreviation(flight.getDestZone(), flight.getArrival());
                String origTimeWithZone = flight.getOriginCode() + " · " + flight.getDepartureTime()
                        + (origZoneAbbr.isEmpty() ? "" : " " + origZoneAbbr);
                String destTimeWithZone = flight.getDestCode() + " · " + flight.getArrivalTime()
                        + (destZoneAbbr.isEmpty() ? "" : " " + destZoneAbbr);

                HBox leg = buildLegRow(
                        flight.getFlightNumber(),
                        flight.getAircraftModel() != null ? flight.getAircraftModel() : "Aircraft",
                        AirportNames.shortName(flight.getOriginAirportName()),
                        origTimeWithZone,
                        flight.getDuration(),
                        AirportNames.shortName(flight.getDestAirportName()),
                        destTimeWithZone
                );
                contentBox.getChildren().add(leg);
            }
            case "Date details" -> {
                String depDateStr;
                if (flight.getDeparture() != null && flight.getOriginZone() != null) {
                    DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("d MMM ''yy (EEEE)", Locale.ENGLISH);
                    depDateStr = flight.getDeparture().atZone(flight.getOriginZone()).format(dFmt);
                } else {
                    String depDate = (depDateLabel != null && !depDateLabel.getText().isBlank()) ? depDateLabel.getText() : "2 Nov '26";
                    String depDay = (depDayLabel != null && !depDayLabel.getText().isBlank()) ? depDayLabel.getText() : "Monday";
                    depDateStr = depDate + " (" + depDay + ")";
                }
                String origZoneAbbr = FlightTimes.zoneAbbreviation(flight.getOriginZone(), flight.getDeparture());
                String destZoneAbbr = FlightTimes.zoneAbbreviation(flight.getDestZone(), flight.getArrival());

                contentBox.getChildren().addAll(
                        buildRuleRow("Departure Date", depDateStr),
                        buildRuleRow("Departure Time", flight.getDepartureTime() + (origZoneAbbr.isEmpty() ? "" : " " + origZoneAbbr)),
                        buildRuleRow("Arrival Time", flight.getArrivalTime() + (destZoneAbbr.isEmpty() ? "" : " " + destZoneAbbr)),
                        buildRuleRow("Return Date", "One way")
                );
            }
            case "Baggage rules" -> {
                for (FarePolicy.PolicyRule r : FarePolicy.getBaggageRules()) {
                    contentBox.getChildren().add(buildRuleRow(r.label(), r.value()));
                }
            }
            case "Cancellation rules" -> {
                for (FarePolicy.PolicyRule r : FarePolicy.getCancellationRules()) {
                    contentBox.getChildren().add(buildRuleRow(r.label(), r.value()));
                }
            }
        }

        box.getChildren().add(contentBox);
        return box;
    }

    private HBox buildLegRow(String flNo, String plane, String origAirport, String origTime,
                             String duration, String destAirport, String destTime) {
        HBox row = new HBox(18);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));

        VBox flightBlock = new VBox(2);
        flightBlock.setPrefWidth(90);
        flightBlock.setMinWidth(Region.USE_PREF_SIZE);
        Label flLabel = new Label(flNo);
        flLabel.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #ffffff;");
        flLabel.setMinWidth(Region.USE_PREF_SIZE);
        Label planeLabel = new Label(plane);
        planeLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.58);");
        planeLabel.setMinWidth(Region.USE_PREF_SIZE);
        flightBlock.getChildren().addAll(flLabel, planeLabel);

        VBox origBlock = new VBox(2);
        origBlock.setPrefWidth(140);
        origBlock.setMinWidth(Region.USE_PREF_SIZE);
        Label origAp = new Label(origAirport);
        origAp.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.58);");
        origAp.setMinWidth(Region.USE_PREF_SIZE);
        Label origTm = new Label(origTime);
        origTm.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #ffffff;");
        origTm.setMinWidth(Region.USE_PREF_SIZE);
        origBlock.getChildren().addAll(origAp, origTm);

        HBox path = new HBox(8);
        path.setAlignment(Pos.CENTER);
        path.setMaxWidth(Double.MAX_VALUE);
        path.setPrefWidth(120);
        path.setMinWidth(50);
        HBox.setHgrow(path, Priority.ALWAYS);

        Region dot = new Region();
        dot.getStyleClass().add("flight-path-dot");
        Region dash1 = new Region();
        dash1.getStyleClass().add("flight-path-line");
        dash1.setMinWidth(6);
        dash1.setPrefWidth(24);
        dash1.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dash1, Priority.ALWAYS);
        Label dur = new Label(duration);
        dur.getStyleClass().add("flight-duration-pill");
        dur.setMinWidth(Region.USE_PREF_SIZE);
        Region dash2 = new Region();
        dash2.getStyleClass().add("flight-path-line");
        dash2.setMinWidth(6);
        dash2.setPrefWidth(24);
        dash2.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dash2, Priority.ALWAYS);
        SVGPath planeIcon = new SVGPath();
        planeIcon.setContent(SVG_PLANE_TINY);
        planeIcon.setStyle("-fx-fill: #e8c46a; -fx-rotate: 90; -fx-scale-x: 0.65; -fx-scale-y: 0.65;");
        path.getChildren().addAll(dot, dash1, dur, dash2, planeIcon);

        VBox destBlock = new VBox(2);
        destBlock.setPrefWidth(140);
        destBlock.setMinWidth(Region.USE_PREF_SIZE);
        destBlock.setAlignment(Pos.CENTER_RIGHT);
        Label destAp = new Label(destAirport);
        destAp.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.58);");
        destAp.setMinWidth(Region.USE_PREF_SIZE);
        Label destTm = new Label(destTime);
        destTm.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #ffffff;");
        destTm.setMinWidth(Region.USE_PREF_SIZE);
        destBlock.getChildren().addAll(destAp, destTm);

        row.getChildren().addAll(flightBlock, origBlock, path, destBlock);
        return row;
    }

    private HBox buildRuleRow(String label, String value) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 12, 6, 12));

        Label lbl = new Label(label);
        lbl.setPrefWidth(150);
        lbl.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-text-fill: #e8c46a;");

        Label val = new Label(value);
        val.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 13px; -fx-text-fill: rgba(255,255,255,0.85);");
        HBox.setHgrow(val, Priority.ALWAYS);

        row.getChildren().addAll(lbl, val);
        return row;
    }
}
