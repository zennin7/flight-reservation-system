package com.group2.flightres.tools;

import com.group2.flightres.controller.BookingConfirmationController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import java.time.Instant;
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
 * Renders the Glass Booking Confirmation screen across responsive viewports:
 * - 1280x800 (standard two-column)
 * - 1100x700 (compact two-column)
 * - 900x680 (single-column stacked top & bottom)
 * - 1920x1080 (expanded full-HD two-column)
 */
public class GlassConfirmationScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassConfirmationScreenshotRunner.class);

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
        logger.info("Saved confirmation screenshot to: {}", outputFile.getAbsolutePath());

        if (brainDir != null && brainDir.exists()) {
            File brainArtifact = new File(brainDir, filename);
            Files.copy(outputFile.toPath(), brainArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Copied screenshot to brain artifact directory: {}", brainArtifact.getAbsolutePath());
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("altitude.screenshots", "true");
        FontLoader.loadFonts();

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setDepartureInstant(Instant.parse("2026-11-14T04:30:00Z"));
        ctx.setArrivalInstant(Instant.parse("2026-11-14T06:35:00Z"));
        ctx.setDepartureDate("2026-11-14");
        ctx.setPassengerName("Amahle Mokoena");
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

        Scene scene = new Scene(root, 1280, 800);
        ThemeManager.getInstance().applyTheme(scene);
        stage.setScene(scene);
        stage.setTitle("Glass Confirmation Screenshot");
        stage.setResizable(true);
        stage.setWidth(1280);
        stage.setHeight(800);
        stage.show();

        Path outputDir = getOutputDir();
        Files.createDirectories(outputDir);
        File brainDir = new File("C:/Users/tttttttttttttttttttt/.gemini/antigravity/brain/dd68c2a6-b6fe-4136-9f98-988170ed66c9");

        // Sequence: 1280x800 -> 1366x768 -> 1100x700 -> 900x680 top -> 900x680 middle -> 900x680 bottom -> 1920x1080
        PauseTransition p1 = new PauseTransition(Duration.millis(800));
        p1.setOnFinished(e1 -> {
            try {
                controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                captureSnapshot(scene, "glass-confirmation.png", outputDir, brainDir);

                // 2. 1366x768
                stage.setWidth(1366);
                stage.setHeight(768);

                PauseTransition p1366 = new PauseTransition(Duration.millis(600));
                p1366.setOnFinished(e1366 -> {
                    try {
                        controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                        captureSnapshot(scene, "glass-confirmation-1366x768.png", outputDir, brainDir);

                        // 3. 1100x700
                        stage.setWidth(1100);
                        stage.setHeight(700);

                        PauseTransition p2 = new PauseTransition(Duration.millis(600));
                        p2.setOnFinished(e2 -> {
                            try {
                                controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                                captureSnapshot(scene, "glass-confirmation-1100x700.png", outputDir, brainDir);

                                // 4. 900x680 (top)
                                stage.setWidth(900);
                                stage.setHeight(680);

                                PauseTransition p3 = new PauseTransition(Duration.millis(600));
                                p3.setOnFinished(e3 -> {
                                    try {
                                        controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                                        if (controller.getConfirmationScrollPane() != null) {
                                            controller.getConfirmationScrollPane().setVvalue(0.0);
                                        }
                                        captureSnapshot(scene, "glass-confirmation-900x680.png", outputDir, brainDir);

                                        // 5. 900x680 (scrolled to MIDDLE: barcode, passenger row, reference fully visible)
                                        if (controller.getConfirmationScrollPane() != null) {
                                            controller.getConfirmationScrollPane().setVvalue(0.38);
                                        }

                                        PauseTransition pMiddle = new PauseTransition(Duration.millis(600));
                                        pMiddle.setOnFinished(eMiddle -> {
                                            try {
                                                captureSnapshot(scene, "glass-confirmation-900x680-middle.png", outputDir, brainDir);

                                                // 6. 900x680 (scrolled to bottom)
                                                if (controller.getConfirmationScrollPane() != null) {
                                                    controller.getConfirmationScrollPane().setVvalue(1.0);
                                                }

                                                PauseTransition p4 = new PauseTransition(Duration.millis(600));
                                                p4.setOnFinished(e4 -> {
                                                    try {
                                                        captureSnapshot(scene, "glass-confirmation-900x680-bottom.png", outputDir, brainDir);

                                                        // 7. 1920x1080
                                                        stage.setWidth(1920);
                                                        stage.setHeight(1080);

                                                        PauseTransition p5 = new PauseTransition(Duration.millis(600));
                                                        p5.setOnFinished(e5 -> {
                                                            try {
                                                                controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                                                                if (controller.getConfirmationScrollPane() != null) {
                                                                    controller.getConfirmationScrollPane().setVvalue(0.0);
                                                                }
                                                                captureSnapshot(scene, "glass-confirmation-1920x1080.png", outputDir, brainDir);
                                                            } catch (Exception ex5) {
                                                                logger.error("Failed 1920x1080 capture: {}", ex5.getMessage(), ex5);
                                                            } finally {
                                                                stage.close();
                                                                Platform.exit();
                                                            }
                                                        });
                                                        p5.play();

                                                    } catch (Exception ex4) {
                                                        logger.error("Failed 900x680-bottom capture: {}", ex4.getMessage(), ex4);
                                                        stage.close();
                                                        Platform.exit();
                                                    }
                                                });
                                                p4.play();

                                            } catch (Exception exM) {
                                                logger.error("Failed 900x680-middle capture: {}", exM.getMessage(), exM);
                                                stage.close();
                                                Platform.exit();
                                            }
                                        });
                                        pMiddle.play();

                                    } catch (Exception ex3) {
                                        logger.error("Failed 900x680 capture: {}", ex3.getMessage(), ex3);
                                        stage.close();
                                        Platform.exit();
                                    }
                                });
                                p3.play();

                            } catch (Exception ex2) {
                                logger.error("Failed 1100x700 capture: {}", ex2.getMessage(), ex2);
                                stage.close();
                                Platform.exit();
                            }
                        });
                        p2.play();

                    } catch (Exception ex1366) {
                        logger.error("Failed 1366x768 capture: {}", ex1366.getMessage(), ex1366);
                        stage.close();
                        Platform.exit();
                    }
                });
                p1366.play();

            } catch (Exception ex1) {
                logger.error("Failed 1280x800 capture: {}", ex1.getMessage(), ex1);
                stage.close();
                Platform.exit();
            }
        });
        p1.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}