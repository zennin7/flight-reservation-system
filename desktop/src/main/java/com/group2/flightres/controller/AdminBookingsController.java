// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.controller;

import com.group2.flightres.component.TicketView;
import com.group2.flightres.model.AdminBookingRow;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.AdminBookingsService;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.Navigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
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
 * Controller for Screen 9: Glass Admin Bookings.
 * Provides server-side search, status filtering, 25-row pagination, RBAC, and TicketView detail drawer.
 */
public class AdminBookingsController {

    private static final Logger logger = LoggerFactory.getLogger(AdminBookingsController.class);

    @FXML private StackPane cardMarginWrapper;
    @FXML private VBox mainContainer;
    @FXML private HBox adminNavLinksBox;
    @FXML private Label roleBadgeLabel;
    @FXML private Label avatarInitialsLabel;

    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private HBox filterBar;
    @FXML private Button filterAllBtn;
    @FXML private Button filterConfirmedBtn;
    @FXML private Button filterCancelledBtn;
    @FXML private Label summaryStatsLabel;

    @FXML private HBox splitBody;
    @FXML private ScrollPane tableScrollPane;
    @FXML private VBox rowsContainer;
    @FXML private Label paginationLabel;
    @FXML private Button prevPageButton;
    @FXML private Button nextPageButton;

    @FXML private VBox detailDrawer;
    @FXML private Label selectedPnrLabel;
    @FXML private TicketView ticketView;
    @FXML private Label drawerPlaceholderLabel;

    @FXML private VBox emptyStateContainer;
    @FXML private VBox errorStateContainer;
    @FXML private Label errorMessageLabel;
    @FXML private VBox deniedStateContainer;
    @FXML private Label deniedMessageLabel;
    @FXML private Button retryButton;

    private final AdminBookingsService bookingsService;
    private BookingContext bookingContext;
    private User currentUser;

    private String activeStatusFilter = "ALL";
    private int currentPage = 1;
    private int totalPages = 1;
    private AdminBookingRow selectedRow;

    public AdminBookingsController() {
        this(new AdminBookingsService());
    }

    public AdminBookingsController(AdminBookingsService bookingsService) {
        this.bookingsService = bookingsService != null ? bookingsService : new AdminBookingsService();
    }

    public void init(BookingContext context) {
        this.bookingContext = context != null ? context : new BookingContext();
        this.currentUser = this.bookingContext.getCurrentUser();
        updateHeaderUser();
        loadPage(1);
    }

    public void init(User user) {
        BookingContext ctx = new BookingContext();
        ctx.setCurrentUser(user);
        init(ctx);
    }

    @FXML
    public void initialize() {
        FontLoader.loadFonts();
        updateFilterPillClasses();
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
            if (detailDrawer != null) {
                detailDrawer.setPrefWidth(320);
                detailDrawer.setMinWidth(260);
            }
        } else {
            cardMarginWrapper.setPadding(new Insets(4, 56, 20, 56));
            if (mainContainer != null) {
                mainContainer.getStyleClass().remove("admin-main-card-compact");
                if (!mainContainer.getStyleClass().contains("admin-main-card")) {
                    mainContainer.getStyleClass().add("admin-main-card");
                }
            }
            if (detailDrawer != null) {
                detailDrawer.setPrefWidth(400);
                detailDrawer.setMinWidth(320);
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

    public void loadPage(int page) {
        try {
            String term = searchField != null ? searchField.getText() : "";
            AdminBookingsPage result = bookingsService.searchBookings(currentUser, term, activeStatusFilter, page);
            this.currentPage = result.getPage();
            this.totalPages = result.getTotalPages();

            if (summaryStatsLabel != null) {
                summaryStatsLabel.setText(String.format(Locale.US, "%d bookings · R %,.2f",
                        result.getTotalCount(), result.getTotalAmountSum()));
            }

            if (result.getRows().isEmpty()) {
                showEmptyState();
            } else {
                populateRows(result);
            }
        } catch (SecurityException se) {
            logger.warn("Admin Bookings RBAC denied: {}", se.getMessage());
            showDeniedState(se.getMessage());
        } catch (Exception e) {
            logger.error("Failed to load admin bookings", e);
            showErrorState("Could not retrieve bookings list. Please try again.");
        }
    }

    public void populateRows(AdminBookingsPage pageData) {
        setStateVisibility(true, false, false, false);
        if (rowsContainer == null) return;
        rowsContainer.getChildren().clear();

        List<AdminBookingRow> rows = pageData.getRows();
        for (AdminBookingRow row : rows) {
            Node rowNode = buildBookingTableRow(row);
            rowsContainer.getChildren().add(rowNode);
        }

        if (paginationLabel != null) {
            paginationLabel.setText("Page " + pageData.getPage() + " of " + pageData.getTotalPages());
        }
        if (prevPageButton != null) {
            prevPageButton.setDisable(pageData.getPage() <= 1);
        }
        if (nextPageButton != null) {
            nextPageButton.setDisable(pageData.getPage() >= pageData.getTotalPages());
        }

        if (!rows.isEmpty()) {
            selectRow(rows.get(0));
        }
    }

    public Node buildBookingTableRow(AdminBookingRow row) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("admin-row-card");
        box.setFocusTraversable(true);

        Label pnr = new Label(row.bookingReference());
        pnr.getStyleClass().add("admin-row-gold");
        pnr.setMinWidth(Region.USE_PREF_SIZE);
        pnr.setPrefWidth(68);

        Label passenger = new Label(row.passengerName());
        passenger.getStyleClass().add("admin-row-title");
        passenger.setMinWidth(Region.USE_PREF_SIZE);
        passenger.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(passenger, Priority.ALWAYS);

        Label route = new Label(row.getRoute());
        route.getStyleClass().add("admin-row-title");
        route.setMinWidth(Region.USE_PREF_SIZE);
        route.setPrefWidth(82);

        Label dep = new Label(row.getFormattedDepartureDateTime());
        dep.getStyleClass().add("admin-row-meta");
        dep.setMinWidth(Region.USE_PREF_SIZE);

        Label seat = new Label(row.seatLabels() != null && !row.seatLabels().isBlank() ? row.seatLabels() : "-");
        seat.getStyleClass().add("admin-row-gold");
        seat.setMinWidth(Region.USE_PREF_SIZE);
        seat.setPrefWidth(36);

        Label total = new Label(String.format(Locale.US, "R %,.2f", row.totalAmount()));
        total.getStyleClass().add("admin-row-title");
        total.setMinWidth(Region.USE_PREF_SIZE);

        Label status = new Label(row.status());
        status.getStyleClass().add("badge");
        if ("CONFIRMED".equalsIgnoreCase(row.status())) {
            status.getStyleClass().add("badge-green");
        } else if ("CANCELLED".equalsIgnoreCase(row.status())) {
            status.getStyleClass().add("badge-red");
        } else {
            status.getStyleClass().add("badge-gold");
        }
        status.setMinWidth(Region.USE_PREF_SIZE);

        box.setOnMouseClicked(e -> selectRow(row));
        box.setAccessibleText("Booking " + row.bookingReference() + " for " + row.passengerName());
        box.getChildren().addAll(pnr, passenger, route, dep, seat, total, status);
        return box;
    }

    public void selectRow(AdminBookingRow row) {
        this.selectedRow = row;
        if (selectedPnrLabel != null) {
            selectedPnrLabel.setText(row != null ? row.bookingReference() : "");
        }
        if (ticketView != null && row != null) {
            ticketView.setBooking(row.toTicketData());
            ticketView.setVisible(true);
            ticketView.setManaged(true);
            if (drawerPlaceholderLabel != null) {
                drawerPlaceholderLabel.setVisible(false);
                drawerPlaceholderLabel.setManaged(false);
            }
        }
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
        if (splitBody != null) {
            splitBody.setVisible(populated);
            splitBody.setManaged(populated);
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

    private void updateFilterPillClasses() {
        applyFilterClass(filterAllBtn, "ALL".equals(activeStatusFilter));
        applyFilterClass(filterConfirmedBtn, "CONFIRMED".equals(activeStatusFilter));
        applyFilterClass(filterCancelledBtn, "CANCELLED".equals(activeStatusFilter));
    }

    private void applyFilterClass(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("admin-filter-pill", "admin-filter-pill-active");
        btn.getStyleClass().add(active ? "admin-filter-pill-active" : "admin-filter-pill");
    }

    @FXML private void handleSearch() { loadPage(1); }
    @FXML private void handleRefresh() { loadPage(currentPage); }
    @FXML private void handleRetry() { loadPage(currentPage); }
    @FXML private void handlePrevPage() { if (currentPage > 1) loadPage(currentPage - 1); }
    @FXML private void handleNextPage() { if (currentPage < totalPages) loadPage(currentPage + 1); }

    @FXML private void handleFilterAll() { activeStatusFilter = "ALL"; updateFilterPillClasses(); loadPage(1); }
    @FXML private void handleFilterConfirmed() { activeStatusFilter = "CONFIRMED"; updateFilterPillClasses(); loadPage(1); }
    @FXML private void handleFilterCancelled() { activeStatusFilter = "CANCELLED"; updateFilterPillClasses(); loadPage(1); }

    @FXML
    private void handleResetFilters() {
        if (searchField != null) searchField.clear();
        activeStatusFilter = "ALL";
        updateFilterPillClasses();
        loadPage(1);
    }

    @FXML private void handleNavHome() { Navigator.navigateToHome(mainContainer, bookingContext); }
    @FXML private void handleNavFlights() { Navigator.navigateToSearch(mainContainer, bookingContext); }
    @FXML private void handleNavDashboard() { Navigator.navigateToAdminDashboard(mainContainer, bookingContext); }

    @FXML
    private void handleNavAvatar() {
        if (bookingContext != null && bookingContext.getCurrentUser() != null) {
            bookingContext.setCurrentUser(null);
        }
        Navigator.navigateToHome(mainContainer, bookingContext);
    }

    // Getters for tests
    public StackPane getCardMarginWrapper() { return cardMarginWrapper; }
    public VBox getMainContainer() { return mainContainer; }
    public HBox getSplitBody() { return splitBody; }
    public VBox getRowsContainer() { return rowsContainer; }
    public VBox getEmptyStateContainer() { return emptyStateContainer; }
    public VBox getErrorStateContainer() { return errorStateContainer; }
    public VBox getDeniedStateContainer() { return deniedStateContainer; }
    public Label getSummaryStatsLabel() { return summaryStatsLabel; }
    public Label getPaginationLabel() { return paginationLabel; }
    public Label getSelectedPnrLabel() { return selectedPnrLabel; }
    public TicketView getTicketView() { return ticketView; }
    public TextField getSearchField() { return searchField; }
    public HBox getAdminNavLinksBox() { return adminNavLinksBox; }
    public AdminBookingRow getSelectedRow() { return selectedRow; }
}
