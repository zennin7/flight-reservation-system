package com.group2.flightres.tools;

import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.service.PricingService;
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
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Renders the Glass Seat Map screen at 1280x800 and 900x680
 * with real data and a held seat on an open SR flight.
 */
public class GlassSeatMapScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassSeatMapScreenshotRunner.class);

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
        System.setProperty("skyroyal.screenshots", "true");
        FontLoader.loadFonts();

        TestFlightFinder.OpenAltFlight flight = TestFlightFinder.findEarliestOpenAltFlight();
        PricingService pricingService = new PricingService();
        PriceQuote quote = pricingService.quote(flight.flightId(), SeatClass.ECONOMY, Clock.systemUTC());

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
        ctx.setPriceQuotes(List.of(quote));
        ctx.setTotalPrice(quote.total());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/seat-map.fxml"));
        Parent root = loader.load();

        SeatMapController controller = loader.getController();
        controller.init(ctx, () -> {}, () -> {});

        // Select an available seat (e.g. 7C)
        FlightSeat targetSeat = flight.availableSeats().stream()
                .filter(s -> s.getSeatRow() >= 7 && s.getSeatClass() == SeatClass.ECONOMY)
                .findFirst()
                .orElse(flight.availableSeats().get(0));
        controller.selectSeatByLabel(targetSeat.getSeatLabel());

        Scene scene = new Scene(root, 1280, 800);
        ThemeManager.getInstance().applyGlassTheme(scene);

        stage.setTitle("Glass Seat Map Preview");
        stage.setScene(scene);
        stage.setWidth(1280);
        stage.setHeight(800);
        stage.show();

        PauseTransition p1 = new PauseTransition(Duration.millis(900));
        p1.setOnFinished(e1 -> {
            root.applyCss();
            root.layout();
            captureSnapshot(root, "glass-seat-map.png", 1280, 800);

            // Resize to 900x680
            stage.setWidth(900);
            stage.setHeight(680);

            PauseTransition p2 = new PauseTransition(Duration.millis(700));
            p2.setOnFinished(e2 -> {
                root.applyCss();
                root.layout();
                captureSnapshot(root, "glass-seat-map-900.png", 900, 680);

                // Release seat before exiting so database remains completely clean
                controller.handleReleaseSeat();
                logger.info("Seat map screenshots captured successfully at 1280 and 900.");
                Platform.exit();
            });
            p2.play();
        });
        p1.play();
    }

    private static void captureSnapshot(Parent root, String filename, int w, int h) {
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        WritableImage snapshot = new WritableImage(w, h);
        snapshot = root.snapshot(params, snapshot);
        File outputFile = getOutputDir().resolve(filename).toFile();
        try {
            saveImageToPng(snapshot, outputFile);
            logger.info("Successfully saved screenshot: {}", outputFile.getAbsolutePath());
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
