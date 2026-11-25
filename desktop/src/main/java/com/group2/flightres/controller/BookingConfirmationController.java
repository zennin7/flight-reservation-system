package com.group2.flightres.controller;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.component.GlassTopBar;
import com.group2.flightres.component.TicketView;
import com.group2.flightres.model.TicketData;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.util.FlightDateFormatter;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.util.BarcodeGenerator;
import com.group2.flightres.util.BoardingPassPdfGenerator;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.IcsCalendarGenerator;
import com.group2.flightres.util.Motion;
import com.group2.flightres.util.Navigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Controller for Sky Royal Screen 05 / 06 Booking Confirmation.
 */
public class BookingConfirmationController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(BookingConfirmationController.class);

    // Containers
    @FXML private StackPane cardMarginWrapper;
    @FXML private VBox errorContainer;
    @FXML private Label errorTitleLabel;
    @FXML private Label errorMessageLabel;
    @FXML private VBox confirmedContainer;
    @FXML private Button errorBackButton;

    private final BookingService bookingService;

    public BookingConfirmationController() {
        this(new BookingService());
    }

    public BookingConfirmationController(BookingService bookingService) {
        this.bookingService = bookingService != null ? bookingService : new BookingService();
    }
    @FXML private ScrollPane confirmationScrollPane;
    @FXML private StackPane scrollContentRoot;
    @FXML private HBox twoColumnBox;
    @FXML private VBox singleColumnBox;
    @FXML private VBox sidebarContainer;
    @FXML private VBox paymentBox;
    @FXML private VBox nextStepsBox;
    @FXML private HBox buttonRow;

    // Header controls
    @FXML private Label confirmationTitleLabel;
    @FXML private Label confirmationSubtitleLabel;
    @FXML private Label bookingRefLabel;
    @FXML private Label avatarInitialsLabel;

    // Ticket controls
    @FXML private TicketView ticketView;
    @FXML private StackPane ticketWrapperPane;
    @FXML private HBox ticketCardBox;
    @FXML private Label cabinBadgeLabel;
    @FXML private Label originCodeLabel;
    @FXML private Label originCityTimeLabel;
    @FXML private Label durationLabel;
    @FXML private Label destCodeLabel;
    @FXML private Label destCityTimeLabel;
    @FXML private Label passengerNameLabel;
    @FXML private Label flightNumberLabel;
    @FXML private Label flightDateLabel;
    @FXML private Label boardingTimeLabel;
    @FXML private ImageView barcodeImageView;
    @FXML private Region perforationDivider;
    @FXML private VBox stubBox;
    @FXML private Label stubSeatLabel;
    @FXML private Label gateValueLabel;
    @FXML private Label gateNoteLabel;
    @FXML private Circle topNotch;
    @FXML private Circle bottomNotch;

    @FXML private GlassTopBar topBar;
    // Payment & Timeline controls
    @FXML private Label baseFareLabel;
    @FXML private Label demandPricingLabel;
    @FXML private Label taxesFeesLabel;
    @FXML private Label totalPaidLabel;
    @FXML private Label timelineSeatConfirmedLabel;
    @FXML private Label timelineCheckinLabel;
    @FXML private Label timelineBagDropLabel;

    // Action buttons
    @FXML private Button downloadPdfButton;
    @FXML private Button addToCalendarButton;
    @FXML private Button manageBookingButton;
    @FXML private Button backToHomeButton;

    private BookingContext bookingContext;
    private Runnable onHome;

    @Override
    public void init(BookingContext context, Runnable onHome) {
        this.bookingContext = context;
        this.onHome = onHome;
        if (topBar != null) {
            topBar.bind(context);
        }
        populateData();
    }

    @FXML
    public void initialize() {
        FontLoader.loadFonts();
        populateData();

        if (downloadPdfButton != null) Motion.attachHoverLift(downloadPdfButton);
        if (addToCalendarButton != null) Motion.attachHoverLift(addToCalendarButton);
        if (errorBackButton != null) Motion.attachHoverLift(errorBackButton);
        setupResponsiveListeners();

        // Position ticket perforation notches dynamically on stub width
        // Notches managed inside TicketView
    }

    private void updateNotchPositions() {
        if (ticketWrapperPane == null || stubBox == null || topNotch == null || bottomNotch == null) return;
        double stubWidth = stubBox.getWidth() > 0 ? stubBox.getWidth() : 220;
        double totalWidth = ticketWrapperPane.getWidth();
        if (totalWidth <= 0) return;
        double notchX = totalWidth - stubWidth;

        // Position notch circles centered on the perforation line
        topNotch.setTranslateX(-stubWidth + 14);
        topNotch.setTranslateY(-14);
        bottomNotch.setTranslateX(-stubWidth + 14);
        bottomNotch.setTranslateY(14);
    }

    private void showNotFoundErrorState() {
        if (errorTitleLabel != null) {
            errorTitleLabel.setText("We couldn't find your booking");
        }
        if (errorMessageLabel != null) {
            errorMessageLabel.setText("No confirmed reservation details were found in this session. Please search and select a flight.");
        }
        if (errorContainer != null) {
            errorContainer.setVisible(true);
            errorContainer.setManaged(true);
        }
        if (confirmedContainer != null) {
            confirmedContainer.setVisible(false);
            confirmedContainer.setManaged(false);
        }
    }

    private void showIncompleteDataErrorState(String bookingRef) {
        String ref = (bookingRef != null && !bookingRef.isBlank()) ? bookingRef.trim().toUpperCase(Locale.ROOT) : "";
        if (errorTitleLabel != null) {
            errorTitleLabel.setText("We couldn't display this ticket");
        }
        if (errorMessageLabel != null) {
            errorMessageLabel.setText("Your booking " + ref + " is saved. Please contact support.");
        }
        if (errorContainer != null) {
            errorContainer.setVisible(true);
            errorContainer.setManaged(true);
        }
        if (confirmedContainer != null) {
            confirmedContainer.setVisible(false);
            confirmedContainer.setManaged(false);
        }
    }

    private void showErrorState() {
        showNotFoundErrorState();
    }

    private void populateData() {
        // 1. Error state check: null context or null/blank booking reference
        if (bookingContext == null
                || bookingContext.getBookingReference() == null
                || bookingContext.getBookingReference().isBlank()) {
            showNotFoundErrorState();
            return;
        }

        String pnr = bookingContext.getBookingReference().trim().toUpperCase(Locale.ROOT);

        // 2. Ticket data incomplete check: instants are null
        if (bookingContext.getDepartureInstant() == null || bookingContext.getArrivalInstant() == null) {
            showIncompleteDataErrorState(pnr);
            return;
        }

        // Show confirmed state, hide error state
        if (errorContainer != null) {
            errorContainer.setVisible(false);
            errorContainer.setManaged(false);
        }
        if (confirmedContainer != null) {
            confirmedContainer.setVisible(true);
            confirmedContainer.setManaged(true);
        }

        // 2. Populate Header
        if (bookingRefLabel != null) {
            bookingRefLabel.setText(pnr);
        }

        if (confirmationTitleLabel != null) {
            String destCity = resolveShortCity(bookingContext.getDestinationCity(), bookingContext.getDestination());
            confirmationTitleLabel.setText(!destCity.isBlank() ? destCity + " is waiting." : "Your flight is booked");
        }
        if (confirmationSubtitleLabel != null) {
            confirmationSubtitleLabel.setText("Your boarding pass is ready. Download it below or find it any time in My bookings.");
        }

        // User Avatar Initials
        String passengerName = bookingContext.getPassengerName();
        if (avatarInitialsLabel != null) {
            avatarInitialsLabel.setText(extractInitials(passengerName));
        }

        // 3. Populate Ticket Boarding Details
        SeatClass seatCls = bookingContext.getSeatClass();
        if (cabinBadgeLabel != null) {
            cabinBadgeLabel.setText(seatCls == SeatClass.FIRST ? "First Class" : "Economy");
        }

        // 3. Populate Reusable TicketView Component
        if (ticketView != null && bookingContext != null) {
            try {
                ticketView.setBooking(TicketData.from(bookingContext));
            } catch (Exception e) {
                logger.error("Failed to populate TicketView from context", e);
                showIncompleteDataErrorState(pnr);
                return;
            }
        }

        // 5. Populate Payment Summary
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal demand = BigDecimal.ZERO;
        BigDecimal timing = BigDecimal.ZERO;
        if (bookingContext.getPriceQuotes() != null && !bookingContext.getPriceQuotes().isEmpty()) {
            base = bookingContext.getPriceQuotes().stream().map(PriceQuote::classFare).reduce(BigDecimal.ZERO, BigDecimal::add);
            demand = bookingContext.getPriceQuotes().stream().map(PriceQuote::demandAdjustment).reduce(BigDecimal.ZERO, BigDecimal::add);
            timing = bookingContext.getPriceQuotes().stream().map(PriceQuote::timingAdjustment).reduce(BigDecimal.ZERO, BigDecimal::add);
        } else if (bookingContext.getBaseFare() != null) {
            base = bookingContext.getBaseFare();
        }
        BigDecimal total = bookingContext.getTotalPrice() != null ? bookingContext.getTotalPrice() : base.add(demand).add(timing);

        if (baseFareLabel != null) baseFareLabel.setText(formatExactPrice(base));
        if (demandPricingLabel != null) demandPricingLabel.setText(formatExactPrice(demand));
        if (taxesFeesLabel != null) taxesFeesLabel.setText(formatExactPrice(timing));
        if (totalPaidLabel != null) totalPaidLabel.setText(formatTotalText(total));

        // 6. Populate What Happens Next Timeline: Bag drop uses airport NAME from DB
        String seat = bookingContext.getSeatLabel();
        String depTime = bookingContext.getDepartureTime();
        String originStr = bookingContext.getOrigin();
        String originCode = extractAirportCode(originStr, "");

        if (timelineSeatConfirmedLabel != null) {
            timelineSeatConfirmedLabel.setText(seat != null && !seat.isBlank() ? "Seat " + seat + " is yours" : "");
        }
        if (timelineCheckinLabel != null) {
            timelineCheckinLabel.setText(BoardingPolicy.formatCheckinOpens(bookingContext.getDepartureDate(), depTime));
        }
        if (timelineBagDropLabel != null) {
            String airportName = resolveAirportName(originCode, originStr);
            timelineBagDropLabel.setText(BoardingPolicy.formatBagDropTime(depTime, airportName));
        }
    }

    private static javafx.scene.image.Image toFXImage(BufferedImage bimg) {
        if (bimg == null) return null;
        WritableImage wr = new WritableImage(bimg.getWidth(), bimg.getHeight());
        PixelWriter pw = wr.getPixelWriter();
        for (int x = 0; x < bimg.getWidth(); x++) {
            for (int y = 0; y < bimg.getHeight(); y++) {
                pw.setArgb(x, y, bimg.getRGB(x, y));
            }
        }
        return wr;
    }

    private String extractInitials(String name) {
        if (name == null || name.isBlank()) return "AM";
        String clean = name.replaceAll("(?i)^(mr|mrs|ms|dr|prof)\\.?\\s+", "").trim();
        String[] parts = clean.split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        return clean.length() >= 2 ? clean.substring(0, 2).toUpperCase(Locale.ROOT) : "AM";
    }

    private String extractAirportCode(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        int open = raw.indexOf('(');
        int close = raw.indexOf(')');
        if (open >= 0 && close > open) {
            return raw.substring(open + 1, close).trim().toUpperCase(Locale.ROOT);
        }
        if (raw.trim().length() == 3) {
            return raw.trim().toUpperCase(Locale.ROOT);
        }
        return fallback;
    }

    private String resolveAirportName(String code, String raw) {
        String cityPart = (raw != null && raw.contains("(")) ? raw.substring(0, raw.indexOf('(')).trim() : (raw != null ? raw.trim() : "");
        String fallback = !cityPart.isBlank() ? cityPart : (code != null ? code : "");
        return bookingService.findAirportName(code, cityPart)
                .orElse(fallback);
    }

    private String resolveCityName(String raw, String code) {
        if (raw != null && raw.contains("(")) {
            String cityPart = raw.substring(0, raw.indexOf('(')).trim();
            if (!cityPart.isBlank()) return cityPart;
        }
        return bookingService.findAirportCity(code)
                .orElse(code != null ? code : "");
    }


    private String formatDateBadge(String depDate) {
        if (depDate == null || depDate.isBlank()) {
            throw new IllegalStateException("Departure date is required for flight badge.");
        }
        if (depDate.contains(",") && depDate.length() >= 10) {
            return depDate;
        }
        try {
            LocalDate date = LocalDate.parse(depDate.trim());
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);
            return date.format(fmt);
        } catch (DateTimeParseException e) {
            return depDate;
        }
    }

    private String formatExactPrice(BigDecimal price) {
        return com.group2.flightres.util.MoneyFormatter.format(price);
    }

    private String resolveShortCity(String city, String raw) {
        String candidate = (city != null && !city.isBlank()) ? city.trim() : (raw != null ? raw.trim() : "");
        if (candidate.isEmpty()) return "";
        int paren = candidate.indexOf('(');
        if (paren > 0) candidate = candidate.substring(0, paren).trim();
        String cleaned = com.group2.flightres.util.AirportNames.shortName(candidate);
        return !cleaned.isBlank() ? cleaned : candidate;
    }

    private String formatTotalText(BigDecimal price) {
        return com.group2.flightres.util.MoneyFormatter.format(price);
    }

    @FXML
    public void handleDownloadPdf() {
        logger.info("Download Boarding Pass (PDF) clicked.");
        Window window = (downloadPdfButton != null && downloadPdfButton.getScene() != null)
                ? downloadPdfButton.getScene().getWindow() : null;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Boarding Pass");
        String ref = (bookingContext != null && bookingContext.getBookingReference() != null && !bookingContext.getBookingReference().isBlank())
                ? bookingContext.getBookingReference() : "REF";
        fileChooser.setInitialFileName("BoardingPass_" + ref + ".pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));

        File targetFile = fileChooser.showSaveDialog(window);
        if (targetFile == null) {
            logger.info("User cancelled saving boarding pass.");
            return;
        }

        try {
            BoardingPassPdfGenerator.generate(bookingContext, targetFile);

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Boarding Pass Downloaded");
            successAlert.setHeaderText("Success!");
            successAlert.setContentText("Boarding pass successfully saved to:\n" + targetFile.getAbsolutePath());
            if (!Boolean.getBoolean("skyroyal.testing") && !Boolean.getBoolean("skyroyal.screenshots")) {
                successAlert.show();
            }

            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(targetFile);
                }
            } catch (Exception ex) {
                logger.warn("Could not automatically open PDF file via Desktop: {}", ex.getMessage());
            }

        } catch (Exception e) {
            logger.error("Failed to generate boarding pass PDF", e);
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Download Failed");
            errorAlert.setHeaderText("Error Generating PDF");
            errorAlert.setContentText("Could not create boarding pass: " + e.getMessage());
            if (!Boolean.getBoolean("skyroyal.testing") && !Boolean.getBoolean("skyroyal.screenshots")) {
                errorAlert.showAndWait();
            }
        }
    }

    @FXML
    public void handleAddToCalendar() {
        logger.info("Add to Calendar (.ics) clicked.");
        Window window = (addToCalendarButton != null && addToCalendarButton.getScene() != null)
                ? addToCalendarButton.getScene().getWindow() : null;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Calendar Event (.ics)");
        String ref = (bookingContext != null && bookingContext.getBookingReference() != null && !bookingContext.getBookingReference().isBlank())
                ? bookingContext.getBookingReference() : "EVENT";
        fileChooser.setInitialFileName("Flight_" + ref + ".ics");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("iCalendar Files (*.ics)", "*.ics"));

        File targetFile = fileChooser.showSaveDialog(window);
        if (targetFile == null) {
            logger.info("User cancelled saving calendar event.");
            return;
        }

        try {
            String icsContent = IcsCalendarGenerator.generateIcsContent(bookingContext);
            try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
                writer.write(icsContent);
            }

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Calendar Event Saved");
            successAlert.setHeaderText("Success!");
            successAlert.setContentText("Calendar event successfully saved to:\n" + targetFile.getAbsolutePath());
            if (!Boolean.getBoolean("skyroyal.testing") && !Boolean.getBoolean("skyroyal.screenshots")) {
                successAlert.show();
            }

        } catch (Exception e) {
            logger.error("Failed to generate calendar .ics file", e);
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Calendar Export Failed");
            errorAlert.setHeaderText("Error Creating Calendar Event");
            errorAlert.setContentText("Could not create .ics file: " + e.getMessage());
            if (!Boolean.getBoolean("skyroyal.testing") && !Boolean.getBoolean("skyroyal.screenshots")) {
                errorAlert.showAndWait();
            }
        }
    }

    @FXML
    public void handleBackToHome() {
        logger.info("Back to Home clicked.");
        if (onHome != null) {
            onHome.run();
        }
    }

    // Top Navigation Handlers
    @FXML private void handleNavHome() { handleBackToHome(); }
    @FXML private void handleNavFlights() { handleBackToHome(); }
    @FXML private void handleComingSoonMyBookings() {
        Navigator.navigateToMyBookings(downloadPdfButton != null ? downloadPdfButton : cardMarginWrapper, bookingContext);
    }
    @FXML private void handleComingSoonOffers() { showComingSoonAlert("Offers"); }
    @FXML private void handleNavAbout() { showComingSoonAlert("About"); }
    @FXML private void handleNavContact() { showComingSoonAlert("Contact"); }
    @FXML private void handleNavAvatar() { showComingSoonAlert("Profile"); }

    private void showComingSoonAlert(String feature) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, feature + " isn't available yet.");
        alert.setHeaderText("Coming soon");
        if (!Boolean.getBoolean("skyroyal.testing") && !Boolean.getBoolean("skyroyal.screenshots")) {
            alert.showAndWait();
        }
    }

    // Getters for verifier / screenshot testing
    public BookingContext getBookingContext() { return bookingContext; }
    public VBox getErrorContainer() { return errorContainer; }
    public VBox getConfirmedContainer() { return confirmedContainer; }
    public Label getBookingRefLabel() { return bookingRefLabel; }
    public Label getPassengerNameLabel() { return ticketView != null ? ticketView.getPassengerNameLabel() : null; }
    public Label getStubSeatLabel() { return ticketView != null ? ticketView.getStubSeatLabel() : null; }
    public Label getDemandPricingLabel() { return demandPricingLabel; }
    public Label getTotalPaidLabel() { return totalPaidLabel; }
    public Label getGateValueLabel() { return ticketView != null ? ticketView.getGateValueLabel() : null; }
    public Label getGateNoteLabel() { return ticketView != null ? ticketView.getGateNoteLabel() : null; }
    public TicketView getTicketView() { return ticketView; }
    public ImageView getBarcodeImageView() { return ticketView != null ? ticketView.getBarcodeImageView() : null; }
    public Button getDownloadPdfButton() { return downloadPdfButton; }
    public Button getAddToCalendarButton() { return addToCalendarButton; }
    public Button getManageBookingButton() { return manageBookingButton; }

    private boolean updatingLayout = false;

    private void setupResponsiveListeners() {
        if (confirmationScrollPane != null) {
            confirmationScrollPane.viewportBoundsProperty().addListener((obs, oldVp, newVp) -> {
                if (newVp != null && newVp.getWidth() > 0 && newVp.getHeight() > 0) {
                    Scene sc = confirmationScrollPane.getScene();
                    double sw = sc != null ? sc.getWidth() : newVp.getWidth();
                    double sh = sc != null ? sc.getHeight() : newVp.getHeight();
                    updateResponsiveLayout(sw, sh);
                }
            });
        }
        if (confirmedContainer != null) {
            confirmedContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.widthProperty().addListener((o, oldW, newW) ->
                            updateResponsiveLayout(newW.doubleValue(), newScene.getHeight()));
                    newScene.heightProperty().addListener((o, oldH, newH) ->
                            updateResponsiveLayout(newScene.getWidth(), newH.doubleValue()));
                    Platform.runLater(() -> updateResponsiveLayout(newScene.getWidth(), newScene.getHeight()));
                }
            });
        }
    }

    /**
     * Updates the layout dynamically:
     * - width >= 1100: Two-column layout (ticket left, sidebar right).
     * - width < 1100: Single-column layout (sidebar stacked under ticket, scrollable).
     * Sizes everything from scrollPane.viewportBounds, reserving a 16px gutter so the
     * scrollbar never overlaps content. In TWO_COLUMN mode:
     * scale = clamp(min(widthFit, heightFit), 0.90, 1.25), and sidebarContainer is no taller than ticket.
     */
    public void updateResponsiveLayout(double sceneWidth, double sceneHeight) {
        if (sceneWidth <= 0 || updatingLayout) return;
        updatingLayout = true;
        try {
            boolean isTwoColumn = sceneWidth >= 1100.0;
            double outerMarginH = (sceneWidth < 1200.0) ? 14.0 : 28.0;
            double cardPadH = (sceneWidth < 1200.0) ? 14.0 : 24.0;
            double cardPadV = (sceneHeight < 750.0) ? 8.0 : 16.0;
            double cardMarginV = (sceneHeight < 750.0) ? 4.0 : 12.0;
            double cardSpacing = (sceneHeight < 750.0) ? 8.0 : 12.0;

            if (cardMarginWrapper != null) {
                VBox.setMargin(cardMarginWrapper, new Insets(2, outerMarginH, cardMarginV, outerMarginH));
            }
            if (confirmedContainer != null) {
                confirmedContainer.setStyle(String.format(Locale.US, "-fx-padding: %.0f %.0f %.0f %.0f;", cardPadV, cardPadH, cardPadV, cardPadH));
                confirmedContainer.setSpacing(cardSpacing);
            }

            double GUTTER = 16.0;
            double vpWidth = -1;
            double vpHeight = -1;

            if (confirmationScrollPane != null && confirmationScrollPane.getViewportBounds() != null) {
                vpWidth = confirmationScrollPane.getViewportBounds().getWidth();
                vpHeight = confirmationScrollPane.getViewportBounds().getHeight();
            }

            if (vpWidth <= 0) {
                vpWidth = sceneWidth - (outerMarginH * 2.0) - (cardPadH * 2.0);
            }
            if (vpHeight <= 0) {
                vpHeight = sceneHeight - 64.0 - 2.0 - cardMarginV - (cardPadV * 2.0) - 64.0 - cardSpacing;
            }

            double availWidth = Math.max(300.0, vpWidth - GUTTER);
            boolean isLarge = sceneWidth >= 1600.0;

            if (isTwoColumn) {
                double sidebarW = isLarge ? 400.0 : ((availWidth < 1050.0) ? 295.0 : 310.0);
                double gap = isLarge ? 22.0 : ((availWidth < 1050.0) ? 14.0 : 18.0);
                double availForTicketW = Math.max(300.0, availWidth - sidebarW - gap);
                double widthFit = availForTicketW / TicketView.DESIGN_WIDTH;
                double availForTicketH = Math.max(300.0, vpHeight - 6.0);
                double heightFit = availForTicketH / TicketView.DESIGN_HEIGHT;

                double rawScale = Math.min(widthFit, heightFit);
                double scale = Math.max(0.90, Math.min(1.25, rawScale));

                if (ticketView != null) {
                    ticketView.setCardScale(scale);
                }

                double scaledTicketW = scale * TicketView.DESIGN_WIDTH;
                double scaledTicketH = scale * TicketView.DESIGN_HEIGHT;

                double spareH = vpHeight - scaledTicketH;
                if (scrollContentRoot != null) {
                    scrollContentRoot.setAlignment(spareH > 2.0 ? Pos.CENTER : Pos.TOP_CENTER);
                }

                double spacing = isLarge ? 10.0 : 8.0;
                if (sidebarContainer != null) {
                    sidebarContainer.setPrefWidth(sidebarW);
                    sidebarContainer.setMinWidth(sidebarW);
                    sidebarContainer.setMaxWidth(sidebarW);
                    sidebarContainer.setPrefHeight(scaledTicketH);
                    sidebarContainer.setMinHeight(scaledTicketH);
                    sidebarContainer.setMaxHeight(scaledTicketH);
                    sidebarContainer.setSpacing(spacing);
                }

                if (paymentBox != null) {
                    paymentBox.setStyle(isLarge ? "-fx-padding: 14 20;" : "-fx-padding: 8 14;");
                    paymentBox.setSpacing(isLarge ? 6.0 : 4.0);
                }
                if (baseFareLabel != null) {
                    baseFareLabel.setStyle(isLarge
                            ? "-fx-font-family: 'Inter Medium'; -fx-font-size: 15px; -fx-text-fill: #ffffff;"
                            : "-fx-font-family: 'Inter Medium'; -fx-font-size: 14px; -fx-text-fill: #ffffff;");
                }
                if (demandPricingLabel != null) {
                    demandPricingLabel.setStyle(isLarge
                            ? "-fx-font-family: 'Inter Medium'; -fx-font-size: 15px; -fx-text-fill: #ffffff;"
                            : "-fx-font-family: 'Inter Medium'; -fx-font-size: 14px; -fx-text-fill: #ffffff;");
                }
                if (taxesFeesLabel != null) {
                    taxesFeesLabel.setStyle(isLarge
                            ? "-fx-font-family: 'Inter Medium'; -fx-font-size: 15px; -fx-text-fill: #ffffff;"
                            : "-fx-font-family: 'Inter Medium'; -fx-font-size: 14px; -fx-text-fill: #ffffff;");
                }
                if (totalPaidLabel != null) {
                    totalPaidLabel.setStyle(isLarge ? "-fx-font-size: 28px;" : "-fx-font-size: 26px;");
                }

                double dlBtnH = isLarge ? 46.0 : 40.0;
                if (downloadPdfButton != null) {
                    downloadPdfButton.setPrefHeight(dlBtnH);
                    downloadPdfButton.setMinHeight(dlBtnH);
                    downloadPdfButton.setMaxHeight(dlBtnH);
                    downloadPdfButton.setStyle(isLarge ? "-fx-font-size: 14.5px;" : "");
                }
                double btnRowH = isLarge ? 42.0 : 36.0;
                if (buttonRow != null) {
                    buttonRow.setPrefHeight(btnRowH);
                    buttonRow.setMinHeight(btnRowH);
                    buttonRow.setMaxHeight(btnRowH);
                }
                if (addToCalendarButton != null) {
                    addToCalendarButton.setStyle(isLarge ? "-fx-font-size: 13.5px;" : "");
                }

                double payH = isLarge ? 172.0 : 141.6;
                double fixedH = payH + dlBtnH + btnRowH + (3.0 * spacing);
                double flexNextStepsH = Math.max(160.0, scaledTicketH - fixedH);

                if (nextStepsBox != null) {
                    VBox.setVgrow(nextStepsBox, Priority.ALWAYS);
                    nextStepsBox.setStyle(isLarge ? "-fx-padding: 12 18;" : "-fx-padding: 8 14;");
                    nextStepsBox.setSpacing(isLarge ? 8.0 : 6.0);
                    nextStepsBox.setMinHeight(160.0);
                    nextStepsBox.setPrefHeight(flexNextStepsH);
                    nextStepsBox.setMaxHeight(flexNextStepsH);
                }

                if (twoColumnBox != null && singleColumnBox != null && ticketView != null && sidebarContainer != null) {
                    twoColumnBox.setAlignment(Pos.CENTER_LEFT);
                    twoColumnBox.setSpacing(gap);
                    double totalTwoColW = scaledTicketW + gap + sidebarW;
                    twoColumnBox.setPrefWidth(totalTwoColW);
                    twoColumnBox.setMinWidth(totalTwoColW);
                    twoColumnBox.setMaxWidth(totalTwoColW);
                    twoColumnBox.setPrefHeight(scaledTicketH);
                    twoColumnBox.setMinHeight(scaledTicketH);
                    twoColumnBox.setMaxHeight(scaledTicketH);

                    if (!twoColumnBox.getChildren().contains(ticketView)) {
                        singleColumnBox.getChildren().clear();
                        singleColumnBox.setVisible(false);
                        singleColumnBox.setManaged(false);
                        twoColumnBox.getChildren().setAll(ticketView, sidebarContainer);
                        twoColumnBox.setVisible(true);
                        twoColumnBox.setManaged(true);
                    }
                }
            } else {
                if (scrollContentRoot != null) {
                    scrollContentRoot.setAlignment(Pos.TOP_CENTER);
                }

                // Reserve 32px so centered single-column box has at least 16px left and 16px right margin
                // ensuring node.maxX <= vpWidth - 16 and node.maxX < vbar.minX - 4
                availWidth = Math.max(300.0, vpWidth - 32.0);

                if (ticketView != null) {
                    ticketView.scaleToAvailableWidth(availWidth);
                }

                double ticketScaledW = ticketView != null ? (ticketView.getCardScale() * TicketView.DESIGN_WIDTH) : 780.0;
                double singleColW = Math.min(availWidth, ticketScaledW);

                double spacing = 8.0;
                if (sidebarContainer != null) {
                    sidebarContainer.setPrefWidth(singleColW);
                    sidebarContainer.setMinWidth(300.0);
                    sidebarContainer.setMaxWidth(singleColW);
                    sidebarContainer.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    sidebarContainer.setMinHeight(Region.USE_COMPUTED_SIZE);
                    sidebarContainer.setMaxHeight(Double.MAX_VALUE);
                    sidebarContainer.setSpacing(spacing);
                }
                if (paymentBox != null) {
                    paymentBox.setStyle("-fx-padding: 8 16;");
                    paymentBox.setSpacing(4.0);
                }
                if (baseFareLabel != null) {
                    baseFareLabel.setStyle("-fx-font-family: 'Inter Medium'; -fx-font-size: 14px; -fx-text-fill: #ffffff;");
                }
                if (demandPricingLabel != null) {
                    demandPricingLabel.setStyle("-fx-font-family: 'Inter Medium'; -fx-font-size: 14px; -fx-text-fill: #ffffff;");
                }
                if (taxesFeesLabel != null) {
                    taxesFeesLabel.setStyle("-fx-font-family: 'Inter Medium'; -fx-font-size: 14px; -fx-text-fill: #ffffff;");
                }
                if (totalPaidLabel != null) {
                    totalPaidLabel.setStyle("-fx-font-size: 26px;");
                }
                if (nextStepsBox != null) {
                    nextStepsBox.setStyle("-fx-padding: 8 16;");
                    nextStepsBox.setSpacing(6.0);
                    nextStepsBox.setMinHeight(160.0);
                    nextStepsBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    nextStepsBox.setMaxHeight(Double.MAX_VALUE);
                }
                if (downloadPdfButton != null) {
                    downloadPdfButton.setPrefHeight(40.0);
                    downloadPdfButton.setMinHeight(36.0);
                    downloadPdfButton.setMaxHeight(44.0);
                    downloadPdfButton.setStyle("");
                }
                if (buttonRow != null) {
                    buttonRow.setPrefHeight(36.0);
                    buttonRow.setMinHeight(32.0);
                    buttonRow.setMaxHeight(40.0);
                }
                if (addToCalendarButton != null) {
                    addToCalendarButton.setStyle("");
                }

                if (twoColumnBox != null && singleColumnBox != null && ticketView != null && sidebarContainer != null) {
                    singleColumnBox.setAlignment(Pos.TOP_CENTER);
                    singleColumnBox.setSpacing(12.0);
                    singleColumnBox.setPadding(new Insets(0, 0, 16, 0));
                    singleColumnBox.setPrefWidth(singleColW);
                    singleColumnBox.setMaxWidth(singleColW);

                    if (!singleColumnBox.getChildren().contains(ticketView)) {
                        twoColumnBox.getChildren().clear();
                        twoColumnBox.setVisible(false);
                        twoColumnBox.setManaged(false);
                        singleColumnBox.getChildren().setAll(ticketView, sidebarContainer);
                        singleColumnBox.setVisible(true);
                        singleColumnBox.setManaged(true);
                    }
                }
            }
        } finally {
            updatingLayout = false;
        }
    }

    // Additional Getters for Verifier and Testing
    public VBox getSidebarContainer() { return sidebarContainer; }
    public VBox getPaymentBox() { return paymentBox; }
    public VBox getNextStepsBox() { return nextStepsBox; }
    public ScrollPane getConfirmationScrollPane() { return confirmationScrollPane; }
    public HBox getTwoColumnBox() { return twoColumnBox; }
    public VBox getSingleColumnBox() { return singleColumnBox; }
    public StackPane getCardMarginWrapper() { return cardMarginWrapper; }

    public Label getErrorTitleLabel() { return errorTitleLabel; }
    public Label getErrorMessageLabel() { return errorMessageLabel; }
}
