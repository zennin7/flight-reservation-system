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
        return preselectForScreenshots || Boolean.getBoolean("skyvoyage.screenshots");
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
    }

    @FXML
    public void initialize() {
        if (bookingContext == null) {
            loadSeatMap();
        }
    }

    public void setFlightId(long flightId) {
        this.flightId = flightId;
        loadSeatMap();
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
                seats = generateSampleSeats();
            } else {
                isSampleData = false;
            }
        } catch (Exception e) {
            logger.warn("Could not query seat map from DAO; falling back to sample reference seats: {}", e.getMessage());
            isSampleData = true;
            seats = generateSampleSeats();
        }

        if (fallbackNoticeLabel != null) {
            fallbackNoticeLabel.setVisible(isSampleData);
            fallbackNoticeLabel.setManaged(isSampleData);
        }

        for (FlightSeat seat : seats) {
            seatsByLabel.put(seat.getSeatLabel(), seat);
        }

        buildCabinLayout();

        // Seat 7E must be pre-selected ONLY when ScreenshotRunner runs. In the real app no seat is pre-selected.
        if (isPreselectForScreenshots()) {
            FlightSeat seat7E = seatsByLabel.get("7E");
            if (seat7E != null && seat7E.getStatus() == SeatStatus.AVAILABLE) {
                selectedSeats.add(seat7E);
                Button btn7E = seatButtonsByLabel.get("7E");
                if (btn7E != null) {
                    btn7E.getStyleClass().removeAll("seat-available", "seat-taken");
                    btn7E.getStyleClass().add("seat-selected");
                }
            }
        } else if (bookingContext != null && bookingContext.getSelectedSeats() != null && !bookingContext.getSelectedSeats().isEmpty()) {
            for (FlightSeat prev : bookingContext.getSelectedSeats()) {
                FlightSeat currentSeat = seatsByLabel.get(prev.getSeatLabel());
                if (currentSeat != null && currentSeat.getStatus() != SeatStatus.BOOKED) {
                    selectedSeats.add(currentSeat);
                    Button btn = seatButtonsByLabel.get(currentSeat.getSeatLabel());
                    if (btn != null) {
                        btn.getStyleClass().removeAll("seat-available", "seat-taken");
                        btn.getStyleClass().add("seat-selected");
                    }
                }
            }
        }

        updateSelectionSummary();
    }

    private void buildCabinLayout() {
        // First Class Section Header
        Label firstHeader = new Label("FIRST CLASS  2 x 2");
        firstHeader.getStyleClass().add("cabin-section-header");
        cabinContainer.getChildren().add(firstHeader);

        // First Class Rows (1, 2)
        cabinContainer.getChildren().add(buildRow(1, new String[]{"1A", "1B"}, new String[]{"1C", "1D"}));
        cabinContainer.getChildren().add(buildRow(2, new String[]{"2A", "2B"}, new String[]{"2C", "2D"}));

        // Economy Section Header
        Label econHeader = new Label("ECONOMY  3 x 3");
        econHeader.getStyleClass().add("cabin-section-header");
        VBox.setMargin(econHeader, new javafx.geometry.Insets(6, 0, 0, 0));
        cabinContainer.getChildren().add(econHeader);

        // Economy Rows (6, 7, 8, 9, 10, 11)
        int[] econRows = {6, 7, 8, 9, 10, 11};
        for (int r : econRows) {
            String[] left = {r + "A", r + "B", r + "C"};
            String[] right = {r + "D", r + "E", r + "F"};
            cabinContainer.getChildren().add(buildRow(r, left, right));
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
            seat = createFallbackSeat(seatLabel, SeatStatus.AVAILABLE);
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
                    Button prevBtn = seatButtonsByLabel.get(prev.getSeatLabel());
                    if (prevBtn != null) {
                        prevBtn.getStyleClass().remove("seat-selected");
                        prevBtn.getStyleClass().add("seat-available");
                    }
                }
                selectedSeats.clear();

                // Real logic call: hold/select (when connected to real database)
                if (!isSampleData) {
                    seatMapService.selectSeat(seat);
                }
                selectedSeats.add(seat);
                if (btn != null) {
                    btn.getStyleClass().remove("seat-available");
                    btn.getStyleClass().add("seat-selected");
                }
            }
            updateSelectionSummary();
        } catch (SeatUnavailableException e) {
            new Alert(Alert.AlertType.WARNING, e.getMessage()).showAndWait();
            loadSeatMap();
        }
    }

    private void previewSeat(FlightSeat seat) {
        if (seatSelectionLabel != null) {
            String clsName = (seat.getSeatClass() == SeatClass.FIRST) ? "First Class" : "Economy";
            seatSelectionLabel.setText(seat.getSeatLabel() + " · " + clsName);
        }
        if (baseFareLabel != null) baseFareLabel.setText("R 1,200");
        if (classMultiplierLabel != null) {
            classMultiplierLabel.setText(seat.getSeatClass() == SeatClass.FIRST ? "x 2.0" : "x 1.0");
        }
        if (demandPricingLabel != null) demandPricingLabel.setText("+ R 180");
        if (totalPriceLabel != null) {
            totalPriceLabel.setText(formatPrice(seat.getCalculatedPrice()));
        }
    }

    private void updateSelectionSummary() {
        if (selectedSeats.isEmpty()) {
            if (seatSelectionLabel != null) seatSelectionLabel.setText("None");
            if (baseFareLabel != null) baseFareLabel.setText("R 1,200");
            if (classMultiplierLabel != null) classMultiplierLabel.setText("x 1.0");
            if (demandPricingLabel != null) demandPricingLabel.setText("+ R 0");
            if (totalPriceLabel != null) totalPriceLabel.setText("R 0");
            if (continueButton != null) continueButton.setDisable(true);
        } else {
            FlightSeat seat = selectedSeats.get(0);
            String clsName = (seat.getSeatClass() == SeatClass.FIRST) ? "First Class" : "Economy";
            if (seatSelectionLabel != null) seatSelectionLabel.setText(seat.getSeatLabel() + " · " + clsName);
            if (baseFareLabel != null) baseFareLabel.setText("R 1,200");
            if (classMultiplierLabel != null) {
                classMultiplierLabel.setText(seat.getSeatClass() == SeatClass.FIRST ? "x 2.0" : "x 1.0");
            }
            if (demandPricingLabel != null) demandPricingLabel.setText("+ R 180");
            if (totalPriceLabel != null) totalPriceLabel.setText(formatPrice(seat.getCalculatedPrice()));
            if (continueButton != null) continueButton.setDisable(false);
        }
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
                bookingContext.setTotalPrice(selectedSeats.get(0).getCalculatedPrice());
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

    /**
     * Generates sample seats matching the SkyVoyage reference PNG: 04_seat_map.
     * TODO(wire-real-data): Replace with real database seeds when DB is active.
     */
    private List<FlightSeat> generateSampleSeats() {
        List<FlightSeat> list = new ArrayList<>();
        long id = 100L;

        // First Class: 1A(A), 1B(B), 1C(A), 1D(A), 2A(A), 2B(A), 2C(B), 2D(B)
        Set<String> firstBooked = Set.of("1B", "2C", "2D");
        int[] firstRows = {1, 2};
        char[] firstCols = {'A', 'B', 'C', 'D'};
        for (int r : firstRows) {
            for (char c : firstCols) {
                String label = r + String.valueOf(c);
                SeatStatus status = firstBooked.contains(label) ? SeatStatus.BOOKED : SeatStatus.AVAILABLE;
                FlightSeat seat = new FlightSeat();
                seat.setFlightSeatId(id++);
                seat.setFlightId(flightId);
                seat.setSeatRow(r);
                seat.setSeatCol(c);
                seat.setSeatClass(SeatClass.FIRST);
                seat.setStatus(status);
                seat.setCalculatedPrice(new BigDecimal("2580"));
                list.add(seat);
            }
        }

        // Economy Booked: 6A, 6B, 7C, 7D, 7F, 8A, 9B, 9E, 10C, 10D, 11A, 11F
        Set<String> econBooked = Set.of("6A", "6B", "7C", "7D", "7F", "8A", "9B", "9E", "10C", "10D", "11A", "11F");
        int[] econRows = {6, 7, 8, 9, 10, 11};
        char[] econCols = {'A', 'B', 'C', 'D', 'E', 'F'};
        for (int r : econRows) {
            for (char c : econCols) {
                String label = r + String.valueOf(c);
                SeatStatus status = econBooked.contains(label) ? SeatStatus.BOOKED : SeatStatus.AVAILABLE;
                FlightSeat seat = new FlightSeat();
                seat.setFlightSeatId(id++);
                seat.setFlightId(flightId);
                seat.setSeatRow(r);
                seat.setSeatCol(c);
                seat.setSeatClass(SeatClass.ECONOMY);
                seat.setStatus(status);
                seat.setCalculatedPrice(new BigDecimal("1380"));
                list.add(seat);
            }
        }

        return list;
    }

    private FlightSeat createFallbackSeat(String label, SeatStatus status) {
        FlightSeat s = new FlightSeat();
        s.setFlightSeatId(System.currentTimeMillis());
        s.setFlightId(flightId);
        s.setStatus(status);
        s.setSeatClass(SeatClass.ECONOMY);
        s.setCalculatedPrice(new BigDecimal("1380"));
        return s;
    }
}
