// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.controller;

import com.group2.flightres.component.NavBar;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.CabinLoadBreakdown;
import com.group2.flightres.model.AdminDashboardData.DailySeriesPoint;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.AdminDashboardData.Range;
import com.group2.flightres.model.AdminDashboardData.RecentBookingItem;
import com.group2.flightres.model.AdminDashboardData.TopDestinationItem;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AdminDashboardService;
import com.group2.flightres.util.DestinationPhotoResolver;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.MoneyFormatter;
import com.group2.flightres.util.NameInitials;
import com.group2.flightres.util.Navigator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polyline;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller for the Glass Admin Dashboard (Screen 8B), wired strictly to real SQL analytics.
 */
public class AdminDashboardController {

    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);
    private static final ZoneId SA_ZONE = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter SUBTITLE_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);
    private static final List<String> AVATAR_CLASSES = List.of(
            "avatar-circle-gold", "avatar-circle-blue", "avatar-circle-green", "avatar-circle-violet"
    );

    @FXML private NavBar navBar;
    @FXML private StackPane cardMarginWrapper;
    @FXML private VBox mainContainer;

    @FXML private GridPane headerGrid;
    @FXML private VBox headerTitleBox;
    @FXML private HBox headerControlsBox;
    @FXML private Label greetingLabel;
    @FXML private Label creditLabel;
    @FXML private Label dateSubtitleLabel;
    @FXML private Button rangeTodayBtn;
    @FXML private Button range7DaysBtn;
    @FXML private Button range30DaysBtn;
    @FXML private Button exportReportBtn;

    @FXML private StackPane contentStack;
    @FXML private ScrollPane dashboardScrollPane;
    @FXML private VBox populatedContainer;
    @FXML private VBox loadingStateContainer;
    @FXML private VBox emptyStateContainer;
    @FXML private VBox errorStateContainer;
    @FXML private VBox deniedStateContainer;
    @FXML private Label errorMessageLabel;
    @FXML private Label deniedMessageLabel;

    @FXML private GridPane kpiGrid;
    @FXML private VBox kpiCardBookings;
    @FXML private VBox kpiCardRevenue;
    @FXML private VBox kpiCardSeats;
    @FXML private VBox kpiCardCancellations;

    @FXML private Label kpiBookingsLabel;
    @FXML private Label deltaBookingsLabel;
    @FXML private Polyline sparkBookings;

    @FXML private Label kpiRevenueLabel;
    @FXML private Label deltaRevenueLabel;
    @FXML private Polyline sparkRevenue;

    @FXML private Label kpiSeatsSoldLabel;
    @FXML private Label deltaSeatsLabel;
    @FXML private Polyline sparkSeats;

    @FXML private Label kpiCancellationsLabel;
    @FXML private Label deltaCancellationsLabel;
    @FXML private Polyline sparkCancellations;

    @FXML private GridPane chartsGrid;
    @FXML private VBox chartBookingsCard;
    @FXML private VBox cabinLoadCard;
    @FXML private AreaChart<String, Number> bookingsRevenueChart;
    @FXML private CategoryAxis chartDateAxis;
    @FXML private NumberAxis chartValueAxis;
    @FXML private Label chartBookingsLegendLabel;
    @FXML private Label chartRevenueLegendLabel;

    @FXML private PieChart cabinDonutChart;
    @FXML private Label cabinCenterPctLabel;
    @FXML private Label cabinEconomyPctLabel;
    @FXML private Label cabinFirstPctLabel;
    @FXML private Label cabinOpenPctLabel;
    @FXML private Label seatsHeldLabel;
    @FXML private Label lowFreeTitleLabel;
    @FXML private Label flightsLowFreeLabel;

    @FXML private GridPane bottomGrid;
    @FXML private VBox todayFlightsCard;
    @FXML private VBox topDestinationsCard;
    @FXML private VBox recentBookingsCard;
    @FXML private Label todayFlightsCountLabel;
    @FXML private VBox todayFlightsList;
    @FXML private VBox topDestinationsList;
    @FXML private VBox recentBookingsList;
    @FXML private Button viewAllBookingsBtn;

    private final AdminDashboardService dashboardService;
    private final Clock clock;
    private BookingContext bookingContext;
    private User currentUser;
    private Range activeRange = Range.SEVEN_DAYS;
    private AdminDashboardData lastData;

    public AdminDashboardController() {
        this(new AdminDashboardService(), Clock.systemUTC());
    }

    public AdminDashboardController(AdminDashboardService dashboardService, Clock clock) {
        this.dashboardService = dashboardService != null ? dashboardService : new AdminDashboardService();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @FXML
    public void initialize() {
        FontLoader.loadFonts();
        if (bookingsRevenueChart != null) {
            bookingsRevenueChart.setAnimated(false);
            bookingsRevenueChart.setLegendVisible(false);
        }
        if (chartValueAxis != null) {
            chartValueAxis.setLabel("% of period peak");
            chartValueAxis.setAutoRanging(false);
            chartValueAxis.setLowerBound(0);
            chartValueAxis.setUpperBound(100);
            chartValueAxis.setTickUnit(25);
        }
        if (cabinDonutChart != null) {
            cabinDonutChart.setAnimated(false);
            cabinDonutChart.setLegendVisible(false);
            cabinDonutChart.setLabelsVisible(false);
        }
        if (lowFreeTitleLabel != null) {
            lowFreeTitleLabel.setText("Flights below " + AdminDashboardService.LOW_FREE_PERCENT + "% free");
        }
        updateGreetingAndDate();
        updateRangeToggleClasses();
        setupResponsiveListeners();
    }

    public void init(BookingContext context) {
        this.bookingContext = context != null ? context : new BookingContext();
        this.currentUser = this.bookingContext.getCurrentUser();
        updateNavBarAndGreeting();
        refresh();
    }

    public void init(User user) {
        BookingContext ctx = new BookingContext();
        ctx.setCurrentUser(user);
        init(ctx);
    }

    private void updateNavBarAndGreeting() {
        if (navBar != null) {
            navBar.setAdminMode(currentUser != null && currentUser.getRole() == UserRole.ADMIN);
            navBar.setActiveLink("Dashboard");
            navBar.setUserDisplayName(currentUser != null ? currentUser.getFullName() : null);
            navBar.setNavigationHandler(this::handleNavBarClick);
        }
        updateGreetingAndDate();
    }

    private void updateGreetingAndDate() {
        ZonedDateTime nowSast = clock.instant().atZone(SA_ZONE);
        int hour = nowSast.getHour();
        String timeGreeting = hour < 12 ? "Good morning" : (hour < 18 ? "Good afternoon" : "Good evening");
        String firstName = extractFirstName(currentUser);
        if (greetingLabel != null) {
            greetingLabel.setText(timeGreeting + ", " + firstName);
        }
        if (dateSubtitleLabel != null) {
            dateSubtitleLabel.setText(nowSast.format(SUBTITLE_DATE_FMT) + " · live from the booking database");
        }
    }

    private String extractFirstName(User user) {
        if (user == null) return "Admin";
        String name = user.getFullName();
        if (name == null || name.isBlank()) {
            name = user.getUsername();
        }
        if (name == null || name.isBlank()) return "Admin";
        String[] parts = name.trim().split("\\s+");
        for (String p : parts) {
            String clean = p.replace(".", "");
            if (!clean.equalsIgnoreCase("Mr") && !clean.equalsIgnoreCase("Ms")
                    && !clean.equalsIgnoreCase("Mrs") && !clean.equalsIgnoreCase("Dr")) {
                return p;
            }
        }
        return parts[0];
    }

    private void handleNavBarClick(String tab) {
        if ("Bookings".equals(tab)) {
            Navigator.navigateToAdminBookings(mainContainer, bookingContext);
        } else if ("Flights".equals(tab)) {
            Navigator.navigateToAdminBookings(mainContainer, bookingContext);
        } else if ("Home".equals(tab) || "Search".equals(tab)) {
            Navigator.navigateToHome(mainContainer, bookingContext);
        }
    }

    private void setupResponsiveListeners() {
        Platform.runLater(() -> {
            if (cardMarginWrapper != null && cardMarginWrapper.getScene() != null) {
                cardMarginWrapper.getScene().widthProperty().addListener((obs, oldW, newW) ->
                        updateResponsiveLayout(newW.doubleValue(), cardMarginWrapper.getScene().getHeight()));
                updateResponsiveLayout(cardMarginWrapper.getScene().getWidth(), cardMarginWrapper.getScene().getHeight());
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
            reflowHeaderGrid(true);
            reflowKpiGrid(true);
            reflowGridSingleColumn(chartsGrid, true, new double[]{62, 38}, chartBookingsCard, cabinLoadCard);
            reflowGridSingleColumn(bottomGrid, true, new double[]{40, 28, 32}, todayFlightsCard, topDestinationsCard, recentBookingsCard);
        } else {
            cardMarginWrapper.setPadding(new Insets(4, 56, 20, 56));
            if (mainContainer != null) {
                mainContainer.getStyleClass().remove("admin-main-card-compact");
                if (!mainContainer.getStyleClass().contains("admin-main-card")) {
                    mainContainer.getStyleClass().add("admin-main-card");
                }
            }
            reflowHeaderGrid(false);
            reflowKpiGrid(false);
            reflowGridSingleColumn(chartsGrid, false, new double[]{62, 38}, chartBookingsCard, cabinLoadCard);
            reflowGridSingleColumn(bottomGrid, false, new double[]{40, 28, 32}, todayFlightsCard, topDestinationsCard, recentBookingsCard);
        }
    }

    private void reflowHeaderGrid(boolean stacked) {
        if (headerGrid == null) return;
        headerGrid.getColumnConstraints().clear();
        if (stacked) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100);
            cc.setHgrow(Priority.ALWAYS);
            headerGrid.getColumnConstraints().add(cc);
            setGridPos(headerTitleBox, 0, 0);
            setGridPos(headerControlsBox, 0, 1);
            if (headerControlsBox != null) headerControlsBox.setAlignment(Pos.CENTER_LEFT);
        } else {
            ColumnConstraints c0 = new ColumnConstraints();
            c0.setHgrow(Priority.ALWAYS);
            ColumnConstraints c1 = new ColumnConstraints();
            c1.setMinWidth(Region.USE_PREF_SIZE);
            headerGrid.getColumnConstraints().addAll(c0, c1);
            setGridPos(headerTitleBox, 0, 0);
            setGridPos(headerControlsBox, 1, 0);
            if (headerControlsBox != null) headerControlsBox.setAlignment(Pos.CENTER_RIGHT);
        }
    }

    private void reflowKpiGrid(boolean twoByTwo) {
        if (kpiGrid == null) return;
        kpiGrid.getColumnConstraints().clear();
        if (twoByTwo) {
            for (int i = 0; i < 2; i++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(50);
                cc.setHgrow(Priority.ALWAYS);
                kpiGrid.getColumnConstraints().add(cc);
            }
            setGridPos(kpiCardBookings, 0, 0);
            setGridPos(kpiCardRevenue, 1, 0);
            setGridPos(kpiCardSeats, 0, 1);
            setGridPos(kpiCardCancellations, 1, 1);
        } else {
            for (int i = 0; i < 4; i++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(25);
                cc.setHgrow(Priority.ALWAYS);
                kpiGrid.getColumnConstraints().add(cc);
            }
            setGridPos(kpiCardBookings, 0, 0);
            setGridPos(kpiCardRevenue, 1, 0);
            setGridPos(kpiCardSeats, 2, 0);
            setGridPos(kpiCardCancellations, 3, 0);
        }
    }

    private void reflowGridSingleColumn(GridPane grid, boolean singleCol, double[] widths, Node... nodes) {
        if (grid == null) return;
        grid.getColumnConstraints().clear();
        if (singleCol) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
            for (int i = 0; i < nodes.length; i++) {
                setGridPos(nodes[i], 0, i);
            }
        } else {
            for (double w : widths) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(w);
                cc.setHgrow(Priority.ALWAYS);
                grid.getColumnConstraints().add(cc);
            }
            for (int i = 0; i < nodes.length; i++) {
                setGridPos(nodes[i], i, 0);
            }
        }
    }

    private void setGridPos(Node node, int col, int row) {
        if (node != null) {
            GridPane.setColumnIndex(node, col);
            GridPane.setRowIndex(node, row);
        }
    }

    public void refresh() {
        if (currentUser == null || currentUser.getRole() != UserRole.ADMIN) {
            showDeniedState("Administrator privileges are required to view the Admin Dashboard.");
            return;
        }
        try {
            showLoadingState();
            AdminDashboardData data = dashboardService.getDashboardData(currentUser, activeRange);
            if (data == null || data.isEmpty()) {
                showEmptyState();
                return;
            }
            populateData(data);
        } catch (SecurityException se) {
            logger.warn("Admin dashboard RBAC denial", se);
            showDeniedState(se.getMessage());
        } catch (Exception e) {
            logger.error("Failed to load admin dashboard data", e);
            showErrorState("Could not retrieve live analytics from the booking database.");
        }
    }

    public void populateData(AdminDashboardData data) {
        this.lastData = data;
        setStateVisibility(true, false, false, false, false);
        if (exportReportBtn != null) {
            exportReportBtn.setDisable(false);
        }

        if (kpiBookingsLabel != null) {
            kpiBookingsLabel.setText(String.format(Locale.US, "%,d", data.getTotalBookings()));
        }
        if (kpiRevenueLabel != null) {
            kpiRevenueLabel.setText(formatRand(data.getTotalRevenue()));
        }
        if (kpiSeatsSoldLabel != null) {
            kpiSeatsSoldLabel.setText(String.format(Locale.US, "%,d", data.getSeatsSold()));
        }
        if (kpiCancellationsLabel != null) {
            kpiCancellationsLabel.setText(String.format(Locale.US, "%,d", data.getCancellationsCount()));
        }

        applyDeltaPill(deltaBookingsLabel, data.getBookingsDeltaPct(), false);
        applyDeltaPill(deltaRevenueLabel, data.getRevenueDeltaPct(), false);
        applyDeltaPill(deltaSeatsLabel, data.getSeatsSoldDeltaPct(), false);
        applyDeltaPill(deltaCancellationsLabel, data.getCancellationsDeltaPct(), true);

        populateSparkline(sparkBookings, data.getDailySeries().stream().map(p -> (double) p.bookingsCount()).toList());
        populateSparkline(sparkRevenue, data.getDailySeries().stream().map(p -> p.revenue().doubleValue()).toList());
        populateSparkline(sparkSeats, data.getDailySeries().stream().map(p -> (double) p.seatsSold()).toList());
        populateSparkline(sparkCancellations, data.getDailySeries().stream().map(p -> (double) p.cancellationsCount()).toList());

        populateBookingsRevenueChart(data.getDailySeries());
        populateCabinLoad(data.getCabinLoad());
        populateTodayFlights(data.getTodayFlights());
        populateTopDestinations(data.getTopDestinations());
        populateRecentBookings(data.getRecentBookings());
    }

    private void applyDeltaPill(Label label, Double deltaPct, boolean inverseBad) {
        if (label == null) return;
        label.getStyleClass().removeAll("delta-pill-up", "delta-pill-down", "delta-pill-neutral");
        if (deltaPct == null || Double.isNaN(deltaPct) || Double.isInfinite(deltaPct)) {
            label.setText("-");
            label.getStyleClass().add("delta-pill-neutral");
            return;
        }
        String sign = deltaPct >= 0 ? "+" : "";
        label.setText(String.format(Locale.US, "%s%.1f%%", sign, deltaPct));
        boolean positive = deltaPct >= 0;
        if (inverseBad) {
            label.getStyleClass().add(positive ? "delta-pill-down" : "delta-pill-up");
        } else {
            label.getStyleClass().add(positive ? "delta-pill-up" : "delta-pill-down");
        }
    }

    private void populateSparkline(Polyline polyline, List<Double> values) {
        if (polyline == null) return;
        polyline.getPoints().clear();
        if (values == null || values.isEmpty()) return;
        double w = 64.0;
        double h = 22.0;
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double span = Math.max(1.0, max - min);
        int n = values.size();
        for (int i = 0; i < n; i++) {
            double x = n == 1 ? w / 2.0 : (i * w) / (n - 1);
            double y = max == min ? h / 2.0 : h - ((values.get(i) - min) / span) * (h - 4.0) - 2.0;
            polyline.getPoints().addAll(x, y);
        }
    }

    private void populateBookingsRevenueChart(List<DailySeriesPoint> points) {
        if (bookingsRevenueChart == null) return;
        bookingsRevenueChart.getData().clear();

        XYChart.Series<String, Number> bookingsSeries = new XYChart.Series<>();
        bookingsSeries.setName("Bookings");
        XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();
        revenueSeries.setName("Revenue");

        long maxBookings = Math.max(1L, points.stream().mapToLong(DailySeriesPoint::bookingsCount).max().orElse(1L));
        BigDecimal maxRevenueBd = points.stream()
                .map(DailySeriesPoint::revenue)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        double maxRevenue = Math.max(1.0, maxRevenueBd.doubleValue());

        if (chartBookingsLegendLabel != null) {
            chartBookingsLegendLabel.setText("Bookings (peak " + maxBookings + ")");
        }
        if (chartRevenueLegendLabel != null) {
            chartRevenueLegendLabel.setText("Revenue (peak " + MoneyFormatter.format(maxRevenueBd) + ")");
        }

        for (DailySeriesPoint p : points) {
            double indexedBookings = (p.bookingsCount() * 100.0) / maxBookings;
            XYChart.Data<String, Number> bData = new XYChart.Data<>(p.dayLabel(), indexedBookings);
            bookingsSeries.getData().add(bData);

            double indexedRev = (p.revenue().doubleValue() * 100.0) / maxRevenue;
            XYChart.Data<String, Number> rData = new XYChart.Data<>(p.dayLabel(), indexedRev);
            revenueSeries.getData().add(rData);
        }

        bookingsRevenueChart.getData().addAll(bookingsSeries, revenueSeries);

        Platform.runLater(() -> {
            for (int i = 0; i < points.size(); i++) {
                DailySeriesPoint pt = points.get(i);
                String tipText = pt.dayLabel() + ": " + pt.bookingsCount() + " bookings · " + formatRand(pt.revenue());
                if (i < bookingsSeries.getData().size() && bookingsSeries.getData().get(i).getNode() != null) {
                    Tooltip.install(bookingsSeries.getData().get(i).getNode(), new Tooltip(tipText));
                }
                if (i < revenueSeries.getData().size() && revenueSeries.getData().get(i).getNode() != null) {
                    Tooltip.install(revenueSeries.getData().get(i).getNode(), new Tooltip(tipText));
                }
            }
        });
    }

    private void populateCabinLoad(CabinLoadBreakdown cabin) {
        if (cabin == null) return;
        if (cabinDonutChart != null) {
            ObservableList<PieChart.Data> slices = FXCollections.observableArrayList(
                    new PieChart.Data("Economy", Math.max(0.0, cabin.economyBooked())),
                    new PieChart.Data("First", Math.max(0.0, cabin.firstBooked())),
                    new PieChart.Data("Open", Math.max(0.0, cabin.openSeats()))
            );
            cabinDonutChart.setData(slices);
        }
        if (cabinCenterPctLabel != null) {
            cabinCenterPctLabel.setText(formatCabinPercent(cabin.soldPercent()));
        }
        if (cabinEconomyPctLabel != null) {
            cabinEconomyPctLabel.setText(formatCabinPercent(cabin.economyPercent()));
        }
        if (cabinFirstPctLabel != null) {
            cabinFirstPctLabel.setText(formatCabinPercent(cabin.firstPercent()));
        }
        if (cabinOpenPctLabel != null) {
            cabinOpenPctLabel.setText(formatCabinPercent(cabin.openPercent()));
        }
        if (seatsHeldLabel != null) {
            seatsHeldLabel.setText(String.valueOf(cabin.seatsHeldRightNow()));
        }
        if (flightsLowFreeLabel != null) {
            flightsLowFreeLabel.setText(String.valueOf(cabin.flightsBelowLowFreePct()));
        }
    }

    private void populateTodayFlights(List<FlightLoadFactor> flights) {
        if (todayFlightsList == null) return;
        todayFlightsList.getChildren().clear();
        int total = flights.size();
        int shown = Math.min(5, total);
        if (todayFlightsCountLabel != null) {
            todayFlightsCountLabel.setText(shown + " of " + total);
        }
        if (flights.isEmpty()) {
            Label none = new Label("No flights scheduled today (SAST)");
            none.getStyleClass().add("admin-row-meta");
            todayFlightsList.getChildren().add(none);
            return;
        }
        for (int i = 0; i < shown; i++) {
            FlightLoadFactor f = flights.get(i);
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            Label flt = new Label(f.flightNumber());
            flt.getStyleClass().add("admin-row-title");
            flt.setMinWidth(Region.USE_PREF_SIZE);

            Label route = new Label(f.origin() + " to " + f.destination());
            route.getStyleClass().add("admin-row-meta");
            route.setMinWidth(Region.USE_PREF_SIZE);
            route.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(route, Priority.ALWAYS);

            Label dep = new Label(f.getFormattedDepartureTime());
            dep.getStyleClass().add("admin-row-title");
            dep.setMinWidth(Region.USE_PREF_SIZE);

            Label status = new Label(f.status());
            status.getStyleClass().add("badge");
            if ("Boarding".equalsIgnoreCase(f.status())) {
                status.getStyleClass().add("badge-gold");
            } else if ("Departed".equalsIgnoreCase(f.status())) {
                status.getStyleClass().add("badge-red");
            } else {
                status.getStyleClass().add("badge-green");
            }
            status.setMinWidth(Region.USE_PREF_SIZE);

            HBox loadBox = buildLoadBarBox(f.loadFactorPct(), 38);
            row.getChildren().addAll(flt, route, dep, status, loadBox);
            todayFlightsList.getChildren().add(row);
        }
    }

    private void populateTopDestinations(List<TopDestinationItem> items) {
        if (topDestinationsList == null) return;
        topDestinationsList.getChildren().clear();
        if (items.isEmpty()) {
            Label none = new Label("No confirmed destination bookings in range");
            none.getStyleClass().add("admin-row-meta");
            topDestinationsList.getChildren().add(none);
            return;
        }
        long maxCount = Math.max(1L, items.stream().mapToLong(TopDestinationItem::bookingsCount).max().orElse(1L));
        for (TopDestinationItem item : items) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            StackPane thumb = new StackPane();
            thumb.setMinSize(34, 34);
            thumb.setPrefSize(34, 34);
            thumb.setMaxSize(34, 34);
            URL url = DestinationPhotoResolver.resolveUrl(item.airportCode()).orElse(null);
            if (url != null) {
                ImageView iv = new ImageView(new Image(url.toExternalForm(), 34, 34, false, true));
                iv.setFitWidth(34);
                iv.setFitHeight(34);
                thumb.getChildren().add(iv);
            } else {
                Label code = new Label(item.airportCode());
                code.getStyleClass().add("avatar-circle-gold");
                thumb.getChildren().add(code);
            }

            VBox info = new VBox(4);
            HBox.setHgrow(info, Priority.ALWAYS);
            HBox topLine = new HBox(8);
            topLine.setAlignment(Pos.CENTER_LEFT);
            Label city = new Label(item.cityName());
            city.getStyleClass().add("admin-row-title");
            city.setMinWidth(Region.USE_PREF_SIZE);
            city.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(city, Priority.ALWAYS);
            Label count = new Label(String.valueOf(item.bookingsCount()));
            count.getStyleClass().add("admin-row-title");
            count.setMinWidth(Region.USE_PREF_SIZE);
            topLine.getChildren().addAll(city, count);

            double pct = (item.bookingsCount() * 100.0) / maxCount;
            StackPane bar = buildProgressTrack(pct, 120, "load-bar-fill-gold");
            info.getChildren().addAll(topLine, bar);

            row.getChildren().addAll(thumb, info);
            topDestinationsList.getChildren().add(row);
        }
    }

    private void populateRecentBookings(List<RecentBookingItem> items) {
        if (recentBookingsList == null) return;
        recentBookingsList.getChildren().clear();
        if (items.isEmpty()) {
            Label none = new Label("No recent bookings in range");
            none.getStyleClass().add("admin-row-meta");
            recentBookingsList.getChildren().add(none);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            RecentBookingItem item = items.get(i);
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label avatar = new Label(initials(item.passengerName()));
            avatar.getStyleClass().add(AVATAR_CLASSES.get(i % AVATAR_CLASSES.size()));

            VBox mid = new VBox(2);
            HBox.setHgrow(mid, Priority.ALWAYS);
            Label name = new Label(item.passengerName());
            name.getStyleClass().add("admin-row-title");
            name.setMinWidth(Region.USE_PREF_SIZE);
            String seatText = (item.seatLabels() != null && !item.seatLabels().isBlank()) ? "seat " + item.seatLabels() : "seat -";
            Label sub = new Label(item.route() + " · " + seatText + " · " + item.bookingReference());
            sub.getStyleClass().add("admin-row-meta");
            sub.setMinWidth(Region.USE_PREF_SIZE);
            mid.getChildren().addAll(name, sub);

            Label amount = new Label(formatRand(item.totalAmount()));
            amount.getStyleClass().add("admin-row-title");
            amount.setMinWidth(Region.USE_PREF_SIZE);

            row.getChildren().addAll(avatar, mid, amount);
            recentBookingsList.getChildren().add(row);
        }
    }

    private HBox buildLoadBarBox(double loadPct, double trackWidth) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        String fillClass = loadPct >= 80.0 ? "load-bar-fill-gold" : "load-bar-fill-blue";
        if (loadPct >= 90.0) fillClass = "load-bar-fill-red";
        StackPane track = buildProgressTrack(loadPct, trackWidth, fillClass);
        Label pctLbl = new Label(String.format(Locale.US, "%.0f%%", loadPct));
        pctLbl.getStyleClass().add("admin-row-meta");
        pctLbl.setMinWidth(Region.USE_PREF_SIZE);
        box.getChildren().addAll(track, pctLbl);
        return box;
    }

    private StackPane buildProgressTrack(double pct, double trackWidth, String fillClass) {
        StackPane track = new StackPane();
        track.getStyleClass().add("load-bar-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setMinWidth(trackWidth);
        track.setPrefWidth(trackWidth);
        track.setMaxWidth(trackWidth);

        Region fill = new Region();
        fill.getStyleClass().add(fillClass);
        double clamped = Math.max(4.0, Math.min(100.0, pct));
        double fillW = (clamped / 100.0) * trackWidth;
        fill.setMinWidth(fillW);
        fill.setPrefWidth(fillW);
        fill.setMaxWidth(fillW);
        track.getChildren().add(fill);
        return track;
    }

    public static String formatCabinPercent(double pct) {
        if (pct > 0.0 && Math.round(pct) == 0) {
            return String.format(Locale.US, "%.1f%%", pct);
        }
        return String.format(Locale.US, "%.0f%%", pct);
    }

    private String initials(String fullName) {
        return NameInitials.of(fullName);
    }

    private String formatRand(BigDecimal amount) {
        return MoneyFormatter.format(amount);
    }

    public void showLoadingState() {
        clearStaleValues();
        setStateVisibility(false, true, false, false, false);
    }

    public void showEmptyState() {
        clearStaleValues();
        setStateVisibility(false, false, true, false, false);
    }

    public void showErrorState(String message) {
        clearStaleValues();
        setStateVisibility(false, false, false, true, false);
        if (errorMessageLabel != null && message != null) {
            errorMessageLabel.setText(message);
        }
    }

    public void showDeniedState(String message) {
        clearStaleValues();
        setStateVisibility(false, false, false, false, true);
        if (deniedMessageLabel != null && message != null) {
            deniedMessageLabel.setText(message);
        }
    }

    private void clearStaleValues() {
        this.lastData = null;
        if (exportReportBtn != null) exportReportBtn.setDisable(true);
        if (kpiBookingsLabel != null) kpiBookingsLabel.setText("0");
        if (kpiRevenueLabel != null) kpiRevenueLabel.setText("R 0.00");
        if (kpiSeatsSoldLabel != null) kpiSeatsSoldLabel.setText("0");
        if (kpiCancellationsLabel != null) kpiCancellationsLabel.setText("0");
        applyDeltaPill(deltaBookingsLabel, null, false);
        applyDeltaPill(deltaRevenueLabel, null, false);
        applyDeltaPill(deltaSeatsLabel, null, false);
        applyDeltaPill(deltaCancellationsLabel, null, false);
        if (todayFlightsCountLabel != null) todayFlightsCountLabel.setText("0 of 0");
        if (todayFlightsList != null) todayFlightsList.getChildren().clear();
        if (topDestinationsList != null) topDestinationsList.getChildren().clear();
        if (recentBookingsList != null) recentBookingsList.getChildren().clear();
        if (bookingsRevenueChart != null) bookingsRevenueChart.getData().clear();
        if (cabinDonutChart != null) cabinDonutChart.getData().clear();
        if (cabinCenterPctLabel != null) cabinCenterPctLabel.setText("0%");
    }

    private void setStateVisibility(boolean populated, boolean loading, boolean empty, boolean error, boolean denied) {
        setVisibleManaged(dashboardScrollPane, populated);
        setVisibleManaged(loadingStateContainer, loading);
        setVisibleManaged(emptyStateContainer, empty);
        setVisibleManaged(errorStateContainer, error);
        setVisibleManaged(deniedStateContainer, denied);
    }

    private void setVisibleManaged(Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private void updateRangeToggleClasses() {
        applySegmentClass(rangeTodayBtn, activeRange == Range.TODAY);
        applySegmentClass(range7DaysBtn, activeRange == Range.SEVEN_DAYS);
        applySegmentClass(range30DaysBtn, activeRange == Range.THIRTY_DAYS);
    }

    private void applySegmentClass(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("admin-segment-btn", "admin-segment-btn-active");
        btn.getStyleClass().add(active ? "admin-segment-btn-active" : "admin-segment-btn");
    }

    @FXML private void handleRangeToday() { activeRange = Range.TODAY; updateRangeToggleClasses(); refresh(); }
    @FXML private void handleRange7Days() { activeRange = Range.SEVEN_DAYS; updateRangeToggleClasses(); refresh(); }
    @FXML private void handleRange30Days() { activeRange = Range.THIRTY_DAYS; updateRangeToggleClasses(); refresh(); }
    @FXML private void handleRefresh() { refresh(); }
    @FXML private void handleViewAllBookings() { Navigator.navigateToAdminBookings(mainContainer, bookingContext); }
    @FXML private void handleNavHome() { Navigator.navigateToHome(mainContainer, bookingContext); }

    @FXML
    private void handleExportReport() {
        if (lastData == null || currentUser == null || currentUser.getRole() != UserRole.ADMIN) return;
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Admin Dashboard Report");
            chooser.setInitialFileName("admin-dashboard-report.csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File target = chooser.showSaveDialog(mainContainer != null && mainContainer.getScene() != null
                    ? mainContainer.getScene().getWindow() : null);
            if (target != null) {
                String csv = dashboardService.exportDashboardCsv(currentUser, lastData);
                Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.error("Failed to export dashboard CSV", e);
            showErrorState("Could not export dashboard report.");
        }
    }

    // Getters for layout & state tests
    public StackPane getCardMarginWrapper() { return cardMarginWrapper; }
    public VBox getMainContainer() { return mainContainer; }
    public ScrollPane getDashboardScrollPane() { return dashboardScrollPane; }
    public VBox getPopulatedContainer() { return populatedContainer; }
    public VBox getLoadingStateContainer() { return loadingStateContainer; }
    public VBox getEmptyStateContainer() { return emptyStateContainer; }
    public VBox getErrorStateContainer() { return errorStateContainer; }
    public VBox getDeniedStateContainer() { return deniedStateContainer; }
    public GridPane getKpiGrid() { return kpiGrid; }
    public Label getGreetingLabel() { return greetingLabel; }
    public Label getDateSubtitleLabel() { return dateSubtitleLabel; }
    public Label getKpiBookingsLabel() { return kpiBookingsLabel; }
    public Label getKpiRevenueLabel() { return kpiRevenueLabel; }
    public Label getKpiSeatsSoldLabel() { return kpiSeatsSoldLabel; }
    public Label getKpiCancellationsLabel() { return kpiCancellationsLabel; }
    public Label getCabinCenterPctLabel() { return cabinCenterPctLabel; }
    public Label getSeatsHeldLabel() { return seatsHeldLabel; }
    public Label getFlightsLowFreeLabel() { return flightsLowFreeLabel; }
    public Label getTodayFlightsCountLabel() { return todayFlightsCountLabel; }
    public VBox getTodayFlightsList() { return todayFlightsList; }
    public VBox getTopDestinationsList() { return topDestinationsList; }
    public VBox getRecentBookingsList() { return recentBookingsList; }
    public AreaChart<String, Number> getBookingsRevenueChart() { return bookingsRevenueChart; }
    public PieChart getCabinDonutChart() { return cabinDonutChart; }
}
