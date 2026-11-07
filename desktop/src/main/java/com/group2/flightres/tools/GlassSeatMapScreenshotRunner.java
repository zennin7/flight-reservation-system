package com.group2.flightres.tools;

import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
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
import java.nio.file.Path;

/**
 * Renders the Phase 2 Glass Seat Map screen at 1280x860 and captures
 * docs/screenshots/glass-seat-map.png with real data and held seat.
 */
public class GlassSeatMapScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassSeatMapScreenshotRunner.class);
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 860;

    private static Path getOutputDir() {
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
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("JNB");
        ctx.setDestination("CPT");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setDepartureDate("2026-11-14");
        ctx.setBaseFare(new BigDecimal("1200"));
        ctx.setClassMultiplier(BigDecimal.ONE);
        ctx.setDemandPricing(new BigDecimal("180"));
        ctx.setTaxesAndFees(new BigDecimal("142"));
        ctx.recalculateTotals();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/seat-map.fxml"));
        Parent root = loader.load();

        SeatMapController controller = loader.getController();
        controller.init(ctx, () -> {}, () -> {});

        // Select seat 9E (real hold via controller.handleSeatClick) to match target design
        controller.selectSeatByLabel("9E");

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        ThemeManager.getInstance().applyGlassTheme(scene);

        stage.setTitle("Glass Seat Map Preview");
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setResizable(false);
        stage.show();

        PauseTransition p1 = new PauseTransition(Duration.millis(900));
        p1.setOnFinished(e1 -> {
            root.applyCss();
            root.layout();
            captureSnapshot(root, "glass-seat-map-s1.png");
            captureSnapshot(root, "glass-seat-map.png");

            // Switch to Section 2 and hold seat 14B
            controller.selectSeatByLabel("14B");
            if (controller.getGlassBridge() != null) {
                controller.getGlassBridge().setSection(2);
            }

            PauseTransition p2 = new PauseTransition(Duration.millis(700));
            p2.setOnFinished(e2 -> {
                root.applyCss();
                root.layout();
                captureSnapshot(root, "glass-seat-map-s2.png");

                // Switch to Section 3 and hold seat 22D
                controller.selectSeatByLabel("22D");
                if (controller.getGlassBridge() != null) {
                    controller.getGlassBridge().setSection(3);
                }

                PauseTransition p3 = new PauseTransition(Duration.millis(700));
                p3.setOnFinished(e3 -> {
                    root.applyCss();
                    root.layout();
                    captureSnapshot(root, "glass-seat-map-s3.png");

                    // Release seat before exiting so database remains clean
                    controller.handleReleaseSeat();

                    logger.info("All 3 section screenshots captured successfully.");
                    Platform.exit();
                });
                p3.play();
            });
            p2.play();
        });
        p1.play();
    }

    private static void captureSnapshot(Parent root, String filename) {
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        WritableImage snapshot = new WritableImage(WIDTH, HEIGHT);
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
