package com.group2.flightres.tools;

import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.GlassBackground;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
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

/**
 * Developer tool to render and capture the Phase 1 Glass foundation components
 * over a blurred Cape Town photo backdrop to docs/screenshots/glass-foundation.png.
 */
public class GlassFoundationTestRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassFoundationTestRunner.class);
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 740;

    private static Path getOutputDir() {
        if (new File("..", "docs/screenshots").exists()) {
            return Path.of("..", "docs", "screenshots");
        }
        return Path.of("docs", "screenshots");
    }

    @Override
    public void start(Stage stage) {
        // Load fonts BEFORE the Scene is created so CSS can resolve font families
        FontLoader.loadFonts();

        stage.setTitle("Glass Foundation Preview");
        stage.setWidth(WIDTH);
        stage.setHeight(HEIGHT);

        // 1. Create Glass Panel UI components
        VBox glassPanel = new VBox(20);
        glassPanel.getStyleClass().add("glass-panel");
        glassPanel.setPadding(new Insets(32, 40, 32, 40));
        glassPanel.setMaxWidth(680);
        glassPanel.setAlignment(Pos.CENTER);

        Label headline = new Label("Fly beyond the ordinary.");
        headline.getStyleClass().add("headline-serif");

        Label subheadline = new Label("Atmospheric Glass Foundation · Translucent Surfaces & Blur Backdrop");
        subheadline.getStyleClass().add("subheadline");

        // Inner card
        VBox glassCard = new VBox(16);
        glassCard.getStyleClass().add("glass-card");
        glassCard.setPadding(new Insets(24));
        glassCard.setAlignment(Pos.CENTER_LEFT);

        Label cardTitle = new Label("Component Showcase");
        cardTitle.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: white;");

        TextField sampleInput = new TextField();
        sampleInput.setPromptText("Search flight destination or reference (e.g. CPT)...");
        sampleInput.setText("Cape Town International (CPT)");
        sampleInput.deselect(); // prevent selection highlight in screenshot

        // Buttons row
        HBox buttonsRow = new HBox(14);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        Button btnGold = new Button("Confirm Selection");
        btnGold.getStyleClass().add("btn-gold");

        Button btnGhost = new Button("View Flight Details");
        btnGhost.getStyleClass().add("btn-ghost");

        Button btnDanger = new Button("Cancel");
        btnDanger.getStyleClass().add("btn-danger");

        buttonsRow.getChildren().addAll(btnGold, btnGhost, btnDanger);

        // Badges row
        HBox badgesRow = new HBox(12);
        badgesRow.setAlignment(Pos.CENTER_LEFT);

        Label badgeGreen = new Label("Confirmed");
        badgeGreen.getStyleClass().addAll("badge", "badge-green");

        Label badgeGold = new Label("Held · 08:42");
        badgeGold.getStyleClass().addAll("badge", "badge-gold");

        Label badgeRed = new Label("Cancelled");
        badgeRed.getStyleClass().addAll("badge", "badge-red");

        // Seat preview pills
        Label seatFree = new Label("9D");
        seatFree.getStyleClass().addAll("seat", "seat-free");

        Label seatTaken = new Label("9E");
        seatTaken.getStyleClass().addAll("seat", "seat-taken");

        Label seatYours = new Label("9F");
        seatYours.getStyleClass().addAll("seat", "seat-yours");

        badgesRow.getChildren().addAll(badgeGreen, badgeGold, badgeRed, seatFree, seatTaken, seatYours);

        glassCard.getChildren().addAll(cardTitle, sampleInput, buttonsRow, badgesRow);
        glassPanel.getChildren().addAll(headline, subheadline, glassCard);

        // 2. Wrap inside GlassBackground with Cape Town photo
        GlassBackground glassBg = new GlassBackground("/images/destinations/cpt.jpg", glassPanel);

        Scene scene = new Scene(glassBg, WIDTH, HEIGHT);
        ThemeManager.getInstance().applyGlassTheme(scene);

        stage.setScene(scene);
        stage.show();

        // 3. Layout, applyCss, and capture snapshot after rendering transition
        PauseTransition pause = new PauseTransition(Duration.millis(400));
        pause.setOnFinished(e -> {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            sampleInput.deselect(); // ensure no selection highlight in snapshot

            WritableImage snapshot = scene.snapshot(null);
            File outputFile = getOutputDir().resolve("glass-foundation.png").toFile();

            try {
                saveImageToPng(snapshot, outputFile);
                logger.info("Successfully saved glass foundation screenshot: {}", outputFile.getAbsolutePath());
            } catch (IOException ex) {
                logger.error("Failed to save glass foundation screenshot", ex);
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
