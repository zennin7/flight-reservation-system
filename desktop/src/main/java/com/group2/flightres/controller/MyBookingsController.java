package com.group2.flightres.controller;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.Motion;
import com.group2.flightres.util.Navigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller for Traveller Screen: My Bookings.
 * Displays Upcoming, Past, and Cancelled bookings populated from real database queries.
 */
public class MyBookingsController {

    private static final Logger logger = LoggerFactory.getLogger(MyBookingsController.class);

    public enum Tab {
        UPCOMING, PAST, CANCELLED
    }

    // FXML Injected controls
    @FXML private StackPane cardMarginWrapper;
    @FXML private VBox mainContainer;
    @FXML private Label dashboardTitleLabel;
    @FXML private Label dashboardSubtitleLabel;

    @FXML private HBox tabsBar;
    @FXML private Button upcomingTabBtn;
    @FXML private Button pastTabBtn;
    @FXML private Button cancelledTabBtn;

    @FXML private StackPane contentStack;
    @FXML private ScrollPane bookingsScrollPane;
    @FXML private VBox cardsContainer;

    @FXML private VBox emptyStateContainer;
    @FXML private Label emptyTitleLabel;
    @FXML private Label emptySubtitleLabel;
    @FXML private Button searchFlightsButton;

    @FXML private VBox errorStateContainer;
    @FXML private Label errorTitleLabel;
    @FXML private Label errorMessageLabel;
    @FXML private Button retryButton;

    @FXML private Label avatarInitialsLabel;
    @FXML private StackPane avatarCircle;

    private final BookingService bookingService;
    private final Clock clock;

    private BookingContext bookingContext;
    private Runnable onSearchFlights;

    private Tab activeTab = Tab.UPCOMING;
    private final List<BookingSummary> allBookings = new ArrayList<>();

    public MyBookingsController() {
        this(new BookingService(), Clock.systemUTC());
    }

    public MyBookingsController(BookingService bookingService) {
        this(bookingService, Clock.systemUTC());
    }

    public MyBookingsController(BookingService bookingService, Clock clock) {
        this.bookingService = bookingService != null ? bookingService : new BookingService();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void init(BookingContext context, Runnable onSearchFlights) {
        this.bookingContext = context != null ? context : new BookingContext();
        this.onSearchFlights = onSearchFlights;
        updateAvatar();
        loadBookings();
    }

    @FXML
    public void initialize() {
        FontLoader.loadFonts();
        if (bookingContext == null) {
            bookingContext = new BookingContext();
        }
        updateAvatar();
        updateTabStyles();
        setupResponsiveListeners();
    }

    private void setupResponsiveListeners() {
        Platform.runLater(() -> {
            if (cardMarginWrapper != null && cardMarginWrapper.getScene() != null) {
                var scene = cardMarginWrapper.getScene();
                scene.widthProperty().addListener((obs, oldVal, newVal) ->
                        updateResponsiveLayout(newVal.doubleValue(), scene.getHeight()));
                scene.heightProperty().addListener((obs, oldVal, newVal) ->
                        updateResponsiveLayout(scene.getWidth(), newVal.doubleValue()));
                updateResponsiveLayout(scene.getWidth(), scene.getHeight());
            }
        });
    }

    public void updateResponsiveLayout(double width, double height) {
        if (cardMarginWrapper == null) return;
        if (width < 1100) {
            cardMarginWrapper.setPadding(new Insets(4, 20, 16, 20));
            if (mainContainer != null) {
                mainContainer.setStyle("-fx-padding: 16 18 16 18;");
            }
        } else {
            cardMarginWrapper.setPadding(new Insets(4, 56, 20, 56));
            if (mainContainer != null) {
                mainContainer.setStyle("-fx-padding: 24 32 24 32;");
            }
        }
    }

    private void updateAvatar() {
        if (avatarInitialsLabel == null) return;
        if (bookingContext != null && bookingContext.getCurrentUser() != null) {
            String name = bookingContext.getCurrentUser().getFullName();
            if (name != null && !name.isBlank()) {
                String[] parts = name.trim().split("\\s+");
                if (parts.length >= 2) {
                    avatarInitialsLabel.setText(parts[0].substring(0, 1).toUpperCase(Locale.ROOT)
                            + parts[parts.length - 1].substring(0, 1).toUpperCase(Locale.ROOT));
                    return;
                } else if (parts.length == 1 && !parts[0].isEmpty()) {
                    avatarInitialsLabel.setText(parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT));
                    return;
                }
            }
        }
        avatarInitialsLabel.setText("DT");
    }

    public void loadBookings() {
        try {
            long userId = resolveUserId();
            List<BookingSummary> summaries = bookingService.listBookingsForUser(userId, clock);
            allBookings.clear();
            if (summaries != null) {
                allBookings.addAll(summaries);
            }

            hideErrorState();
            updateTabCounts();
            renderActiveTab();
        } catch (Exception e) {
            logger.error("Failed to load bookings for user", e);
            showErrorState("We were unable to load your bookings. Please check your connection and try again.");
        }
    }

    private long resolveUserId() {
        if (bookingContext != null && bookingContext.getCurrentUser() != null
                && bookingContext.getCurrentUser().getUserId() > 0) {
            return bookingContext.getCurrentUser().getUserId();
        }
        // Default to demo traveller user (ID 12) if unauthenticated in current context
        return 12L;
    }

    private void updateTabCounts() {
        long upcoming = allBookings.stream().filter(b -> "UPCOMING".equals(b.getStatus())).count();
        long past = allBookings.stream().filter(b -> "COMPLETED".equals(b.getStatus())).count();
        long cancelled = allBookings.stream().filter(b -> "CANCELLED".equals(b.getStatus())).count();

        if (upcomingTabBtn != null) upcomingTabBtn.setText("Upcoming (" + upcoming + ")");
        if (pastTabBtn != null) pastTabBtn.setText("Past (" + past + ")");
        if (cancelledTabBtn != null) cancelledTabBtn.setText("Cancelled (" + cancelled + ")");
    }

    public void setActiveTab(Tab tab) {
        this.activeTab = tab;
        updateTabStyles();
        renderActiveTab();
    }

    private void updateTabStyles() {
        styleTabButton(upcomingTabBtn, activeTab == Tab.UPCOMING);
        styleTabButton(pastTabBtn, activeTab == Tab.PAST);
        styleTabButton(cancelledTabBtn, activeTab == Tab.CANCELLED);
    }

    private void styleTabButton(Button btn, boolean active) {
        if (btn == null) return;
        if (active) {
            btn.setStyle("-fx-background-color: linear-gradient(to bottom, #e8c46a, #d4a63c); " +
                    "-fx-text-fill: #0b1220; -fx-font-weight: 700; -fx-background-radius: 999px; " +
                    "-fx-border-color: #f3d98b; -fx-border-width: 1px; -fx-border-radius: 999px; " +
                    "-fx-padding: 8 18; -fx-cursor: hand;");
        } else {
            btn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.08); -fx-text-fill: rgba(255, 255, 255, 0.75); " +
                    "-fx-background-radius: 999px; -fx-border-color: rgba(255, 255, 255, 0.12); -fx-border-width: 1px; " +
                    "-fx-border-radius: 999px; -fx-padding: 8 18; -fx-cursor: hand;");
        }
    }

    public void renderActiveTab() {
        if (cardsContainer == null) return;
        cardsContainer.getChildren().clear();

        List<BookingSummary> filtered = allBookings.stream()
                .filter(b -> {
                    return switch (activeTab) {
                        case UPCOMING -> "UPCOMING".equals(b.getStatus());
                        case PAST -> "COMPLETED".equals(b.getStatus());
                        case CANCELLED -> "CANCELLED".equals(b.getStatus());
                    };
                })
                .toList();

        if (filtered.isEmpty()) {
            showEmptyState();
        } else {
            showPopulatedState(filtered);
        }
    }

    private void showPopulatedState(List<BookingSummary> bookings) {
        if (bookingsScrollPane != null) {
            bookingsScrollPane.setVisible(true);
            bookingsScrollPane.setManaged(true);
        }
        if (emptyStateContainer != null) {
            emptyStateContainer.setVisible(false);
            emptyStateContainer.setManaged(false);
        }
        if (errorStateContainer != null) {
            errorStateContainer.setVisible(false);
            errorStateContainer.setManaged(false);
        }

        for (BookingSummary summary : bookings) {
            Node card = buildBookingCard(summary);
            cardsContainer.getChildren().add(card);
        }
    }

    public void showEmptyState() {
        if (bookingsScrollPane != null) {
            bookingsScrollPane.setVisible(false);
            bookingsScrollPane.setManaged(false);
        }
        if (emptyStateContainer != null) {
            emptyStateContainer.setVisible(true);
            emptyStateContainer.setManaged(true);
            if (emptyTitleLabel != null) {
                switch (activeTab) {
                    case UPCOMING -> emptyTitleLabel.setText("No upcoming trips");
                    case PAST -> emptyTitleLabel.setText("No past trips");
                    case CANCELLED -> emptyTitleLabel.setText("No cancelled bookings");
                }
            }
        }
        if (errorStateContainer != null) {
            errorStateContainer.setVisible(false);
            errorStateContainer.setManaged(false);
        }
    }

    public void showErrorState(String message) {
        if (tabsBar != null) {
            tabsBar.setVisible(false);
            tabsBar.setManaged(false);
        }
        if (upcomingTabBtn != null) upcomingTabBtn.setText("Upcoming (—)");
        if (pastTabBtn != null) pastTabBtn.setText("Past (—)");
        if (cancelledTabBtn != null) cancelledTabBtn.setText("Cancelled (—)");

        if (bookingsScrollPane != null) {
            bookingsScrollPane.setVisible(false);
            bookingsScrollPane.setManaged(false);
        }
        if (emptyStateContainer != null) {
            emptyStateContainer.setVisible(false);
            emptyStateContainer.setManaged(false);
        }
        if (errorStateContainer != null) {
            errorStateContainer.setVisible(true);
            errorStateContainer.setManaged(true);
            if (errorMessageLabel != null && message != null) {
                errorMessageLabel.setText(message);
            }
        }
    }

    private void hideErrorState() {
        if (tabsBar != null) {
            tabsBar.setVisible(true);
            tabsBar.setManaged(true);
        }
        if (errorStateContainer != null) {
            errorStateContainer.setVisible(false);
            errorStateContainer.setManaged(false);
        }
    }

    public Node buildBookingCard(BookingSummary summary) {
        VBox card = new VBox(12);
        card.getStyleClass().addAll("inner-card", "glass-inner-panel");
        card.setStyle("-fx-background-color: rgba(8, 12, 22, 0.45); -fx-background-radius: 16px; " +
                "-fx-border-color: rgba(255, 255, 255, 0.08); -fx-border-radius: 16px; -fx-border-width: 1px; " +
                "-fx-padding: 18 24;");

        // Top Row: Flight number, Date, Aircraft, Status badge, Reference
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label flightNoLabel = new Label(summary.getFlightNumber());
        flightNoLabel.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 15px; -fx-text-fill: #d4a63c;");

        Label bullet1 = new Label("·");
        bullet1.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.40);");

        Label dateLabel = new Label(summary.getFormattedDepartureDate());
        dateLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 13px; -fx-text-fill: #ffffff;");

        Label bullet2 = new Label("·");
        bullet2.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.40);");

        Label aircraftLabel = new Label(summary.getAircraftModel());
        aircraftLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-text-fill: rgba(255, 255, 255, 0.60);");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        // Status Badge
        Label statusBadge = new Label(summary.getStatus());
        statusBadge.getStyleClass().add("badge");
        if ("UPCOMING".equals(summary.getStatus())) {
            statusBadge.getStyleClass().add("badge-green");
        } else if ("COMPLETED".equals(summary.getStatus())) {
            statusBadge.getStyleClass().add("badge-gold");
        } else {
            statusBadge.getStyleClass().add("badge-red");
        }

        Label pnrPill = new Label("PNR: " + summary.getBookingReference());
        pnrPill.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-text-fill: #e8c46a; " +
                "-fx-background-color: rgba(212, 166, 60, 0.12); -fx-padding: 4 10; -fx-background-radius: 6px;");

        topRow.getChildren().addAll(flightNoLabel, bullet1, dateLabel, bullet2, aircraftLabel, topSpacer, statusBadge, pnrPill);

        // Divider
        Region div = new Region();
        div.setPrefHeight(1);
        div.setStyle("-fx-background-color: rgba(255, 255, 255, 0.06);");

        // Middle Row: Origin -> Flight Path -> Destination -> Details
        HBox midRow = new HBox(24);
        midRow.setAlignment(Pos.CENTER_LEFT);

        // Origin
        VBox origBox = new VBox(2);
        origBox.setAlignment(Pos.CENTER_LEFT);
        Label origTime = new Label(summary.getFormattedDepartureTime());
        origTime.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 22px; -fx-text-fill: #ffffff;");
        Label origCode = new Label(summary.getOriginCode());
        origCode.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 16px; -fx-text-fill: #e8c46a;");
        Label origCity = new Label(summary.getOriginCity());
        origCity.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-text-fill: rgba(255, 255, 255, 0.60);");
        origBox.getChildren().addAll(origTime, origCode, origCity);

        // Flight Path Visual
        VBox pathBox = new VBox(4);
        pathBox.setAlignment(Pos.CENTER);
        pathBox.setPrefWidth(140);
        Label durLabel = new Label(summary.getDuration());
        durLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-text-fill: rgba(255, 255, 255, 0.60);");

        HBox arrowBox = new HBox(6);
        arrowBox.setAlignment(Pos.CENTER);
        arrowBox.setFillHeight(false);
        Region leftLine = new Region();
        leftLine.getStyleClass().add("route-connector-line");
        leftLine.setPrefWidth(40);
        leftLine.setMinHeight(1.5);
        leftLine.setPrefHeight(1.5);
        leftLine.setMaxHeight(2.0);
        SVGPath planeIcon = new SVGPath();
        planeIcon.setContent("M3 13l18-8-6 16-3-7-9-1z");
        planeIcon.setStyle("-fx-stroke: #d4a63c; -fx-stroke-width: 1.5; -fx-fill: transparent;");
        Region rightLine = new Region();
        rightLine.getStyleClass().add("route-connector-line");
        rightLine.setPrefWidth(40);
        rightLine.setMinHeight(1.5);
        rightLine.setPrefHeight(1.5);
        rightLine.setMaxHeight(2.0);
        arrowBox.getChildren().addAll(leftLine, planeIcon, rightLine);

        Label directLabel = new Label("Direct");
        directLabel.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 11px; -fx-text-fill: #4ade80;");
        pathBox.getChildren().addAll(durLabel, arrowBox, directLabel);

        // Destination
        VBox destBox = new VBox(2);
        destBox.setAlignment(Pos.CENTER_LEFT);
        Label destTime = new Label(summary.getFormattedArrivalTime());
        destTime.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 22px; -fx-text-fill: #ffffff;");
        Label destCode = new Label(summary.getDestinationCode());
        destCode.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 16px; -fx-text-fill: #e8c46a;");
        Label destCity = new Label(summary.getDestinationCity());
        destCity.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-text-fill: rgba(255, 255, 255, 0.60);");
        destBox.getChildren().addAll(destTime, destCode, destCity);

        Region midSpacer = new Region();
        HBox.setHgrow(midSpacer, Priority.ALWAYS);

        // Passenger & Fare details
        VBox detailsBox = new VBox(3);
        detailsBox.setAlignment(Pos.CENTER_RIGHT);
        Label passName = new Label(summary.getPassengerName());
        passName.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 13px; -fx-text-fill: #ffffff;");
        Label seatLabel = new Label("Seat: " + summary.getSeatLabelsString());
        seatLabel.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-text-fill: #e8c46a;");
        Label priceLabel = new Label(String.format(Locale.US, "R %,.2f", summary.getTotalPaid()));
        priceLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 18px; -fx-text-fill: #ffffff;");
        detailsBox.getChildren().addAll(passName, seatLabel, priceLabel);

        midRow.getChildren().addAll(origBox, pathBox, destBox, midSpacer, detailsBox);

        // Bottom Row: Action Buttons
        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);

        Button viewTicketBtn = new Button("View ticket");
        viewTicketBtn.getStyleClass().add("btn-gold");
        viewTicketBtn.setAccessibleText("View ticket for booking " + summary.getBookingReference());
        viewTicketBtn.setOnAction(e -> handleViewTicket(summary));

        bottomRow.getChildren().addAll(viewTicketBtn);

        card.getChildren().addAll(topRow, div, midRow, bottomRow);
        return card;
    }

    private void handleViewTicket(BookingSummary summary) {
        if (summary == null) return;
        try {
            long uid = resolveUserId();
            BookingRecord record = bookingService.getBookingForUser(uid, summary.getBookingReference());

            BookingContext ctx = new BookingContext();
            User user = bookingContext != null ? bookingContext.getCurrentUser() : null;
            if (user == null) {
                user = new User();
                user.setUserId(uid);
                user.setRole(UserRole.CUSTOMER);
                user.setEmail(summary.getPassengerEmail());
                user.setFullName(summary.getPassengerName());
            }
            ctx.setCurrentUser(user);
            ctx.setBookingReference(record.getBookingReference());
            ctx.setFlightId(record.getFlightId());
            ctx.setFlightNumber(record.getFlightNumber());
            ctx.setOrigin(summary.getOriginCity() + " (" + summary.getOriginCode() + ")");
            ctx.setOriginCity(summary.getOriginCity());
            ctx.setDestination(summary.getDestinationCity() + " (" + summary.getDestinationCode() + ")");
            ctx.setDestinationCity(summary.getDestinationCity());
            ctx.setDepartureTime(summary.getFormattedDepartureTime());
            ctx.setArrivalTime(summary.getFormattedArrivalTime());
            ctx.setDepartureDate(summary.getFormattedDepartureDate());
            ctx.setDepartureInstant(summary.getDepartureInstant());
            ctx.setArrivalInstant(summary.getArrivalInstant());
            ctx.setDuration(summary.getDuration());
            ctx.setAircraftModel(summary.getAircraftModel());
            ctx.setPassengerName(summary.getPassengerName());
            ctx.setPassengerEmail(summary.getPassengerEmail());
            ctx.setPassportNumber(record.getPassportNumber() != null ? record.getPassportNumber() : "DT123456");
            ctx.setTotalPrice(summary.getTotalPaid());
            ctx.setBaseFare(summary.getTotalPaid());

            com.group2.flightres.model.FlightSeat seat = new com.group2.flightres.model.FlightSeat();
            String label = summary.getSeatLabelsString();
            int row = 7;
            char col = 'A';
            if (label != null && label.length() >= 2) {
                try {
                    row = Integer.parseInt(label.substring(0, label.length() - 1));
                    col = label.charAt(label.length() - 1);
                } catch (Exception ignored) {}
            }
            seat.setSeatRow(row);
            seat.setSeatCol(col);
            seat.setSeatClass(record.getSeatClass() != null ? record.getSeatClass() : SeatClass.ECONOMY);
            seat.setCalculatedPrice(summary.getTotalPaid());
            ctx.setSelectedSeats(List.of(seat));

            Navigator.navigateToConfirmation(mainContainer, ctx);
        } catch (Exception e) {
            logger.error("Failed to open ticket for booking {}", summary.getBookingReference(), e);
            showErrorState("Unable to open ticket: " + e.getMessage());
            if (!Boolean.getBoolean("altitude.testing") && !Boolean.getBoolean("altitude.screenshots")) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Unable to open ticket: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    // Nav and tab event handlers
    @FXML
    private void handleShowUpcoming() {
        setActiveTab(Tab.UPCOMING);
    }

    @FXML
    private void handleShowPast() {
        setActiveTab(Tab.PAST);
    }

    @FXML
    private void handleShowCancelled() {
        setActiveTab(Tab.CANCELLED);
    }

    @FXML
    private void handleSearchFlights() {
        if (onSearchFlights != null) {
            onSearchFlights.run();
        } else {
            Navigator.navigateToSearch(mainContainer, bookingContext);
        }
    }

    @FXML
    private void handleRetry() {
        loadBookings();
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
    private void handleNavMyBookings() {
        loadBookings();
    }

    @FXML
    private void handleComingSoonOffers() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Offers aren't available yet.");
        alert.setTitle("Information");
        alert.setHeaderText("Offers");
        alert.showAndWait();
    }

    @FXML
    private void handleNavAbout() {
        Navigator.navigate(mainContainer, "/com/group2/flightres/fxml/about.fxml", false, "About", bookingContext);
    }

    @FXML
    private void handleNavContact() {
        Navigator.navigate(mainContainer, "/com/group2/flightres/fxml/contact.fxml", false, "Contact", bookingContext);
    }

    @FXML
    private void handleNavAvatar() {
        if (bookingContext != null && bookingContext.getCurrentUser() != null) {
            bookingContext.setCurrentUser(null);
            Navigator.navigateToHome(mainContainer, bookingContext);
        } else {
            Navigator.navigate(mainContainer, "/com/group2/flightres/fxml/login.fxml", false, "Login / Sign Up", bookingContext);
        }
    }

    // Getters for layout tests and assertions
    public List<BookingSummary> getAllBookings() { return allBookings; }
    public Tab getActiveTab() { return activeTab; }
    public VBox getCardsContainer() { return cardsContainer; }
    public VBox getEmptyStateContainer() { return emptyStateContainer; }
    public VBox getErrorStateContainer() { return errorStateContainer; }
    public StackPane getCardMarginWrapper() { return cardMarginWrapper; }
    public Button getUpcomingTabBtn() { return upcomingTabBtn; }
    public Button getPastTabBtn() { return pastTabBtn; }
    public Button getCancelledTabBtn() { return cancelledTabBtn; }
    public Button getSearchFlightsButton() { return searchFlightsButton; }
    public Button getRetryButton() { return retryButton; }
    public Label getErrorMessageLabel() { return errorMessageLabel; }
    public HBox getTabsBar() { return tabsBar; }
}
