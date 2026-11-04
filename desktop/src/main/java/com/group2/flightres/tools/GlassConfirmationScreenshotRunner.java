package com.group2.flightres.tools;

import com.group2.flightres.controller.BookingConfirmationController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Glass Booking Confirmation screen at 1280x860 and captures
 * docs/screenshots/glass-confirmation.png with sample data matching design-glass/confirmation.html.
 */
public class GlassConfirmationScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassConfirmationScreenshotRunner.class);
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
        System.setProperty("skyvoyage.screenshots", "true");
        FontLoader.loadFonts();

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setDepartureDate("2026-11-14");
        ctx.setPassengerName("Ms Amahle Mokoena");
        ctx.setPassengerEmail("amahle@example.com");
        ctx.setBookingReference("K7QX2M");
        ctx.setBaseFare(new BigDecimal("1200"));
        ctx.setDemandPricing(new BigDecimal("180"));
        ctx.setTaxesAndFees(new BigDecimal("142"));
        ctx.setTotalPrice(new BigDecimal("1522"));

        FlightSeat seat9E = new FlightSeat();
        seat9E.setFlightId(1L);
        seat9E.setSeatRow(9);
        seat9E.setSeatCol('E');
        seat9E.setSeatClass(SeatClass.ECONOMY);
        seat9E.setStatus(SeatStatus.BOOKED);
        seat9E.setCalculatedPrice(new BigDecimal("1200"));

        List<FlightSeat> seats = new ArrayList<>();
        seats.add(seat9E);
        ctx.setSelectedSeats(seats);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/booking-confirmation.fxml"));
        Parent root = loader.load();

        BookingConfirmationController controller = loader.getController();
        controller.init(ctx, () -> {});

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        ThemeManager.getInstance().applyTheme(scene);
        stage.setScene(scene);
        stage.setTitle("Glass Confirmation Screenshot");
        stage.setResizable(false);
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.millis(800));
        pause.setOnFinished(e -> {
            try {
                WritableImage fxImage = scene.snapshot(null);
                int w = (int) fxImage.getWidth();
                int h = (int) fxImage.getHeight();

                BufferedImage bImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                PixelReader pr = fxImage.getPixelReader();
                int[] buffer = new int[w * h];
                pr.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), buffer, 0, w);
                bImage.setRGB(0, 0, w, h, buffer, 0, w);

                Path outputDir = getOutputDir();
                Files.createDirectories(outputDir);
                File outputFile = outputDir.resolve("glass-confirmation.png").toFile();
                ImageIO.write(bImage, "png", outputFile);
                logger.info("Saved confirmation screenshot to: {}", outputFile.getAbsolutePath());

                // Also copy to brain directory for artifact viewing
                File brainDir = new File("C:/Users/tttttttttttttttttttt/.gemini/antigravity/brain/dd68c2a6-b6fe-4136-9f98-988170ed66c9");
                if (brainDir.exists()) {
                    File brainArtifact = new File(brainDir, "glass-confirmation.png");
                    Files.copy(outputFile.toPath(), brainArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Copied screenshot to brain artifact directory: {}", brainArtifact.getAbsolutePath());
                }

            } catch (IOException ex) {
                logger.error("Failed to capture confirmation screenshot: {}", ex.getMessage(), ex);
            } finally {
                stage.close();
                Platform.exit();
            }
        });
        pause.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
