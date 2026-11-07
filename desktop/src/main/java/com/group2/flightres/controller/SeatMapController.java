package com.group2.flightres.controller;

import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.SeatMapService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

/**
 * Controller for SkyVoyage Screen 04 Seat Map.
 * Preserves real SeatMapService hold/release logic while styling First 2x2
 * and Economy 3x3 with aisle gap, row numbers, legend, and selection summary.
 */
public class SeatMapController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(SeatMapController.class);

    @FXML private Label flightTitleLabel;
    @FXML private Label fallbackNoticeLabel;
    @FXML private VBox cabinContainer;

    @FXML private Label seatSelectionLabel;
    @FXML private Label baseFareLabel;
    @FXML private Label classMultiplierLabel;
    @FXML private Label demandPricingLabel;
    @FXML private Label totalPriceLabel;
    @FXML private Button continueButton;
    @FXML private Button backButton;

    private static boolean preselectForScreenshots = false;

    public static void setPreselectForScreenshots(boolean preselect) {
        preselectForScreenshots = preselect;
    }

    public static boolean isPreselectForScreenshots() {
        return preselectForScreenshots || Boolean.getBoolean("altitude.screenshots");
    }

    private final SeatMapService seatMapService = new SeatMapService();
    private final Map<String, Button> seatButtonsByLabel = new HashMap<>();
    private final Map<String, FlightSeat> seatsByLabel = new HashMap<>();
    private final List<FlightSeat> selectedSeats = new ArrayList<>();
    private boolean isSampleData = false;

    private BookingContext bookingContext;
    private Runnable onNext;
    private Runnable onBack;
    private long flightId = 1L; // default GRP 204

    @Override
    public void init(BookingContext context, Runnable onNext) {
        init(context, onNext, null);
    }

    @Override
    public void init(BookingContext context, Runnable onNext, Runnable onBack) {
        this.bookingContext = context;
        this.onNext = onNext;
        this.onBack = onBack;
        if (context != null && context.getFlightId() != null) {
            this.flightId = context.getFlightId();
        }
        loadSeatMap();
        if (glassBridge != null) {
            glassBridge.setController(this);
            glassBridge.updateFlightCardData();
        }
    }

    @FXML
    public void initialize() {
        if (continueButton != null) {
            com.group2.flightres.util.Motion.attachHoverLift(continueButton);
        }
        if (glassBridge != null) {
            glassBridge.setController(this);
        }
        if (bookingContext == null) {
            loadSeatMap();
        }
    }

    public void setFlightId(long flightId) {
        this.flightId = flightId;
        loadSeatMap();
        if (glassBridge != null) {
            glassBridge.setController(this);
            glassBridge.updateFlightCardData();
        }
    }

    private void loadSeatMap() {
        if (cabinContainer == null) return;
        cabinContainer.getChildren().clear();
        seatButtonsByLabel.clear();
        seatsByLabel.clear();
        selectedSeats.clear();

        List<FlightSeat> seats;
        try {
            seats = seatMapService.loadSeatMap(flightId);
            if (seats == null || seats.isEmpty()) {
                logger.warn("Seat map from DAO returned empty list; falling back to sample reference seats.");
                isSampleData = true;
                seats = SeatMapFallback.generateSampleSeats(flightId, bookingContext);
            } else {
                isSampleData = false;
            }
        } catch (Exception e) {
            logger.warn("Could not query seat map from DAO; falling back to sample reference seats: {}", e.getMessage());
            isSampleData = true;
            seats = SeatMapFallback.generateSampleSeats(flightId, bookingContext);
        }

        if (fallbackNoticeLabel != null) {
            fallbackNoticeLabel.setVisible(isSampleData);
            fallbackNoticeLabel.setManaged(isSampleData);
        }

        for (FlightSeat seat : seats) {
            seatsByLabel.put(seat.getSeatLabel(), seat);
        }

        buildCabinLayout();

        if (flightTitleLabel != null) {
            String flightInfo = (bookingContext != null)
                    ? bookingContext.getFlightRouteDisplay()
                    : "";
            flightTitleLabel.setText(flightInfo.isEmpty() ? "Choose your seat" : "Choose your seat · " + flightInfo);
        }

        applyInitialSelection();
        updateSelectionSummary();
    }

    private void applyInitialSelection() {
        if (isPreselectForScreenshots()) {
            FlightSeat seat7E = seatsByLabel.get("7E");
            if (seat7E != null && seat7E.getStatus() == SeatStatus.AVAILABLE) {
                markSeatSelected(seat7E);
            }
        } else if (bookingContext != null && bookingContext.getSelectedSeats() != null) {
            for (FlightSeat prev : bookingContext.getSelectedSeats()) {
                FlightSeat currentSeat = seatsByLabel.get(prev.getSeatLabel());
                if (currentSeat != null && currentSeat.getStatus() != SeatStatus.BOOKED) {
                    markSeatSelected(currentSeat);
                }
            }
        }
    }

    private void markSeatSelected(FlightSeat seat) {
        selectedSeats.add(seat);
        Button btn = seatButtonsByLabel.get(seat.getSeatLabel());
        if (btn != null) {
            btn.getStyleClass().removeAll("seat-available", "seat-taken");
            btn.getStyleClass().add("seat-selected");
        }
    }

    private void buildCabinLayout() {
        Map<Integer, List<FlightSeat>> rowMap = new TreeMap<>();
        for (FlightSeat s : seatsByLabel.values()) {
            rowMap.computeIfAbsent(s.getSeatRow(), k -> new ArrayList<>()).add(s);
        }

        boolean firstClassHeaderAdded = false;
        boolean economyHeaderAdded = false;

        for (Map.Entry<Integer, List<FlightSeat>> entry : rowMap.entrySet()) {
            int rowNum = entry.getKey();
            List<FlightSeat> rowSeats = entry.getValue();
            boolean isFirstClass = rowSeats.stream().anyMatch(s -> s.getSeatClass() == SeatClass.FIRST);

            if (isFirstClass && !firstClassHeaderAdded) {
                Label firstHeader = new Label("FIRST CLASS  2 x 2");
                firstHeader.getStyleClass().add("cabin-section-header");
                cabinContainer.getChildren().add(firstHeader);
                firstClassHeaderAdded = true;
            } else if (!isFirstClass && !economyHeaderAdded) {
                Label econHeader = new Label("ECONOMY  3 x 3");
                econHeader.getStyleClass().add("cabin-section-header");
                VBox.setMargin(econHeader, new javafx.geometry.Insets(6, 0, 0, 0));
                cabinContainer.getChildren().add(econHeader);
                economyHeaderAdded = true;
            }

            if (isFirstClass) {
                String[] left = {rowNum + "A", rowNum + "C"};
                String[] right = {rowNum + "D", rowNum + "F"};
                cabinContainer.getChildren().add(buildRow(rowNum, left, right));
            } else {
                String[] left = {rowNum + "A", rowNum + "B", rowNum + "C"};
                String[] right = {rowNum + "D", rowNum + "E", rowNum + "F"};
                cabinContainer.getChildren().add(buildRow(rowNum, left, right));
            }
        }
    }

    private HBox buildRow(int rowNumber, String[] leftSeats, String[] rightSeats) {
        HBox rowBox = new HBox();
        rowBox.setAlignment(Pos.CENTER);
        rowBox.setSpacing(6);
        rowBox.getStyleClass().add("seat-row-box");

        // Row number label on left
        Label numLabel = new Label(String.valueOf(rowNumber));
        numLabel.getStyleClass().add("row-number-label");
        rowBox.getChildren().add(numLabel);

        // Left seat group
        for (String label : leftSeats) {
            rowBox.getChildren().add(createSeatButton(label));
        }

        // Aisle spacer (26px)
        Region aisle = new Region();
        aisle.getStyleClass().add("aisle-spacer");
        rowBox.getChildren().add(aisle);

        // Right seat group
        for (String label : rightSeats) {
            rowBox.getChildren().add(createSeatButton(label));
        }

        return rowBox;
    }

    private Button createSeatButton(String seatLabel) {
        Button btn = new Button(seatLabel);
        btn.getStyleClass().add("seat");

        FlightSeat seat = seatsByLabel.get(seatLabel);
        if (seat == null) {
            seat = SeatMapFallback.createFallbackSeat(flightId, seatLabel, SeatStatus.AVAILABLE, bookingContext);
            seatsByLabel.put(seatLabel, seat);
        }

        if (seat.getStatus() == SeatStatus.BOOKED) {
            btn.getStyleClass().add("seat-taken");
        } else {
            btn.getStyleClass().add("seat-available");
            btn.setOnAction(e -> handleSeatClick(seatLabel));
        }

        // Tooltip
        String seatClassStr = (seat.getSeatClass() == SeatClass.FIRST) ? "First Class" : "Economy";
        Tooltip tooltip = new Tooltip("Seat " + seatLabel + " — " + seatClassStr + "\nPrice: R " + seat.getCalculatedPrice());
        tooltip.setShowDelay(Duration.millis(150));
        btn.setTooltip(tooltip);

        // Hover effect for available seats: updates summary preview on hover
        final FlightSeat hoveredSeat = seat;
        btn.setOnMouseEntered(e -> {
            if (hoveredSeat.getStatus() == SeatStatus.AVAILABLE && selectedSeats.isEmpty()) {
                previewSeat(hoveredSeat);
            }
        });
        btn.setOnMouseExited(e -> {
            if (selectedSeats.isEmpty()) {
                updateSelectionSummary();
            }
        });

        seatButtonsByLabel.put(seatLabel, btn);
        return btn;
    }

    private void handleSeatClick(String seatLabel) {
        FlightSeat seat = seatsByLabel.get(seatLabel);
        if (seat == null || seat.getStatus() == SeatStatus.BOOKED) return;

        boolean alreadySelected = selectedSeats.contains(seat);
        Button btn = seatButtonsByLabel.get(seatLabel);

        try {
            if (alreadySelected) {
                // Real logic call: deselect (when connected to real database)
                if (!isSampleData) {
                    seatMapService.deselectSeat(seat);
                }
                seat.setHeldUntil(null);
                if (bookingContext != null) {
                    bookingContext.setHoldExpiresAt(null);
                }
                selectedSeats.remove(seat);
                if (btn != null) {
                    btn.getStyleClass().remove("seat-selected");
                    btn.getStyleClass().add("seat-available");
                }
            } else {
                // Single seat selection for this wizard flow: clear previous
                for (FlightSeat prev : new ArrayList<>(selectedSeats)) {
                    if (!isSampleData) {
                        seatMapService.deselectSeat(prev);
                    }
                    prev.setHeldUntil(null);
                    Button prevBtn = seatButtonsByLabel.get(prev.getSeatLabel());
                    if (prevBtn != null) {
                        prevBtn.getStyleClass().remove("seat-selected");
                        prevBtn.getStyleClass().add("seat-available");
                    }
                }
                selectedSeats.clear();
                if (bookingContext != null) {
                    bookingContext.setHoldExpiresAt(null);
                }

                // Real logic call: hold/select (when connected to real database)
                if (!isSampleData) {
                    seatMapService.selectSeat(seat);
                } else {
                    seat.setHeldUntil(java.time.Instant.now().plusSeconds(com.group2.flightres.config.SeatHoldPolicy.HOLD_SECONDS));
                }
                selectedSeats.add(seat);
                if (bookingContext != null) {
                    bookingContext.setHoldExpiresAt(seat.getHeldUntil());
                }
                if (btn != null) {
                    btn.getStyleClass().remove("seat-available");
                    btn.getStyleClass().add("seat-selected");
                    com.group2.flightres.util.Motion.pop(btn);
                }
            }
            updateSelectionSummary();
        } catch (SeatUnavailableException e) {
            new Alert(Alert.AlertType.WARNING, e.getMessage()).showAndWait();
            loadSeatMap();
        }
    }

    private void renderSelectionSummary(FlightSeat seat, boolean isInteractive) {
        BigDecimal base = (bookingContext != null && bookingContext.getBaseFare() != null)
                ? bookingContext.getBaseFare() : new BigDecimal("1200");

        if (seat == null) {
            if (seatSelectionLabel != null) seatSelectionLabel.setText("None");
            if (baseFareLabel != null) baseFareLabel.setText(formatPrice(base));
            if (classMultiplierLabel != null) classMultiplierLabel.setText("x 1.0");
            if (demandPricingLabel != null) demandPricingLabel.setText("+ R 0");
            if (totalPriceLabel != null) totalPriceLabel.setText("R 0");
            if (isInteractive && continueButton != null) continueButton.setDisable(true);
            return;
        }

        String clsName = (seat.getSeatClass() == SeatClass.FIRST) ? "First Class" : "Economy";
        if (seatSelectionLabel != null) seatSelectionLabel.setText(seat.getSeatLabel() + " · " + clsName);
        if (baseFareLabel != null) baseFareLabel.setText(formatPrice(base));
        BigDecimal mult = (seat.getSeatClass() == SeatClass.FIRST) ? new BigDecimal("2.0") : new BigDecimal("1.0");
        if (classMultiplierLabel != null) {
            classMultiplierLabel.setText(seat.getSeatClass() == SeatClass.FIRST ? "x 2.0" : "x 1.0");
        }
        if (demandPricingLabel != null) demandPricingLabel.setText("+ R 180");
        BigDecimal seatPrice = seat.getCalculatedPrice() != null
                ? seat.getCalculatedPrice()
                : base.multiply(mult).add(new BigDecimal("180"));
        if (totalPriceLabel != null) totalPriceLabel.setText(formatPrice(seatPrice));
        if (isInteractive && continueButton != null) continueButton.setDisable(false);
    }

    private void previewSeat(FlightSeat seat) {
        renderSelectionSummary(seat, false);
    }

    private void updateSelectionSummary() {
        FlightSeat activeSeat = selectedSeats.isEmpty() ? null : selectedSeats.get(0);
        renderSelectionSummary(activeSeat, true);
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "R 0";
        return String.format(Locale.US, "R %,d", price.longValue());
    }

    @FXML
    private void handleContinue() {
        logger.info("Proceeding to checkout with {} seat(s) selected.", selectedSeats.size());
        if (bookingContext != null) {
            bookingContext.setSelectedSeats(new ArrayList<>(selectedSeats));
            if (!selectedSeats.isEmpty()) {
                FlightSeat seat = selectedSeats.get(0);
                BigDecimal base = (bookingContext.getBaseFare() != null)
                        ? bookingContext.getBaseFare() : new BigDecimal("1200");
                bookingContext.setBaseFare(base);
                BigDecimal mult = (seat.getSeatClass() == SeatClass.FIRST)
                        ? new BigDecimal("2.0") : new BigDecimal("1.0");
                bookingContext.setClassMultiplier(mult);
                bookingContext.setDemandPricing(new BigDecimal("180"));
                bookingContext.setTaxesAndFees(new BigDecimal("142"));
                bookingContext.recalculateTotals();
                if (bookingContext.getHoldExpiresAt() == null && seat.getHeldUntil() != null) {
                    bookingContext.setHoldExpiresAt(seat.getHeldUntil());
                }
            }
        }
        if (onNext != null) {
            onNext.run();
        }
    }

    @FXML
    private void handleBack() {
        logger.info("Going back to flight search.");
        if (onBack != null) {
            onBack.run();
        }
    }
// ===================================================================
    // Glass Reskin (Phase 2 Screen 03) Additions
    // ===================================================================
    @FXML private com.group2.flightres.util.GlassSeatMapBridge glassBridge;

    public void setGlassBridge(com.group2.flightres.util.GlassSeatMapBridge bridge) {
        this.glassBridge = bridge;
        if (bridge != null) {
            bridge.setController(this);
        }
    }

    public com.group2.flightres.util.GlassSeatMapBridge getGlassBridge() {
        return glassBridge;
    }

    public BookingContext getBookingContext() {
        return bookingContext;
    }

    public long getFlightId() {
        return flightId;
    }

    public List<FlightSeat> getSelectedSeatsList() {
        return Collections.unmodifiableList(selectedSeats);
    }

    public Map<String, FlightSeat> getSeatsByLabelMap() {
        return Collections.unmodifiableMap(seatsByLabel);
    }

    public Map<String, Button> getSeatButtonsMap() {
        return Collections.unmodifiableMap(seatButtonsByLabel);
    }

    public void selectSeatByLabel(String label) {
        handleSeatClick(label);
    }

    @FXML
    public void handleReleaseSeat() {
        if (!selectedSeats.isEmpty()) {
            FlightSeat seat = selectedSeats.get(0);
            handleSeatClick(seat.getSeatLabel());
        }
    }

    @FXML
    private void handleComingSoonCabinView() {
        showComingSoon("Cabin view", "Cabin view isn't available yet.");
    }

    @FXML
    private void handleZoomGrid() {
        if (glassBridge != null) {
            glassBridge.toggleZoom();
        }
    }

    @FXML
    private void handleSelectSection1() {
        if (glassBridge != null) glassBridge.setSection(1);
    }

    @FXML
    private void handleSelectSection2() {
        if (glassBridge != null) glassBridge.setSection(2);
    }

    @FXML
    private void handleSelectSection3() {
        if (glassBridge != null) glassBridge.setSection(3);
    }

    @FXML
    private void handleNavHome() {
        navigateTo("/com/group2/flightres/fxml/home.fxml", "Home");
    }

    @FXML
    private void handleNavFlights() {
        navigateTo("/com/group2/flightres/fxml/flight-search.fxml", "Flights");
    }

    @FXML
    private void handleComingSoonMyBookings() {
        showComingSoon("My Bookings", "My Bookings isn't available yet.");
    }

    @FXML
    private void handleComingSoonOffers() {
        showComingSoon("Offers", "Offers aren't available yet.");
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

    private void showComingSoon(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle("Coming soon");
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private void navigateTo(String fxmlPath, String screenName) {
        javafx.scene.Scene scene = null;
        if (continueButton != null && continueButton.getScene() != null) {
            scene = continueButton.getScene();
        } else if (cabinContainer != null && cabinContainer.getScene() != null) {
            scene = cabinContainer.getScene();
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
