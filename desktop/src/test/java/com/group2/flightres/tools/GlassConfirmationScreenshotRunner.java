package com.group2.flightres.tools;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.BookingConfirmationController;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.TestFlightFinder;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Glass Booking Confirmation screen across responsive viewports:
 * - 1280x800 (standard two-column)
 * - 900x680 (single-column stacked)
 * Creates exactly 1 authorized demo booking for "Demo Traveller" via BookingService.
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

    private static long countBookings() {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM bookings")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            logger.error("Failed to count bookings", e);
        }
        return -1;
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
            try {
                Files.copy(outputFile.toPath(), brainArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied screenshot to brain artifact directory: {}", brainArtifact.getAbsolutePath());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("skyroyal.screenshots", "true");
        FontLoader.loadFonts();

        long beforeCount = countBookings();
        System.out.println("=== DEMO BOOKING AUDIT ===");
        System.out.println("Bookings count before: " + beforeCount);

        TestFlightFinder.OpenAltFlight flight = TestFlightFinder.findEarliestOpenAltFlight();
        PricingService pricingService = new PricingService();
        SeatMapService seatMapService = new SeatMapService(new SeatDaoImpl());
        BookingService bookingService = new BookingService();

        PriceQuote quote = pricingService.quote(flight.flightId(), SeatClass.ECONOMY, Clock.systemUTC());

        FlightSeat seat = flight.availableSeats().stream()
                .filter(s -> s.getSeatRow() >= 7 && s.getSeatClass() == SeatClass.ECONOMY)
                .findFirst()
                .orElse(flight.availableSeats().get(0));

        seatMapService.selectSeat(seat);

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
        ctx.setPassengerName("Demo Traveller");
        ctx.setPassengerEmail("demo.traveller@example.com");
        ctx.setPassportNumber("DT987654");
        ctx.setDateOfBirth(LocalDate.of(1990, 5, 20));
        ctx.setBaseFare(flight.basePrice());
        ctx.setPriceQuotes(List.of(quote));
        ctx.setTotalPrice(quote.total());
        seat.setCalculatedPrice(quote.total());
        ctx.setSelectedSeats(new ArrayList<>(List.of(seat)));

        // Simulated payment gateway
        PaymentResult payRes = new SimulatedPaymentGateway().processPayment(
                "4242424242424242", "08/28", "123", quote.total()
        );
        ctx.setPaymentResult(payRes);

        // Authorised 1 demo booking through BookingService
        BookingRecord record = bookingService.createBooking(ctx);
        long afterCount = countBookings();

        System.out.println("Booking confirmed with PNR: " + record.getBookingReference());
        System.out.println("Booking ID: " + record.getBookingId());
        System.out.println("Bookings count after: " + afterCount);
        System.out.println("==========================");

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

        PauseTransition p1 = new PauseTransition(Duration.millis(900));
        p1.setOnFinished(e1 -> {
            try {
                controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                captureSnapshot(scene, "glass-confirmation.png", outputDir, brainDir);

                // Resize to 900x680
                stage.setWidth(900);
                stage.setHeight(680);

                PauseTransition p2 = new PauseTransition(Duration.millis(700));
                p2.setOnFinished(e2 -> {
                    try {
                        controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                        captureSnapshot(scene, "glass-confirmation-900.png", outputDir, brainDir);
                        logger.info("Confirmation screenshots captured successfully at 1280 and 900.");
                        Platform.exit();
                    } catch (Exception ex) {
                        logger.error("Failed to capture 900x680 screenshot", ex);
                        Platform.exit();
                    }
                });
                p2.play();
            } catch (Exception ex) {
                logger.error("Failed to capture 1280x800 screenshot", ex);
                Platform.exit();
            }
        });
        p1.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
