package com.group2.flightres.tools;

import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Phase 2 Glass Checkout screen at 1280x860 and captures
 * docs/screenshots/glass-checkout.png with real data matching design-glass/checkout.html.
 */
public class GlassCheckoutScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassCheckoutScreenshotRunner.class);
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 860;

    private static Path getOutputDir() {
        File shadowDocs = new File("c:/Users/tttttttttttttttttttt/Desktop/flight-reservation-system-shadow/docs/screenshots");
        if (shadowDocs.exists()) {
            return shadowDocs.toPath();
        }
        if (new File("..", "docs/screenshots").exists()) {
            return Path.of("..", "docs", "screenshots");
        }
        return Path.of("docs", "screenshots");
    }

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("altitude.screenshots", "true");
        FontLoader.loadFonts();

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setDepartureDate("2026-11-14");
        ctx.setBaseFare(new BigDecimal("1200"));

        // 9E held seat matching target design
        FlightSeat seat9E = new FlightSeat();
        seat9E.setFlightId(1L);
        seat9E.setSeatRow(9);
        seat9E.setSeatCol('E');
        seat9E.setSeatClass(SeatClass.ECONOMY);
        seat9E.setStatus(SeatStatus.HELD);
        seat9E.setCalculatedPrice(new BigDecimal("1380"));

        List<FlightSeat> seats = new ArrayList<>();
        seats.add(seat9E);
        ctx.setSelectedSeats(seats);

        PriceQuote quote = PriceQuote.calculate(new BigDecimal("1200.00"), BigDecimal.ONE, 0.0, 100.0);
        ctx.setPriceQuotes(List.of(quote));
        ctx.setTotalPrice(quote.total());

        // Set hold expiry to 282 seconds from now -> countdown shows 04:42
        ctx.setHoldExpiresAt(Instant.now().plusSeconds(282));

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
        Parent root = loader.load();

        CheckoutController controller = loader.getController();
        controller.init(ctx, () -> {}, () -> {});

        // Populate fields with sample passenger matching design-glass/checkout.html
        if (controller.getFirstNameField() != null) controller.getFirstNameField().setText("Amahle");
        if (controller.getLastNameField() != null) controller.getLastNameField().setText("Mokoena");
        if (controller.getPassportField() != null) controller.getPassportField().setText("A91827364");
        if (controller.getDobField() != null) controller.getDobField().setText("12 May 1998");
        if (controller.getEmailField() != null) controller.getEmailField().setText("amahle@example.com");

        if (controller.getCardNumberField() != null) controller.getCardNumberField().setText("4242 4242 4242 4242");
        if (controller.getExpiryField() != null) controller.getExpiryField().setText("08 / 28");
        if (controller.getCvvField() != null) controller.getCvvField().setText("123");

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        ThemeManager.getInstance().applyGlassTheme(scene);

        stage.setTitle("SkyVoyage Glass Checkout Preview");
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setResizable(false);
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.millis(900));
        pause.setOnFinished(e -> {
            root.applyCss();
            root.layout();
            captureSnapshot(root, "glass-checkout.png");
            logger.info("Checkout screenshot capture completed successfully.");
            Platform.exit();
        });
        pause.play();
    }

    private static void captureSnapshot(Parent root, String filename) {
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        WritableImage snapshot = new WritableImage(WIDTH, HEIGHT);
        snapshot = root.snapshot(params, snapshot);
        File outputFile = getOutputDir().resolve(filename).toFile();
        try {
            saveImageToPng(snapshot, outputFile);
            logger.info("Successfully saved screenshot: {}", outputFile.getAbsolutePath());

            // Also copy to artifact directory if present
            Path brainArtifact = Path.of("C:\\Users\\tttttttttttttttttttt\\.gemini\\antigravity\\brain\\dd68c2a6-b6fe-4136-9f98-988170ed66c9", filename);
            try {
                Files.copy(outputFile.toPath(), brainArtifact, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied screenshot to artifact directory: {}", brainArtifact.toAbsolutePath());
            } catch (Exception ex) {
                logger.warn("Could not copy to brain artifact dir: {}", ex.getMessage());
            }
        } catch (IOException ex) {
            logger.error("Failed to save screenshot {}", filename, ex);
        }
    }

    private static void saveImageToPng(WritableImage image, File targetFile) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] buffer = new int[width * height];
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), buffer, 0, width);
        bufferedImage.setRGB(0, 0, width, height, buffer, 0, width);

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ImageIO.write(bufferedImage, "png", targetFile);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
