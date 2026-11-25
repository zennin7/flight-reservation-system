package com.group2.flightres.util;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.controller.FlightSearchController;
import com.group2.flightres.model.Airport;
import com.group2.flightres.model.FlightResultSummary;
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

import java.time.Duration;
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
    private ComboBox<?> fromComboBox;
    private ComboBox<?> toComboBox;
    private DatePicker departureDatePickerNode;

    private Label fromCityLabel;
    private Label fromSubLabel;
    private Label toCityLabel;
    private Label toSubLabel;
    private Label depDateLabel;
    private Label depDayLabel;

    // Filters sidebar container
    private VBox filtersCard;
    private Label filtersTitleLabel;

    // Interactive filter state
    private String selectedDepartureFilter = null;
    private double maxPriceFilter = Double.MAX_VALUE;
    private double maxDurationMinutes = Double.MAX_VALUE;
    private double minPriceBound = 0.0;
    private double maxPriceBound = Double.MAX_VALUE;
    private double minDurationBound = 0.0;
    private double maxDurationBound = Double.MAX_VALUE;

    private boolean economyFilterActive = true;
    private boolean firstClassFilterActive = false;

    private Long expandedFlightId = null; // expand 2nd card (index 1) by default
    private String activeDetailsTab = "Flight information";

    // UI Pill and control references for visual toggle updates
    private boolean isInitializingBindings = false;
    private final Map<String, Button> departurePillMap = new HashMap<>();
    private Slider priceSlider;
    private Label priceFilterValueLabel;
    private Label priceSingleValueLabel;

    private Slider durationSlider;
    private Label durationFilterValueLabel;
    private Label durationSingleValueLabel;

    private StackPane economyCheckCircle;
    private StackPane firstClassCheckCircle;
    private Label economyCabinLabel;
    private Label firstCabinLabel;
    private HBox economyCabinRow;
    private HBox firstCabinRow;

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
                    updateFilterBounds(new ArrayList<>(newItems));
                    Platform.runLater(this::refreshCardsView);
                });
                updateFilterBounds(new ArrayList<>(newItems));
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
        if (isInitializingBindings) return;
        isInitializingBindings = true;
        try {
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

            fromComboBox = (ComboBox<?>) root.lookup("#fromCombo");
            toComboBox = (ComboBox<?>) root.lookup("#toCombo");
            departureDatePickerNode = (DatePicker) root.lookup("#departureDatePicker");

            fromCityLabel = (Label) root.lookup("#fromCityDisplay");
            fromSubLabel = (Label) root.lookup("#fromSubDisplay");
            toCityLabel = (Label) root.lookup("#toCityDisplay");
            toSubLabel = (Label) root.lookup("#toSubDisplay");
            depDateLabel = (Label) root.lookup("#depDateDisplay");
            depDayLabel = (Label) root.lookup("#depDayDisplay");

            filtersCard = (VBox) root.lookup("#filtersCard");
            filtersTitleLabel = (Label) root.lookup("#filtersTitleLabel");

            if (fromComboBox != null) {
                fromComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateLocationLabels());
            }
            if (toComboBox != null) {
                toComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateLocationLabels());
            }
            if (departureDatePickerNode != null) {
                departureDatePickerNode.valueProperty().addListener((obs, oldVal, newVal) -> updateLocationLabels());
            }

            priceFilterValueLabel = (Label) root.lookup("#priceFilterValueLabel");
            priceSingleValueLabel = (Label) root.lookup("#priceSingleValueLabel");
            Slider pSlider = (Slider) root.lookup("#priceSlider");
            if (pSlider != null) {
                this.priceSlider = pSlider;
                pSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    setMaxPriceFilter(newVal.doubleValue());
                    updateSliderGradient(pSlider);
                });
                pSlider.minProperty().addListener((obs, o, n) -> updateSliderGradient(pSlider));
                pSlider.maxProperty().addListener((obs, o, n) -> updateSliderGradient(pSlider));
                pSlider.skinProperty().addListener((obs, o, n) -> {
                    if (n != null) {
                        Platform.runLater(() -> updateSliderGradient(pSlider));
                    }
                });
                Platform.runLater(() -> updateSliderGradient(pSlider));
            }

            durationFilterValueLabel = (Label) root.lookup("#durationFilterValueLabel");
            durationSingleValueLabel = (Label) root.lookup("#durationSingleValueLabel");
            Slider dSlider = (Slider) root.lookup("#durationSlider");
            if (dSlider != null) {
                this.durationSlider = dSlider;
                dSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    setMaxDurationFilter(newVal.doubleValue());
                    updateSliderGradient(dSlider);
                });
                dSlider.minProperty().addListener((obs, o, n) -> updateSliderGradient(dSlider));
                dSlider.maxProperty().addListener((obs, o, n) -> updateSliderGradient(dSlider));
                dSlider.skinProperty().addListener((obs, o, n) -> {
                    if (n != null) {
                        Platform.runLater(() -> updateSliderGradient(dSlider));
                    }
                });
                Platform.runLater(() -> updateSliderGradient(dSlider));
            }

            economyCheckCircle = (StackPane) root.lookup("#economyCheckCircle");
            firstClassCheckCircle = (StackPane) root.lookup("#firstClassCheckCircle");
            economyCabinLabel = (Label) root.lookup("#economyCabinLabel");
            firstCabinLabel = (Label) root.lookup("#firstCabinLabel");
            economyCabinRow = (HBox) root.lookup("#economyCabinRow");
            firstCabinRow = (HBox) root.lookup("#firstCabinRow");

            // Find departure pill buttons
            String[] pillLabels = {"Before 6 AM", "6 AM to 12 PM", "12 PM to 6 PM", "After 6 PM"};
            for (String pillText : pillLabels) {
                for (Node btnNode : root.lookupAll(".pill-button")) {
                    if (btnNode instanceof Button b && pillText.equals(b.getText())) {
                        departurePillMap.put(pillText, b);
                    }
                }
            }

            updateLocationLabels();
            updateCheckStyles();
            updatePillStyles();
            ObservableList<FlightSearchResult> items = getItems();
            if (items != null && !items.isEmpty() && cardsContainer != null) {
                updateFilterBounds(new ArrayList<>(items));
            }
            if (cardsContainer != null) {
                refreshCardsView();
            }
        } finally {
            isInitializingBindings = false;
        }
    }

    public void registerDeparturePill(String key, Button button) {
        departurePillMap.put(key, button);
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
     * Synchronizes From / To / Departure date display labels from data entities.
     */
    public void updateLocationLabels() {
        if (fromComboBox != null && fromCityLabel != null) {
            Object obj = fromComboBox.getValue();
            if (obj instanceof Airport from) {
                fromCityLabel.setText(from.city());
                if (fromSubLabel != null) {
                    fromSubLabel.setText(from.code() + ", " + AirportNames.shortName(from.name()));
                }
            }
        }

        if (toComboBox != null && toCityLabel != null) {
            Object obj = toComboBox.getValue();
            if (obj instanceof Airport to) {
                toCityLabel.setText(to.city());
                if (toSubLabel != null) {
                    toSubLabel.setText(to.code() + ", " + AirportNames.shortName(to.name()));
                }
            }
        }

        if (departureDatePickerNode != null && depDateLabel != null) {
            LocalDate date = departureDatePickerNode.getValue();
            if (date != null) {
                DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("d MMM ''yy", Locale.ENGLISH);
                DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
                depDateLabel.setText(date.format(dFmt));
                if (depDayLabel != null) depDayLabel.setText(date.format(dayFmt));
            }
        }

        if (resultsTitleLabel != null) {
            String fromCity = (fromCityLabel != null && !fromCityLabel.getText().isBlank()) ? fromCityLabel.getText() : "Johannesburg";
            String toCity = (toCityLabel != null && !toCityLabel.getText().isBlank()) ? toCityLabel.getText() : "Cape Town";
            if (fromComboBox != null && fromComboBox.getValue() instanceof Airport from) {
                fromCity = from.city();
            }
            if (toComboBox != null && toComboBox.getValue() instanceof Airport to) {
                toCity = to.city();
            }
            resultsTitleLabel.setText("Flights from " + fromCity + " to " + toCity);
        }
    }

    private String formatPrice(double val) {
        return MoneyFormatter.format(java.math.BigDecimal.valueOf(val));
    }

    /**
     * Dynamically updates filter bounds from current search results.
     */
    public void updateFilterBounds(List<FlightSearchResult> results) {
        if (cardsContainer == null && !isInitializingBindings) {
            initUiBindings();
        }

        if (results == null || results.isEmpty()) {
            if (filtersCard != null) {
                filtersCard.setVisible(false);
                filtersCard.setManaged(false);
            }
            if (filtersTitleLabel != null) {
                filtersTitleLabel.setVisible(false);
                filtersTitleLabel.setManaged(false);
            }
            return;
        }

        if (filtersCard != null) {
            filtersCard.setVisible(true);
            filtersCard.setManaged(true);
        }
        if (filtersTitleLabel != null) {
            filtersTitleLabel.setVisible(true);
            filtersTitleLabel.setManaged(true);
        }

        FlightResultSummary summary = FlightResultSummary.fromResults(results);

        // Price: rounded down/up to nearest 50
        minPriceBound = Math.floor(summary.minPrice().doubleValue() / 50.0) * 50.0;
        maxPriceBound = Math.ceil(summary.maxPrice().doubleValue() / 50.0) * 50.0;
        maxPriceFilter = maxPriceBound;

        if (priceSlider != null) {
            if (minPriceBound == maxPriceBound) {
                priceSlider.setVisible(false);
                priceSlider.setManaged(false);
                if (priceSingleValueLabel != null) {
                    priceSingleValueLabel.setText("Fixed fare: " + formatPrice(maxPriceBound));
                    priceSingleValueLabel.setVisible(true);
                    priceSingleValueLabel.setManaged(true);
                }
                if (priceFilterValueLabel != null) {
                    priceFilterValueLabel.setText("");
                    priceFilterValueLabel.setVisible(false);
                    priceFilterValueLabel.setManaged(false);
                }
            } else {
                priceSlider.setMin(minPriceBound);
                priceSlider.setMax(maxPriceBound);
                priceSlider.setValue(maxPriceBound);
                priceSlider.setVisible(true);
                priceSlider.setManaged(true);
                if (priceSingleValueLabel != null) {
                    priceSingleValueLabel.setVisible(false);
                    priceSingleValueLabel.setManaged(false);
                }
                if (priceFilterValueLabel != null) {
                    priceFilterValueLabel.setText(formatPrice(maxPriceBound));
                    priceFilterValueLabel.setVisible(true);
                    priceFilterValueLabel.setManaged(true);
                }
            }
        }

        // Duration: minutes from Instants
        minDurationBound = summary.minDurationMinutes();
        maxDurationBound = summary.maxDurationMinutes();
        maxDurationMinutes = maxDurationBound;

        if (durationSlider != null) {
            long maxHrs = (long) maxDurationBound / 60;
            long maxMins = (long) maxDurationBound % 60;
            String formattedMax = String.format(Locale.ENGLISH, "%dh %02dm", maxHrs, maxMins);
            if (minDurationBound == maxDurationBound) {
                durationSlider.setVisible(false);
                durationSlider.setManaged(false);
                if (durationSingleValueLabel != null) {
                    durationSingleValueLabel.setText("Fixed duration: " + formattedMax);
                    durationSingleValueLabel.setVisible(true);
                    durationSingleValueLabel.setManaged(true);
                }
                if (durationFilterValueLabel != null) {
                    durationFilterValueLabel.setText("");
                    durationFilterValueLabel.setVisible(false);
                    durationFilterValueLabel.setManaged(false);
                }
            } else {
                durationSlider.setMin(minDurationBound);
                durationSlider.setMax(maxDurationBound);
                durationSlider.setValue(maxDurationBound);
                durationSlider.setVisible(true);
                durationSlider.setManaged(true);
                if (durationSingleValueLabel != null) {
                    durationSingleValueLabel.setVisible(false);
                    durationSingleValueLabel.setManaged(false);
                }
                if (durationFilterValueLabel != null) {
                    durationFilterValueLabel.setText(formattedMax);
                    durationFilterValueLabel.setVisible(true);
                    durationFilterValueLabel.setManaged(true);
                }
            }
        }

        // Cabin counts & row status
        long econCount = results.stream().filter(f -> f.getAvailableEconomy() > 0).count();
        long firstCount = results.stream().filter(f -> f.getAvailableFirst() > 0).count();

        if (economyCabinLabel != null) {
            if (econCount > 0) {
                economyCabinLabel.setText("Economy · " + (econCount == 1 ? "1 flight" : econCount + " flights"));
                if (economyCabinRow != null) {
                    economyCabinRow.setDisable(false);
                    economyCabinRow.setStyle("");
                }
            } else {
                economyCabinLabel.setText("Economy · None available");
                if (economyCabinRow != null) {
                    economyCabinRow.setDisable(true);
                    economyCabinRow.setStyle("-fx-opacity: 0.45;");
                }
                economyFilterActive = false;
            }
        }

        if (firstCabinLabel != null) {
            if (firstCount > 0) {
                firstCabinLabel.setText("First Class · " + (firstCount == 1 ? "1 flight" : firstCount + " flights"));
                if (firstCabinRow != null) {
                    firstCabinRow.setDisable(false);
                    firstCabinRow.setStyle("");
                }
            } else {
                firstCabinLabel.setText("First Class · None available");
                if (firstCabinRow != null) {
                    firstCabinRow.setDisable(true);
                    firstCabinRow.setStyle("-fx-opacity: 0.45;");
                }
                firstClassFilterActive = false;
            }
        }

        // Departure pill counts (dimmed when 0 matches)
        for (Map.Entry<String, Button> entry : departurePillMap.entrySet()) {
            String bucket = entry.getKey();
            Button pill = entry.getValue();
            long count = results.stream().filter(f -> matchesDeparturePill(f, bucket)).count();
            if (count == 0) {
                pill.setStyle("-fx-opacity: 0.45;");
            } else {
                pill.setStyle("");
            }
            pill.setDisable(false);
        }

        updatePillStyles();
        updateCheckStyles();
    }

    public static void updateSliderGradient(Slider slider) {
        if (slider == null) return;
        Node trackNode = slider.lookup(".track");
        if (trackNode instanceof StackPane track) {
            double min = slider.getMin();
            double max = slider.getMax();
            double val = slider.getValue();
            double pct = (max > min) ? Math.max(0.0, Math.min(100.0, (val - min) / (max - min) * 100.0)) : 100.0;
            track.setStyle(String.format(Locale.US,
                    "-fx-background-color: linear-gradient(to right, -glass-accent 0%%, -glass-accent %.2f%%, rgba(255, 255, 255, 0.12) %.2f%%, rgba(255, 255, 255, 0.12) 100%%); -fx-background-radius: 4px;",
                    pct, pct));
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

    public void setMaxPriceFilter(double maxPrice) {
        this.maxPriceFilter = maxPrice;
        if (priceFilterValueLabel != null && minPriceBound != maxPriceBound) {
            priceFilterValueLabel.setText(formatPrice(maxPrice));
        }
        refreshCardsView();
    }

    public void resetPriceFilter() {
        this.maxPriceFilter = maxPriceBound;
        if (priceSlider != null) {
            priceSlider.setValue(maxPriceBound);
        }
        if (priceFilterValueLabel != null && minPriceBound != maxPriceBound) {
            priceFilterValueLabel.setText(formatPrice(maxPriceBound));
        }
        refreshCardsView();
    }

    public void setMaxDurationFilter(double maxDuration) {
        this.maxDurationMinutes = maxDuration;
        if (durationFilterValueLabel != null && minDurationBound != maxDurationBound) {
            long hrs = (long) maxDuration / 60;
            long mins = (long) maxDuration % 60;
            durationFilterValueLabel.setText(String.format(Locale.ENGLISH, "%dh %02dm", hrs, mins));
        }
        refreshCardsView();
    }

    public void resetDurationFilter() {
        this.maxDurationMinutes = maxDurationBound;
        if (durationSlider != null) {
            durationSlider.setValue(maxDurationBound);
        }
        if (durationFilterValueLabel != null && minDurationBound != maxDurationBound) {
            long hrs = (long) maxDurationBound / 60;
            long mins = (long) maxDurationBound % 60;
            durationFilterValueLabel.setText(String.format(Locale.ENGLISH, "%dh %02dm", hrs, mins));
        }
        refreshCardsView();
    }

    public void toggleCabinFilterEconomy() {
        this.economyFilterActive = !this.economyFilterActive;
        updateCheckStyles();
        refreshCardsView();
    }

    public void toggleCabinFilterFirst() {
        this.firstClassFilterActive = !this.firstClassFilterActive;
        updateCheckStyles();
        refreshCardsView();
    }

    public void resetCabinFilter() {
        this.economyFilterActive = false;
        this.firstClassFilterActive = false;
        updateCheckStyles();
        refreshCardsView();
    }

    public void resetFiltersToDefault() {
        this.selectedDepartureFilter = null;
        this.maxPriceFilter = maxPriceBound;
        this.maxDurationMinutes = maxDurationBound;
        this.economyFilterActive = false;
        this.firstClassFilterActive = false;
        if (priceSlider != null) priceSlider.setValue(maxPriceBound);
        if (durationSlider != null) durationSlider.setValue(maxDurationBound);
        updatePillStyles();
        updateCheckStyles();
    }

    private void updatePillStyles() {
        for (Map.Entry<String, Button> entry : departurePillMap.entrySet()) {
            boolean active = entry.getKey().equals(selectedDepartureFilter);
            entry.getValue().getStyleClass().remove("pill-button-on");
            if (active) entry.getValue().getStyleClass().add("pill-button-on");
        }
    }

    private void updateCheckStyles() {
        if (economyCheckCircle != null) {
            economyCheckCircle.getStyleClass().remove("check-circle-on");
            if (economyFilterActive) {
                economyCheckCircle.getStyleClass().add("check-circle-on");
            }
        }
        if (firstClassCheckCircle != null) {
            firstClassCheckCircle.getStyleClass().remove("check-circle-on");
            if (firstClassFilterActive) {
                firstClassCheckCircle.getStyleClass().add("check-circle-on");
            }
        }
    }

    public void swapLocations() {
        FlightSearchController ctrl = FlightSearchController.currentInstance;
        if (ctrl != null) {
            // Handled via controller
        }
    }

    /**
     * Displays a non-blocking loading indicator card while flights are fetched on a background thread.
     */
    public void showLoadingState(String message) {
        if (cardsContainer == null) {
            initUiBindings();
        }
        if (cardsContainer == null) return;
        cardsContainer.getChildren().clear();

        VBox loadingBox = new VBox(12);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.getStyleClass().addAll("inner", "flight-search-state-card");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(32, 32);
        spinner.setMaxSize(32, 32);

        Label msgLabel = new Label(message != null ? message : "Searching flights...");
        msgLabel.getStyleClass().add("flight-search-state-text");

        loadingBox.getChildren().addAll(spinner, msgLabel);
        cardsContainer.getChildren().add(loadingBox);
    }

    /**
     * Displays an honest error state card in the results container.
     */
    public void showErrorState(Runnable retryAction) {
        if (cardsContainer == null) {
            initUiBindings();
        }
        if (filtersCard != null) {
            filtersCard.setVisible(false);
            filtersCard.setManaged(false);
        }
        if (filtersTitleLabel != null) {
            filtersTitleLabel.setVisible(false);
            filtersTitleLabel.setManaged(false);
        }
        if (resultsCounterLabel != null) {
            resultsCounterLabel.setText("");
            resultsCounterLabel.setVisible(false);
        }
        if (cardsContainer == null) return;

        cardsContainer.getChildren().clear();

        VBox errorBox = new VBox(16);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.getStyleClass().addAll("inner", "flight-search-state-card");

        Label msgLabel = new Label("We couldn't load flights. Please check your connection and try again.");
        msgLabel.getStyleClass().add("flight-search-state-text");
        msgLabel.setWrapText(true);

        Button retryBtn = new Button("Retry");
        retryBtn.getStyleClass().addAll("btn-gold", "flight-search-state-btn", "search-retry-btn");
        if (retryAction != null) {
            retryBtn.setOnAction(e -> retryAction.run());
        }
        Motion.attachHoverLift(retryBtn);

        errorBox.getChildren().addAll(msgLabel, retryBtn);
        cardsContainer.getChildren().add(errorBox);
    }

    /**
     * Displays an honest empty state card when no open scheduled flights exist on the selected date.
     */
    public void showEmptyState(Runnable nextDateAction) {
        if (cardsContainer == null) {
            initUiBindings();
        }
        if (filtersCard != null) {
            filtersCard.setVisible(false);
            filtersCard.setManaged(false);
        }
        if (filtersTitleLabel != null) {
            filtersTitleLabel.setVisible(false);
            filtersTitleLabel.setManaged(false);
        }
        if (resultsCounterLabel != null) {
            resultsCounterLabel.setText("");
            resultsCounterLabel.setVisible(false);
        }
        if (cardsContainer == null) return;

        cardsContainer.getChildren().clear();

        VBox emptyBox = new VBox(16);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.getStyleClass().addAll("inner", "flight-search-state-card");

        Label msgLabel = new Label("No flights available for this date");
        msgLabel.getStyleClass().add("flight-search-state-text");
        msgLabel.setWrapText(true);

        Button nextDateBtn = new Button("Next available date");
        nextDateBtn.getStyleClass().addAll("btn-gold", "flight-search-state-btn", "search-retry-btn");
        if (nextDateAction != null) {
            nextDateBtn.setOnAction(e -> nextDateAction.run());
        }
        Motion.attachHoverLift(nextDateBtn);

        emptyBox.getChildren().addAll(msgLabel, nextDateBtn);
        cardsContainer.getChildren().add(emptyBox);
    }

    /**
     * Displays a message when no upcoming flights exist on the route.
     */
    public void showNoUpcomingFlightsState() {
        if (cardsContainer == null) {
            initUiBindings();
        }
        if (filtersCard != null) {
            filtersCard.setVisible(false);
            filtersCard.setManaged(false);
        }
        if (filtersTitleLabel != null) {
            filtersTitleLabel.setVisible(false);
            filtersTitleLabel.setManaged(false);
        }
        if (resultsCounterLabel != null) {
            resultsCounterLabel.setText("");
            resultsCounterLabel.setVisible(false);
        }
        if (cardsContainer == null) return;

        cardsContainer.getChildren().clear();

        VBox emptyBox = new VBox(16);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.getStyleClass().addAll("inner", "flight-search-state-card");

        Label msgLabel = new Label("No upcoming flights available on this route.");
        msgLabel.getStyleClass().add("flight-search-state-text");
        msgLabel.setWrapText(true);

        emptyBox.getChildren().add(msgLabel);
        cardsContainer.getChildren().add(emptyBox);
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
            return;
        }

        // Apply in-memory filters
        List<FlightSearchResult> filtered = new ArrayList<>();
        for (FlightSearchResult flight : allFlights) {
            if (!matchesDeparture(flight)) continue;
            if (!matchesPrice(flight)) continue;
            if (!matchesDuration(flight)) continue;
            if (!matchesCabin(flight)) continue;
            filtered.add(flight);
        }

        if (filtered.isEmpty()) {
            VBox emptyFilterBox = new VBox(14);
            emptyFilterBox.setAlignment(Pos.CENTER);
            emptyFilterBox.getStyleClass().addAll("inner", "flight-search-state-card");
            Label msgLabel = new Label("No flights match your active filter criteria.");
            msgLabel.getStyleClass().add("flight-search-state-text");
            emptyFilterBox.getChildren().add(msgLabel);
            cardsContainer.getChildren().add(emptyFilterBox);
            return;
        }

        int index = 0;
        for (FlightSearchResult flight : filtered) {
            boolean isExpanded = (expandedFlightId != null && expandedFlightId.equals(flight.getFlightId()))
                    || (expandedFlightId == null && index == 1);
            Node card = buildFlightResultCard(flight, isExpanded);
            cardsContainer.getChildren().add(card);
            index++;
        }
    }

    public boolean matchesDeparture(FlightSearchResult flight) {
        if (selectedDepartureFilter == null) return true;
        return matchesDeparturePill(flight, selectedDepartureFilter);
    }

    private boolean matchesDeparturePill(FlightSearchResult flight, String bucket) {
        if (flight == null || flight.getDeparture() == null || flight.getOriginZone() == null) {
            return false;
        }
        int hour = flight.getDeparture().atZone(flight.getOriginZone()).getHour();
        return switch (bucket) {
            case "Before 6 AM" -> hour < 6;
            case "6 AM to 12 PM" -> hour >= 6 && hour < 12;
            case "12 PM to 6 PM" -> hour >= 12 && hour < 18;
            case "After 6 PM" -> hour >= 18;
            default -> true;
        };
    }

    public boolean matchesPrice(FlightSearchResult flight) {
        if (flight == null || flight.getBasePrice() == null) return true;
        return flight.getBasePrice().doubleValue() <= maxPriceFilter;
    }

    public boolean matchesDuration(FlightSearchResult flight) {
        if (flight == null || flight.getDeparture() == null || flight.getArrival() == null) return true;
        long flightMinutes = Duration.between(flight.getDeparture(), flight.getArrival()).toMinutes();
        return flightMinutes <= maxDurationMinutes;
    }

    public boolean matchesCabin(FlightSearchResult flight) {
        if (flight == null) return false;
        if ((!economyFilterActive && !firstClassFilterActive) || (economyFilterActive && firstClassFilterActive)) {
            return true;
        }
        if (economyFilterActive && !firstClassFilterActive) {
            return flight.getAvailableEconomy() > 0;
        }
        if (firstClassFilterActive && !economyFilterActive) {
            return flight.getAvailableFirst() > 0;
        }
        return true;
    }

    /**
     * Builds a single flight result card bound strictly to FlightSearchResult data.
     */
    public void setExpandedCardIndex(int index) {
        javafx.collections.ObservableList<FlightSearchResult> items = getItems();
        if (items != null && index >= 0 && index < items.size()) {
            this.expandedFlightId = items.get(index).getFlightId();
            refreshCardsView();
        }
    }

    public void setExpandedFlightId(Long flightId) {
        this.expandedFlightId = flightId;
        refreshCardsView();
    }

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
        brandLabel.setMinWidth(Region.USE_PREF_SIZE);

        String aircraft = (flight.getAircraftModel() != null && !flight.getAircraftModel().isBlank())
                ? flight.getAircraftModel() : "Aircraft";
        String flightMeta = flight.getFlightNumber() + " · " + aircraft;
        Label metaLabel = new Label(flightMeta);
        metaLabel.getStyleClass().add("flight-number-muted");
        metaLabel.setMinWidth(Region.USE_PREF_SIZE);

        headerRow.getChildren().addAll(logoCircle, brandLabel, metaLabel);

        // 2. Flight Path Row
        HBox pathRow = new HBox(10);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        pathRow.setPadding(new Insets(12, 0, 0, 0));
        pathRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathRow, Priority.ALWAYS);

        // Origin Block
        VBox originBlock = new VBox(3);
        originBlock.setMinWidth(Region.USE_PREF_SIZE);
        String origShort = AirportNames.shortName(flight.getOriginAirportName());
        String originTitle = (flight.getOriginCode() != null ? flight.getOriginCode() : "")
                + (!origShort.isBlank() ? ", " + origShort : "");
        Label originName = new Label(originTitle);
        originName.getStyleClass().add("airport-code-muted");
        originName.setWrapText(true);
        originName.setMinWidth(Region.USE_PREF_SIZE);
        Label originTime = new Label(flight.getDepartureTime());
        originTime.getStyleClass().add("flight-time-bold");
        originTime.setMinWidth(Region.USE_PREF_SIZE);
        originBlock.getChildren().addAll(originName, originTime);

        // Center flight path graphic (flex-grow)
        HBox flightPath = new HBox(6);
        flightPath.setAlignment(Pos.CENTER);
        flightPath.setMaxWidth(Double.MAX_VALUE);
        flightPath.setPrefWidth(220);
        HBox.setHgrow(flightPath, Priority.ALWAYS);

        Region dot = new Region();
        dot.getStyleClass().add("flight-path-dot");

        Region dashLine1 = new Region();
        dashLine1.getStyleClass().add("flight-path-line");
        dashLine1.setMinWidth(4);
        dashLine1.setPrefWidth(60);
        dashLine1.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dashLine1, Priority.ALWAYS);

        String durText = flight.getDuration() + " · Direct";
        Label durLabel = new Label(durText);
        durLabel.getStyleClass().add("flight-duration-pill");
        durLabel.setMinWidth(Region.USE_PREF_SIZE);

        Region dashLine2 = new Region();
        dashLine2.getStyleClass().add("flight-path-line");
        dashLine2.setMinWidth(4);
        dashLine2.setPrefWidth(60);
        dashLine2.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dashLine2, Priority.ALWAYS);

        SVGPath planeIconGold = new SVGPath();
        planeIconGold.setContent(SVG_PLANE_TINY);
        planeIconGold.setStyle("-fx-fill: -glass-cream; -fx-rotate: 90; -fx-scale-x: 0.75; -fx-scale-y: 0.75;");

        flightPath.getChildren().addAll(dot, dashLine1, durLabel, dashLine2, planeIconGold);

        // Destination Block
        VBox destBlock = new VBox(3);
        destBlock.setMinWidth(Region.USE_PREF_SIZE);
        String destShort = AirportNames.shortName(flight.getDestAirportName());
        String destTitle = (flight.getDestCode() != null ? flight.getDestCode() : "")
                + (!destShort.isBlank() ? ", " + destShort : "");
        Label destName = new Label(destTitle);
        destName.getStyleClass().add("airport-code-muted");
        destName.setWrapText(true);
        destName.setMinWidth(Region.USE_PREF_SIZE);
        Label destTime = new Label(flight.getArrivalTime());
        destTime.getStyleClass().add("flight-time-bold");
        destTime.setMinWidth(Region.USE_PREF_SIZE);
        destBlock.getChildren().addAll(destName, destTime);

        // Price Block (right aligned) — single MoneyFormatter format (R 1,284.00)
        String priceStr = flight.getBasePrice() != null
                ? MoneyFormatter.format(flight.getBasePrice())
                : flight.getPriceFormatted();
        Label priceLabel = new Label(priceStr);
        priceLabel.getStyleClass().add("flight-price-bold");
        priceLabel.setMinWidth(Region.USE_PREF_SIZE);
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
        tags.setMinWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button detailsBtn = new Button("Flight details");
        detailsBtn.getStyleClass().add("flight-details-btn");
        detailsBtn.setMinWidth(Region.USE_PREF_SIZE);

        SVGPath chevron = new SVGPath();
        chevron.setContent(isExpanded ? SVG_CHEVRON_UP : SVG_CHEVRON_DOWN);
        chevron.setStyle("-fx-stroke: -glass-cream; -fx-stroke-width: 3; -fx-stroke-linecap: round; -fx-fill: transparent; -fx-scale-x: 0.6; -fx-scale-y: 0.6;");
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
        contentBox.setPadding(new Insets(4, 0, 4, 0));

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
                    String depDate = (depDateLabel != null && !depDateLabel.getText().isBlank())
                            ? depDateLabel.getText()
                            : FlightTimes.formatLocalDate(flight.getDeparture(), flight.getOriginZone());
                    String depDay = (depDayLabel != null && !depDayLabel.getText().isBlank()) ? depDayLabel.getText() : "Monday";
                    depDateStr = depDate + (!depDay.isBlank() ? " (" + depDay + ")" : "");
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
        HBox row = new HBox(8);
        row.getStyleClass().add("expanded-leg-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));

        VBox flightBlock = new VBox(2);
        flightBlock.setMinWidth(Region.USE_PREF_SIZE);
        Label flLabel = new Label(flNo);
        flLabel.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #ffffff;");
        flLabel.setMinWidth(Region.USE_PREF_SIZE);
        Label planeLabel = new Label(plane);
        planeLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.58);");
        planeLabel.setMinWidth(Region.USE_PREF_SIZE);
        flightBlock.getChildren().addAll(flLabel, planeLabel);

        VBox origBlock = new VBox(2);
        origBlock.setMinWidth(Region.USE_PREF_SIZE);
        Label origAp = new Label(origAirport);
        origAp.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.58);");
        origAp.setWrapText(true);
        origAp.setMinWidth(Region.USE_PREF_SIZE);
        Label origTm = new Label(origTime);
        origTm.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #ffffff;");
        origTm.setMinWidth(Region.USE_PREF_SIZE);
        origBlock.getChildren().addAll(origAp, origTm);

        HBox path = new HBox(4);
        path.setAlignment(Pos.CENTER);
        path.setMaxWidth(Double.MAX_VALUE);
        path.setPrefWidth(120);
        path.setMinWidth(20);
        HBox.setHgrow(path, Priority.ALWAYS);

        Region dot = new Region();
        dot.getStyleClass().add("flight-path-dot");
        Region dash1 = new Region();
        dash1.getStyleClass().add("flight-path-line");
        dash1.setMinWidth(4);
        dash1.setPrefWidth(24);
        dash1.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dash1, Priority.ALWAYS);
        Label dur = new Label(duration);
        dur.getStyleClass().add("flight-duration-pill");
        dur.setMinWidth(Region.USE_PREF_SIZE);
        Region dash2 = new Region();
        dash2.getStyleClass().add("flight-path-line");
        dash2.setMinWidth(4);
        dash2.setPrefWidth(24);
        dash2.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dash2, Priority.ALWAYS);
        SVGPath planeIcon = new SVGPath();
        planeIcon.setContent(SVG_PLANE_TINY);
        planeIcon.setStyle("-fx-fill: -glass-cream; -fx-rotate: 90; -fx-scale-x: 0.65; -fx-scale-y: 0.65;");
        path.getChildren().addAll(dot, dash1, dur, dash2, planeIcon);

        VBox destBlock = new VBox(2);
        destBlock.setMinWidth(Region.USE_PREF_SIZE);
        destBlock.setAlignment(Pos.CENTER_RIGHT);
        Label destAp = new Label(destAirport);
        destAp.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.58);");
        destAp.setWrapText(true);
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
        lbl.setMinWidth(Region.USE_PREF_SIZE);
        lbl.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-text-fill: -glass-cream;");

        Label val = new Label(value);
        val.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 13px; -fx-text-fill: rgba(255,255,255,0.85);");
        val.setWrapText(true);
        val.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(val, Priority.ALWAYS);

        row.getChildren().addAll(lbl, val);
        return row;
    }
}
