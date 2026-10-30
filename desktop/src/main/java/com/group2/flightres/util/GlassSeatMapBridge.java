package com.group2.flightres.util;

import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingStep;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.component.StepperBar;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

/**
 * Controller-bound Glass Bridge for Screen 03 (Seat Map).
 * Coordinates section slicing, horizontal rotated seat grid, side-view aircraft
 * graphic, hold countdown timer with SVG-style arc, selection summary, and flight stats.
 * Does NOT use static singletons (references are passed directly or resolved on scene mount).
 */
public class GlassSeatMapBridge extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(GlassSeatMapBridge.class);

    private SeatMapController controller;
    private BookingContext bookingContext;

    // UI Nodes in FXML
    private GlassPlaneGraphic planeGraphic;
    private Button section1Tab;
    private Button section2Tab;
    private Button section3Tab;
    private Label sectionHeaderLabel;
    private HBox seatGridNumbersRow;
    private VBox seatGridRowsContainer;
    private Pane zoomContainer;

    // Selection Card UI Nodes
    private Label bigSeatLabel;
    private Label bigPriceLabel;
    private HBox chipsContainer;
    private Label timerLabel;
    private Arc timerArc;
    private Button continueButton;
    private Button releaseButton;

    // Flight Card UI Nodes
    private Label flightOriginCode;
    private Label flightOriginTime;
    private Label flightDestCode;
    private Label flightDestTime;
    private Label flightNoLabel;
    private Label flightDurationLabel;
    private Label freeSeatsLabel;
    private Label planeCardSubtitle;

    // References to Controller's original nodes
    private VBox cabinContainer;
    private Label seatSelectionLabel;
    private Label totalPriceLabel;
    private StepperBar stepperBar;

    // State
    private int currentSection = 1;
    private boolean isZoomed = false;
    private final Map<String, Button> seatButtons = new HashMap<>();
    private final Map<String, FlightSeat> seatsByLabel = new HashMap<>();
    private final List<Integer> distinctRows = new ArrayList<>();
    private final List<List<Integer>> sectionSlices = new ArrayList<>();

    // Hold Timer (10 minutes = 600s)
    private Timeline holdTimeline;
    private int remainingSeconds = 600;
    private static final int TOTAL_HOLD_SECONDS = 600;

    public GlassSeatMapBridge() {
        setVisible(false);
        setManaged(false);
        setPrefSize(0, 0);

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(this::initUiBindings);
            }
        });

        parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent != null) {
                Platform.runLater(this::initUiBindings);
            }
        });
    }

    public void setController(SeatMapController controller) {
        this.controller = controller;
        if (controller != null) {
            this.bookingContext = controller.getBookingContext();
        }
    }

    public void initUiBindings() {
        Parent root = getScene() != null ? getScene().getRoot() : getParent();
        if (root == null) return;

        // Lookup Glass elements
        this.planeGraphic = (GlassPlaneGraphic) root.lookup("#planeGraphic");
        this.section1Tab = (Button) root.lookup("#section1Tab");
        this.section2Tab = (Button) root.lookup("#section2Tab");
        this.section3Tab = (Button) root.lookup("#section3Tab");
        this.sectionHeaderLabel = (Label) root.lookup("#sectionHeaderLabel");
        this.seatGridNumbersRow = (HBox) root.lookup("#seatGridNumbersRow");
        this.seatGridRowsContainer = (VBox) root.lookup("#seatGridRowsContainer");
        this.zoomContainer = (Pane) root.lookup("#zoomContainer");

        this.bigSeatLabel = (Label) root.lookup("#bigSeatLabel");
        this.bigPriceLabel = (Label) root.lookup("#bigPriceLabel");
        this.chipsContainer = (HBox) root.lookup("#chipsContainer");
        this.timerLabel = (Label) root.lookup("#timerLabel");
        this.timerArc = (Arc) root.lookup("#timerArc");
        this.continueButton = (Button) root.lookup("#continueButton");
        this.releaseButton = (Button) root.lookup("#releaseButton");

        this.flightOriginCode = (Label) root.lookup("#flightOriginCode");
        this.flightOriginTime = (Label) root.lookup("#flightOriginTime");
        this.flightDestCode = (Label) root.lookup("#flightDestCode");
        this.flightDestTime = (Label) root.lookup("#flightDestTime");
        this.flightNoLabel = (Label) root.lookup("#flightNoLabel");
        this.flightDurationLabel = (Label) root.lookup("#flightDurationLabel");
        this.freeSeatsLabel = (Label) root.lookup("#freeSeatsLabel");
        this.planeCardSubtitle = (Label) root.lookup("#planeCardSubtitle");

        this.cabinContainer = (VBox) root.lookup("#cabinContainer");
        this.seatSelectionLabel = (Label) root.lookup("#seatSelectionLabel");
        this.totalPriceLabel = (Label) root.lookup("#totalPriceLabel");
        this.stepperBar = (StepperBar) root.lookup("#stepperBar");

        if (stepperBar != null) {
            stepperBar.setActiveStep(BookingStep.SEAT_SELECTION);
        }

        if (seatSelectionLabel != null) {
            seatSelectionLabel.textProperty().addListener((obs, oldV, newV) -> {
                handleSelectionLabelChanged(newV);
            });
        }

        if (cabinContainer != null) {
            cabinContainer.getChildren().addListener((javafx.collections.ListChangeListener<Node>) change -> {
                Platform.runLater(this::harvestSeatsFromCabinContainer);
            });
            harvestSeatsFromCabinContainer();
        }

        updateFlightCardData();
    }

    private void harvestSeatsFromCabinContainer() {
        if (cabinContainer == null) return;

        seatButtons.clear();
        seatsByLabel.clear();
        distinctRows.clear();
        sectionSlices.clear();

        if (controller != null && controller.getSeatsByLabelMap() != null) {
            seatsByLabel.putAll(controller.getSeatsByLabelMap());
        }

        for (Node child : cabinContainer.getChildren()) {
            if (child instanceof HBox rowBox) {
                for (Node btnNode : rowBox.getChildren()) {
                    if (btnNode instanceof Button btn) {
                        String txt = btn.getText();
                        if (txt != null && txt.matches("\\d+[A-F]")) {
                            seatButtons.put(txt, btn);
                            try {
                                int r = Integer.parseInt(txt.replaceAll("[^0-9]", ""));
                                if (!distinctRows.contains(r)) {
                                    distinctRows.add(r);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }

        Collections.sort(distinctRows);

        // Partition distinct rows into 3 sections: Section 1 (Front, rows 1-9), Section 2 (Wings, rows 10-18), Section 3 (Aft, rows 19+)
        List<Integer> s1 = new ArrayList<>();
        List<Integer> s2 = new ArrayList<>();
        List<Integer> s3 = new ArrayList<>();

        for (int r : distinctRows) {
            if (r <= 9) {
                s1.add(r);
            } else if (r <= 18) {
                s2.add(r);
            } else {
                s3.add(r);
            }
        }

        if (s1.isEmpty() && !distinctRows.isEmpty()) {
            s1.addAll(distinctRows);
        }

        sectionSlices.add(s1);
        sectionSlices.add(s2);
        sectionSlices.add(s3);

        // Attach taken seat feedback for any seat marked taken/booked
        for (Map.Entry<String, FlightSeat> entry : seatsByLabel.entrySet()) {
            String label = entry.getKey();
            FlightSeat s = entry.getValue();
            Button btn = seatButtons.get(label);
            if (btn != null && s.getStatus() == SeatStatus.BOOKED) {
                btn.setOnAction(e -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.WARNING,
                            "Seat " + label + " is already taken. Please choose a different seat.");
                    alert.setTitle("Seat unavailable");
                    alert.setHeaderText("Seat taken");
                    alert.showAndWait();
                });
            }
        }

        updateFlightCardData();
        renderActiveSection();

        // Check if there is already an active selection
        if (seatSelectionLabel != null && seatSelectionLabel.getText() != null) {
            handleSelectionLabelChanged(seatSelectionLabel.getText());
        }
    }

    public void setSection(int section) {
        if (section < 1 || section > 3) return;
        this.currentSection = section;

        if (section1Tab != null) {
            section1Tab.getStyleClass().remove("section-tab-on");
            if (section == 1) section1Tab.getStyleClass().add("section-tab-on");
        }
        if (section2Tab != null) {
            section2Tab.getStyleClass().remove("section-tab-on");
            if (section == 2) section2Tab.getStyleClass().add("section-tab-on");
        }
        if (section3Tab != null) {
            section3Tab.getStyleClass().remove("section-tab-on");
            if (section == 3) section3Tab.getStyleClass().add("section-tab-on");
        }

        if (planeGraphic != null) {
            planeGraphic.setSection(section);
        }

        renderActiveSection();
    }

    private void renderActiveSection() {
        if (sectionSlices.isEmpty()) return;
        int sliceIdx = Math.max(0, Math.min(2, currentSection - 1));
        List<Integer> sectionRows = sectionSlices.get(sliceIdx);

        // Update Section Header Label
        if (sectionHeaderLabel != null && !sectionRows.isEmpty()) {
            int minR = sectionRows.get(0);
            int maxR = sectionRows.get(sectionRows.size() - 1);
            String desc = switch (currentSection) {
                case 1 -> "First class and front economy";
                case 2 -> "Mid economy";
                default -> "Rear economy";
            };
            sectionHeaderLabel.setText("Section " + currentSection + " · " + desc + " · rows " + minR + " to " + maxR);
        }

        // Render Column Numbers Row
        if (seatGridNumbersRow != null) {
            seatGridNumbersRow.getChildren().clear();
            seatGridNumbersRow.setAlignment(Pos.CENTER);
            Region leftSpacer = new Region();
            leftSpacer.setPrefWidth(26);
            leftSpacer.setMinWidth(26);
            leftSpacer.setMaxWidth(26);
            seatGridNumbersRow.getChildren().add(leftSpacer);

            for (int r : sectionRows) {
                Label colLabel = new Label(String.valueOf(r));
                colLabel.setPrefWidth(40);
                colLabel.setMinWidth(40);
                colLabel.setMaxWidth(40);
                colLabel.setAlignment(Pos.CENTER);
                colLabel.getStyleClass().add("seat-col-header");
                seatGridNumbersRow.getChildren().add(colLabel);
            }
        }

        // Render Letter Rows
        if (seatGridRowsContainer != null) {
            seatGridRowsContainer.getChildren().clear();
            char[] letters = {'A', 'B', 'C', '_', 'D', 'E', 'F'};

            for (char letter : letters) {
                if (letter == '_') {
                    Region aisleGap = new Region();
                    aisleGap.setPrefHeight(18);
                    aisleGap.setMinHeight(18);
                    aisleGap.setMaxHeight(18);
                    seatGridRowsContainer.getChildren().add(aisleGap);
                } else {
                    HBox rowHBox = new HBox(8);
                    rowHBox.setAlignment(Pos.CENTER);
                    rowHBox.setPrefHeight(34);
                    rowHBox.setMinHeight(34);
                    rowHBox.setMaxHeight(34);

                    Label letterLabel = new Label(String.valueOf(letter));
                    letterLabel.setPrefWidth(26);
                    letterLabel.setMinWidth(26);
                    letterLabel.setMaxWidth(26);
                    letterLabel.setAlignment(Pos.CENTER);
                    letterLabel.getStyleClass().add("seat-row-letter");
                    rowHBox.getChildren().add(letterLabel);

                    for (int r : sectionRows) {
                        // Check if First Class (rows 1 & 2) and (letter == 'B' || letter == 'E')
                        if (r <= 2 && (letter == 'B' || letter == 'E')) {
                            Region gap = new Region();
                            gap.setPrefSize(40, 34);
                            gap.setMinSize(40, 34);
                            gap.setMaxSize(40, 34);
                            rowHBox.getChildren().add(gap);
                        } else {
                            String seatLabel = r + String.valueOf(letter);
                            Button btn = seatButtons.get(seatLabel);
                            if (btn != null) {
                                btn.setPrefSize(40, 34);
                                btn.setMinSize(40, 34);
                                btn.setMaxSize(40, 34);
                                syncButtonVisual(btn, seatLabel);
                                rowHBox.getChildren().add(btn);
                            } else {
                                Region gap = new Region();
                                gap.setPrefSize(40, 34);
                                gap.setMinSize(40, 34);
                                gap.setMaxSize(40, 34);
                                rowHBox.getChildren().add(gap);
                            }
                        }
                    }
                    seatGridRowsContainer.getChildren().add(rowHBox);
                }
            }
        }
    }

    public void syncButtonVisual(Button btn, String seatLabel) {
        if (btn == null) return;
        boolean isSelected = btn.getStyleClass().contains("seat-selected") || btn.getStyleClass().contains("seat-yours");
        if (isSelected) {
            btn.setText(seatLabel);
        } else {
            btn.setText("");
        }
    }

    private void handleSelectionLabelChanged(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("None")) {
            // Cleared
            if (bigSeatLabel != null) bigSeatLabel.setText("—");
            if (bigPriceLabel != null) bigPriceLabel.setText("R 0");
            if (chipsContainer != null) chipsContainer.getChildren().clear();
            stopHoldTimer();
            if (timerLabel != null) timerLabel.setText("--:--");
            if (timerArc != null) timerArc.setLength(0);
        } else {
            // e.g. "9E · Economy" or "7E · Economy"
            String seatLabel = text.split(" ")[0];
            FlightSeat seat = seatsByLabel.get(seatLabel);

            if (bigSeatLabel != null) bigSeatLabel.setText(seatLabel);
            if (totalPriceLabel != null && bigPriceLabel != null) {
                bigPriceLabel.setText(totalPriceLabel.getText());
            }

            // Populate Chips: [Class] [Window/Middle/Aisle] [Legroom]
            if (chipsContainer != null && seat != null) {
                chipsContainer.getChildren().clear();
                boolean isFirst = (seat.getSeatClass() == SeatClass.FIRST);
                char col = seat.getSeatCol();

                String cls = isFirst ? "First Class" : "Economy";
                String pos = (col == 'A' || col == 'F') ? "Window seat" :
                        ((col == 'C' || col == 'D') ? "Aisle seat" : "Middle seat");
                String leg = isFirst ? "Extra legroom" :
                        (seat.getSeatRow() == 6 ? "Extra legroom" : "Standard legroom");

                chipsContainer.getChildren().addAll(createChip(cls), createChip(pos), createChip(leg));
            }

            // Sync visual text across buttons
            for (Map.Entry<String, Button> entry : seatButtons.entrySet()) {
                syncButtonVisual(entry.getValue(), entry.getKey());
            }

            // Start countdown timer (10 mins or preselected 08:42 for screenshots)
            int initialSecs = TOTAL_HOLD_SECONDS;
            if (SeatMapController.isPreselectForScreenshots()) {
                initialSecs = 522; // 08:42 exactly matching design-glass/seat-map.png
            }
            startHoldTimer(initialSecs);
        }

        updateFlightCardData();
    }

    private Label createChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("selection-chip");
        return chip;
    }

    public void toggleZoom() {
        isZoomed = !isZoomed;
        if (zoomContainer != null) {
            double scale = isZoomed ? 1.12 : 1.0;
            zoomContainer.setScaleX(scale);
            zoomContainer.setScaleY(scale);
        }
    }

    public void startHoldTimer(int initialSeconds) {
        stopHoldTimer();
        this.remainingSeconds = initialSeconds;
        updateTimerVisuals();

        holdTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                remainingSeconds = 0;
                stopHoldTimer();
                // Expired hold: release seat
                if (controller != null) {
                    controller.handleReleaseSeat();
                }
            }
            updateTimerVisuals();
        }));
        holdTimeline.setCycleCount(Timeline.INDEFINITE);
        holdTimeline.play();
    }

    public void stopHoldTimer() {
        if (holdTimeline != null) {
            holdTimeline.stop();
            holdTimeline = null;
        }
    }

    private void updateTimerVisuals() {
        int mins = remainingSeconds / 60;
        int secs = remainingSeconds % 60;
        if (timerLabel != null) {
            timerLabel.setText(String.format("%02d:%02d", mins, secs));
        }
        if (timerArc != null) {
            double fraction = (double) remainingSeconds / TOTAL_HOLD_SECONDS;
            timerArc.setLength(-Math.max(0, fraction * 360.0));
        }
    }

    public void updateFlightCardData() {
        String orig = "JNB";
        String dest = "CPT";
        String depTime = "06:30";
        String arrTime = "08:40";
        String flightNo = "GRP 204";
        String duration = "2h 10m, direct";
        String dateStr = "Fri 14 Nov";
        String aircraft = "Airbus A320";

        if (controller != null && controller.getBookingContext() != null) {
            this.bookingContext = controller.getBookingContext();
        }

        if (bookingContext != null) {
            if (bookingContext.getOrigin() != null && !bookingContext.getOrigin().isBlank()) {
                orig = bookingContext.getOrigin();
            }
            if (bookingContext.getDestination() != null && !bookingContext.getDestination().isBlank()) {
                dest = bookingContext.getDestination();
            }
            if (bookingContext.getDepartureTime() != null && !bookingContext.getDepartureTime().isBlank()) {
                depTime = bookingContext.getDepartureTime();
            }
            if (bookingContext.getArrivalTime() != null && !bookingContext.getArrivalTime().isBlank()) {
                arrTime = bookingContext.getArrivalTime();
            }
            if (bookingContext.getFlightNumber() != null && !bookingContext.getFlightNumber().isBlank()) {
                flightNo = bookingContext.getFlightNumber();
            }
            if (bookingContext.getDepartureDate() != null && !bookingContext.getDepartureDate().isBlank()) {
                String rawDate = bookingContext.getDepartureDate();
                try {
                    java.time.LocalDate d = java.time.LocalDate.parse(rawDate);
                    dateStr = d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Locale.US));
                } catch (Exception ignored) {
                    dateStr = rawDate;
                }
            }
        }

        if (flightOriginCode != null) flightOriginCode.setText(orig);
        if (flightOriginTime != null) flightOriginTime.setText(depTime);
        if (flightDestCode != null) flightDestCode.setText(dest);
        if (flightDestTime != null) flightDestTime.setText(arrTime);
        if (flightNoLabel != null) flightNoLabel.setText(flightNo);
        if (flightDurationLabel != null) flightDurationLabel.setText(duration);

        if (planeCardSubtitle != null) {
            planeCardSubtitle.setText(orig + " to " + dest + " · " + dateStr + " · " + aircraft);
        }

        // Count free seats
        if (freeSeatsLabel != null) {
            int total = seatsByLabel.isEmpty() ? 44 : seatsByLabel.size();
            long freeCount = seatsByLabel.values().stream()
                    .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                    .count();
            if (freeCount == 0 && !seatsByLabel.isEmpty()) {
                // If counting from buttons
                freeCount = seatButtons.values().stream()
                        .filter(b -> b.getStyleClass().contains("seat-available") || b.getStyleClass().contains("seat-free"))
                        .count();
            }
            if (freeCount == 0) freeCount = 29;
            freeSeatsLabel.setText(freeCount + " of " + total);
        }
    }
}
