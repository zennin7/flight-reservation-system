package com.group2.flightres.tools;

import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.AppSession;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Captures P8b checkout and seat-map screenshots at 900, 1100, and 1366 px wide into docs/screenshots/p8b/.
 */
public class P8bScreenshotRunner extends Application {

    private static final Path OUT_DIR = Path.of("docs", "screenshots", "p8b");

    private static BookingContext sampleContext() {
        User u = new User();
        u.setUserId(2L);
        u.setUsername("customer");
        u.setFullName("Demo Customer");
        u.setEmail("customer@example.com");
        u.setRole(UserRole.CUSTOMER);
        AppSession.setCurrentUser(u);

        BookingContext ctx = new BookingContext();
        ctx.setCurrentUser(u);
        ctx.setFlightId(1L);
        ctx.setFlightNumber("SR 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setOriginCity("Johannesburg");
        ctx.setDestinationCity("Cape Town");
        ctx.setDepartureDate("26 Nov 2026");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:40");
        ctx.setDepartureInstant(Instant.parse("2026-11-26T04:30:00Z"));
        ctx.setArrivalInstant(Instant.parse("2026-11-26T06:40:00Z"));
        ctx.setAircraftModel("Airbus A320");
        ctx.setDuration("2h 10m");

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setFlightId(1L);
        seat.setSeatRow(7);
        seat.setSeatCol('C');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.HELD);
        seat.setCalculatedPrice(new BigDecimal("1344.00"));
        ctx.setSelectedSeats(List.of(seat));

        PriceQuote quote = PriceQuote.calculate(new BigDecimal("1200.00"), BigDecimal.ONE, 0.0, 24.0);
        ctx.setPriceQuotes(List.of(quote));
        ctx.setBaseFare(quote.baseFare());
        ctx.setTotalPrice(quote.total());
        return ctx;
    }

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("skyroyal.screenshots", "true");
        FontLoader.loadFonts();
        OUT_DIR.toFile().mkdirs();

        BookingContext ctx = sampleContext();

        // Load Checkout first
        FXMLLoader checkoutLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
        Parent checkoutRoot = checkoutLoader.load();
        CheckoutController checkoutCtrl = checkoutLoader.getController();
        checkoutCtrl.init(ctx, () -> {});

        Scene scene = new Scene(checkoutRoot, 900, 750);
        ThemeManager.getInstance().applyGlassTheme(scene);
        stage.setScene(scene);
        stage.setWidth(900);
        stage.setHeight(750);
        stage.show();

        captureStep(stage, checkoutRoot, 900, 750, "checkout-900.png", () ->
                captureStep(stage, checkoutRoot, 1100, 750, "checkout-1100.png", () ->
                        captureStep(stage, checkoutRoot, 1366, 768, "checkout-1366.png", () -> {
                            if (checkoutCtrl.getGlassBridge() != null) {
                                checkoutCtrl.getGlassBridge().stopTimer();
                            }
                            try {
                                FXMLLoader seatLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/seat-map.fxml"));
                                Parent seatRoot = seatLoader.load();
                                SeatMapController seatCtrl = seatLoader.getController();
                                seatCtrl.init(ctx, () -> {});
                                seatCtrl.selectSeatByLabel("7C");
                                scene.setRoot(seatRoot);

                                captureStep(stage, seatRoot, 900, 750, "seat-map-900.png", () ->
                                        captureStep(stage, seatRoot, 1100, 750, "seat-map-1100.png", () ->
                                                captureStep(stage, seatRoot, 1366, 768, "seat-map-1366.png", () -> {
                                                    Platform.exit();
                                                    System.exit(0);
                                                })));
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                Platform.exit();
                                System.exit(1);
                            }
                        })));
    }

    private static void captureStep(Stage stage, Parent root, double w, double h, String fileName, Runnable next) {
        stage.setWidth(w);
        stage.setHeight(h);
        PauseTransition pause = new PauseTransition(Duration.millis(650));
        pause.setOnFinished(e -> {
            root.applyCss();
            root.layout();
            capture(root, fileName);
            if (next != null) next.run();
        });
        pause.play();
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
            System.out.println("CAPTURED " + out.getAbsolutePath() + " (" + w + "x" + h + ", " + out.length() + " bytes)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
