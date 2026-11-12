package com.group2.flightres.controller;

import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.util.FlightTimes;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for SkyVoyage Screen 04 Seat Map.
 * Manages seat selection, live database seat holds, dynamic price quotes via PricingService,
 * and transparent breakdown summary rows.
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

    // Reskin breakdown labels
    @FXML private Label seatBaseFareLabel;
    @FXML private Label seatCabinTitleLabel;
    @FXML private Label seatCabinLabel;
    @FXML private Label seatDemandLabel;
    @FXML private Label seatTimingLabel;
    @FXML private Label seatTotalLabel;
    @FXML private Label seatVatLabel;

    @FXML private Label bigSeatLabel;
    @FXML private Label bigPriceLabel;

    // Flight Card labels
    @FXML private Label flightNoLabel;
    @FXML private Label flightAircraftLabel;
    @FXML private Label flightDurationLabel;
    @FXML private Label freeSeatsLabel;
    @FXML private Label planeCardSubtitle;

    private static boolean preselectForScreenshots = false;

    public static void setPreselectForScreenshots(boolean preselect) {
        preselectForScreenshots = preselect;
    }

    public static boolean isPreselectForScreenshots() {
        return preselectForScreenshots || Boolean.getBoolean("altitude.screenshots");
    }

    private final SeatMapService seatMapService;
    private final FlightDao flightDao;
    private final PricingService pricingService;
    private final Clock clock;

    private final Map<String, Button> seatButtonsByLabel = new HashMap<>();
    private final Map<String, FlightSeat> seatsByLabel = new HashMap<>();
    private final List<FlightSeat> selectedSeats = new ArrayList<>();
    private boolean isSampleData = false;

    private BookingContext bookingContext;
    private Runnable onNext;
    private Runnable onBack;
    private long flightId = 1L;

    public SeatMapController() {
        this(new SeatMapService(), new FlightDaoImpl(), new PricingService(new SeatDaoImpl(), Clock.systemDefaultZone()), Clock.systemDefaultZone());
    }

    public SeatMapController(SeatMapService seatMapService, FlightDao flightDao, PricingService pricingService, Clock clock) {
        this.seatMapService = seatMapService != null ? seatMapService : new SeatMapService();
        this.flightDao = flightDao != null ? flightDao : new FlightDaoImpl();
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.pricingService = pricingService != null ? pricingService : new PricingService(new SeatDaoImpl(), this.clock);
    }

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
        loadFlightMetadata();
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
            loadFlightMetadata();
            loadSeatMap();
        }
    }

    public void setFlightId(long flightId) {
        this.flightId = flightId;
        loadFlightMetadata();
        loadSeatMap();
        if (glassBridge != null) {
            glassBridge.setController(this);
            glassBridge.updateFlightCardData();
        }
    }

    private void loadFlightMetadata() {
        String aircraft = (bookingContext != null && bookingContext.getAircraftModel() != null)
                ? bookingContext.getAircraftModel() : "";
        Instant dep = (bookingContext != null) ? bookingContext.getDepartureInstant() : null;
        Instant arr = (bookingContext != null) ? bookingContext.getArrivalInstant() : null;

        if ((aircraft.isBlank() || dep == null || arr == null) && flightId > 0) {
            FlightSearchResult flight = flightDao.findById(flightId).orElse(null);
            if (flight != null) {
                if (aircraft.isBlank()) aircraft = flight.getAircraftModel();
                if (dep == null) dep = flight.getDeparture();
                if (arr == null) arr = flight.getArrival();
                if (bookingContext != null) {
                    if (bookingContext.getAircraftModel() == null) bookingContext.setAircraftModel(flight.getAircraftModel());
                    if (bookingContext.getDepartureInstant() == null) bookingContext.setDepartureInstant(flight.getDeparture());
                    if (bookingContext.getArrivalInstant() == null) bookingContext.setArrivalInstant(flight.getArrival());
                    if (bookingContext.getBaseFare() == null) bookingContext.setBaseFare(flight.getBasePrice());
                    if (bookingContext.getFlightNumber() == null) bookingContext.setFlightNumber(flight.getFlightNumber());
                }
            }
        }

        String durStr = (dep != null && arr != null)
                ? FlightTimes.formatDuration(dep, arr) + ", direct"
                : (bookingContext != null && bookingContext.getDuration() != null ? bookingContext.getDuration() : "");

        if (flightDurationLabel != null) flightDurationLabel.setText(durStr);
        if (flightAircraftLabel != null) flightAircraftLabel.setText(aircraft);
        if (flightNoLabel != null && bookingContext != null) {
            flightNoLabel.setText(bookingContext.getFlightNumber() != null ? bookingContext.getFlightNumber() : "");
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
                seats = SeatMapFallback.generateSampleSeats(flightId, bookingContext);
                isSampleData = true;
            } else {
                isSampleData = false;
            }
        } catch (Exception e) {
            logger.error("Failed to load seat map from database: {}. Falling back to sample seats.", e.getMessage());
            seats = SeatMapFallback.generateSampleSeats(flightId, bookingContext);
            isSampleData = true;
        }

        if (fallbackNoticeLabel != null) {
            fallbackNoticeLabel.setVisible(isSampleData);
            fallbackNoticeLabel.setManaged(isSampleData);
        }

        for (FlightSeat seat : seats) {
            String label = seat.getSeatLabel();
            seatsByLabel.put(label, seat);
        }

        if (glassBridge != null) {
            glassBridge.populateSeats(seats);
        }

        updateSelectionSummary();
    }

    public void handleSeatClick(String label) {
        FlightSeat seat = seatsByLabel.get(label);
        if (seat == null) return;

        if (seat.getStatus() == SeatStatus.BOOKED) {
            return;
        }

        // Toggle selection
        if (selectedSeats.contains(seat)) {
            try {
                if (!isSampleData) {
                    seatMapService.deselectSeat(seat);
                }
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldUntil(null);
                selectedSeats.remove(seat);
                if (bookingContext != null) {
                    bookingContext.setSelectedSeats(new ArrayList<>(selectedSeats));
                    bookingContext.setPriceQuotes(new ArrayList<>());
                    bookingContext.setTotalPrice(BigDecimal.ZERO);
                }
                updateSelectionSummary();
                if (glassBridge != null) {
                    glassBridge.onSeatSelectionChanged(null);
                }
            } catch (Exception e) {
                logger.warn("Failed to deselect seat: {}", e.getMessage());
            }
            return;
        }

        // Single seat selection: release previous hold if exists
        if (!selectedSeats.isEmpty()) {
            FlightSeat prev = selectedSeats.get(0);
            try {
                if (!isSampleData) {
                    seatMapService.deselectSeat(prev);
                }
                prev.setStatus(SeatStatus.AVAILABLE);
                prev.setHeldUntil(null);
            } catch (Exception e) {
                logger.warn("Failed to release prior seat: {}", e.getMessage());
            }
            selectedSeats.clear();
        }

        try {
            if (!isSampleData) {
                seatMapService.selectSeat(seat);
            } else {
                seat.setStatus(SeatStatus.HELD);
            }
            selectedSeats.add(seat);

            PriceQuote quote = pricingService.quote(flightId, seat.getSeatClass(), clock);
            seat.setCalculatedPrice(quote.total());

            if (bookingContext != null) {
                bookingContext.setSelectedSeats(new ArrayList<>(selectedSeats));
                bookingContext.setPriceQuotes(List.of(quote));
                bookingContext.setBaseFare(quote.baseFare());
                bookingContext.setTotalPrice(quote.total());
                bookingContext.setHoldExpiresAt(seat.getHeldUntil());
            }

            updateSelectionSummary();
            if (glassBridge != null) {
                glassBridge.onSeatSelectionChanged(seat);
            }
        } catch (SeatUnavailableException e) {
            new Alert(Alert.AlertType.WARNING, e.getMessage()).showAndWait();
            loadSeatMap();
        } catch (Exception e) {
            logger.error("Failed to hold seat {}: {}", label, e.getMessage(), e);
            new Alert(Alert.AlertType.ERROR, "Unable to hold seat: " + e.getMessage()).showAndWait();
            loadSeatMap();
        }
    }

    private void renderSelectionSummary(FlightSeat seat, boolean isInteractive) {
        if (seat == null) {
            if (seatSelectionLabel != null) seatSelectionLabel.setText("None");
            if (baseFareLabel != null) baseFareLabel.setText("-");
            if (classMultiplierLabel != null) classMultiplierLabel.setText("-");
            if (demandPricingLabel != null) demandPricingLabel.setText("-");
            if (totalPriceLabel != null) totalPriceLabel.setText("-");

            if (seatBaseFareLabel != null) seatBaseFareLabel.setText("-");
            if (seatCabinTitleLabel != null) seatCabinTitleLabel.setText("Cabin");
            if (seatCabinLabel != null) seatCabinLabel.setText("-");
            if (seatDemandLabel != null) seatDemandLabel.setText("-");
            if (seatTimingLabel != null) seatTimingLabel.setText("-");
            if (seatTotalLabel != null) seatTotalLabel.setText("-");
            if (seatVatLabel != null) seatVatLabel.setText("");

            if (bigSeatLabel != null) bigSeatLabel.setText("—");
            if (bigPriceLabel != null) bigPriceLabel.setText("R 0");

            if (isInteractive && continueButton != null) continueButton.setDisable(true);
            return;
        }

        String clsName = (seat.getSeatClass() == SeatClass.FIRST) ? "First Class" : "Economy";
        if (seatSelectionLabel != null) seatSelectionLabel.setText(seat.getSeatLabel() + " · " + clsName);
        if (bigSeatLabel != null) bigSeatLabel.setText(seat.getSeatLabel());

        PriceQuote quote = null;
        if (bookingContext != null && bookingContext.getPriceQuotes() != null && !bookingContext.getPriceQuotes().isEmpty()) {
            quote = bookingContext.getPriceQuotes().get(0);
        } else {
            try {
                quote = pricingService.quote(flightId, seat.getSeatClass(), clock);
            } catch (Exception e) {
                logger.warn("Could not calculate live quote: {}", e.getMessage());
            }
        }

        if (quote != null) {
            String baseStr = formatPrice(quote.baseFare());
            String classFareStr = formatPrice(quote.classFare());
            String demandStr = (quote.demandAdjustment().compareTo(BigDecimal.ZERO) >= 0 ? "+ " : "") + formatPrice(quote.demandAdjustment());
            String timingStr = (quote.timingAdjustment().compareTo(BigDecimal.ZERO) >= 0 ? "+ " : "") + formatPrice(quote.timingAdjustment());
            String totalStr = formatPrice(quote.total());
            String vatStr = String.format(Locale.US, "Includes VAT R %,.2f", quote.vatIncluded().doubleValue());

            if (baseFareLabel != null) baseFareLabel.setText(baseStr);
            if (classMultiplierLabel != null) classMultiplierLabel.setText("x " + quote.classMultiplier());
            if (demandPricingLabel != null) demandPricingLabel.setText(demandStr);
            if (totalPriceLabel != null) totalPriceLabel.setText(totalStr);

            if (seatBaseFareLabel != null) seatBaseFareLabel.setText(baseStr);
            if (seatCabinTitleLabel != null) seatCabinTitleLabel.setText("Cabin (x " + quote.classMultiplier() + ")");
            if (seatCabinLabel != null) seatCabinLabel.setText(classFareStr);
            if (seatDemandLabel != null) seatDemandLabel.setText(demandStr);
            if (seatTimingLabel != null) seatTimingLabel.setText(timingStr);
            if (seatTotalLabel != null) seatTotalLabel.setText(totalStr);
            if (seatVatLabel != null) seatVatLabel.setText(vatStr);

            if (bigPriceLabel != null) bigPriceLabel.setText(totalStr);
            if (isInteractive && continueButton != null) continueButton.setDisable(false);
        } else {
            // No quote state: honest messaging, disable continue
            if (seatBaseFareLabel != null) seatBaseFareLabel.setText("-");
            if (seatCabinLabel != null) seatCabinLabel.setText("-");
            if (seatDemandLabel != null) seatDemandLabel.setText("-");
            if (seatTimingLabel != null) seatTimingLabel.setText("-");
            if (seatTotalLabel != null) seatTotalLabel.setText("-");
            if (seatVatLabel != null) seatVatLabel.setText("Pricing unavailable");
            if (bigPriceLabel != null) bigPriceLabel.setText("—");
            if (isInteractive && continueButton != null) continueButton.setDisable(true);
        }
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
                PriceQuote quote = pricingService.quote(flightId, seat.getSeatClass(), clock);
                bookingContext.setPriceQuotes(List.of(quote));
                bookingContext.setBaseFare(quote.baseFare());
                bookingContext.setTotalPrice(quote.total());
                if (seat.getHeldUntil() != null) {
                    bookingContext.setHoldExpiresAt(seat.getHeldUntil());
                }
            }
        }
        if (onNext != null) {
            onNext.run();
        }
    }

    public void releaseHeldSeats() {
        if (seatMapService != null && selectedSeats != null && !selectedSeats.isEmpty()) {
            for (FlightSeat seat : new ArrayList<>(selectedSeats)) {
                seatMapService.deselectSeat(seat);
            }
            selectedSeats.clear();
        }
    }

    @FXML
    private void handleBack() {
        logger.info("Going back to flight search.");
        releaseHeldSeats();
        if (onBack != null) {
            onBack.run();
        }
    }

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
        if (glassBridge != null && !glassBridge.getSeatButtons().isEmpty()) {
            return glassBridge.getSeatButtons();
        }
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

    public void handleHoldExpired() {
        logger.info("Seat hold expired for flight ID {}", flightId);
        if (!selectedSeats.isEmpty()) {
            FlightSeat seat = selectedSeats.get(0);
            try {
                if (!isSampleData) {
                    seatMapService.deselectSeat(seat);
                }
            } catch (Exception e) {
                logger.warn("Failed to release expired seat hold in DB: {}", e.getMessage());
            }
            selectedSeats.clear();
        }
        if (bookingContext != null) {
            bookingContext.setSelectedSeats(new ArrayList<>());
            bookingContext.setPriceQuotes(new ArrayList<>());
            bookingContext.setTotalPrice(BigDecimal.ZERO);
            bookingContext.setHoldExpiresAt(null);
        }
        renderSelectionSummary(null, true);
        if (glassBridge != null) {
            glassBridge.clearSelectionVisuals();
        }
        loadSeatMap();
        showInfoAlert("Seat hold expired", "Your seat hold has expired. The seat has been released.");
    }

    private String lastAlertMessage;

    public String getLastAlertMessage() {
        return lastAlertMessage;
    }

    private void showInfoAlert(String header, String message) {
        this.lastAlertMessage = message;
        logger.warn("SeatMap Alert [{}]: {}", header, message);
        if (!Boolean.getBoolean("altitude.testing") && !Boolean.getBoolean("altitude.screenshots")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
            alert.setHeaderText(header);
            alert.showAndWait();
        }
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
    private void handleNavOffers() {
        showComingSoon("Offers", "Offers aren't available yet.");
    }

    @FXML
    private void handleNavBookings() {
        showComingSoon("My Bookings", "My Bookings isn't available yet.");
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
        showComingSoon("User Profile", "Profile isn't available yet.");
    }

    private void showComingSoon(String feature, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(feature);
        alert.showAndWait();
    }

    private void navigateTo(String fxmlPath, String screenName) {
        releaseHeldSeats();
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
            } catch (Exception e) {
                logger.error("Failed to navigate to {}", fxmlPath, e);
            }
        }
    }
}
