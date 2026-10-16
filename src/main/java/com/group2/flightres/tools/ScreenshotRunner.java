package com.group2.flightres.tools;

import com.group2.flightres.controller.MainShellController;
import com.group2.flightres.model.BookingStep;
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
import java.util.ArrayList;
import java.util.List;
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

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("SkyVoyage Screenshot Runner");
        stage.setWidth(WIDTH);
        stage.setHeight(HEIGHT);

        File outDir = OUTPUT_DIR.toFile();
        if (!outDir.exists()) {
            boolean created = outDir.mkdirs();
            logger.info("Created output directory {}: {}", OUTPUT_DIR, created);
        }

        Parameters params = getParameters();
        Map<String, String> namedParams = params.getNamed();

        String targetScreen = namedParams.getOrDefault("screen", System.getProperty("screen", "01_home"));
        String targetTheme = namedParams.getOrDefault("theme", System.getProperty("theme", "all"));

        logger.info("Starting ScreenshotRunner: targetScreen={}, targetTheme={}", targetScreen, targetTheme);

        List<String> screensToCapture = new ArrayList<>();
        if ("all".equalsIgnoreCase(targetScreen)) {
            screensToCapture.add("01_home");
            screensToCapture.add("02_login");
            screensToCapture.add("03_flight_search");
            screensToCapture.add("04_seat_map");
            screensToCapture.add("05_checkout");
            screensToCapture.add("06_confirmation");
            screensToCapture.add("07_admin_dashboard");
        } else {
            screensToCapture.add(targetScreen);
        }

        List<ThemeManager.Theme> themesToCapture = new ArrayList<>();
        if ("light".equalsIgnoreCase(targetTheme)) {
            themesToCapture.add(ThemeManager.Theme.LIGHT);
        } else if ("dark".equalsIgnoreCase(targetTheme)) {
            themesToCapture.add(ThemeManager.Theme.DARK);
        } else {
            themesToCapture.add(ThemeManager.Theme.LIGHT);
            themesToCapture.add(ThemeManager.Theme.DARK);
        }

        runQueue(screensToCapture, themesToCapture);
    }

    private void runQueue(List<String> screens, List<ThemeManager.Theme> themes) {
        List<Runnable> tasks = new ArrayList<>();

        for (String screen : screens) {
            for (ThemeManager.Theme theme : themes) {
                String outName = getOutputFileName(screen, theme);
                tasks.add(() -> captureScreen(screen, theme, outName, () -> executeNext(tasks)));
            }
        }

        executeNext(tasks);
    }

    private void executeNext(List<Runnable> tasks) {
        if (tasks.isEmpty()) {
            logger.info("All screenshot captures finished.");
            Platform.exit();
            System.exit(0);
        } else {
            Runnable nextTask = tasks.remove(0);
            nextTask.run();
        }
    }

    private String getOutputFileName(String screenKey, ThemeManager.Theme theme) {
        String suffix = (theme == ThemeManager.Theme.DARK) ? "_dark.png" : "_light.png";
        if ("shell".equalsIgnoreCase(screenKey)) {
            return "shell" + suffix;
        }
        return screenKey + suffix;
    }

    private void captureScreen(String targetScreen, ThemeManager.Theme theme, String outputFileName, Runnable onComplete) {
        logger.info("Rendering {} in {} theme...", outputFileName, theme);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/main-shell.fxml"));
            Parent root = loader.load();
            MainShellController controller = loader.getController();

            configureShellScreen(controller, targetScreen);

            Scene scene = new Scene(root, WIDTH, HEIGHT);
            ThemeManager.getInstance().setTheme(theme);
            ThemeManager.getInstance().applyTheme(scene);

            primaryStage.setScene(scene);
            primaryStage.show();

            PauseTransition pause = new PauseTransition(Duration.millis(350));
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
            logger.error("Error setting up screen {} for screenshot", targetScreen, e);
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private void configureShellScreen(MainShellController controller, String targetScreen) {
        if (controller == null) return;

        switch (targetScreen) {
            case "01_home":
                controller.handleNavigation("Home");
                break;
            case "02_login":
                controller.handleNavigation("Login");
                break;
            case "03_flight_search":
                controller.goToStep(BookingStep.SEARCH);
                break;
            case "04_seat_map":
                controller.goToStep(BookingStep.SEAT_SELECTION);
                break;
            case "05_checkout":
                controller.goToStep(BookingStep.CHECKOUT);
                break;
            case "06_confirmation":
                controller.goToStep(BookingStep.CONFIRMATION);
                break;
            case "07_admin_dashboard":
                if (controller.getNavBar() != null) {
                    controller.getNavBar().setAdminMode(true);
                }
                controller.handleNavigation("Dashboard");
                break;
            case "shell":
            default:
                break;
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
