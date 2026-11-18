package com.group2.flightres.tools;

import com.group2.flightres.controller.FlightSearchController;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.GlassFlightResultsBridge;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the Phase 2 Glass Flight Search screen at 1280x800 and captures:
 * 1) docs/screenshots/glass-flight-search.png (default route and date, 2nd card expanded)
 * 2) docs/screenshots/glass-flight-search-dur.png (JNB to DUR tomorrow, proves the airport name comes from data)
 * 3) docs/screenshots/glass-flight-search-empty.png (empty state, 40 days ahead)
 * 4) docs/screenshots/glass-flight-search-error.png (error state with Retry button)
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
                ComboBox<?> fromCombo = (ComboBox<?>) root.lookup("#fromCombo");
                ComboBox<?> toCombo = (ComboBox<?>) root.lookup("#toCombo");
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

            // Ensure card is expanded for leg inspection (2nd card if available, else 1st card)
            GlassFlightResultsBridge bridgeInstance = GlassFlightResultsBridge.getInstance();
            if (bridgeInstance != null) {
                if (cardCount > 1) {
                    bridgeInstance.setExpandedCardIndex(1);
                } else if (cardCount == 1) {
                    bridgeInstance.setExpandedCardIndex(0);
                }
                root.applyCss();
                root.layout();
            }

            // Print card label texts directly from scene graph (Default Route)
            System.out.println("=== SCENE GRAPH CARD LABELS (DEFAULT ROUTE) ===");
            printCardLabelTexts(root);

            // Check baseline Y of the 3 search bar value labels
            Label depDate = (Label) root.lookup("#depDateDisplay");
            Label retVal = (Label) root.lookup("#returnValueDisplay");
            Label travVal = (Label) root.lookup("#travellersValueDisplay");
            if (depDate != null && retVal != null && travVal != null) {
                double depY = depDate.localToScene(depDate.getBoundsInLocal()).getMinY();
                double retY = retVal.localToScene(retVal.getBoundsInLocal()).getMinY();
                double travY = travVal.localToScene(travVal.getBoundsInLocal()).getMinY();
                System.out.printf(Locale.US, "=== SEARCH BAR VALUE LABELS SCENE GRAPH Y ===%n"
                                + "Departure: %.2f%n"
                                + "Return: %.2f%n"
                                + "Travellers: %.2f%n"
                                + "Within 1px: %b%n"
                                + "==============================================%n",
                        depY, retY, travY,
                        Math.abs(depY - retY) <= 1.0 && Math.abs(depY - travY) <= 1.0);
            }

            // Measure right padding of expanded leg at 1280
            measureAndPrintLegPadding(root, 1280);

            // Snapshot 1: Default route and date, 2nd card expanded
            WritableImage snapshot1 = scene.snapshot(null);
            File outputFile1 = getOutputDir().resolve("glass-flight-search.png").toFile();
            try {
                saveImageToPng(snapshot1, outputFile1);
                logger.info("Successfully saved glass flight search screenshot: {}", outputFile1.getAbsolutePath());
            } catch (IOException ex) {
                logger.error("Failed to save glass flight search screenshot", ex);
            }

            // Measure at 1100
            stage.setWidth(1100);
            stage.setHeight(700);
            root.applyCss();
            root.layout();
            measureAndPrintLegPadding(root, 1100);

            // Produce glass-flight-search-900.png at 900x680
            stage.setWidth(900);
            stage.setHeight(680);
            root.applyCss();
            root.layout();
            measureAndPrintLegPadding(root, 900);

            WritableImage snapshot900 = scene.snapshot(null);
            File outputFile900 = getOutputDir().resolve("glass-flight-search-900.png").toFile();
            try {
                saveImageToPng(snapshot900, outputFile900);
                logger.info("Successfully saved glass flight search 900 screenshot: {}", outputFile900.getAbsolutePath());
            } catch (IOException ex) {
                logger.error("Failed to save glass flight search 900 screenshot", ex);
            }

            // Restore stage to 1280x800 for subsequent snapshots
            stage.setWidth(WIDTH);
            stage.setHeight(HEIGHT);
            root.applyCss();
            root.layout();

            // Transition to Snapshot 2: JNB to DUR tomorrow
            LocalDate tomorrow = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(1);
            if (controller != null) {
                controller.setSearchRoute("JNB", "DUR", tomorrow);
            }

            PauseTransition pause2 = new PauseTransition(Duration.millis(700));
            pause2.setOnFinished(e2 -> {
                root.applyCss();
                root.layout();

                System.out.println("=== SCENE GRAPH CARD LABELS (JNB -> DUR) ===");
                printCardLabelTexts(root);

                WritableImage snapshot2 = scene.snapshot(null);
                File outputFile2 = getOutputDir().resolve("glass-flight-search-dur.png").toFile();
                try {
                    saveImageToPng(snapshot2, outputFile2);
                    logger.info("Successfully saved glass flight search DUR screenshot: {}", outputFile2.getAbsolutePath());
                } catch (IOException ex) {
                    logger.error("Failed to save glass flight search DUR screenshot", ex);
                }

                // Transition to Snapshot 3: Empty state (40 days ahead)
                LocalDate emptyDate = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(40);
                if (controller != null) {
                    controller.setSearchDate(emptyDate);
                }

                PauseTransition pause3 = new PauseTransition(Duration.millis(700));
                pause3.setOnFinished(e3 -> {
                    root.applyCss();
                    root.layout();

                    WritableImage snapshot3 = scene.snapshot(null);
                    File outputFile3 = getOutputDir().resolve("glass-flight-search-empty.png").toFile();
                    try {
                        saveImageToPng(snapshot3, outputFile3);
                        logger.info("Successfully saved glass flight search empty screenshot: {}", outputFile3.getAbsolutePath());
                    } catch (IOException ex) {
                        logger.error("Failed to save glass flight search empty screenshot", ex);
                    }

                    // Transition to Snapshot 4: Error state with Retry button
                    GlassFlightResultsBridge bridge = GlassFlightResultsBridge.getInstance();
                    if (bridge != null) {
                        bridge.showErrorState(() -> logger.info("Retry button clicked"));
                    }

                    PauseTransition pause4 = new PauseTransition(Duration.millis(500));
                    pause4.setOnFinished(e4 -> {
                        root.applyCss();
                        root.layout();

                        WritableImage snapshot4 = scene.snapshot(null);
                        File outputFile4 = getOutputDir().resolve("glass-flight-search-error.png").toFile();
                        try {
                            saveImageToPng(snapshot4, outputFile4);
                            logger.info("Successfully saved glass flight search error screenshot: {}", outputFile4.getAbsolutePath());
                        } catch (IOException ex) {
                            logger.error("Failed to save glass flight search error screenshot", ex);
                        }

                        Platform.exit();
                    });
                    pause4.play();
                });
                pause3.play();
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

    private static void measureAndPrintLegPadding(Parent root, int sceneWidth) {
        VBox cardsContainer = (VBox) root.lookup("#resultsCardsContainer");
        if (cardsContainer != null) {
            for (Node card : cardsContainer.getChildren()) {
                if (card.getStyleClass().contains("flight-result-card")) {
                    Node leg = card.lookup(".expanded-leg-row");
                    if (leg != null) {
                        Bounds cardBounds = card.localToScene(card.getBoundsInLocal());
                        Bounds legBounds = leg.localToScene(leg.getBoundsInLocal());
                        double leftPad = legBounds.getMinX() - cardBounds.getMinX();
                        double rightPad = cardBounds.getMaxX() - legBounds.getMaxX();
                        double totalRightPadding = rightPad + 12.0;
                        double totalLeftPadding = leftPad + 12.0;
                        System.out.printf(Locale.US, "Expanded leg right padding at %d: %.1f px (left padding: %.1f px)%n",
                                sceneWidth, totalRightPadding, totalLeftPadding);
                        break;
                    }
                }
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
