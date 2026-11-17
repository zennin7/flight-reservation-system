// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.tools;

import com.group2.flightres.controller.AdminBookingsController;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class GlassAdminBookingsScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassAdminBookingsScreenshotRunner.class);

    private static Path getOutputDir() {
        File shadowDocs = new File("c:/Users/tttttttttttttttttttt/Desktop/flight-reservation-system-shadow/docs/screenshots");
        if (shadowDocs.exists()) return shadowDocs.toPath();
        return Path.of("docs", "screenshots");
    }

    private static void captureSnapshot(Scene scene, String filename, Path outputDir, File brainDir) throws Exception {
        WritableImage fxImage = scene.snapshot(null);
        int w = (int) fxImage.getWidth();
        int h = (int) fxImage.getHeight();
        BufferedImage bImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = fxImage.getPixelReader();
        int[] buffer = new int[w * h];
        pr.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), buffer, 0, w);
        bImage.setRGB(0, 0, w, h, buffer, 0, w);

        File outputFile = outputDir.resolve(filename).toFile();
        ImageIO.write(bImage, "png", outputFile);
        if (brainDir != null && brainDir.exists()) {
            try {
                Files.copy(outputFile.toPath(), new File(brainDir, filename).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("altitude.screenshots", "true");
        FontLoader.loadFonts();

        User adminUser = new User();
        adminUser.setUserId(1L);
        adminUser.setEmail("testadmin@example.com");
        adminUser.setFullName("Test Admin");
        adminUser.setRole(UserRole.ADMIN);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/admin-bookings.fxml"));
        Parent root = loader.load();
        AdminBookingsController controller = loader.getController();
        controller.init(adminUser);

        Scene scene = new Scene(root, 1280, 800);
        ThemeManager.getInstance().applyTheme(scene);
        stage.setScene(scene);
        stage.setWidth(1280);
        stage.setHeight(800);
        stage.show();

        Path outputDir = getOutputDir();
        Files.createDirectories(outputDir);
        File brainDir = new File("C:/Users/tttttttttttttttttttt/.gemini/antigravity/brain/dd68c2a6-b6fe-4136-9f98-988170ed66c9");

        PauseTransition p1 = new PauseTransition(Duration.millis(900));
        p1.setOnFinished(e1 -> {
            try {
                controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                captureSnapshot(scene, "glass-admin-bookings-populated.png", outputDir, brainDir);

                stage.setWidth(900);
                stage.setHeight(680);
                PauseTransition p2 = new PauseTransition(Duration.millis(700));
                p2.setOnFinished(e2 -> {
                    try {
                        controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                        captureSnapshot(scene, "glass-admin-bookings-900.png", outputDir, brainDir);

                        stage.setWidth(1280);
                        stage.setHeight(800);
                        controller.updateResponsiveLayout(1280, 800);
                        controller.showEmptyState();
                        PauseTransition p3 = new PauseTransition(Duration.millis(500));
                        p3.setOnFinished(e3 -> {
                            try {
                                captureSnapshot(scene, "glass-admin-bookings-empty.png", outputDir, brainDir);
                                controller.showErrorState("Could not retrieve bookings list. Please try again.");
                                PauseTransition p4 = new PauseTransition(Duration.millis(500));
                                p4.setOnFinished(e4 -> {
                                    try {
                                        captureSnapshot(scene, "glass-admin-bookings-error.png", outputDir, brainDir);
                                        Platform.exit();
                                    } catch (Exception ex) {
                                        logger.error("Error screenshot failed", ex);
                                        Platform.exit();
                                    }
                                });
                                p4.play();
                            } catch (Exception ex) {
                                logger.error("Empty screenshot failed", ex);
                                Platform.exit();
                            }
                        });
                        p3.play();
                    } catch (Exception ex) {
                        logger.error("900 screenshot failed", ex);
                        Platform.exit();
                    }
                });
                p2.play();
            } catch (Exception ex) {
                logger.error("Populated screenshot failed", ex);
                Platform.exit();
            }
        });
        p1.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
