package com.group2.flightres.tools;

import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.GlassBackground;
import com.group2.flightres.util.TestFlightFinder;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Captures P5e audit screenshots in a single JavaFX session:
 * - Desktop seat map with Section 1, Section 2, and Section 3 selected
 * - Desktop idle backdrop rotation Frame 1 and Frame 2
 */
public class GlassP5eAuditScreenshotRunner extends Application {

    private static final Path OUT_DIR = Path.of("docs", "screenshots", "p5e");

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("skyroyal.screenshots", "true");
        FontLoader.loadFonts();
        OUT_DIR.toFile().mkdirs();

        TestFlightFinder.OpenAltFlight flight = TestFlightFinder.findEarliestOpenAltFlight();
        PricingService pricingService = new PricingService();
        PriceQuote quote = pricingService.quote(flight.flightId(), SeatClass.ECONOMY, Clock.systemUTC());

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(flight.flightId());
        ctx.setFlightNumber(flight.flightNumber());
        ctx.setOrigin(flight.originAirportName());
        ctx.setDestination(flight.destAirportName());
        ctx.setDepartureTime(flight.departureTime());
        ctx.setArrivalTime(flight.arrivalTime());
        ctx.setDepartureDate(flight.departureDate());
        ctx.setDepartureInstant(flight.departureInstant());
        ctx.setArrivalInstant(flight.arrivalInstant());
        ctx.setAircraftModel(flight.aircraftModel());
        ctx.setDuration(flight.duration());
        ctx.setBaseFare(flight.basePrice());
        ctx.setPriceQuotes(List.of(quote));
        ctx.setTotalPrice(quote.total());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/seat-map.fxml"));
        Parent seatRoot = loader.load();
        SeatMapController controller = loader.getController();
        controller.init(ctx, () -> {}, () -> {});

        Scene scene = new Scene(seatRoot, 1280, 800);
        ThemeManager.getInstance().applyGlassTheme(scene);
        stage.setScene(scene);
        stage.setWidth(1280);
        stage.setHeight(800);
        stage.show();

        PauseTransition s1Pause = new PauseTransition(Duration.millis(800));
        s1Pause.setOnFinished(e1 -> {
            seatRoot.applyCss();
            seatRoot.layout();
            capture(seatRoot, "desktop-seatmap-section1.png");

            Button tab2 = (Button) seatRoot.lookup("#section2Tab");
            if (tab2 != null) tab2.fire();

            PauseTransition s2Pause = new PauseTransition(Duration.millis(550));
            s2Pause.setOnFinished(e2 -> {
                seatRoot.applyCss();
                seatRoot.layout();
                capture(seatRoot, "desktop-seatmap-section2.png");

                Button tab3 = (Button) seatRoot.lookup("#section3Tab");
                if (tab3 != null) tab3.fire();

                PauseTransition s3Pause = new PauseTransition(Duration.millis(550));
                s3Pause.setOnFinished(e3 -> {
                    seatRoot.applyCss();
                    seatRoot.layout();
                    capture(seatRoot, "desktop-seatmap-section3.png");

                    // Now load Login screen (idleRotation=true) for 2 idle backdrop frames
                    try {
                        FXMLLoader loginLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/login.fxml"));
                        Parent loginRoot = loginLoader.load();
                        scene.setRoot(loginRoot);
                        loginRoot.applyCss();
                        loginRoot.layout();

                        PauseTransition idle1 = new PauseTransition(Duration.millis(550));
                        idle1.setOnFinished(e4 -> {
                            capture(loginRoot, "desktop-idle-frame1.png");
                            if (loginRoot instanceof GlassBackground gb) {
                                gb.advanceIdleBackdropNow();
                            }
                            PauseTransition idle2 = new PauseTransition(Duration.millis(550));
                            idle2.setOnFinished(e5 -> {
                                loginRoot.applyCss();
                                loginRoot.layout();
                                capture(loginRoot, "desktop-idle-frame2.png");
                                Platform.exit();
                                System.exit(0);
                            });
                            idle2.play();
                        });
                        idle1.play();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Platform.exit();
                        System.exit(1);
                    }
                });
                s3Pause.play();
            });
            s2Pause.play();
        });
        s1Pause.play();
    }

    private static void capture(Parent root, String fileName) {
        try {
            WritableImage fxImage = root.snapshot(null, null);
            int w = (int) fxImage.getWidth();
            int h = (int) fxImage.getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = new int[w * h];
            PixelReader pr = fxImage.getPixelReader();
            pr.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
            bi.setRGB(0, 0, w, h, pixels, 0, w);
            File out = OUT_DIR.resolve(fileName).toFile();
            ImageIO.write(bi, "png", out);
            System.out.println("CAPTURED " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
