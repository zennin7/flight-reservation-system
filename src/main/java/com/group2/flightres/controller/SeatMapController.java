package com.group2.flightres.controller;

import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.SeatMapService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeatMapController {

    @FXML private GridPane cabinGrid;
    @FXML private Label totalPriceLabel;
    @FXML private Button continueButton;

    private final SeatMapService seatMapService = new SeatMapService();
    private final Map<Long, StackPane> seatNodesById = new HashMap<>();
    private final List<FlightSeat> selectedSeats = new ArrayList<>();

    private long flightId;

    public void setFlightId(long flightId) {
        this.flightId = flightId;
        loadSeatMap();
    }

    private void loadSeatMap() {
        cabinGrid.getChildren().clear();
        seatNodesById.clear();
        selectedSeats.clear();
        updateTotalPrice();

        List<FlightSeat> seats = seatMapService.loadSeatMap(flightId);

        for (FlightSeat seat : seats) {
            StackPane seatNode = buildSeatNode(seat);
            cabinGrid.add(seatNode, seat.getDisplayCol(), seat.getDisplayRow());
            seatNodesById.put(seat.getFlightSeatId(), seatNode);
        }
    }

    private StackPane buildSeatNode(FlightSeat seat) {
        Rectangle rect = new Rectangle(36, 36);
        rect.setArcWidth(8);
        rect.setArcHeight(8);
        applySeatColor(rect, seat.getStatus());

        Label label = new Label(seat.getSeatLabel());
        label.setStyle("-fx-font-size: 9px; -fx-text-fill: white; -fx-font-weight: bold;");

        StackPane pane = new StackPane(rect, label);
        pane.setUserData(seat);

        Tooltip tooltip = new Tooltip(
                "Seat " + seat.getSeatLabel() + " — " + seat.getSeatClass()
                        + "\nPrice: R " + seat.getCalculatedPrice());
        tooltip.setShowDelay(Duration.millis(150));
        Tooltip.install(pane, tooltip);

        if (seat.getStatus() == SeatStatus.AVAILABLE) {
            pane.setStyle("-fx-cursor: hand;");
            pane.setOnMouseClicked(e -> handleSeatClick(seat, rect));
        }

        return pane;
    }

    private void applySeatColor(Rectangle rect, SeatStatus status) {
        switch (status) {
            case AVAILABLE -> rect.setFill(Color.web("#16a34a"));  // green
            case BOOKED -> rect.setFill(Color.web("#dc2626"));     // red
            case HELD -> rect.setFill(Color.web("#eab308"));       // yellow
        }
    }

    private void handleSeatClick(FlightSeat seat, Rectangle rect) {
        boolean alreadySelected = selectedSeats.contains(seat);

        try {
            if (alreadySelected) {
                seatMapService.deselectSeat(seat);
                selectedSeats.remove(seat);
                rect.setFill(Color.web("#16a34a")); // back to green
            } else {
                seatMapService.selectSeat(seat);
                selectedSeats.add(seat);
                rect.setFill(Color.web("#eab308")); // yellow = selected
            }
            updateTotalPrice();
        } catch (SeatUnavailableException e) {
            new Alert(Alert.AlertType.WARNING, e.getMessage()).showAndWait();
            loadSeatMap(); // refresh grid — seat state changed under us
        }
    }

    private void updateTotalPrice() {
        BigDecimal total = selectedSeats.stream()
                .map(FlightSeat::getCalculatedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalPriceLabel.setText("Total: R " + total);
        continueButton.setDisable(selectedSeats.isEmpty());
    }

    @FXML
    private void handleContinue() {
        // TODO: pass selectedSeats forward to CheckoutController via SceneManager
        System.out.println("Proceeding to checkout with " + selectedSeats.size() + " seat(s) selected.");
    }
}
