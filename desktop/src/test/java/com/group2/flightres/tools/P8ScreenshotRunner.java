package com.group2.flightres.tools;

import com.group2.flightres.controller.FlightSearchController;
import com.group2.flightres.controller.HomeController;
import com.group2.flightres.controller.MyBookingsController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import com.group2.flightres.service.AuthService;
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
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Captures P8 desktop screenshots into docs/screenshots/p8/ at:
 * - 1366x768 (100%)
 * - 1920x1080 (100%)
 * - 1093x614 (1366x768 at 125% Windows display scaling)
 */
public class P8ScreenshotRunner extends Application {

    private static final Path OUT_DIR = Path.of("docs", "screenshots", "p8");

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("skyroyal.screenshots", "true");
        FontLoader.loadFonts();
        OUT_DIR.toFile().mkdirs();

        User customer = new AuthService().authenticate("customer", "Passw0rd!");
        BookingContext ctx = new BookingContext();
        ctx.setCurrentUser(customer);
        AppSession.setCurrentUser(customer);
        AppSession.setDbStatus("localhost", 5433, "sr_p8", 950);

        // 1. Flight Search at 1366x768
        FXMLLoader searchLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/flight-search.fxml"));
        Parent searchRoot = searchLoader.load();
        FlightSearchController searchCtrl = searchLoader.getController();
        searchCtrl.init(ctx, () -> {});

        Scene scene = new Scene(searchRoot, 1366, 768);
        ThemeManager.getInstance().applyGlassTheme(scene);
        stage.setScene(scene);
        stage.setWidth(1366);
        stage.setHeight(768);
        stage.show();

        PauseTransition p1 = new PauseTransition(Duration.millis(2500));
        p1.setOnFinished(e1 -> {
            searchCtrl.setSearchRoute("JNB", "CPT", LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(1));
            PauseTransition p2 = new PauseTransition(Duration.millis(1800));
            p2.setOnFinished(e2 -> {
                searchRoot.applyCss();
                searchRoot.layout();
                capture(searchRoot, "flight-search-1366x768.png");

                // 2. Flight Search at 1920x1080
                stage.setWidth(1920);
                stage.setHeight(1080);
                PauseTransition p3 = new PauseTransition(Duration.millis(600));
                p3.setOnFinished(e3 -> {
                    searchRoot.applyCss();
                    searchRoot.layout();
                    capture(searchRoot, "flight-search-1920x1080.png");

                    // 3. Flight Search at 125% scaling logical viewport (1093x614)
                    stage.setWidth(1093);
                    stage.setHeight(614);
                    PauseTransition p4 = new PauseTransition(Duration.millis(600));
                    p4.setOnFinished(e4 -> {
                        searchRoot.applyCss();
                        searchRoot.layout();
                        capture(searchRoot, "flight-search-125pct-1093x614.png");

                        // 4. Home at 1366x768
                        try {
                            stage.setWidth(1366);
                            stage.setHeight(768);
                            FXMLLoader homeLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/home.fxml"));
                            Parent homeRoot = homeLoader.load();
                            HomeController homeCtrl = homeLoader.getController();
                            homeCtrl.init(ctx, () -> {});
                            scene.setRoot(homeRoot);

                            PauseTransition p5 = new PauseTransition(Duration.millis(1200));
                            p5.setOnFinished(e5 -> {
                                homeRoot.applyCss();
                                homeRoot.layout();
                                capture(homeRoot, "home-1366x768.png");

                                // 5. My Bookings (Guest state) at 1366x768
                                try {
                                    AppSession.signOut(null);
                                    FXMLLoader mbLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
                                    Parent mbRoot = mbLoader.load();
                                    MyBookingsController mbCtrl = mbLoader.getController();
                                    mbCtrl.init(new BookingContext(), () -> {});
                                    scene.setRoot(mbRoot);

                                    PauseTransition p6 = new PauseTransition(Duration.millis(600));
                                    p6.setOnFinished(e6 -> {
                                        mbRoot.applyCss();
                                        mbRoot.layout();
                                        capture(mbRoot, "my-bookings-guest-1366x768.png");
                                        Platform.exit();
                                        System.exit(0);
                                    });
                                    p6.play();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    Platform.exit();
                                    System.exit(1);
                                }
                            });
                            p5.play();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Platform.exit();
                            System.exit(1);
                        }
                    });
                    p4.play();
                });
                p3.play();
            });
            p2.play();
        });
        p1.play();
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
