package com.group2.flightres.util;

import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingStep;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.component.StepperBar;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.stage.Modality;
import javafx.stage.Stage;
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
    private VBox zoomContainer;
    private StackPane cabinPhotoCard;
    private ImageView cabinPhotoView;
    private Label cabinKickerLabel;
    private GlassBackground glassBackground;
    private String activeCabinPhotoPath;

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
    private static final int TOTAL_HOLD_SECONDS = com.group2.flightres.config.SeatHoldPolicy.HOLD_SECONDS;

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

    public GlassSeatMapBridge(VBox zoomContainer) {
        this();
        this.zoomContainer = zoomContainer;
    }

    
    public void populateSeats(List<FlightSeat> seats) {
        if (seatGridRowsContainer == null) {
            initUiBindings();
        }
        if (seats != null) {
            for (FlightSeat seat : seats) {
                if (seat != null) {
                    seatsByLabel.put(seat.getSeatLabel(), seat);
                }
            }
        }
        harvestSeatsFromCabinContainer();
        for (Map.Entry<String, Button> entry : seatButtons.entrySet()) {
            syncButtonVisual(entry.getValue(), entry.getKey());
        }
    }

    public Map<String, Button> getSeatButtons() {
        return Collections.unmodifiableMap(seatButtons);
    }

    public void onSeatSelectionChanged(FlightSeat seat) {
        if (seat == null) {
            handleSelectionLabelChanged("None");
        } else {
            handleSelectionLabelChanged(seat.getSeatLabel());
        }
    }

    public void clearSelectionVisuals() {
        handleSelectionLabelChanged("None");
    }

    public void setController(SeatMapController controller) {
        this.controller = controller;
        if (controller != null) {
            this.bookingContext = controller.getBookingContext();
        }
    }

    public void initUiBindings() {
        Parent root = null;
        if (getScene() != null && getScene().getRoot() != null) {
            root = getScene().getRoot();
        } else {
            Node curr = this;
            while (curr.getParent() != null) {
                curr = curr.getParent();
            }
            if (curr instanceof Parent p) {
                root = p;
            }
        }
        if (root == null) return;

        // Lookup Glass elements
        this.planeGraphic = (GlassPlaneGraphic) root.lookup("#planeGraphic");
        this.section1Tab = (Button) root.lookup("#section1Tab");
        this.section2Tab = (Button) root.lookup("#section2Tab");
        this.section3Tab = (Button) root.lookup("#section3Tab");
        this.sectionHeaderLabel = (Label) root.lookup("#sectionHeaderLabel");
        this.seatGridNumbersRow = (HBox) root.lookup("#seatGridNumbersRow");
        this.seatGridRowsContainer = (VBox) root.lookup("#seatGridRowsContainer");
        this.zoomContainer = (VBox) root.lookup("#zoomContainer");
        this.cabinPhotoCard = (StackPane) root.lookup("#cabinPhotoCard");
        this.cabinPhotoView = (ImageView) root.lookup("#cabinPhotoView");
        this.cabinKickerLabel = (Label) root.lookup("#cabinKickerLabel");
        if (root instanceof GlassBackground gb) {
            this.glassBackground = gb;
        }
        if (this.cabinPhotoCard != null) {
            this.cabinPhotoCard.setOnMouseClicked(e -> openCabinLightbox());
        }

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
        if (seatGridRowsContainer == null) {
            initUiBindings();
        }
        if (cabinContainer == null && seatsByLabel.isEmpty() && (controller == null || controller.getSeatsByLabelMap().isEmpty())) return;

        seatButtons.clear();
        seatsByLabel.clear();
        distinctRows.clear();
        sectionSlices.clear();

        if (controller != null && controller.getSeatsByLabelMap() != null) {
            seatsByLabel.putAll(controller.getSeatsByLabelMap());
        }
        if (controller != null && controller.getSeatButtonsMap() != null) {
            seatButtons.putAll(controller.getSeatButtonsMap());
        }

        if (cabinContainer != null) {
            for (Node child : cabinContainer.getChildren()) {
                if (child instanceof HBox rowBox) {
                    for (Node btnNode : rowBox.getChildren()) {
                        if (btnNode instanceof Button btn) {
                            String txt = btn.getText();
                            if (txt != null && txt.matches("\\d+[A-F]")) {
                                seatButtons.putIfAbsent(txt, btn);
                            }
                        }
                    }
                }
            }
        }

        for (FlightSeat seat : seatsByLabel.values()) {
            if (!distinctRows.contains(seat.getSeatRow())) {
                distinctRows.add(seat.getSeatRow());
            }
        }
        if (distinctRows.isEmpty()) {
            for (String key : seatButtons.keySet()) {
                try {
                    int r = Integer.parseInt(key.replaceAll("[^0-9]", ""));
                    if (!distinctRows.contains(r)) {
                        distinctRows.add(r);
                    }
                } catch (Exception ignored) {}
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
        logger.info("harvestSeats: distinctRows={}, s1={}, s2={}, s3={}", distinctRows, s1, s2, s3);

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

        updateCabinSectionPhoto(section);
        renderActiveSection();
    }

    private void updateCabinSectionPhoto(int section) {
        String path = DestinationPhotos.sectionCabinPhotoPath(section);
        if (cabinKickerLabel != null) {
            cabinKickerLabel.setText("SECTION " + section + " CABIN");
        }
        if (path.equals(activeCabinPhotoPath)) {
            return;
        }
        activeCabinPhotoPath = path;
        Optional<Image> imgOpt = DestinationPhotos.loadResource(path);
        if (imgOpt.isPresent() && cabinPhotoView != null) {
            Image nextImg = imgOpt.get();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(95), cabinPhotoView);
            fadeOut.setFromValue(cabinPhotoView.getOpacity());
            fadeOut.setToValue(0.25);
            fadeOut.setOnFinished(e -> {
                cabinPhotoView.setImage(nextImg);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(115), cabinPhotoView);
                fadeIn.setFromValue(0.25);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();
        }
        if (glassBackground != null) {
            glassBackground.setPhotoWithFade(path);
        }
    }

    private void openCabinLightbox() {
        String path = activeCabinPhotoPath != null ? activeCabinPhotoPath : DestinationPhotos.sectionCabinPhotoPath(currentSection);
        Optional<Image> imgOpt = DestinationPhotos.loadResource(path);
        if (imgOpt.isEmpty() || getScene() == null || getScene().getWindow() == null) {
            return;
        }
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle(DestinationPhotos.sectionLightboxTitle(currentSection));
        ImageView view = new ImageView(imgOpt.get());
        view.setPreserveRatio(true);
        view.setFitWidth(760);
        view.setFitHeight(520);
        StackPane box = new StackPane(view);
        box.setStyle("-fx-background-color: #15181c; -fx-padding: 16;");
        dialog.setScene(new Scene(box, 800, 560));
        dialog.show();
    }

    private void renderActiveSection() {
        if (sectionSlices.isEmpty()) return;
        int sliceIdx = Math.max(0, Math.min(2, currentSection - 1));
        List<Integer> sectionRows = sectionSlices.get(sliceIdx);

        // Update Section Header Label dynamically from seat classes in the section
        if (sectionHeaderLabel != null && !sectionRows.isEmpty()) {
            int minR = sectionRows.get(0);
            int maxR = sectionRows.get(sectionRows.size() - 1);
            boolean hasFirst = false;
            boolean hasEconomy = false;
            for (int r : sectionRows) {
                for (FlightSeat s : seatsByLabel.values()) {
                    if (s.getSeatRow() == r) {
                        if (s.getSeatClass() == SeatClass.FIRST) hasFirst = true;
                        if (s.getSeatClass() == SeatClass.ECONOMY) hasEconomy = true;
                    }
                }
            }
            String desc;
            if (hasFirst && hasEconomy) {
                desc = "First class and economy";
            } else if (hasFirst) {
                desc = "First class";
            } else {
                desc = "Economy";
            }
            sectionHeaderLabel.setText("Section " + currentSection + " · " + desc + " · rows " + minR + " to " + maxR);
        } else if (sectionHeaderLabel != null) {
            sectionHeaderLabel.setText("-");
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

            // Find all seat col letters present in this section
            Set<Character> presentCols = new TreeSet<>();
            for (int r : sectionRows) {
                for (FlightSeat s : seatsByLabel.values()) {
                    if (s.getSeatRow() == r) {
                        presentCols.add(s.getSeatCol());
                    }
                }
            }

            boolean hasOnlyFirstClassCols = !presentCols.contains('E') && !presentCols.contains('F');

            List<Character> rowLetters = new ArrayList<>();
            if (hasOnlyFirstClassCols) {
                // Section only has First Class: rows 1-3 (A, B | aisle | C, D)
                rowLetters.add('A');
                rowLetters.add('B');
                rowLetters.add('_'); // aisle
                rowLetters.add('C');
                rowLetters.add('D');
            } else {
                // Standard 3-3 rows or mixed section: A, B, C | aisle | D, E, F
                if (presentCols.contains('A')) rowLetters.add('A');
                if (presentCols.contains('B')) rowLetters.add('B');
                if (presentCols.contains('C')) rowLetters.add('C');
                rowLetters.add('_'); // aisle
                if (presentCols.contains('D')) rowLetters.add('D');
                if (presentCols.contains('E')) rowLetters.add('E');
                if (presentCols.contains('F')) rowLetters.add('F');
            }

            for (char letter : rowLetters) {
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
                        // Check if row r is a First Class row
                        boolean isRowFirstClass = false;
                        for (FlightSeat s : seatsByLabel.values()) {
                            if (s.getSeatRow() == r && s.getSeatClass() == SeatClass.FIRST) {
                                isRowFirstClass = true;
                                break;
                            }
                        }

                        String seatLabel = null;
                        if (hasOnlyFirstClassCols) {
                            seatLabel = r + String.valueOf(letter);
                        } else if (isRowFirstClass) {
                            // First class 2-2 mapping in 6-row grid (A, C, D, F):
                            // Top of aisle: A -> rA, B -> empty gap, C -> rC
                            // Bottom of aisle: D -> rD, E -> empty gap, F -> rF
                            seatLabel = switch (letter) {
                                case 'A' -> r + "A";
                                case 'C' -> r + "C";
                                case 'D' -> r + "D";
                                case 'F' -> r + "F";
                                default -> null;
                            };
                        } else {
                            seatLabel = r + String.valueOf(letter);
                        }

                        Button btn = (seatLabel != null) ? seatButtons.get(seatLabel) : null;
                        if (btn == null && seatLabel != null && seatsByLabel.containsKey(seatLabel)) {
                            btn = createSeatButton(seatLabel, seatsByLabel.get(seatLabel), r, letter, sectionRows, rowLetters);
                            seatButtons.put(seatLabel, btn);
                        }
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
                    seatGridRowsContainer.getChildren().add(rowHBox);
                }
            }
        }
    }

    public void syncButtonVisual(Button btn, String seatLabel) {
        if (btn == null) return;
        boolean isSelected = btn.getStyleClass().contains("seat-selected") || btn.getStyleClass().contains("seat-yours");
        boolean isTaken = btn.getStyleClass().contains("seat-occupied") || btn.getStyleClass().contains("seat-taken");
        if (isSelected) {
            btn.setText(seatLabel);
        } else if (isTaken) {
            btn.setText("×");
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

            if (bigSeatLabel != null) {
                bigSeatLabel.setText(seatLabel);
                bigSeatLabel.setStyle(null);
            }
            if (totalPriceLabel != null && bigPriceLabel != null) {
                bigPriceLabel.setText(totalPriceLabel.getText());
            }

            // Populate Chips: [Class] [Window/Middle/Aisle] [Legroom]
            if (chipsContainer != null && seat != null) {
                chipsContainer.getChildren().clear();
                boolean isFirst = (seat.getSeatClass() == SeatClass.FIRST);
                char col = seat.getSeatCol();

                String cls = isFirst ? "First" : "Economy";
                String pos = (col == 'A' || col == 'F') ? "Window" :
                        ((col == 'C' || col == 'D') ? "Aisle" : "Middle");
                String leg = isFirst ? "Extra legroom" :
                        (seat.getSeatRow() == 6 ? "Extra legroom" : "Std legroom");

                chipsContainer.getChildren().addAll(createChip(cls), createChip(pos), createChip(leg));
            }

            // Sync visual text across buttons
            for (Map.Entry<String, Button> entry : seatButtons.entrySet()) {
                syncButtonVisual(entry.getValue(), entry.getKey());
            }

            // Start countdown timer from authoritative DB held_until
            int initialSecs = TOTAL_HOLD_SECONDS;
            if (seat != null && seat.getHeldUntil() != null) {
                long rem = java.time.Duration.between(java.time.Instant.now(), seat.getHeldUntil()).toSeconds();
                initialSecs = (int) Math.max(0, Math.min(TOTAL_HOLD_SECONDS, rem));
            }
            startHoldTimer(initialSecs);
        }

        updateFlightCardData();
    }

    private Label createChip(String text) {
        Label chip = new Label(text);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.getStyleClass().add("selection-chip");
        return chip;
    }

    public void toggleZoom() {
        isZoomed = !isZoomed;
        if (zoomContainer != null) {
            double scale = isZoomed ? 1.25 : 1.0;
            zoomContainer.setScaleX(scale);
            zoomContainer.setScaleY(scale);
        }
    }

    public boolean isZoomed() {
        return isZoomed;
    }

    public VBox getZoomContainer() {
        return zoomContainer;
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
                // Expired hold: release seat and notify controller
                if (controller != null) {
                    controller.handleHoldExpired();
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
        String orig = "";
        String dest = "";
        String depTime = "";
        String arrTime = "";
        String flightNo = "";
        String duration = "";
        String dateStr = "";
        String aircraft = "";

        if (controller != null && controller.getBookingContext() != null) {
            this.bookingContext = controller.getBookingContext();
        }

        if (bookingContext != null) {
            orig = bookingContext.getOriginCode() != null && !bookingContext.getOriginCode().isBlank()
                    ? bookingContext.getOriginCode()
                    : extractAirportCode(bookingContext.getOrigin());
            dest = bookingContext.getDestinationCode() != null && !bookingContext.getDestinationCode().isBlank()
                    ? bookingContext.getDestinationCode()
                    : extractAirportCode(bookingContext.getDestination());
            depTime = bookingContext.getDepartureTime() != null ? bookingContext.getDepartureTime() : "";
            arrTime = bookingContext.getArrivalTime() != null ? bookingContext.getArrivalTime() : "";
            flightNo = bookingContext.getFlightNumber() != null ? bookingContext.getFlightNumber() : "";
            aircraft = bookingContext.getAircraftModel() != null ? bookingContext.getAircraftModel() : "";

            if (bookingContext.getDepartureInstant() != null && bookingContext.getArrivalInstant() != null) {
                duration = com.group2.flightres.util.FlightTimes.formatDuration(bookingContext.getDepartureInstant(), bookingContext.getArrivalInstant()) + ", direct";
            } else if (bookingContext.getDuration() != null && !bookingContext.getDuration().isBlank()) {
                duration = bookingContext.getDuration();
                if (!duration.contains("direct")) {
                    duration = duration + ", direct";
                }
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
            String route = (!orig.isBlank() && !dest.isBlank()) ? (orig + " to " + dest) : "";
            StringBuilder sb = new StringBuilder();
            if (!route.isBlank()) sb.append(route);
            if (!dateStr.isBlank()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(dateStr);
            }
            if (!aircraft.isBlank()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(aircraft);
            }
            planeCardSubtitle.setText(sb.toString());
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

    private String extractAirportCode(String val) {
        if (val == null || val.isBlank()) return "";
        int open = val.lastIndexOf('(');
        int close = val.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return val.substring(open + 1, close).trim().toUpperCase(Locale.ROOT);
        }
        String trimmed = val.trim();
        if (trimmed.length() == 3 && trimmed.chars().allMatch(Character::isLetter)) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("tambo") || lower.contains("johannesburg")) return "JNB";
        if (lower.contains("cape town")) return "CPT";
        if (lower.contains("shaka") || lower.contains("durban")) return "DUR";
        if (lower.contains("stuurman") || lower.contains("gqeberha") || lower.contains("port elizabeth")) return "PLZ";
        if (lower.contains("geneva")) return "GVA";
        if (lower.contains("nice") || lower.contains("monaco")) return "NCE";
        if (lower.contains("dubai")) return "DXB";
        if (lower.contains("rome") || lower.contains("fiumicino")) return "FCO";
        if (lower.contains("santorini")) return "JTR";
        return trimmed.length() <= 6 ? trimmed : trimmed.substring(0, 3).toUpperCase(Locale.ROOT);
    }


    private Button createSeatButton(String label, FlightSeat seat, int rowNum, char colLetter,
                                     List<Integer> sectionRows, List<Character> rowLetters) {
        Button btn = new Button();
        btn.setId("seatBtn_" + label);
        btn.setPrefSize(40, 34);
        btn.setMinSize(40, 34);
        btn.setMaxSize(40, 34);
        btn.setFocusTraversable(true);
        btn.setAccessibleText("Seat " + label + " " + (seat != null ? seat.getSeatClass() : "") + " " + (seat != null ? seat.getStatus() : ""));

        btn.getStyleClass().add("seat-btn");
        if (seat != null && seat.getStatus() == SeatStatus.BOOKED) {
            btn.getStyleClass().add("seat-taken");
            btn.setText("× " + label);
        } else if (seat != null && seat.getStatus() == SeatStatus.HELD) {
            btn.getStyleClass().add("seat-held-other");
            btn.setText("• " + label);
        } else {
            btn.getStyleClass().add("seat-available");
            btn.setText(label);
            if (seat != null && seat.getSeatClass() == SeatClass.FIRST) {
                btn.getStyleClass().add("seat-first-class");
            }
        }

        btn.setOnAction(e -> {
            if (controller != null) {
                controller.handleSeatClick(label);
            }
        });

        // Keyboard navigation per Gate 4c: arrow keys move focus, Enter selects, Esc closes/toggles
        btn.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case UP -> {
                    navigateGrid(rowNum, colLetter, -1, 0, sectionRows, rowLetters);
                    event.consume();
                }
                case DOWN -> {
                    navigateGrid(rowNum, colLetter, 1, 0, sectionRows, rowLetters);
                    event.consume();
                }
                case LEFT -> {
                    navigateGrid(rowNum, colLetter, 0, -1, sectionRows, rowLetters);
                    event.consume();
                }
                case RIGHT -> {
                    navigateGrid(rowNum, colLetter, 0, 1, sectionRows, rowLetters);
                    event.consume();
                }
                case ENTER, SPACE -> {
                    btn.fire();
                    event.consume();
                }
                case ESCAPE -> {
                    if (isZoomed) {
                        toggleZoom();
                    }
                    event.consume();
                }
                default -> {}
            }
        });

        return btn;
    }

    private void navigateGrid(int rowNum, char colLetter, int dLetter, int dRow,
                              List<Integer> sectionRows, List<Character> rowLetters) {
        if (dRow != 0) {
            int currentIdx = sectionRows.indexOf(rowNum);
            int targetIdx = currentIdx + dRow;
            if (targetIdx >= 0 && targetIdx < sectionRows.size()) {
                int targetRow = sectionRows.get(targetIdx);
                String targetLabel = targetRow + String.valueOf(colLetter);
                Button targetBtn = seatButtons.get(targetLabel);
                if (targetBtn != null) {
                    targetBtn.requestFocus();
                }
            }
        } else if (dLetter != 0) {
            int currentIdx = rowLetters.indexOf(colLetter);
            int targetIdx = currentIdx + dLetter;
            while (targetIdx >= 0 && targetIdx < rowLetters.size()) {
                char targetLetter = rowLetters.get(targetIdx);
                if (targetLetter != '_') {
                    String targetLabel = rowNum + String.valueOf(targetLetter);
                    Button targetBtn = seatButtons.get(targetLabel);
                    if (targetBtn != null) {
                        targetBtn.requestFocus();
                        break;
                    }
                }
                targetIdx += dLetter;
            }
        }
    }

}
