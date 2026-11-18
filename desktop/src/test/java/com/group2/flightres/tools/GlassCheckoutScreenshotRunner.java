package com.group2.flightres.tools;

import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.TestFlightFinder;
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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Glass Checkout screen at 1280x800 and 900x680
 * with real data on an open ALT flight.
 */
public class GlassCheckoutScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassCheckoutScreenshotRunner.class);

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

        TestFlightFinder.OpenAltFlight flight = TestFlightFinder.findEarliestOpenAltFlight();
        SeatMapService seatMapService = new SeatMapService(new SeatDaoImpl());
        PricingService pricingService = new PricingService();
        PriceQuote quote = pricingService.quote(flight.flightId(), SeatClass.ECONOMY, Clock.systemUTC());

        FlightSeat selectedSeat = flight.availableSeats().stream()
                .filter(s -> s.getSeatRow() >= 7 && s.getSeatClass() == SeatClass.ECONOMY)
                .findFirst()
                .orElse(flight.availableSeats().get(0));

        seatMapService.selectSeat(selectedSeat);

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(flight.flightId());
        ctx.setFlightNumber(flight.flightNumber());
        ctx.setOrigin(flight.originAirportName());
        ctx.setDestination(flight.destAirportName());
        ctx.setDepartureTime(flight.departureTime());
        ctx.setArrivalTime(flight.arrivalTime());
        ctx.setDepartureDate(flight.departureDate());
        ctx.setDepartureInstant(flight.departureInstant());
        ctx.setArrivalInstant(flight.arrivalInstant());
        ctx.setAircraftModel(flight.aircraftModel());
        ctx.setDuration(flight.duration());
        ctx.setBaseFare(flight.basePrice());

        selectedSeat.setStatus(SeatStatus.HELD);
        selectedSeat.setCalculatedPrice(quote.total());
        ctx.setSelectedSeats(new ArrayList<>(List.of(selectedSeat)));
        ctx.setPriceQuotes(List.of(quote));
        ctx.setTotalPrice(quote.total());

        // Set hold expiry to 282 seconds from now -> countdown shows 04:42
        ctx.setHoldExpiresAt(Instant.now().plusSeconds(282));

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
        Parent root = loader.load();

        CheckoutController controller = loader.getController();
        controller.init(ctx, () -> {}, () -> {});

        // Populate fields with sample passenger matching design
        if (controller.getFirstNameField() != null) controller.getFirstNameField().setText("Amahle");
        if (controller.getLastNameField() != null) controller.getLastNameField().setText("Mokoena");
        if (controller.getPassportField() != null) controller.getPassportField().setText("A91827364");
        if (controller.getDobField() != null) controller.getDobField().setText("12 May 1998");
        if (controller.getEmailField() != null) controller.getEmailField().setText("amahle@example.com");

        if (controller.getCardNumberField() != null) controller.getCardNumberField().setText("4242 4242 4242 4242");
        if (controller.getExpiryField() != null) controller.getExpiryField().setText("08 / 28");
        if (controller.getCvvField() != null) controller.getCvvField().setText("123");

        Scene scene = new Scene(root, 1280, 800);
        ThemeManager.getInstance().applyGlassTheme(scene);

        stage.setTitle("Altitude Glass Checkout Preview");
        stage.setScene(scene);
        stage.setWidth(1280);
        stage.setHeight(800);
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.millis(900));
        pause.setOnFinished(e -> {
            root.applyCss();
            root.layout();
            captureSnapshot(root, "glass-checkout.png", 1280, 800);

            // Resize to 900x680
            stage.setWidth(900);
            stage.setHeight(680);

            PauseTransition p2 = new PauseTransition(Duration.millis(700));
            p2.setOnFinished(e2 -> {
                root.applyCss();
                root.layout();
                captureSnapshot(root, "glass-checkout-900.png", 900, 680);

                // Release seat before exiting
                seatMapService.deselectSeat(selectedSeat);
                logger.info("Checkout screenshots captured successfully at 1280 and 900.");
                Platform.exit();
            });
            p2.play();
        });
        pause.play();
    }

    private static void captureSnapshot(Parent root, String filename, int w, int h) {
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        WritableImage snapshot = new WritableImage(w, h);
        snapshot = root.snapshot(params, snapshot);
        File outputFile = getOutputDir().resolve(filename).toFile();
        try {
            saveImageToPng(snapshot, outputFile);
            logger.info("Successfully saved screenshot: {}", outputFile.getAbsolutePath());

            Path brainArtifact = Path.of("C:\\Users\\tttttttttttttttttttt\\.gemini\\antigravity\\brain\\dd68c2a6-b6fe-4136-9f98-988170ed66c9", filename);
            try {
                Files.copy(outputFile.toPath(), brainArtifact, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied screenshot to artifact directory: {}", brainArtifact.toAbsolutePath());
            } catch (Exception ignored) {}
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
