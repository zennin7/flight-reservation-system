// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.controller;

import com.group2.flightres.component.NavBar;
import com.group2.flightres.dao.AdminBookingsDao.AdminBookingsFilter;
import com.group2.flightres.dao.AdminBookingsDao.FlightFilterOption;
import com.group2.flightres.model.AdminBookingRow;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AdminBookingsService;
import com.group2.flightres.service.AdminDashboardService;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.MoneyFormatter;
import com.group2.flightres.util.NameInitials;
import com.group2.flightres.util.Navigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller for the Glass Admin Bookings and Flights screen (Screen 9B).
 */
public class AdminBookingsController {

    private static final Logger logger = LoggerFactory.getLogger(AdminBookingsController.class);
    private static final List<String> AVATAR_CLASSES = List.of(
            "avatar-circle-gold", "avatar-circle-blue", "avatar-circle-green", "avatar-circle-violet"
    );

    @FXML private NavBar navBar;
    @FXML private StackPane cardMarginWrapper;
    @FXML private VBox mainContainer;

    @FXML private GridPane headerGrid;
    @FXML private HBox headerLeftBox;
    @FXML private HBox headerRightBox;
    @FXML private Button tabBookingsBtn;
    @FXML private Button tabFlightsBtn;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button exportCsvButton;

    @FXML private StackPane contentStack;
    @FXML private ScrollPane outerScrollPane;
    @FXML private GridPane bookingsGrid;
    @FXML private VBox filterSidebar;
    @FXML private VBox tableCard;
    @FXML private VBox detailPanel;

    @FXML private Button statusAllBtn;
    @FXML private Button statusConfirmedBtn;
    @FXML private Button statusCancelledBtn;
    @FXML private Button dateAllBtn;
    @FXML private Button date7DaysBtn;
    @FXML private Button date30DaysBtn;
    @FXML private Button dateCustomBtn;
    @FXML private VBox customDateBox;
    @FXML private DatePicker customFromPicker;
    @FXML private DatePicker customToPicker;
    @FXML private Button cabinAllBtn;
    @FXML private Button cabinEconomyBtn;
    @FXML private Button cabinFirstBtn;
    @FXML private ComboBox<FlightFilterOption> flightFilterCombo;
    @FXML private Button resetFiltersBtn;
    @FXML private Button filtersToggleBtn;
    @FXML private HBox bookingsTableHeader;
    @FXML private Label hdrRefLabel;
    @FXML private Label hdrPaxLabel;
    @FXML private Label hdrRouteLabel;
    @FXML private Label hdrSeatLabel;
    @FXML private Label hdrTotalLabel;
    @FXML private Label hdrStatusLabel;

    @FXML private Label totalCountLabel;
    @FXML private Label showingRangeLabel;
    @FXML private ScrollPane tableScrollPane;
    @FXML private VBox rowsContainer;
    @FXML private Label paginationLabel;
    @FXML private Button prevPageButton;
    @FXML private Button nextPageButton;

    @FXML private Label detailStatusBadge;
    @FXML private Label detailPnrLabel;
    @FXML private Label detailAvatarLabel;
    @FXML private Label detailPassengerLabel;
    @FXML private Label detailEmailLabel;
    @FXML private Label detailOriginCodeLabel;
    @FXML private Label detailOriginTimeLabel;
    @FXML private Label detailDestCodeLabel;
    @FXML private Label detailDestTimeLabel;
    @FXML private Label detailFlightDateLabel;
    @FXML private Label detailSeatCabinLabel;
    @FXML private Label detailPaidLabel;
    @FXML private Button downloadBoardingPassBtn;

    @FXML private VBox flightsTabContainer;
    @FXML private Label flightsTabCountLabel;
    @FXML private VBox flightsRowsContainer;

    @FXML private VBox loadingStateContainer;
    @FXML private VBox emptyStateContainer;
    @FXML private VBox errorStateContainer;
    @FXML private VBox deniedStateContainer;
    @FXML private Label errorMessageLabel;
    @FXML private Label deniedMessageLabel;

    private final AdminBookingsService bookingsService;
    private final AdminDashboardService dashboardService;
    private BookingContext bookingContext;
    private User currentUser;

    private String activeTab = "BOOKINGS";
    private String activeStatusFilter = "ALL";
    private String activeDateFilter = "ALL";
    private String activeCabinFilter = "ALL";
    private Long activeFlightId = null;
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean compactFiltersExpanded = false;
    private AdminBookingRow selectedRow;
    private final List<HBox> rowNodes = new ArrayList<>();
    private final List<AdminBookingRow> rowModels = new ArrayList<>();

    public AdminBookingsController() {
        this(new AdminBookingsService(), new AdminDashboardService());
    }

    public AdminBookingsController(AdminBookingsService bookingsService, AdminDashboardService dashboardService) {
        this.bookingsService = bookingsService != null ? bookingsService : new AdminBookingsService();
        this.dashboardService = dashboardService != null ? dashboardService : new AdminDashboardService();
    }

    @FXML
    public void initialize() {
        FontLoader.loadFonts();
        if (bookingsTableHeader != null && rowsContainer != null) {
            bookingsTableHeader.prefWidthProperty().bind(rowsContainer.widthProperty());
            bookingsTableHeader.maxWidthProperty().bind(rowsContainer.widthProperty());
        }
        updateTabClasses();
        updateFilterClasses();
        setupResponsiveListeners();
    }

    public void init(BookingContext context) {
        this.bookingContext = context != null ? context : new BookingContext();
        this.currentUser = this.bookingContext.getCurrentUser();
        updateNavBar();
        populateFlightDropdown();
        loadPage(1);
    }

    public void init(User user) {
        BookingContext ctx = new BookingContext();
        ctx.setCurrentUser(user);
        init(ctx);
    }

    private void updateNavBar() {
        if (navBar != null) {
            navBar.setAdminMode(currentUser != null && currentUser.getRole() == UserRole.ADMIN);
            navBar.setActiveLink("BOOKINGS".equals(activeTab) ? "Bookings" : "Flights");
            navBar.setUserDisplayName(currentUser != null ? currentUser.getFullName() : null);
            navBar.setNavigationHandler(tab -> {
                if ("Dashboard".equals(tab)) {
                    Navigator.navigateToAdminDashboard(mainContainer, bookingContext);
                } else if ("Flights".equals(tab)) {
                    handleShowFlightsTab();
                } else if ("Bookings".equals(tab)) {
                    handleShowBookingsTab();
                } else if ("Home".equals(tab) || "Search".equals(tab)) {
                    Navigator.navigateToHome(mainContainer, bookingContext);
                }
            });
        }
    }

    private void populateFlightDropdown() {
        if (flightFilterCombo == null || currentUser == null || currentUser.getRole() != UserRole.ADMIN) return;
        try {
            flightFilterCombo.getItems().clear();
            flightFilterCombo.getItems().add(new FlightFilterOption(-1L, "All flights"));
            flightFilterCombo.getItems().addAll(bookingsService.listFlightOptions(currentUser));
            flightFilterCombo.getSelectionModel().selectFirst();
        } catch (Exception e) {
            logger.warn("Could not populate flight dropdown", e);
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
            reflowHeader(true);
            reflowBookingsGrid(true);
            setVisibleManaged(filtersToggleBtn, true);
            setVisibleManaged(filterSidebar, compactFiltersExpanded);
        } else {
            cardMarginWrapper.setPadding(new Insets(4, 56, 20, 56));
            if (mainContainer != null) {
                mainContainer.getStyleClass().remove("admin-main-card-compact");
                if (!mainContainer.getStyleClass().contains("admin-main-card")) {
                    mainContainer.getStyleClass().add("admin-main-card");
                }
            }
            reflowHeader(false);
            reflowBookingsGrid(false);
            setVisibleManaged(filtersToggleBtn, false);
            setVisibleManaged(filterSidebar, true);
        }
    }

    private void reflowHeader(boolean stacked) {
        if (headerGrid == null) return;
        headerGrid.getColumnConstraints().clear();
        if (stacked) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100);
            cc.setHgrow(Priority.ALWAYS);
            headerGrid.getColumnConstraints().add(cc);
            setGridPos(headerLeftBox, 0, 0);
            setGridPos(headerRightBox, 0, 1);
            if (headerRightBox != null) headerRightBox.setAlignment(Pos.CENTER_LEFT);
        } else {
            ColumnConstraints c0 = new ColumnConstraints();
            c0.setMinWidth(Region.USE_PREF_SIZE);
            ColumnConstraints c1 = new ColumnConstraints();
            c1.setHgrow(Priority.ALWAYS);
            headerGrid.getColumnConstraints().addAll(c0, c1);
            setGridPos(headerLeftBox, 0, 0);
            setGridPos(headerRightBox, 1, 0);
            if (headerRightBox != null) headerRightBox.setAlignment(Pos.CENTER_RIGHT);
        }
    }

    private void reflowBookingsGrid(boolean stacked) {
        if (bookingsGrid == null) return;
        bookingsGrid.getColumnConstraints().clear();
        if (stacked) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100);
            cc.setHgrow(Priority.ALWAYS);
            bookingsGrid.getColumnConstraints().add(cc);
            setGridPos(filterSidebar, 0, 0);
            setGridPos(tableCard, 0, 1);
            setGridPos(detailPanel, 0, 2);
        } else {
            ColumnConstraints c0 = new ColumnConstraints(142, 148, 180);
            ColumnConstraints c1 = new ColumnConstraints();
            c1.setHgrow(Priority.ALWAYS);
            ColumnConstraints c2 = new ColumnConstraints(220, 228, 290);
            bookingsGrid.getColumnConstraints().addAll(c0, c1, c2);
            setGridPos(filterSidebar, 0, 0);
            setGridPos(tableCard, 1, 0);
            setGridPos(detailPanel, 2, 0);
        }
    }

    private void setGridPos(Node node, int col, int row) {
        if (node != null) {
            GridPane.setColumnIndex(node, col);
            GridPane.setRowIndex(node, row);
        }
    }

    public AdminBookingsFilter currentFilter() {
        String search = searchField != null ? searchField.getText() : "";
        LocalDate from = customFromPicker != null ? customFromPicker.getValue() : null;
        LocalDate to = customToPicker != null ? customToPicker.getValue() : null;
        return new AdminBookingsFilter(search, activeStatusFilter, activeDateFilter, from, to, activeCabinFilter, activeFlightId);
    }

    public void loadPage(int page) {
        if (currentUser == null || currentUser.getRole() != UserRole.ADMIN) {
            showDeniedState("Administrator privileges are required to view Admin Bookings and Flights.");
            return;
        }
        try {
            showLoadingState();
            if ("FLIGHTS".equals(activeTab)) {
                AdminDashboardData dash = dashboardService.getDashboardData(currentUser, AdminDashboardData.Range.SEVEN_DAYS);
                populateFlightsTab(dash != null ? dash.getUpcomingFlights() : List.of());
                return;
            }
            AdminBookingsPage pageData = bookingsService.searchBookings(currentUser, currentFilter(), page);
            this.currentPage = pageData.getPage();
            this.totalPages = pageData.getTotalPages();
            if (pageData.getTotalCount() == 0 || pageData.getRows().isEmpty()) {
                showEmptyState();
                return;
            }
            populateRows(pageData);
        } catch (SecurityException se) {
            logger.warn("Admin bookings RBAC denial", se);
            showDeniedState(se.getMessage());
        } catch (Exception e) {
            logger.error("Failed to load admin bookings", e);
            showErrorState("Could not retrieve bookings list. Please try again.");
        }
    }

    public void populateRows(AdminBookingsPage pageData) {
        setStateVisibility(true, false, false, false, false, false);
        if (exportCsvButton != null) exportCsvButton.setDisable(false);

        long totalCount = pageData.getTotalCount();
        int fromIdx = ((pageData.getPage() - 1) * pageData.getPageSize()) + 1;
        int toIdx = fromIdx + pageData.getRows().size() - 1;
        if (totalCountLabel != null) {
            totalCountLabel.setText(totalCount + (totalCount == 1 ? " booking" : " bookings"));
        }
        if (showingRangeLabel != null) {
            showingRangeLabel.setText("Showing " + fromIdx + " to " + toIdx);
        }

        if (rowsContainer == null) return;
        rowsContainer.getChildren().clear();
        rowNodes.clear();
        rowModels.clear();

        List<AdminBookingRow> rows = pageData.getRows();
        for (int i = 0; i < rows.size(); i++) {
            AdminBookingRow row = rows.get(i);
            HBox rowNode = buildBookingTableRow(row, i);
            rowNodes.add(rowNode);
            rowModels.add(row);
            rowsContainer.getChildren().add(rowNode);
        }

        if (paginationLabel != null) {
            paginationLabel.setText("Page " + pageData.getPage() + " of " + pageData.getTotalPages());
        }
        if (prevPageButton != null) prevPageButton.setDisable(pageData.getPage() <= 1);
        if (nextPageButton != null) nextPageButton.setDisable(pageData.getPage() >= pageData.getTotalPages());

        if (!rows.isEmpty()) {
            selectRow(rows.get(0));
        }
    }

    private void populateFlightsTab(List<FlightLoadFactor> flights) {
        setStateVisibility(false, true, false, false, false, false);
        if (flightsTabCountLabel != null) {
            flightsTabCountLabel.setText(flights.size() + (flights.size() == 1 ? " flight" : " flights"));
        }
        if (flightsRowsContainer == null) return;
        flightsRowsContainer.getChildren().clear();
        if (flights.isEmpty()) {
            Label none = new Label("No upcoming flights available.");
            none.getStyleClass().add("admin-row-meta");
            flightsRowsContainer.getChildren().add(none);
            return;
        }
        for (FlightLoadFactor f : flights) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("admin-row-card");

            Label flt = new Label(f.flightNumber());
            flt.getStyleClass().add("admin-row-gold");
            flt.setMinWidth(Region.USE_PREF_SIZE);
            flt.setPrefWidth(84);

            Label route = new Label(f.getRoute());
            route.getStyleClass().add("admin-row-title");
            route.setMinWidth(Region.USE_PREF_SIZE);
            route.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(route, Priority.ALWAYS);

            Label dep = new Label(f.getFormattedDepartureDate() + " " + f.getFormattedDepartureTime());
            dep.getStyleClass().add("admin-row-meta");
            dep.setMinWidth(Region.USE_PREF_SIZE);
            dep.setPrefWidth(128);

            Label status = new Label(f.status());
            status.getStyleClass().add("badge");
            status.getStyleClass().add("Boarding".equalsIgnoreCase(f.status()) ? "badge-gold" : "badge-green");
            status.setMinWidth(Region.USE_PREF_SIZE);
            status.setPrefWidth(88);

            Label seats = new Label(f.bookedSeats() + " / " + f.totalSeats());
            seats.getStyleClass().add("admin-row-title");
            seats.setMinWidth(Region.USE_PREF_SIZE);
            seats.setPrefWidth(86);

            Label load = new Label(String.format(Locale.US, "%.0f%%", f.loadFactorPct()));
            load.getStyleClass().add("admin-row-gold");
            load.setMinWidth(Region.USE_PREF_SIZE);
            load.setPrefWidth(70);

            row.getChildren().addAll(flt, route, dep, status, seats, load);
            flightsRowsContainer.getChildren().add(row);
        }
    }

    public HBox buildBookingTableRow(AdminBookingRow row, int idx) {
        HBox box = new HBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("admin-row-card");
        box.setFocusTraversable(true);

        Label pnr = new Label(row.bookingReference());
        pnr.getStyleClass().add("admin-row-gold");
        pnr.setMinWidth(Region.USE_PREF_SIZE);
        pnr.setPrefWidth(60);

        HBox paxCell = new HBox(6);
        paxCell.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(paxCell, Priority.ALWAYS);
        Label avatar = new Label(initials(row.passengerName()));
        avatar.getStyleClass().add(AVATAR_CLASSES.get(idx % AVATAR_CLASSES.size()));
        Label passenger = new Label(row.passengerName());
        passenger.getStyleClass().add("admin-row-title");
        passenger.setMinWidth(Region.USE_PREF_SIZE);
        paxCell.getChildren().addAll(avatar, passenger);

        Label route = new Label(row.originCode() + " to " + row.destinationCode());
        route.getStyleClass().add("admin-row-meta");
        route.setMinWidth(Region.USE_PREF_SIZE);
        route.setPrefWidth(58);

        Label seat = new Label(row.seatLabels() != null && !row.seatLabels().isBlank() ? row.seatLabels() : "-");
        seat.getStyleClass().add("admin-row-title");
        seat.setMinWidth(Region.USE_PREF_SIZE);
        seat.setPrefWidth(28);

        Label total = new Label(MoneyFormatter.format(row.totalAmount()));
        total.getStyleClass().add("admin-row-title");
        total.setMinWidth(Region.USE_PREF_SIZE);
        total.setPrefWidth(68);

        String displayStatus = formatStatusLabel(row.status());
        Label status = new Label(displayStatus);
        status.getStyleClass().add("badge");
        if ("CONFIRMED".equalsIgnoreCase(row.status())) {
            status.getStyleClass().add("badge-green");
        } else if ("CANCELLED".equalsIgnoreCase(row.status())) {
            status.getStyleClass().add("badge-red");
        } else {
            status.getStyleClass().add("badge-gold");
        }
        status.setMinWidth(Region.USE_PREF_SIZE);
        status.setPrefWidth(78);

        box.setOnMouseClicked(e -> selectRow(row));
        box.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER, SPACE -> selectRow(row);
                case DOWN -> moveSelection(1);
                case UP -> moveSelection(-1);
                default -> {}
            }
        });
        box.setAccessibleText("Booking " + row.bookingReference() + " for " + row.passengerName());
        box.getChildren().addAll(pnr, paxCell, route, seat, total, status);
        return box;
    }

    private void moveSelection(int delta) {
        if (rowModels.isEmpty()) return;
        int cur = rowModels.indexOf(selectedRow);
        int next = Math.max(0, Math.min(rowModels.size() - 1, cur + delta));
        selectRow(rowModels.get(next));
        if (next < rowNodes.size()) {
            rowNodes.get(next).requestFocus();
        }
    }

    public void selectRow(AdminBookingRow row) {
        this.selectedRow = row;
        for (int i = 0; i < rowNodes.size(); i++) {
            HBox node = rowNodes.get(i);
            boolean isSel = i < rowModels.size() && rowModels.get(i).equals(row);
            node.getStyleClass().removeAll("admin-row-card", "admin-row-card-selected");
            node.getStyleClass().add(isSel ? "admin-row-card-selected" : "admin-row-card");
        }
        if (row == null) return;
        if (detailPnrLabel != null) detailPnrLabel.setText(row.bookingReference());
        if (detailStatusBadge != null) {
            detailStatusBadge.setText(formatStatusLabel(row.status()));
            detailStatusBadge.getStyleClass().removeAll("badge-green", "badge-red", "badge-gold");
            detailStatusBadge.getStyleClass().add("CANCELLED".equalsIgnoreCase(row.status()) ? "badge-red" : "badge-green");
        }
        if (detailAvatarLabel != null) detailAvatarLabel.setText(initials(row.passengerName()));
        if (detailPassengerLabel != null) detailPassengerLabel.setText(row.passengerName());
        if (detailEmailLabel != null) detailEmailLabel.setText(row.passengerEmail() != null ? row.passengerEmail() : "—");
        if (detailOriginCodeLabel != null) detailOriginCodeLabel.setText(row.originCode());
        if (detailOriginTimeLabel != null) detailOriginTimeLabel.setText(row.getFormattedDepartureTime());
        if (detailDestCodeLabel != null) detailDestCodeLabel.setText(row.destinationCode());
        if (detailDestTimeLabel != null) detailDestTimeLabel.setText(row.getFormattedArrivalTime());
        if (detailFlightDateLabel != null) {
            detailFlightDateLabel.setText(row.flightNumber() + ", " + row.getFormattedDepartureDate());
        }
        if (detailSeatCabinLabel != null) {
            String cabin = row.seatClass() == SeatClass.FIRST ? "First" : "Economy";
            String seat = (row.seatLabels() != null && !row.seatLabels().isBlank()) ? row.seatLabels() : "-";
            detailSeatCabinLabel.setText(seat + ", " + cabin);
        }
        if (detailPaidLabel != null) {
            detailPaidLabel.setText(MoneyFormatter.format(row.totalAmount()));
        }
        if (downloadBoardingPassBtn != null) {
            downloadBoardingPassBtn.setDisable(false);
        }
    }

    private String formatStatusLabel(String status) {
        if (status == null || status.isBlank()) return "Confirmed";
        String lower = status.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String initials(String fullName) {
        return NameInitials.of(fullName);
    }

    public void showLoadingState() {
        clearStaleValues();
        setStateVisibility(false, false, true, false, false, false);
    }

    public void showEmptyState() {
        clearStaleValues();
        setStateVisibility(false, false, false, true, false, false);
    }

    public void showErrorState(String message) {
        clearStaleValues();
        setStateVisibility(false, false, false, false, true, false);
        if (errorMessageLabel != null && message != null) errorMessageLabel.setText(message);
    }

    public void showDeniedState(String message) {
        clearStaleValues();
        setStateVisibility(false, false, false, false, false, true);
        if (deniedMessageLabel != null && message != null) deniedMessageLabel.setText(message);
    }

    private void clearStaleValues() {
        this.selectedRow = null;
        if (exportCsvButton != null) exportCsvButton.setDisable(true);
        if (downloadBoardingPassBtn != null) downloadBoardingPassBtn.setDisable(true);
        if (totalCountLabel != null) totalCountLabel.setText("0 bookings");
        if (showingRangeLabel != null) showingRangeLabel.setText("Showing 0 to 0");
        if (paginationLabel != null) paginationLabel.setText("Page 1 of 1");
        if (rowsContainer != null) rowsContainer.getChildren().clear();
        rowNodes.clear();
        rowModels.clear();
        if (detailPnrLabel != null) detailPnrLabel.setText("—");
        if (detailPassengerLabel != null) detailPassengerLabel.setText("—");
        if (detailEmailLabel != null) detailEmailLabel.setText("—");
        if (detailPaidLabel != null) detailPaidLabel.setText(MoneyFormatter.format(java.math.BigDecimal.ZERO));
    }

    private void setStateVisibility(boolean bookings, boolean flights, boolean loading, boolean empty, boolean error, boolean denied) {
        setVisibleManaged(outerScrollPane, bookings);
        setVisibleManaged(flightsTabContainer, flights);
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

    private void updateTabClasses() {
        applySegmentClass(tabBookingsBtn, "BOOKINGS".equals(activeTab));
        applySegmentClass(tabFlightsBtn, "FLIGHTS".equals(activeTab));
    }

    private void applySegmentClass(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("admin-segment-btn", "admin-segment-btn-active");
        btn.getStyleClass().add(active ? "admin-segment-btn-active" : "admin-segment-btn");
    }

    private void updateFilterClasses() {
        applyPillClass(statusAllBtn, "ALL".equals(activeStatusFilter));
        applyPillClass(statusConfirmedBtn, "CONFIRMED".equals(activeStatusFilter));
        applyPillClass(statusCancelledBtn, "CANCELLED".equals(activeStatusFilter));

        applyChoiceClass(dateAllBtn, "ALL".equals(activeDateFilter));
        applyChoiceClass(date7DaysBtn, "NEXT_7_DAYS".equals(activeDateFilter));
        applyChoiceClass(date30DaysBtn, "NEXT_30_DAYS".equals(activeDateFilter));
        applyChoiceClass(dateCustomBtn, "CUSTOM".equals(activeDateFilter));
        setVisibleManaged(customDateBox, "CUSTOM".equals(activeDateFilter));

        applyPillClass(cabinAllBtn, "ALL".equals(activeCabinFilter));
        applyPillClass(cabinEconomyBtn, "ECONOMY".equals(activeCabinFilter));
        applyPillClass(cabinFirstBtn, "FIRST".equals(activeCabinFilter));
    }

    private void applyPillClass(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("admin-filter-pill", "admin-filter-pill-active");
        btn.getStyleClass().add(active ? "admin-filter-pill-active" : "admin-filter-pill");
    }

    private void applyChoiceClass(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("admin-filter-choice-btn", "admin-filter-choice-btn-active");
        btn.getStyleClass().add(active ? "admin-filter-choice-btn-active" : "admin-filter-choice-btn");
    }

    @FXML private void handleShowBookingsTab() { activeTab = "BOOKINGS"; updateTabClasses(); updateNavBar(); loadPage(1); }
    @FXML private void handleShowFlightsTab() { activeTab = "FLIGHTS"; updateTabClasses(); updateNavBar(); loadPage(1); }
    @FXML private void handleSearch() { loadPage(1); }
    @FXML private void handleRetry() { loadPage(currentPage); }
    @FXML private void handlePrevPage() { if (currentPage > 1) loadPage(currentPage - 1); }
    @FXML private void handleNextPage() { if (currentPage < totalPages) loadPage(currentPage + 1); }

    @FXML private void handleStatusAll() { activeStatusFilter = "ALL"; updateFilterClasses(); loadPage(1); }
    @FXML private void handleStatusConfirmed() { activeStatusFilter = "CONFIRMED"; updateFilterClasses(); loadPage(1); }
    @FXML private void handleStatusCancelled() { activeStatusFilter = "CANCELLED"; updateFilterClasses(); loadPage(1); }

    @FXML private void handleDateAll() { activeDateFilter = "ALL"; updateFilterClasses(); loadPage(1); }
    @FXML private void handleDate7Days() { activeDateFilter = "NEXT_7_DAYS"; updateFilterClasses(); loadPage(1); }
    @FXML private void handleDate30Days() { activeDateFilter = "NEXT_30_DAYS"; updateFilterClasses(); loadPage(1); }
    @FXML private void handleDateCustom() { activeDateFilter = "CUSTOM"; updateFilterClasses(); }
    @FXML private void handleApplyCustomDate() { loadPage(1); }

    @FXML private void handleCabinAll() { activeCabinFilter = "ALL"; updateFilterClasses(); loadPage(1); }
    @FXML private void handleCabinEconomy() { activeCabinFilter = "ECONOMY"; updateFilterClasses(); loadPage(1); }
    @FXML private void handleCabinFirst() { activeCabinFilter = "FIRST"; updateFilterClasses(); loadPage(1); }

    @FXML
    private void handleFlightFilterChanged() {
        if (flightFilterCombo == null) return;
        FlightFilterOption sel = flightFilterCombo.getSelectionModel().getSelectedItem();
        activeFlightId = (sel != null && sel.flightId() > 0) ? sel.flightId() : null;
        loadPage(1);
    }

    @FXML
    private void handleResetFilters() {
        if (searchField != null) searchField.clear();
        activeStatusFilter = "ALL";
        activeDateFilter = "ALL";
        activeCabinFilter = "ALL";
        activeFlightId = null;
        if (customFromPicker != null) customFromPicker.setValue(null);
        if (customToPicker != null) customToPicker.setValue(null);
        if (flightFilterCombo != null && !flightFilterCombo.getItems().isEmpty()) {
            flightFilterCombo.getSelectionModel().selectFirst();
        }
        updateFilterClasses();
        loadPage(1);
    }

    @FXML
    private void handleExportCsv() {
        if (currentUser == null || currentUser.getRole() != UserRole.ADMIN) return;
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Filtered Bookings CSV");
            chooser.setInitialFileName("admin-bookings.csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File target = chooser.showSaveDialog(mainContainer != null && mainContainer.getScene() != null
                    ? mainContainer.getScene().getWindow() : null);
            if (target != null) {
                String csv = bookingsService.exportFilteredCsv(currentUser, currentFilter());
                Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.error("Failed to export bookings CSV", e);
            showErrorState("Could not export bookings CSV.");
        }
    }

    @FXML
    private void handleDownloadBoardingPass() {
        if (selectedRow == null || currentUser == null || currentUser.getRole() != UserRole.ADMIN) return;
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Boarding Pass PDF");
            chooser.setInitialFileName("boarding-pass-" + selectedRow.bookingReference() + ".pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File target = chooser.showSaveDialog(mainContainer != null && mainContainer.getScene() != null
                    ? mainContainer.getScene().getWindow() : null);
            if (target != null) {
                bookingsService.generateBoardingPassPdf(currentUser, selectedRow, target);
            }
        } catch (Exception e) {
            logger.error("Failed to generate boarding pass PDF", e);
            showErrorState("Could not generate boarding pass PDF.");
        }
    }

    @FXML
    private void handleToggleCompactFilters() {
        compactFiltersExpanded = !compactFiltersExpanded;
        setVisibleManaged(filterSidebar, compactFiltersExpanded);
    }

    @FXML private void handleNavHome() { Navigator.navigateToHome(mainContainer, bookingContext); }

    // Getters for layout & state tests
    public StackPane getCardMarginWrapper() { return cardMarginWrapper; }
    public VBox getMainContainer() { return mainContainer; }
    public ScrollPane getOuterScrollPane() { return outerScrollPane; }
    public ScrollPane getTableScrollPane() { return tableScrollPane; }
    public GridPane getBookingsGrid() { return bookingsGrid; }
    public VBox getFilterSidebar() { return filterSidebar; }
    public VBox getTableCard() { return tableCard; }
    public VBox getDetailPanel() { return detailPanel; }
    public VBox getRowsContainer() { return rowsContainer; }
    public Button getFiltersToggleBtn() { return filtersToggleBtn; }
    public HBox getBookingsTableHeader() { return bookingsTableHeader; }
    public Label getHdrRefLabel() { return hdrRefLabel; }
    public Label getHdrRouteLabel() { return hdrRouteLabel; }
    public Label getHdrSeatLabel() { return hdrSeatLabel; }
    public Label getHdrTotalLabel() { return hdrTotalLabel; }
    public Label getHdrStatusLabel() { return hdrStatusLabel; }
    public VBox getFlightsTabContainer() { return flightsTabContainer; }
    public VBox getFlightsRowsContainer() { return flightsRowsContainer; }
    public VBox getLoadingStateContainer() { return loadingStateContainer; }
    public VBox getEmptyStateContainer() { return emptyStateContainer; }
    public VBox getErrorStateContainer() { return errorStateContainer; }
    public VBox getDeniedStateContainer() { return deniedStateContainer; }
    public Label getTotalCountLabel() { return totalCountLabel; }
    public Label getShowingRangeLabel() { return showingRangeLabel; }
    public Label getPaginationLabel() { return paginationLabel; }
    public Label getDetailPnrLabel() { return detailPnrLabel; }
    public Label getDetailPaidLabel() { return detailPaidLabel; }
    public AdminBookingRow getSelectedRow() { return selectedRow; }
    public Button getTabFlightsBtn() { return tabFlightsBtn; }
}
