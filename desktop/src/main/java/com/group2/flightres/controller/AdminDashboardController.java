// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.controller;

import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.DailyBookingsPoint;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.AdminDashboardData.RouteRevenuePoint;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AdminDashboardService;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.Navigator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * Controller for Screen 8: Glass Admin Dashboard.
 * Displays live SQL revenue, 14-day SAST booking chart, route share pie chart,
 * upcoming flight load factors, and low-seat alerts with service-layer RBAC.
 */
public class AdminDashboardController {

    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);

    @FXML private StackPane cardMarginWrapper;
    @FXML private VBox mainContainer;
    @FXML private HBox adminNavLinksBox;
    @FXML private Label roleBadgeLabel;
    @FXML private Label avatarInitialsLabel;

    @FXML private ScrollPane dashboardScrollPane;
    @FXML private VBox populatedContainer;
    @FXML private HBox kpiRow;
    @FXML private HBox chartsRow;
    @FXML private HBox tablesRow;

    @FXML private Label totalRevenueLabel;
    @FXML private Label bookingsCountLabel;
    @FXML private Label ticketsSoldLabel;
    @FXML private Label lowSeatCountLabel;
    @FXML private Label lowSeatTitleLabel;

    @FXML private BarChart<String, Number> bookingsPerDayChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private PieChart revenueByRouteChart;

    @FXML private VBox upcomingFlightsContainer;
    @FXML private VBox lowSeatAlertsContainer;

    @FXML private VBox emptyStateContainer;
    @FXML private VBox errorStateContainer;
    @FXML private Label errorMessageLabel;
    @FXML private VBox deniedStateContainer;
    @FXML private Label deniedMessageLabel;
    @FXML private Button retryButton;
    @FXML private Button refreshButton;

    private final AdminDashboardService dashboardService;
    private BookingContext bookingContext;
    private User currentUser;

    public AdminDashboardController() {
        this(new AdminDashboardService());
    }

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService != null ? dashboardService : new AdminDashboardService();
    }

    public void init(BookingContext context) {
        this.bookingContext = context != null ? context : new BookingContext();
        this.currentUser = this.bookingContext.getCurrentUser();
        updateHeaderUser();
        refresh();
    }

    public void init(User user) {
        BookingContext ctx = new BookingContext();
        ctx.setCurrentUser(user);
        init(ctx);
    }

    @FXML
    public void initialize() {
        FontLoader.loadFonts();
        if (bookingsPerDayChart != null) {
            bookingsPerDayChart.setAnimated(false);
            bookingsPerDayChart.setLegendVisible(false);
        }
        if (revenueByRouteChart != null) {
            revenueByRouteChart.setAnimated(false);
        }
        if (lowSeatTitleLabel != null) {
            lowSeatTitleLabel.setText("LOW-SEAT ALERTS (< " + AdminDashboardService.LOW_SEAT_THRESHOLD + " FREE)");
        }
        setupResponsiveListeners();
    }

    private void setupResponsiveListeners() {
        Platform.runLater(() -> {
            if (cardMarginWrapper != null && cardMarginWrapper.getScene() != null) {
                var scene = cardMarginWrapper.getScene();
                scene.widthProperty().addListener((obs, oldVal, newVal) ->
                        updateResponsiveLayout(newVal.doubleValue(), scene.getHeight()));
                updateResponsiveLayout(scene.getWidth(), scene.getHeight());
            }
        });
    }

    public void updateResponsiveLayout(double width, double height) {
        if (cardMarginWrapper == null) return;
        boolean compact = width < 1100;
        if (compact) {
            cardMarginWrapper.setPadding(new Insets(4, 20, 16, 20));
            if (mainContainer != null) {
                mainContainer.getStyleClass().remove("admin-main-card");
                if (!mainContainer.getStyleClass().contains("admin-main-card-compact")) {
                    mainContainer.getStyleClass().add("admin-main-card-compact");
                }
            }
        } else {
            cardMarginWrapper.setPadding(new Insets(4, 56, 20, 56));
            if (mainContainer != null) {
                mainContainer.getStyleClass().remove("admin-main-card-compact");
                if (!mainContainer.getStyleClass().contains("admin-main-card")) {
                    mainContainer.getStyleClass().add("admin-main-card");
                }
            }
        }
    }

    private void updateHeaderUser() {
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        if (adminNavLinksBox != null) {
            adminNavLinksBox.setVisible(isAdmin);
            adminNavLinksBox.setManaged(isAdmin);
        }
        if (roleBadgeLabel != null) {
            roleBadgeLabel.setVisible(isAdmin);
            roleBadgeLabel.setManaged(isAdmin);
        }
        if (avatarInitialsLabel != null) {
            if (currentUser != null && currentUser.getFullName() != null && !currentUser.getFullName().isBlank()) {
                String[] parts = currentUser.getFullName().trim().split("\\s+");
                String initials = parts.length >= 2
                        ? parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)
                        : parts[0].substring(0, Math.min(2, parts[0].length()));
                avatarInitialsLabel.setText(initials.toUpperCase(Locale.ROOT));
            } else {
                avatarInitialsLabel.setText("AD");
            }
        }
    }

    public void refresh() {
        try {
            AdminDashboardData data = dashboardService.getDashboardData(currentUser);
            if (data == null || data.isEmpty()) {
                showEmptyState();
            } else {
                populateData(data);
            }
        } catch (SecurityException se) {
            logger.warn("Admin Dashboard RBAC denied: {}", se.getMessage());
            showDeniedState(se.getMessage());
        } catch (Exception e) {
            logger.error("Failed to load admin dashboard data", e);
            showErrorState("Could not retrieve platform metrics. Please try again.");
        }
    }

    public void populateData(AdminDashboardData data) {
        setStateVisibility(true, false, false, false);

        if (totalRevenueLabel != null) {
            totalRevenueLabel.setText(String.format(Locale.US, "R %,.2f", data.getTotalRevenue()));
        }
        if (bookingsCountLabel != null) {
            bookingsCountLabel.setText(String.valueOf(data.getBookingsCount()));
        }
        if (ticketsSoldLabel != null) {
            ticketsSoldLabel.setText(String.valueOf(data.getTicketsSold()));
        }
        if (lowSeatCountLabel != null) {
            lowSeatCountLabel.setText(String.valueOf(data.getLowSeatAlerts().size()));
        }

        if (bookingsPerDayChart != null) {
            bookingsPerDayChart.getData().clear();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Bookings");
            for (DailyBookingsPoint p : data.getBookingsPerDay()) {
                series.getData().add(new XYChart.Data<>(p.dayLabel(), p.bookingsCount()));
            }
            bookingsPerDayChart.getData().add(series);
        }

        if (revenueByRouteChart != null) {
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            for (RouteRevenuePoint r : data.getRevenuePerRoute()) {
                pieData.add(new PieChart.Data(r.route(), r.revenue().doubleValue()));
            }
            revenueByRouteChart.setData(pieData);
        }

        if (upcomingFlightsContainer != null) {
            upcomingFlightsContainer.getChildren().clear();
            List<FlightLoadFactor> flights = data.getUpcomingFlights();
            if (flights.isEmpty()) {
                Label emptyLbl = new Label("No upcoming scheduled flights.");
                emptyLbl.getStyleClass().add("admin-row-meta");
                upcomingFlightsContainer.getChildren().add(emptyLbl);
            } else {
                for (FlightLoadFactor f : flights) {
                    upcomingFlightsContainer.getChildren().add(buildUpcomingFlightRow(f));
                }
            }
        }

        if (lowSeatAlertsContainer != null) {
            lowSeatAlertsContainer.getChildren().clear();
            List<FlightLoadFactor> alerts = data.getLowSeatAlerts();
            if (alerts.isEmpty()) {
                Label okLbl = new Label("All upcoming flights have >= "
                        + AdminDashboardService.LOW_SEAT_THRESHOLD + " seats available.");
                okLbl.getStyleClass().add("admin-row-meta");
                okLbl.setWrapText(true);
                lowSeatAlertsContainer.getChildren().add(okLbl);
            } else {
                for (FlightLoadFactor f : alerts) {
                    lowSeatAlertsContainer.getChildren().add(buildLowSeatAlertRow(f));
                }
            }
        }
    }

    public Node buildUpcomingFlightRow(FlightLoadFactor f) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("admin-row-card");

        Label flightNo = new Label(f.flightNumber());
        flightNo.getStyleClass().add("admin-row-gold");
        flightNo.setMinWidth(Region.USE_PREF_SIZE);

        Label route = new Label(f.route());
        route.getStyleClass().add("admin-row-title");
        route.setMinWidth(Region.USE_PREF_SIZE);

        Label dep = new Label(f.getFormattedDepartureDate() + " " + f.getFormattedDepartureTime());
        dep.getStyleClass().add("admin-row-meta");
        dep.setMinWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label seats = new Label(f.bookedSeats() + "/" + f.totalSeats() + " booked");
        seats.getStyleClass().add("admin-row-meta");
        seats.setMinWidth(Region.USE_PREF_SIZE);

        Label loadPct = new Label(String.format(Locale.US, "%.1f%%", f.loadFactorPct()));
        loadPct.getStyleClass().add("admin-row-gold");
        loadPct.setMinWidth(Region.USE_PREF_SIZE);
        loadPct.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(flightNo, route, dep, spacer, seats, loadPct);
        return row;
    }

    private Node buildLowSeatAlertRow(FlightLoadFactor f) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("admin-alert-row");

        VBox left = new VBox(2);
        HBox.setHgrow(left, Priority.ALWAYS);
        Label title = new Label(f.flightNumber() + " · " + f.route());
        title.getStyleClass().add("admin-row-title");
        Label dep = new Label(f.getFormattedDepartureDate() + " " + f.getFormattedDepartureTime());
        dep.getStyleClass().add("admin-row-meta");
        left.getChildren().addAll(title, dep);

        Label avail = new Label(f.availableSeats() + " left");
        avail.getStyleClass().addAll("badge", "badge-red");

        row.getChildren().addAll(left, avail);
        return row;
    }

    public void showEmptyState() {
        setStateVisibility(false, true, false, false);
    }

    public void showErrorState(String message) {
        setStateVisibility(false, false, true, false);
        if (errorMessageLabel != null && message != null) {
            errorMessageLabel.setText(message);
        }
    }

    public void showDeniedState(String message) {
        setStateVisibility(false, false, false, true);
        if (deniedMessageLabel != null && message != null) {
            deniedMessageLabel.setText(message);
        }
    }

    private void setStateVisibility(boolean populated, boolean empty, boolean error, boolean denied) {
        if (dashboardScrollPane != null) {
            dashboardScrollPane.setVisible(populated);
            dashboardScrollPane.setManaged(populated);
        }
        if (emptyStateContainer != null) {
            emptyStateContainer.setVisible(empty);
            emptyStateContainer.setManaged(empty);
        }
        if (errorStateContainer != null) {
            errorStateContainer.setVisible(error);
            errorStateContainer.setManaged(error);
        }
        if (deniedStateContainer != null) {
            deniedStateContainer.setVisible(denied);
            deniedStateContainer.setManaged(denied);
        }
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleRetry() {
        refresh();
    }

    @FXML
    private void handleNavHome() {
        Navigator.navigateToHome(mainContainer, bookingContext);
    }

    @FXML
    private void handleNavFlights() {
        Navigator.navigateToSearch(mainContainer, bookingContext);
    }

    @FXML
    private void handleNavBookings() {
        Navigator.navigateToAdminBookings(mainContainer, bookingContext);
    }

    @FXML
    private void handleNavAvatar() {
        if (bookingContext != null && bookingContext.getCurrentUser() != null) {
            bookingContext.setCurrentUser(null);
        }
        Navigator.navigateToHome(mainContainer, bookingContext);
    }

    // Getters for layout & state tests
    public StackPane getCardMarginWrapper() { return cardMarginWrapper; }
    public VBox getMainContainer() { return mainContainer; }
    public ScrollPane getDashboardScrollPane() { return dashboardScrollPane; }
    public VBox getEmptyStateContainer() { return emptyStateContainer; }
    public VBox getErrorStateContainer() { return errorStateContainer; }
    public VBox getDeniedStateContainer() { return deniedStateContainer; }
    public Label getTotalRevenueLabel() { return totalRevenueLabel; }
    public Label getBookingsCountLabel() { return bookingsCountLabel; }
    public Label getTicketsSoldLabel() { return ticketsSoldLabel; }
    public Label getLowSeatCountLabel() { return lowSeatCountLabel; }
    public BarChart<String, Number> getBookingsPerDayChart() { return bookingsPerDayChart; }
    public PieChart getRevenueByRouteChart() { return revenueByRouteChart; }
    public VBox getUpcomingFlightsContainer() { return upcomingFlightsContainer; }
    public VBox getLowSeatAlertsContainer() { return lowSeatAlertsContainer; }
    public HBox getAdminNavLinksBox() { return adminNavLinksBox; }
}
