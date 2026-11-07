package com.group2.flightres.tools;

import com.group2.flightres.controller.FlightSearchController;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.GlassFlightResultsBridge;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Phase 2 Glass Flight Search screen at 1280x800 and captures:
 * 1) docs/screenshots/glass-flight-search.png (real DB cards)
 * 2) docs/screenshots/glass-flight-search-error.png (error state with Retry button)
 * Also prints the scene graph label texts for cards to stdout.
 */
public class GlassFlightSearchScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassFlightSearchScreenshotRunner.class);
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;

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

        // Optional search date from CLI arg (--date=YYYY-MM-DD); otherwise DB's next available date is used by controller
        LocalDate customDate = null;
        for (String arg : getParameters().getRaw()) {
            if (arg.startsWith("--date=")) {
                customDate = LocalDate.parse(arg.substring("--date=".length()).trim());
            }
        }
        if (customDate != null && controller != null) {
            controller.setSearchDate(customDate);
        }

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        ThemeManager.getInstance().applyGlassTheme(scene);

        stage.setTitle("Glass Flight Search Preview");
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setResizable(false);
        stage.show();

        PauseTransition pause1 = new PauseTransition(Duration.millis(800));
        pause1.setOnFinished(e1 -> {
            root.applyCss();
            root.layout();

            // Verify flights are present; exit non-zero if none found
            VBox cardsContainer = (VBox) root.lookup("#resultsCardsContainer");
            long cardCount = 0;
            if (cardsContainer != null) {
                cardCount = cardsContainer.getChildren().stream()
                        .filter(child -> child.getStyleClass().contains("flight-result-card"))
                        .count();
            }

            if (cardCount == 0) {
                ComboBox<?> fromCombo = (ComboBox<?>) root.lookup("#fromAirportCombo");
                ComboBox<?> toCombo = (ComboBox<?>) root.lookup("#toAirportCombo");
                DatePicker depDatePicker = (DatePicker) root.lookup("#departureDatePicker");

                String from = (fromCombo != null && fromCombo.getValue() != null) ? fromCombo.getValue().toString() : "JNB";
                String to = (toCombo != null && toCombo.getValue() != null) ? toCombo.getValue().toString() : "CPT";
                String fromCode = (from.contains("(") && from.contains(")"))
                        ? from.substring(from.indexOf('(') + 1, from.indexOf(')'))
                        : from;
                String toCode = (to.contains("(") && to.contains(")"))
                        ? to.substring(to.indexOf('(') + 1, to.indexOf(')'))
                        : to;
                String date = (depDatePicker != null && depDatePicker.getValue() != null)
                        ? depDatePicker.getValue().toString()
                        : "unknown";

                System.err.println("no flights for " + fromCode + "-" + toCode + " on " + date + "; run core/sql/seed_flights_relative.sql");
                Platform.exit();
                System.exit(1);
                return;
            }

            // Print card label texts directly from scene graph
            printCardLabelTexts(root);

            // Snapshot 1: Normal cards (snapshot entire 1280x800 scene without window border offset)
            WritableImage snapshot1 = scene.snapshot(null);
            File outputFile1 = getOutputDir().resolve("glass-flight-search.png").toFile();

            try {
                saveImageToPng(snapshot1, outputFile1);
                logger.info("Successfully saved glass flight search screenshot: {}", outputFile1.getAbsolutePath());
            } catch (IOException ex) {
                logger.error("Failed to save glass flight search screenshot", ex);
            }

            // Trigger Error State for Snapshot 2
            GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
            if (bridge != null) {
                bridge.showErrorState(() -> logger.info("Retry button clicked"));
            }

            PauseTransition pause2 = new PauseTransition(Duration.millis(500));
            pause2.setOnFinished(e2 -> {
                root.applyCss();
                root.layout();

                WritableImage snapshot2 = scene.snapshot(null);
                File outputFile2 = getOutputDir().resolve("glass-flight-search-error.png").toFile();

                try {
                    saveImageToPng(snapshot2, outputFile2);
                    logger.info("Successfully saved glass flight search error screenshot: {}", outputFile2.getAbsolutePath());
                } catch (IOException ex) {
                    logger.error("Failed to save glass flight search error screenshot", ex);
                }

                Platform.exit();
            });
            pause2.play();
        });
        pause1.play();
    }

    private static void printCardLabelTexts(Parent root) {
        VBox cardsContainer = (VBox) root.lookup("#resultsCardsContainer");
        if (cardsContainer == null) {
            System.out.println("No cardsContainer found in scene");
            return;
        }

        System.out.println("=== SCENE GRAPH CARD LABELS ===");
        int cardIdx = 1;
        for (Node child : cardsContainer.getChildren()) {
            if (child.getStyleClass().contains("flight-result-card")) {
                List<String> labels = new ArrayList<>();
                collectLabels(child, labels);
                System.out.println("--- CARD " + cardIdx + " ---");
                for (String lbl : labels) {
                    System.out.println("  " + lbl);
                }
                cardIdx++;
            }
        }
        System.out.println("===============================");
    }

    private static void collectLabels(Node node, List<String> list) {
        if (node instanceof Label lbl && lbl.getText() != null && !lbl.getText().isBlank()) {
            list.add(lbl.getText());
        }
        if (node instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                collectLabels(c, list);
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

    public static void main(String[] args) {
        launch(args);
    }
}
