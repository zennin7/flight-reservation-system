package com.group2.flightres.tools;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Developer tool for rendering JavaFX screens and shells to PNG at 1000x740
 * for visual comparison against /design/screens.
 */
public class ScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotRunner.class);
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 740;
    private static final Path OUTPUT_DIR = Path.of("docs", "screenshots");

    // Standard screen name to FXML mapping
    private static final Map<String, String> SCREEN_FXML_MAP = new HashMap<>();

    static {
        SCREEN_FXML_MAP.put("shell", "/com/group2/flightres/fxml/main-shell.fxml");
        SCREEN_FXML_MAP.put("01_home", "/com/group2/flightres/fxml/home.fxml");
        SCREEN_FXML_MAP.put("02_login", "/com/group2/flightres/fxml/login.fxml");
        SCREEN_FXML_MAP.put("03_flight_search", "/com/group2/flightres/fxml/flight-search.fxml");
        SCREEN_FXML_MAP.put("04_seat_map", "/com/group2/flightres/fxml/seat-map.fxml");
        SCREEN_FXML_MAP.put("05_checkout", "/com/group2/flightres/fxml/checkout.fxml");
        SCREEN_FXML_MAP.put("06_confirmation", "/com/group2/flightres/fxml/booking-confirmation.fxml");
        SCREEN_FXML_MAP.put("07_admin_dashboard", "/com/group2/flightres/fxml/admin-dashboard.fxml");
    }

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("SkyVoyage Screenshot Runner");
        stage.setWidth(WIDTH);
        stage.setHeight(HEIGHT);

        // Ensure output folder exists
        File outDir = OUTPUT_DIR.toFile();
        if (!outDir.exists()) {
            boolean created = outDir.mkdirs();
            logger.info("Created output directory {}: {}", OUTPUT_DIR, created);
        }

        Parameters params = getParameters();
        Map<String, String> namedParams = params.getNamed();
        String targetScreen = namedParams.getOrDefault("screen", "shell");
        String targetTheme = namedParams.getOrDefault("theme", "all");

        logger.info("Starting ScreenshotRunner: targetScreen={}, targetTheme={}", targetScreen, targetTheme);

        runCaptureSequence(targetScreen, targetTheme);
    }

    private void runCaptureSequence(String targetScreen, String targetTheme) {
        String fxmlPath = SCREEN_FXML_MAP.getOrDefault(targetScreen, "/com/group2/flightres/fxml/main-shell.fxml");

        if ("light".equalsIgnoreCase(targetTheme)) {
            captureSingle(fxmlPath, ThemeManager.Theme.LIGHT, getOutputFileName(targetScreen, ThemeManager.Theme.LIGHT), () -> finish());
        } else if ("dark".equalsIgnoreCase(targetTheme)) {
            captureSingle(fxmlPath, ThemeManager.Theme.DARK, getOutputFileName(targetScreen, ThemeManager.Theme.DARK), () -> finish());
        } else {
            // Capture both LIGHT and DARK sequentially
            captureSingle(fxmlPath, ThemeManager.Theme.LIGHT, getOutputFileName(targetScreen, ThemeManager.Theme.LIGHT), () -> {
                captureSingle(fxmlPath, ThemeManager.Theme.DARK, getOutputFileName(targetScreen, ThemeManager.Theme.DARK), () -> finish());
            });
        }
    }

    private String getOutputFileName(String screenKey, ThemeManager.Theme theme) {
        String suffix = (theme == ThemeManager.Theme.DARK) ? "_dark.png" : "_light.png";
        if ("shell".equalsIgnoreCase(screenKey)) {
            return "shell" + suffix;
        }
        return screenKey + suffix;
    }

    private void captureSingle(String fxmlPath, ThemeManager.Theme theme, String outputFileName, Runnable onComplete) {
        logger.info("Rendering {} in {} theme...", outputFileName, theme);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Scene scene = new Scene(root, WIDTH, HEIGHT);
            ThemeManager.getInstance().setTheme(theme);
            ThemeManager.getInstance().applyTheme(scene);

            primaryStage.setScene(scene);
            primaryStage.show();

            // Wait one pulse + brief delay to ensure CSS styling and layout settle completely
            PauseTransition pause = new PauseTransition(Duration.millis(300));
            pause.setOnFinished(e -> {
                root.applyCss();
                root.layout();

                WritableImage snapshot = scene.snapshot(null);
                File targetFile = OUTPUT_DIR.resolve(outputFileName).toFile();
                try {
                    saveImageToPng(snapshot, targetFile);
                    logger.info("Successfully generated: {}", targetFile.getAbsolutePath());
                } catch (IOException ex) {
                    logger.error("Failed to save image for {}", outputFileName, ex);
                }

                if (onComplete != null) {
                    onComplete.run();
                }
            });
            pause.play();

        } catch (Exception e) {
            logger.error("Error loading FXML {} for screenshot", fxmlPath, e);
            if (onComplete != null) {
                onComplete.run();
            }
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

    private void finish() {
        logger.info("Screenshot capture completed.");
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
