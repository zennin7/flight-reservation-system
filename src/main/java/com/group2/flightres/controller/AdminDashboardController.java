package com.group2.flightres.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.ResourceBundle;

public class AdminDashboardController implements Initializable {

    @FXML
    private Label totalFlightsLabel;

    @FXML
    private Label totalBookingsLabel;

    @FXML
    private Label revenueLabel;

    @FXML
    private Label seatsSoldLabel;

    @FXML
    private BarChart<String, Number> bookingTrendsChart;

    @FXML
    private CategoryAxis xAxis;

    @FXML
    private NumberAxis yAxis;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        populateStatCards();
        populateBookingTrends();
    }

    private void populateStatCards() {
        // Sample data matching design mockup exactly
        // TODO(wire-real-data)
        if (totalFlightsLabel != null) {
            totalFlightsLabel.setText("24");
        }
        if (totalBookingsLabel != null) {
            totalBookingsLabel.setText("312");
        }
        if (revenueLabel != null) {
            revenueLabel.setText("R 458,200");
        }
        if (seatsSoldLabel != null) {
            seatsSoldLabel.setText("86%");
        }
    }

    private void populateBookingTrends() {
        if (bookingTrendsChart == null) return;

        bookingTrendsChart.setAnimated(false);
        bookingTrendsChart.setLegendVisible(false);

        // TODO(wire-real-data)
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Bookings");

        series.getData().add(new XYChart.Data<>("May", 80));
        series.getData().add(new XYChart.Data<>("Jun", 110));
        series.getData().add(new XYChart.Data<>("Jul", 140));
        series.getData().add(new XYChart.Data<>("Aug", 115));
        series.getData().add(new XYChart.Data<>("Sep", 175));
        series.getData().add(new XYChart.Data<>("Oct", 205));

        bookingTrendsChart.getData().add(series);
    }
}
