package com.group2.flightres.tools;

import com.group2.flightres.controller.FlightSearchController;
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
import java.nio.file.Path;

/**
 * Renders the Phase 2 Glass Flight Search screen at 1280x860 and captures
 * docs/screenshots/glass-flight-search.png for visual verification.
 */
public class GlassFlightSearchScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassFlightSearchScreenshotRunner.class);
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
        FontLoader.loadFonts();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/flight-search.fxml"));
        Parent root = loader.load();

        FlightSearchController controller = loader.getController();

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        ThemeManager.getInstance().applyGlassTheme(scene);

        stage.setTitle("Glass Flight Search Preview");
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setResizable(false);
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.millis(750));
        pause.setOnFinished(e -> {
            root.applyCss();
            root.layout();

            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            WritableImage snapshot = new WritableImage(WIDTH, HEIGHT);
            snapshot = root.snapshot(params, snapshot);
            File outputFile = getOutputDir().resolve("glass-flight-search.png").toFile();

            try {
                saveImageToPng(snapshot, outputFile);
                logger.info("Successfully saved glass flight search screenshot: {}", outputFile.getAbsolutePath());
            } catch (IOException ex) {
                logger.error("Failed to save glass flight search screenshot", ex);
            }

            Platform.exit();
        });
        pause.play();
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
