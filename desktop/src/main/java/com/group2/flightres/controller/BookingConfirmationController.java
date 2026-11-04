package com.group2.flightres.controller;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.util.FlightDateFormatter;
import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.util.BarcodeGenerator;
import com.group2.flightres.util.BoardingPassPdfGenerator;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.IcsCalendarGenerator;
import com.group2.flightres.util.Motion;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Controller for SkyVoyage / Altitude Screen 05 / 06 Booking Confirmation.
 */
public class BookingConfirmationController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(BookingConfirmationController.class);

    // Containers
    @FXML private VBox errorContainer;
    @FXML private VBox confirmedContainer;
    @FXML private Button errorBackButton;

    // Header controls
    @FXML private Label confirmationTitleLabel;
    @FXML private Label confirmationSubtitleLabel;
    @FXML private Label bookingRefLabel;
    @FXML private Label avatarInitialsLabel;

    // Ticket controls
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
        populateData();
    }

    @FXML
    public void initialize() {
        FontLoader.loadFonts();
        populateData();

        if (downloadPdfButton != null) Motion.attachHoverLift(downloadPdfButton);
        if (addToCalendarButton != null) Motion.attachHoverLift(addToCalendarButton);
        if (errorBackButton != null) Motion.attachHoverLift(errorBackButton);

        // Position ticket perforation notches dynamically on stub width
        if (ticketWrapperPane != null && stubBox != null) {
            ticketWrapperPane.widthProperty().addListener((obs, oldVal, newVal) -> updateNotchPositions());
        }
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

    private void populateData() {
        // 1. Error state check: null context or null/blank booking reference
        boolean hasBooking = (bookingContext != null
                && bookingContext.getBookingReference() != null
                && !bookingContext.getBookingReference().isBlank());

        if (!hasBooking) {
            if (errorContainer != null) {
                errorContainer.setVisible(true);
                errorContainer.setManaged(true);
            }
            if (confirmedContainer != null) {
                confirmedContainer.setVisible(false);
                confirmedContainer.setManaged(false);
            }
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
        String pnr = bookingContext.getBookingReference().trim().toUpperCase(Locale.ROOT);
        if (bookingRefLabel != null) {
            bookingRefLabel.setText(pnr);
        }

        String email = bookingContext.getPassengerEmail();
        if (confirmationSubtitleLabel != null) {
            if (email != null && !email.isBlank()) {
                confirmationSubtitleLabel.setText("Booking confirmation for " + email.trim());
            } else {
                confirmationSubtitleLabel.setText("Booking confirmation");
            }
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

        String originStr = bookingContext.getOrigin();
        String destStr = bookingContext.getDestination();
        String originCode = extractAirportCode(originStr, "JNB");
        String destCode = extractAirportCode(destStr, "CPT");
        String originCity = resolveCityName(originStr, originCode);
        String destCity = resolveCityName(destStr, destCode);

        String depTime = bookingContext.getDepartureTime() != null ? bookingContext.getDepartureTime() : "06:30";
        String arrTime = bookingContext.getArrivalTime() != null ? bookingContext.getArrivalTime() : "08:35";

        if (originCodeLabel != null) originCodeLabel.setText(originCode);
        if (originCityTimeLabel != null) originCityTimeLabel.setText(originCity + " · " + depTime);

        if (destCodeLabel != null) destCodeLabel.setText(destCode);
        if (destCityTimeLabel != null) destCityTimeLabel.setText(destCity + " · " + arrTime);

        if (durationLabel != null) {
            durationLabel.setText(calculateDuration(depTime, arrTime));
        }

        // Grid fields
        if (passengerNameLabel != null) {
            passengerNameLabel.setText(passengerName != null && !passengerName.isBlank() ? passengerName : "Passenger");
        }
        if (flightNumberLabel != null) {
            String fn = bookingContext.getFlightNumber();
            flightNumberLabel.setText(fn != null && !fn.isBlank() ? fn : "FL 204");
        }
        if (flightDateLabel != null) {
            flightDateLabel.setText(FlightDateFormatter.format(bookingContext.getDepartureDate()));
        }
        if (boardingTimeLabel != null) {
            boardingTimeLabel.setText(BoardingPolicy.formatBoardingTime(depTime));
        }

        // Real Code 128 Barcode Image: crisp, no smoothing blur, filling ticket-body width
        if (barcodeImageView != null) {
            try {
                if (barcodeImageView.getParent() instanceof Region parentRegion) {
                    barcodeImageView.fitWidthProperty().bind(parentRegion.widthProperty());
                }
                BufferedImage bimg = BarcodeGenerator.generateCode128Image(pnr, 48, 4.0f, Color.WHITE, new Color(0, 0, 0, 0));
                if (bimg != null) {
                    barcodeImageView.setImage(toFXImage(bimg));
                }
            } catch (Exception bex) {
                logger.warn("Could not generate barcode image: {}", bex.getMessage());
            }
        }

        // 4. Populate Ticket Stub (SEAT top, GATE/note middle, note bottom)
        String seat = bookingContext.getSeatLabel();
        if (stubSeatLabel != null) {
            stubSeatLabel.setText(seat != null && !seat.isBlank() ? seat : "9E");
        }
        if (gateValueLabel != null) {
            gateValueLabel.setText("Shown at the airport");
        }
        if (gateNoteLabel != null) {
            gateNoteLabel.setText(BoardingPolicy.GATE_NOTE + ".");
        }

        // 5. Populate Payment Summary: THREE rows (Base fare, Demand pricing, Taxes and fees) then Paid
        BigDecimal base = bookingContext.getBaseFare() != null ? bookingContext.getBaseFare() : new BigDecimal("1200.00");
        BigDecimal demand = bookingContext.getDemandPricing() != null ? bookingContext.getDemandPricing() : new BigDecimal("180.00");
        BigDecimal taxes = bookingContext.getTaxesAndFees() != null ? bookingContext.getTaxesAndFees() : new BigDecimal("142.00");
        BigDecimal total = bookingContext.getTotalPrice() != null ? bookingContext.getTotalPrice() : base.add(demand).add(taxes);

        if (baseFareLabel != null) baseFareLabel.setText(formatExactPrice(base));
        if (demandPricingLabel != null) demandPricingLabel.setText(formatExactPrice(demand));
        if (taxesFeesLabel != null) taxesFeesLabel.setText(formatExactPrice(taxes));
        if (totalPaidLabel != null) totalPaidLabel.setText(formatTotalText(total));

        // 6. Populate What Happens Next Timeline: Bag drop uses airport NAME from DB
        if (timelineSeatConfirmedLabel != null) {
            timelineSeatConfirmedLabel.setText("Seat " + (seat != null ? seat : "9E") + " is yours");
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
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM airports WHERE UPPER(iata_code) = UPPER(?) OR UPPER(city) = UPPER(?) LIMIT 1")) {
            ps.setString(1, code != null ? code.trim() : "");
            String cityPart = (raw != null && raw.contains("(")) ? raw.substring(0, raw.indexOf('(')).trim() : (raw != null ? raw.trim() : "");
            ps.setString(2, cityPart);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.isBlank()) return name;
                }
            }
        } catch (Exception ignored) {}
        return "O. R. Tambo International Airport";
    }

    private String resolveCityName(String raw, String code) {
        if (raw != null && raw.contains("(")) {
            String cityPart = raw.substring(0, raw.indexOf('(')).trim();
            if (!cityPart.isBlank()) return cityPart;
        }
        // Query airports DB table
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT city FROM airports WHERE UPPER(iata_code) = UPPER(?) OR UPPER(city) = UPPER(?) LIMIT 1")) {
            ps.setString(1, code);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String c = rs.getString(1);
                    if (c != null && !c.isBlank()) return c;
                }
            }
        } catch (Exception ignored) {}
        return code;
    }

    private String calculateDuration(String depTimeStr, String arrTimeStr) {
        try {
            LocalTime dep = BoardingPolicy.parseTime(depTimeStr);
            LocalTime arr = BoardingPolicy.parseTime(arrTimeStr);
            java.time.Duration dur = java.time.Duration.between(dep, arr);
            if (dur.isNegative()) {
                dur = dur.plusDays(1);
            }
            long hours = dur.toHours();
            long mins = dur.toMinutesPart();
            return String.format(Locale.ROOT, "%dh %02dm", hours, mins);
        } catch (Exception e) {
            return "2h 05m";
        }
    }

    private String formatDateBadge(String depDate) {
        if (depDate == null || depDate.isBlank()) {
            return "Fri, 14 Nov 2026";
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
        if (price == null) return "R 0";
        return String.format(Locale.US, "R %,d", price.longValue());
    }

    private String formatTotalText(BigDecimal price) {
        if (price == null) return "R 0";
        return String.format(Locale.US, "R %,d", price.longValue());
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
            if (!Boolean.getBoolean("skyvoyage.testing") && !Boolean.getBoolean("skyvoyage.screenshots")) {
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
            if (!Boolean.getBoolean("skyvoyage.testing") && !Boolean.getBoolean("skyvoyage.screenshots")) {
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
            if (!Boolean.getBoolean("skyvoyage.testing") && !Boolean.getBoolean("skyvoyage.screenshots")) {
                successAlert.show();
            }

        } catch (Exception e) {
            logger.error("Failed to generate calendar .ics file", e);
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Calendar Export Failed");
            errorAlert.setHeaderText("Error Creating Calendar Event");
            errorAlert.setContentText("Could not create .ics file: " + e.getMessage());
            if (!Boolean.getBoolean("skyvoyage.testing") && !Boolean.getBoolean("skyvoyage.screenshots")) {
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
    @FXML private void handleComingSoonMyBookings() { showComingSoonAlert("My Bookings"); }
    @FXML private void handleComingSoonOffers() { showComingSoonAlert("Offers"); }
    @FXML private void handleNavAbout() { showComingSoonAlert("About"); }
    @FXML private void handleNavContact() { showComingSoonAlert("Contact"); }
    @FXML private void handleNavAvatar() { showComingSoonAlert("Profile"); }

    private void showComingSoonAlert(String feature) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, feature + " isn't available yet.");
        alert.setHeaderText("Coming soon");
        if (!Boolean.getBoolean("skyvoyage.testing") && !Boolean.getBoolean("skyvoyage.screenshots")) {
            alert.showAndWait();
        }
    }

    // Getters for verifier / screenshot testing
    public BookingContext getBookingContext() { return bookingContext; }
    public VBox getErrorContainer() { return errorContainer; }
    public VBox getConfirmedContainer() { return confirmedContainer; }
    public Label getBookingRefLabel() { return bookingRefLabel; }
    public Label getPassengerNameLabel() { return passengerNameLabel; }
    public Label getStubSeatLabel() { return stubSeatLabel; }
    public Label getDemandPricingLabel() { return demandPricingLabel; }
    public Label getTotalPaidLabel() { return totalPaidLabel; }
    public Label getGateValueLabel() { return gateValueLabel; }
    public Label getGateNoteLabel() { return gateNoteLabel; }
    public ImageView getBarcodeImageView() { return barcodeImageView; }
    public Button getDownloadPdfButton() { return downloadPdfButton; }
    public Button getAddToCalendarButton() { return addToCalendarButton; }
    public Button getManageBookingButton() { return manageBookingButton; }
}
