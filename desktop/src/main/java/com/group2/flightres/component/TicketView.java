package com.group2.flightres.component;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.TicketData;
import com.group2.flightres.util.BarcodeGenerator;
import com.group2.flightres.util.DestinationPhotos;
import com.group2.flightres.util.FontLoader;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

/**
 * Reusable full-width boarding pass ticket component.
 * Displays destination photo cover panel, route details, passenger metadata grid,
 * Code 128 barcode strip (dark bars on light background), and tear-off stub.
 */
public class TicketView extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(TicketView.class);

    public static final double DESIGN_WIDTH = 780.0;
    public static final double DESIGN_HEIGHT = 575.0;
    public static final double MIN_SCALE = 0.90;
    public static final double MAX_SCALE = 1.25;

    @FXML private Group scaleGroup;

    @FXML private StackPane designContainer;
    @FXML private HBox ticketCardBox;

    @FXML private StackPane photoPanel;
    @FXML private ImageView photoImageView;
    @FXML private Region photoGradientOverlay;
    @FXML private Label welcomeCityLabel;

    @FXML private VBox ticketBodyBox;
    @FXML private Label cabinBadgeLabel;
    @FXML private Label originCodeLabel;
    @FXML private Label originCityTimeLabel;
    @FXML private Label durationLabel;
    @FXML private Label destCodeLabel;
    @FXML private Label destCityTimeLabel;

    @FXML private GridPane metaGrid;
    @FXML private Label passengerNameLabel;
    @FXML private Label flightNumberLabel;
    @FXML private Label flightDateLabel;
    @FXML private Label boardingTimeLabel;

    @FXML private StackPane barcodeContainer;
    @FXML private ImageView barcodeImageView;
    @FXML private Region perforationDivider;

    @FXML private VBox stubBox;
    @FXML private Label stubSeatLabel;
    @FXML private Label gateValueLabel;
    @FXML private Label gateNoteLabel;
    @FXML private Label stubReferenceLabel;
    @FXML private VBox stubFareContainer;
    @FXML private Label stubFareLabel;

    @FXML private Circle topNotch;
    @FXML private Circle bottomNotch;

    private TicketData ticketData;
    private double currentScale = 1.0;

    public TicketView() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/ticket-view.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            logger.error("Failed to load ticket-view.fxml", e);
            throw new IllegalStateException("Failed to load ticket-view.fxml", e);
        }
    }

    @FXML
    public void initialize() {
        FontLoader.loadFonts();
        setCardScale(1.0);

        // Ensure glass.css stylesheet is attached
        String cssUrl = getClass().getResource("/com/group2/flightres/css/glass.css") != null
                ? getClass().getResource("/com/group2/flightres/css/glass.css").toExternalForm() : null;
        if (cssUrl != null && !getStylesheets().contains(cssUrl)) {
            getStylesheets().add(cssUrl);
        }

        // Clip the photo panel to the top-left and bottom-left rounded corners
        if (photoPanel != null) {
            Rectangle clip = new Rectangle();
            clip.setArcWidth(48);
            clip.setArcHeight(48);
            clip.setWidth(165 + 30);
            clip.setHeight(575);
            photoPanel.setClip(clip);

            // Re-crop photo whenever panel dimensions change
            photoPanel.widthProperty().addListener((obs, oldVal, newVal) -> updatePhotoCoverCrop());
            photoPanel.heightProperty().addListener((obs, oldVal, newVal) -> updatePhotoCoverCrop());
        }

        // Notch position updating
        widthProperty().addListener((obs, oldVal, newVal) -> updateNotches());

        // Listen for ticket body width changes to resize barcode crisply to fill content width
        if (ticketBodyBox != null) {
            ticketBodyBox.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (ticketData != null) {
                    renderBarcode(ticketData.bookingReference());
                }
            });
        }
    }

    /**
     * Binds the component to immutable TicketData.
     */
    public void setBooking(TicketData data) {
        if (data == null) {
            throw new IllegalStateException("TicketData cannot be null in TicketView.setBooking()");
        }
        this.ticketData = data;
        populate();
    }

    /**
     * Convenience factory binding from BookingContext.
     */
    public void setBooking(BookingContext context) {
        setBooking(TicketData.from(context));
    }

    /**
     * Convenience factory binding from BookingRecord.
     */
    public void setBooking(BookingRecord record) {
        setBooking(TicketData.from(record));
    }

    private void populate() {
        if (ticketData == null) return;

        // 1. Photo Panel
        String destCode = ticketData.destinationCode();
        Optional<Image> photoOpt = DestinationPhotos.getImage(destCode);
        if (photoOpt.isPresent() && photoImageView != null) {
            photoImageView.setImage(photoOpt.get());
            photoImageView.setVisible(true);
            updatePhotoCoverCrop();
            if (photoGradientOverlay != null) {
                photoGradientOverlay.getStyleClass().setAll("ticket-photo-gradient");
            }
        } else {
            if (photoImageView != null) {
                photoImageView.setImage(null);
                photoImageView.setVisible(false);
            }
            if (photoGradientOverlay != null) {
                photoGradientOverlay.getStyleClass().setAll("ticket-photo-gradient-fallback");
            }
        }

        if (welcomeCityLabel != null) {
            welcomeCityLabel.setText(shortCity(ticketData.destinationCity(), ticketData.destinationCode()));
        }

        // 2. Ticket Body
        if (cabinBadgeLabel != null) {
            cabinBadgeLabel.setText(ticketData.cabin());
        }
        if (originCodeLabel != null) {
            originCodeLabel.setText(ticketData.originCode());
        }
        if (originCityTimeLabel != null) {
            originCityTimeLabel.setText(shortCity(ticketData.originCity(), ticketData.originCode()) + " · " + ticketData.departureTime());
        }
        if (durationLabel != null) {
            durationLabel.setText(ticketData.duration());
        }
        if (destCodeLabel != null) {
            destCodeLabel.setText(ticketData.destinationCode());
        }
        if (destCityTimeLabel != null) {
            destCityTimeLabel.setText(shortCity(ticketData.destinationCity(), ticketData.destinationCode()) + " · " + ticketData.arrivalTime());
        }

        if (passengerNameLabel != null) {
            passengerNameLabel.setText(ticketData.passenger());
        }
        if (flightNumberLabel != null) {
            flightNumberLabel.setText(ticketData.flightNumber());
        }
        if (flightDateLabel != null) {
            flightDateLabel.setText(ticketData.formattedDate());
        }
        if (boardingTimeLabel != null) {
            boardingTimeLabel.setText(ticketData.boardingTime());
        }

        // 3. Barcode
        renderBarcode(ticketData.bookingReference());

        // 4. Stub
        if (stubSeatLabel != null) {
            stubSeatLabel.setText(ticketData.seatLabel());
        }
        if (gateValueLabel != null) {
            gateValueLabel.setText("Shown at the airport");
        }
        if (gateNoteLabel != null) {
            gateNoteLabel.setText(BoardingPolicy.GATE_NOTE);
        }
        if (stubReferenceLabel != null) {
            stubReferenceLabel.setText(ticketData.bookingReference());
        }
        if (stubFareContainer != null && stubFareLabel != null) {
            if (ticketData.totalPrice() != null) {
                stubFareLabel.setText(com.group2.flightres.util.MoneyFormatter.format(ticketData.totalPrice()));
                stubFareContainer.setVisible(true);
                stubFareContainer.setManaged(true);
            } else {
                stubFareContainer.setVisible(false);
                stubFareContainer.setManaged(false);
            }
        }

        updateNotches();
    }

    private static String shortCity(String city, String code) {
        String c = (city != null ? city.trim() : "");
        String lower = c.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("tambo") || lower.contains("johannesburg") || "JNB".equalsIgnoreCase(code)) return "Johannesburg";
        if (lower.contains("cape town") || "CPT".equalsIgnoreCase(code)) return "Cape Town";
        if (lower.contains("shaka") || lower.contains("durban") || "DUR".equalsIgnoreCase(code)) return "Durban";
        if (lower.contains("stuurman") || lower.contains("gqeberha") || "PLZ".equalsIgnoreCase(code)) return "Gqeberha";
        if (lower.contains("geneva") || "GVA".equalsIgnoreCase(code)) return "Geneva";
        if (lower.contains("nice") || lower.contains("monaco") || "NCE".equalsIgnoreCase(code)) return "Monaco";
        if (lower.contains("dubai") || "DXB".equalsIgnoreCase(code)) return "Dubai";
        if (!c.isEmpty()) return c;
        return code != null ? code : "";
    }

    /**
     * Center-crops the destination photo to fully cover photoPanel height & width without distortion.
     */
    public void updatePhotoCoverCrop() {
        if (photoImageView == null || photoPanel == null) return;
        Image img = photoImageView.getImage();
        if (img == null) return;
        if (img.getProgress() < 1.0) {
            img.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() >= 1.0) {
                    updatePhotoCoverCrop();
                }
            });
            return;
        }
        if (img.getWidth() <= 0 || img.getHeight() <= 0) return;

        double panelW = photoPanel.getWidth() > 0 ? photoPanel.getWidth() : (photoPanel.getPrefWidth() > 0 ? photoPanel.getPrefWidth() : 165.0);
        double panelH = photoPanel.getHeight() > 0 ? photoPanel.getHeight() : (photoPanel.getPrefHeight() > 0 ? photoPanel.getPrefHeight() : 575.0);

        double imgW = img.getWidth();
        double imgH = img.getHeight();

        double panelRatio = panelW / panelH;
        double imgRatio = imgW / imgH;

        double cropX = 0;
        double cropY = 0;
        double cropW = imgW;
        double cropH = imgH;

        if (panelRatio < imgRatio) {
            // Panel is narrower than image -> crop horizontal edges, center crop
            cropH = imgH;
            cropW = imgH * panelRatio;
            cropX = (imgW - cropW) / 2.0;
            cropY = 0;
        } else {
            // Panel is wider than image -> crop vertical edges, center crop
            cropW = imgW;
            cropH = imgW / panelRatio;
            cropX = 0;
            cropY = (imgH - cropH) / 2.0;
        }

        photoImageView.setViewport(new Rectangle2D(cropX, cropY, cropW, cropH));
        photoImageView.setFitWidth(panelW);
        photoImageView.setFitHeight(panelH);
        photoImageView.setPreserveRatio(false);
    }

    /**
     * Renders a Code 128 barcode as dark bars on a light strip with quiet zones,
     * spanning the ticket body content width.
     */
    private void renderBarcode(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank() || barcodeImageView == null) return;
        try {
            // Tightened white strip with bars filling >= 85% of width, height ~44px
            BufferedImage bimg = BarcodeGenerator.generateCode128Image(bookingRef, 44, 2.4f, Color.BLACK, Color.WHITE);
            if (bimg != null) {
                barcodeImageView.setImage(toFXImage(bimg));
                barcodeImageView.setFitWidth(bimg.getWidth());
                barcodeImageView.setFitHeight(44);
                barcodeImageView.setSmooth(false);
                if (barcodeContainer != null) {
                    barcodeContainer.setMaxWidth(bimg.getWidth());
                    barcodeContainer.setPrefWidth(bimg.getWidth());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not generate barcode image in TicketView: {}", e.getMessage());
        }
    }

    private void updateNotches() {
        if (ticketCardBox == null || stubBox == null || topNotch == null || bottomNotch == null) return;
        double stubWidth = stubBox.getWidth() > 0 ? stubBox.getWidth() : 190;
        double totalWidth = getWidth() > 0 ? getWidth() : ticketCardBox.getWidth();
        if (totalWidth <= 0) return;

        double notchOffsetFromRight = stubWidth - 14.0;
        topNotch.setTranslateX(-notchOffsetFromRight);
        topNotch.setTranslateY(-14.0);
        bottomNotch.setTranslateX(-notchOffsetFromRight);
        bottomNotch.setTranslateY(14.0);
    }

    private static Image toFXImage(BufferedImage bimg) {
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

    // Getters for testing and verifier
    public TicketData getTicketData() { return ticketData; }
    public HBox getTicketCardBox() { return ticketCardBox; }
    public StackPane getPhotoPanel() { return photoPanel; }
    public ImageView getPhotoImageView() { return photoImageView; }
    public Region getPhotoGradientOverlay() { return photoGradientOverlay; }
    public Label getWelcomeCityLabel() { return welcomeCityLabel; }
    public VBox getTicketBodyBox() { return ticketBodyBox; }
    public Label getCabinBadgeLabel() { return cabinBadgeLabel; }
    public Label getOriginCodeLabel() { return originCodeLabel; }
    public Label getDestCodeLabel() { return destCodeLabel; }
    public Label getPassengerNameLabel() { return passengerNameLabel; }
    public Label getFlightNumberLabel() { return flightNumberLabel; }
    public Label getFlightDateLabel() { return flightDateLabel; }
    public Label getBoardingTimeLabel() { return boardingTimeLabel; }
    public StackPane getBarcodeContainer() { return barcodeContainer; }
    public ImageView getBarcodeImageView() { return barcodeImageView; }
    public VBox getStubBox() { return stubBox; }
    public Label getStubSeatLabel() { return stubSeatLabel; }
    public Label getGateValueLabel() { return gateValueLabel; }
    public Label getGateNoteLabel() { return gateNoteLabel; }
    public Label getStubReferenceLabel() { return stubReferenceLabel; }
    public VBox getStubFareContainer() { return stubFareContainer; }
    public Label getStubFareLabel() { return stubFareLabel; }

    /**
     * Sets the uniform scale of the ticket, clamped to [0.90, 1.25],
     * and reports the exact scaled size to the layout.
     */
    public void setCardScale(double scale) {
        double clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        this.currentScale = clamped;
        if (designContainer != null) {
            designContainer.setScaleX(clamped);
            designContainer.setScaleY(clamped);
        }
        double w = Math.round(DESIGN_WIDTH * clamped);
        double h = Math.round(DESIGN_HEIGHT * clamped);
        setMinWidth(w);
        setPrefWidth(w);
        setMaxWidth(w);
        setMinHeight(h);
        setPrefHeight(h);
        setMaxHeight(h);
        updatePhotoCoverCrop();
        requestLayout();
    }

    public double getCardScale() {
        return currentScale;
    }

    public void scaleToAvailableWidth(double availableWidth) {
        if (availableWidth <= 0) return;
        setCardScale(availableWidth / DESIGN_WIDTH);
    }

    public Group getScaleGroup() { return scaleGroup; }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (designContainer != null) {
            designContainer.setScaleX(currentScale);
            designContainer.setScaleY(currentScale);
        }
        updatePhotoCoverCrop();
    }

    @Override
    protected double computeMinWidth(double height) {
        return DESIGN_WIDTH * currentScale;
    }

    @Override
    protected double computeMinHeight(double width) {
        return DESIGN_HEIGHT * currentScale;
    }

    @Override
    protected double computePrefWidth(double height) {
        return DESIGN_WIDTH * currentScale;
    }

    @Override
    protected double computePrefHeight(double width) {
        return DESIGN_HEIGHT * currentScale;
    }

    @Override
    protected double computeMaxWidth(double height) {
        return DESIGN_WIDTH * currentScale;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return DESIGN_HEIGHT * currentScale;
    }

    public StackPane getDesignContainer() { return designContainer; }
}
