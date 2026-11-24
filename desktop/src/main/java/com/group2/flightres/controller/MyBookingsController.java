package com.group2.flightres.controller;

import com.group2.flightres.component.GlassTopBar;
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
import com.group2.flightres.util.MoneyFormatter;
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

    @FXML private GlassTopBar topBar;
    @FXML private HBox populatedSplitBox;
    @FXML private VBox passPreviewCard;
    @FXML private Label previewStatusBadge;
    @FXML private Label previewPnrLabel;
    @FXML private Label previewRouteCodeLabel;
    @FXML private Label previewFlightNoLabel;
    @FXML private Label previewRouteCitiesLabel;
    @FXML private Label previewDateTimeLabel;
    @FXML private Label previewPassengerLabel;
    @FXML private Label previewSeatLabel;
    @FXML private Label previewAircraftLabel;
    @FXML private Label previewFareLabel;
    @FXML private javafx.scene.image.ImageView previewQrImageView;
    @FXML private Button previewViewPassBtn;
    @FXML private Label avatarInitialsLabel;
    @FXML private StackPane avatarCircle;

    private BookingSummary selectedBooking;

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
        if (topBar != null) {
            topBar.bind(this.bookingContext);
            if (onSearchFlights != null) {
                topBar.setOnFlightsAction(onSearchFlights);
            }
        }
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
        if (passPreviewCard != null) {
            boolean showPreview = width >= 1000;
            passPreviewCard.setVisible(showPreview);
            passPreviewCard.setManaged(showPreview);
        }
        if (width < 1100) {
            cardMarginWrapper.setPadding(new Insets(4, 20, 16, 20));
            if (mainContainer != null) {
                mainContainer.setStyle("-fx-padding: 16 18 16 18;");
            }
        } else {
            cardMarginWrapper.setPadding(new Insets(4, 56, 20, 56));
            if (mainContainer != null) {
                mainContainer.setStyle("-fx-padding: 20 26 20 26;");
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
            btn.setStyle("-fx-background-color: -glass-white; " +
                    "-fx-text-fill: -glass-ink; -fx-font-weight: 700; -fx-background-radius: 999px; " +
                    "-fx-border-color: -glass-white; -fx-border-width: 1px; -fx-border-radius: 999px; " +
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
        if (populatedSplitBox != null) {
            populatedSplitBox.setVisible(true);
            populatedSplitBox.setManaged(true);
        }
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

        if (selectedBooking == null || !bookings.contains(selectedBooking)) {
            selectedBooking = bookings.isEmpty() ? null : bookings.get(0);
        }

        for (BookingSummary summary : bookings) {
            Node card = buildBookingCard(summary);
            cardsContainer.getChildren().add(card);
        }
        updatePassPreview(selectedBooking);
    }

    public void showEmptyState() {
        if (populatedSplitBox != null) {
            populatedSplitBox.setVisible(false);
            populatedSplitBox.setManaged(false);
        }
        if (populatedSplitBox != null) {
            populatedSplitBox.setVisible(false);
            populatedSplitBox.setManaged(false);
        }
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

        if (populatedSplitBox != null) {
            populatedSplitBox.setVisible(false);
            populatedSplitBox.setManaged(false);
        }
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
        boolean isSelected = (selectedBooking != null
                && summary.getBookingReference() != null
                && summary.getBookingReference().equals(selectedBooking.getBookingReference()));

        VBox card = new VBox(6);
        card.getStyleClass().addAll("inner-card", isSelected ? "mb-row-selected" : "mb-row");

        // Top row: Route (CPT -> JNB) + Status badge
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label routeLabel = new Label(summary.getOriginCode() + " → " + summary.getDestinationCode());
        routeLabel.getStyleClass().add("mb-row-route");
        routeLabel.setMinWidth(Region.USE_PREF_SIZE);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label statusBadge = new Label(summary.getStatus());
        if (isSelected) {
            statusBadge.getStyleClass().add("mb-pass-status");
        } else {
            statusBadge.getStyleClass().add("badge");
            if ("UPCOMING".equals(summary.getStatus())) {
                statusBadge.getStyleClass().add("badge-green");
            } else if ("CANCELLED".equals(summary.getStatus())) {
                statusBadge.getStyleClass().add("badge-red");
            } else {
                statusBadge.getStyleClass().add("badge-gold");
            }
        }
        statusBadge.setMinWidth(Region.USE_PREF_SIZE);

        topRow.getChildren().addAll(routeLabel, topSpacer, statusBadge);

        // Middle row: Flight number · Date · Time
        Label flightMeta = new Label(summary.getFlightNumber() + " · " + summary.getFormattedDepartureDate()
                + " · " + summary.getFormattedDepartureTime());
        flightMeta.getStyleClass().add("mb-row-meta");

        // Bottom row: PNR · Seat | Price
        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        Label pnrSeat = new Label("PNR " + summary.getBookingReference() + " · Seat " + summary.getSeatLabelsString());
        pnrSeat.getStyleClass().add("mb-row-meta");

        Region botSpacer = new Region();
        HBox.setHgrow(botSpacer, Priority.ALWAYS);

        Label priceLabel = new Label(MoneyFormatter.format(summary.getTotalPaid()));
        priceLabel.getStyleClass().add("mb-row-price");
        priceLabel.setMinWidth(Region.USE_PREF_SIZE);

        bottomRow.getChildren().addAll(pnrSeat, botSpacer, priceLabel);

        card.getChildren().addAll(topRow, flightMeta, bottomRow);
        card.setFocusTraversable(true);
        card.setAccessibleText("Select booking " + summary.getBookingReference() + " "
                + summary.getOriginCode() + " to " + summary.getDestinationCode());
        card.setOnMouseClicked(e -> {
            selectedBooking = summary;
            renderActiveTab();
        });
        card.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                selectedBooking = summary;
                renderActiveTab();
                e.consume();
            }
        });
        return card;
    }

    private void updatePassPreview(BookingSummary summary) {
        if (passPreviewCard == null) return;
        if (summary == null) {
            if (previewPnrLabel != null) previewPnrLabel.setText("PNR —");
            return;
        }
        if (previewStatusBadge != null) previewStatusBadge.setText(summary.getStatus());
        if (previewPnrLabel != null) previewPnrLabel.setText("PNR " + summary.getBookingReference());
        if (previewRouteCodeLabel != null) {
            previewRouteCodeLabel.setText(summary.getOriginCode() + " → " + summary.getDestinationCode());
        }
        if (previewFlightNoLabel != null) previewFlightNoLabel.setText(summary.getFlightNumber());
        if (previewRouteCitiesLabel != null) {
            previewRouteCitiesLabel.setText(summary.getOriginCity() + " to " + summary.getDestinationCity());
        }
        if (previewDateTimeLabel != null) {
            previewDateTimeLabel.setText(summary.getFormattedDepartureDate() + " · "
                    + summary.getFormattedDepartureTime() + " – " + summary.getFormattedArrivalTime()
                    + " · " + summary.getDuration());
        }
        if (previewPassengerLabel != null) previewPassengerLabel.setText(summary.getPassengerName());
        if (previewSeatLabel != null) previewSeatLabel.setText(summary.getSeatLabelsString());
        if (previewAircraftLabel != null) previewAircraftLabel.setText(summary.getAircraftModel());
        if (previewFareLabel != null) previewFareLabel.setText(MoneyFormatter.format(summary.getTotalPaid()));
        if (previewViewPassBtn != null) {
            previewViewPassBtn.setAccessibleText("View pass for booking " + summary.getBookingReference());
        }

        if (previewQrImageView != null) {
            try {
                String qrPayload = "SKYROYAL|" + summary.getBookingReference() + "|" + summary.getFlightNumber()
                        + "|" + summary.getOriginCode() + "-" + summary.getDestinationCode()
                        + "|" + summary.getSeatLabelsString();
                java.awt.image.BufferedImage qrImg = com.group2.flightres.util.QrCodeGenerator.generateQrImage(qrPayload, 320);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(qrImg, "png", baos);
                previewQrImageView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(baos.toByteArray())));
            } catch (Exception e) {
                logger.warn("Could not generate QR preview for {}: {}", summary.getBookingReference(), e.getMessage());
            }
        }
    }

    @FXML
    public void handlePreviewViewPass() {
        if (selectedBooking != null) {
            handleViewTicket(selectedBooking);
        }
    }

    @FXML
    public void handlePreviewDownloadPdf() {
        if (selectedBooking == null) return;
        try {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Save Boarding Pass PDF");
            chooser.setInitialFileName("BoardingPass_" + selectedBooking.getBookingReference() + ".pdf");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
            javafx.stage.Window win = passPreviewCard != null && passPreviewCard.getScene() != null
                    ? passPreviewCard.getScene().getWindow() : null;
            java.io.File target = chooser.showSaveDialog(win);
            if (target != null) {
                BookingContext ctx = buildContextFromSummary(selectedBooking);
                com.group2.flightres.util.BoardingPassPdfGenerator.generate(ctx, target);
            }
        } catch (Exception e) {
            logger.error("Failed to download PDF from My Bookings: {}", e.getMessage(), e);
        }
    }

    @FXML
    public void handlePreviewAddCalendar() {
        if (selectedBooking == null) return;
        try {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Save Calendar Invite (.ics)");
            chooser.setInitialFileName("Flight_" + selectedBooking.getBookingReference() + ".ics");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Calendar Files (*.ics)", "*.ics"));
            javafx.stage.Window win = passPreviewCard != null && passPreviewCard.getScene() != null
                    ? passPreviewCard.getScene().getWindow() : null;
            java.io.File target = chooser.showSaveDialog(win);
            if (target != null) {
                java.time.format.DateTimeFormatter icsFmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .withZone(java.time.ZoneOffset.UTC);
                String start = selectedBooking.getDepartureInstant() != null ? icsFmt.format(selectedBooking.getDepartureInstant()) : "";
                String end = selectedBooking.getArrivalInstant() != null ? icsFmt.format(selectedBooking.getArrivalInstant()) : "";
                String ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Sky Royal//Flight Reservation//EN\r\n"
                        + "BEGIN:VEVENT\r\nUID:" + selectedBooking.getBookingReference() + "@skyroyal\r\n"
                        + "DTSTART:" + start + "\r\nDTEND:" + end + "\r\n"
                        + "SUMMARY:Sky Royal " + selectedBooking.getFlightNumber() + " (" + selectedBooking.getOriginCode() + " to " + selectedBooking.getDestinationCode() + ")\r\n"
                        + "DESCRIPTION:PNR " + selectedBooking.getBookingReference() + " · Seat " + selectedBooking.getSeatLabelsString() + "\r\n"
                        + "END:VEVENT\r\nEND:VCALENDAR\r\n";
                java.nio.file.Files.writeString(target.toPath(), ics, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.error("Failed to save calendar invite from My Bookings: {}", e.getMessage(), e);
        }
    }

    private BookingContext buildContextFromSummary(BookingSummary summary) {
        BookingContext ctx = new BookingContext();
        ctx.setBookingReference(summary.getBookingReference());
        ctx.setFlightNumber(summary.getFlightNumber());
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
        ctx.setTotalPrice(summary.getTotalPaid());
        ctx.setBaseFare(summary.getTotalPaid());
        return ctx;
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
            if (!Boolean.getBoolean("skyroyal.testing") && !Boolean.getBoolean("skyroyal.screenshots")) {
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
