package com.group2.flightres.tools;

import com.group2.flightres.controller.LoginController;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
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
 * Renders the Phase 2 Glass Login screen at 1000x740 and captures
 * docs/screenshots/glass-login.png for visual verification.
 */
public class GlassLoginScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassLoginScreenshotRunner.class);
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 740;

    private static Path getOutputDir() {
        if (new File("..", "docs/screenshots").exists()) {
            return Path.of("..", "docs", "screenshots");
        }
        return Path.of("docs", "screenshots");
    }

    @Override
    public void start(Stage stage) throws Exception {
        FontLoader.loadFonts();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/login.fxml"));
        Parent root = loader.load();

        LoginController controller = loader.getController();

        // Populate sample text matching design-glass/login.png
        TextField usernameField = (TextField) root.lookup("#usernameField");
        if (usernameField != null) {
            usernameField.setText("david@example.com");
            usernameField.deselect();
        }

        TextField passwordField = (TextField) root.lookup("#passwordField");
        if (passwordField != null) {
            passwordField.setText("••••••••••");
            passwordField.deselect();
        }

        javafx.scene.control.CheckBox rememberMe = (javafx.scene.control.CheckBox) root.lookup("#rememberMeCheckBox");
        if (rememberMe != null) {
            rememberMe.setSelected(true);
        }

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        ThemeManager.getInstance().applyGlassTheme(scene);

        stage.setTitle("Glass Login Preview");
        stage.setScene(scene);
        stage.setWidth(WIDTH);
        stage.setHeight(HEIGHT);
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.millis(450));
        pause.setOnFinished(e -> {
            root.applyCss();
            root.layout();

            if (usernameField != null) usernameField.deselect();
            if (passwordField != null) passwordField.deselect();

            WritableImage snapshot = scene.snapshot(null);
            File outputFile = getOutputDir().resolve("glass-login.png").toFile();

            try {
                saveImageToPng(snapshot, outputFile);
                logger.info("Successfully saved glass login screenshot: {}", outputFile.getAbsolutePath());
            } catch (IOException ex) {
                logger.error("Failed to save glass login screenshot", ex);
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
