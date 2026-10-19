package com.group2.flightres.controller;

import com.group2.flightres.model.DashboardStats;
import com.group2.flightres.service.DashboardStatsService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class AdminDashboardController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);

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

    @FXML private VBox cardFlights;
    @FXML private VBox cardBookings;
    @FXML private VBox cardRevenue;
    @FXML private VBox cardSeats;
    @FXML private VBox cardChart;
    @FXML private javafx.scene.control.Button refreshButton;

    private static DashboardStats sampleStatsForTesting;

    public static void setSampleStatsForTesting(DashboardStats stats) {
        sampleStatsForTesting = stats;
    }

    private final DashboardStatsService statsService;

    public AdminDashboardController() {
        this(new DashboardStatsService());
    }

    public AdminDashboardController(DashboardStatsService statsService) {
        this.statsService = statsService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (bookingTrendsChart != null) {
            bookingTrendsChart.setAnimated(false);
            bookingTrendsChart.setLegendVisible(false);
        }

        com.group2.flightres.util.Motion.attachHoverLift(cardFlights);
        com.group2.flightres.util.Motion.attachHoverLift(cardBookings);
        com.group2.flightres.util.Motion.attachHoverLift(cardRevenue);
        com.group2.flightres.util.Motion.attachHoverLift(cardSeats);
        com.group2.flightres.util.Motion.attachHoverLift(cardChart);
        if (refreshButton != null) {
            com.group2.flightres.util.Motion.attachHoverLift(refreshButton);
        }

        refresh();
    }

    @FXML
    private void handleRefresh() {
        logger.info("Admin dashboard refresh triggered by user.");
        refresh();
    }

    public void refresh() {
        DashboardStats stats = sampleStatsForTesting;
        if (stats == null) {
            try {
                stats = statsService.getStats();
            } catch (Exception e) {
                logger.warn("Could not query platform statistics from database: {}", e.getMessage());
            }
        }
        populateStats(stats);
    }

    private void populateStats(DashboardStats stats) {
        if (stats != null) {
            if (com.group2.flightres.util.Motion.isEnabled()) {
                com.group2.flightres.util.Motion.countUp(totalFlightsLabel, stats.getTotalFlights(), "", "");
                com.group2.flightres.util.Motion.countUp(totalBookingsLabel, stats.getTotalBookings(), "", "");
                com.group2.flightres.util.Motion.countUp(revenueLabel, stats.getTotalRevenue() != null ? stats.getTotalRevenue().longValue() : 0, "R ", "");
            } else {
                if (totalFlightsLabel != null) totalFlightsLabel.setText(String.valueOf(stats.getTotalFlights()));
                if (totalBookingsLabel != null) totalBookingsLabel.setText(String.valueOf(stats.getTotalBookings()));
                if (revenueLabel != null) revenueLabel.setText(stats.getRevenueFormatted());
            }
            if (seatsSoldLabel != null) {
                seatsSoldLabel.setText(stats.getSeatsSoldPercentageFormatted());
            }

            if (bookingTrendsChart != null) {
                bookingTrendsChart.getData().clear();
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Bookings");
                if (stats.getBookingsPerDay() != null) {
                    for (DashboardStats.DailyBookingStat day : stats.getBookingsPerDay()) {
                        series.getData().add(new XYChart.Data<>(day.getDayLabel(), day.getBookingCount()));
                    }
                }
                bookingTrendsChart.getData().add(series);
            }
        } else {
            if (totalFlightsLabel != null) totalFlightsLabel.setText("0");
            if (totalBookingsLabel != null) totalBookingsLabel.setText("0");
            if (revenueLabel != null) revenueLabel.setText("R 0");
            if (seatsSoldLabel != null) seatsSoldLabel.setText("0%");
        }
    }
}
