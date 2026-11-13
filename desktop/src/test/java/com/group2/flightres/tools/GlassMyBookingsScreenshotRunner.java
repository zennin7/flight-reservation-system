package com.group2.flightres.tools;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.MyBookingsController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.util.FlightDateFormatter;
import com.group2.flightres.util.FlightTimes;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GlassMyBookingsScreenshotRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassMyBookingsScreenshotRunner.class);

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

    private static long countBookingsForUser(long userId) {
        String sql = "SELECT count(*) FROM bookings WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to count bookings for user {}", userId, e);
        }
        return -1;
    }

    private static long countUpcomingBookingsForUser(long userId) {
        String sql = """
            SELECT count(*)
            FROM bookings b
            JOIN flights f ON f.flight_id = b.flight_id
            WHERE b.user_id = ?
              AND b.status != 'CANCELLED'
              AND f.departure_time > NOW()
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to count upcoming bookings for user {}", userId, e);
        }
        return -1;
    }

    private static List<String> listBookingRefsForUser(long userId) {
        String sql = "SELECT booking_ref FROM bookings WHERE user_id = ? ORDER BY created_at ASC";
        List<String> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("booking_ref"));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to list booking references for user {}", userId, e);
        }
        return list;
    }

    private static void ensureDemoBookingsExist(BookingService bookingService, PricingService pricingService, Clock clock) {
        long userId = 12L;
        long countBefore = countBookingsForUser(userId);
        long upcomingBefore = countUpcomingBookingsForUser(userId);
        List<String> refsBefore = listBookingRefsForUser(userId);

        System.out.println("==================================================");
        System.out.println("=== DEMO DATA AUDIT BEFORE ===");
        System.out.println("Authorised Demo User ID: " + userId + " (demo.traveller@example.com)");
        System.out.println("Total Bookings Count Before: " + countBefore + " (Upcoming: " + upcomingBefore + ")");
        System.out.println("Existing Booking References: " + refsBefore);
        System.out.println("==================================================");

        int neededTotal = countBefore < 4 ? (int) (4 - countBefore) : 0;
        int neededUpcoming = upcomingBefore < 3 ? (int) (3 - upcomingBefore) : 0;
        int needed = Math.max(neededTotal, neededUpcoming);

        if (needed > 0) {
            System.out.println("Creating " + needed + " upcoming demo booking(s) on ALT flights (3-14 days out)...");

            String query = """
                SELECT f.flight_id, f.flight_number, f.departure_time, f.arrival_time, f.base_price,
                       orig.name AS origin_name, orig.iata_code AS origin_code, orig.city AS origin_city, orig.timezone AS origin_tz,
                       dest.name AS dest_name, dest.iata_code AS dest_code, dest.city AS dest_city, dest.timezone AS dest_tz
                FROM flights f
                JOIN airports orig ON orig.airport_id = f.origin_airport_id
                JOIN airports dest ON dest.airport_id = f.dest_airport_id
                WHERE f.flight_number LIKE 'ALT%'
                  AND f.status = 'SCHEDULED'
                  AND f.departure_time >= NOW() + INTERVAL '3 days'
                  AND f.departure_time <= NOW() + INTERVAL '14 days'
                ORDER BY f.departure_time ASC
                LIMIT 20;
                """;

            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                int created = 0;
                while (rs.next() && created < needed) {
                    long flightId = rs.getLong("flight_id");
                    String flightNumber = rs.getString("flight_number");
                    Timestamp depTs = rs.getTimestamp("departure_time");
                    Timestamp arrTs = rs.getTimestamp("arrival_time");
                    Instant depInstant = depTs != null ? depTs.toInstant() : Instant.now().plusSeconds(7200);
                    Instant arrInstant = arrTs != null ? arrTs.toInstant() : depInstant.plusSeconds(7200);

                    BigDecimal basePrice = rs.getBigDecimal("base_price");
                    String originName = rs.getString("origin_name");
                    String originCode = rs.getString("origin_code");
                    String originCity = rs.getString("origin_city");
                    String destName = rs.getString("dest_name");
                    String destCode = rs.getString("dest_code");
                    String destCity = rs.getString("dest_city");

                    ZoneId saZone = ZoneId.of("Africa/Johannesburg");
                    String depDate = FlightDateFormatter.format(LocalDate.ofInstant(depInstant, saZone));
                    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(saZone);
                    String depTime = timeFmt.format(depInstant);
                    String arrTime = timeFmt.format(arrInstant);
                    String duration = FlightTimes.formatDuration(depInstant, arrInstant);

                    // Find available seats for this flight
                    List<FlightSeat> seats = loadAvailableSeatsForFlight(conn, flightId);
                    if (seats.isEmpty()) continue;

                    FlightSeat seat = seats.get(0);
                    PriceQuote quote = pricingService.quote(flightId, seat.getSeatClass(), clock);

                    String[] romanNumerals = {"II", "III", "IV", "V", "VI"};
                    String roman = created < romanNumerals.length ? romanNumerals[created] : "Plus";
                    String passName = "Demo Traveller " + roman;

                    User demoUser = new User();
                    demoUser.setUserId(userId);
                    demoUser.setEmail("demo.traveller@example.com");
                    demoUser.setFullName(passName);
                    demoUser.setRole(UserRole.CUSTOMER);

                    BookingContext ctx = new BookingContext();
                    ctx.setCurrentUser(demoUser);
                    ctx.setFlightId(flightId);
                    ctx.setFlightNumber(flightNumber);
                    ctx.setOrigin(originCity + " (" + originCode + ")");
                    ctx.setOriginCity(originCity);
                    ctx.setDestination(destCity + " (" + destCode + ")");
                    ctx.setDestinationCity(destCity);
                    ctx.setDepartureTime(depTime);
                    ctx.setArrivalTime(arrTime);
                    ctx.setDepartureDate(depDate);
                    ctx.setDepartureInstant(depInstant);
                    ctx.setArrivalInstant(arrInstant);
                    ctx.setAircraftModel("Airbus A320");
                    ctx.setDuration(duration);
                    ctx.setPassengerName(passName);
                    ctx.setPassengerEmail("demo.traveller@example.com");
                    ctx.setPassportNumber("DT98765" + (created + 2));
                    ctx.setDateOfBirth(LocalDate.of(1990, 5, 20));
                    ctx.setBaseFare(basePrice);
                    ctx.setPriceQuotes(List.of(quote));
                    ctx.setTotalPrice(quote.total());
                    seat.setCalculatedPrice(quote.total());
                    ctx.setSelectedSeats(new ArrayList<>(List.of(seat)));

                    // Payment via simulated gateway
                    PaymentResult payRes = new SimulatedPaymentGateway().processPayment(
                            "4242424242424242", "08/28", "123", quote.total()
                    );
                    ctx.setPaymentResult(payRes);

                    // Create booking through service
                    BookingRecord createdRec = bookingService.createBooking(ctx);
                    System.out.println("Created Demo Booking: " + createdRec.getBookingReference() +
                            " (Flight " + flightNumber + " " + originCode + "->" + destCode + ", Seat " + seat.getSeatLabel() + ", Total R " + quote.total() + ")");
                    created++;
                }
            } catch (Exception e) {
                logger.error("Error creating demo bookings", e);
            }
        }

        long countAfter = countBookingsForUser(userId);
        List<String> refsAfter = listBookingRefsForUser(userId);

        System.out.println("==================================================");
        System.out.println("=== DEMO DATA AUDIT AFTER ===");
        System.out.println("Bookings Count After: " + countAfter);
        System.out.println("All Booking References for User 12: " + refsAfter);
        System.out.println("==================================================");
    }

    private static List<FlightSeat> loadAvailableSeatsForFlight(Connection conn, long flightId) throws Exception {
        String sql = """
            SELECT fs.flight_seat_id, fs.flight_id, fs.status, fs.held_until,
                   st.seat_row, st.seat_col, st.seat_class
            FROM flight_seats fs
            JOIN seat_templates st ON fs.seat_template_id = st.seat_template_id
            WHERE fs.flight_id = ? AND fs.status = 'AVAILABLE'
            ORDER BY st.seat_row, st.seat_col
            """;
        List<FlightSeat> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, flightId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FlightSeat seat = new FlightSeat();
                    seat.setFlightSeatId(rs.getLong("flight_seat_id"));
                    seat.setFlightId(rs.getLong("flight_id"));
                    seat.setSeatRow(rs.getInt("seat_row"));
                    seat.setSeatCol(rs.getString("seat_col").charAt(0));
                    seat.setSeatClass(SeatClass.valueOf(rs.getString("seat_class")));
                    seat.setStatus(SeatStatus.valueOf(rs.getString("status")));
                    list.add(seat);
                }
            }
        }
        return list;
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
        logger.info("Saved My Bookings screenshot to: {}", outputFile.getAbsolutePath());

        if (brainDir != null && brainDir.exists()) {
            File brainArtifact = new File(brainDir, filename);
            try {
                Files.copy(outputFile.toPath(), brainArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied screenshot to brain artifact directory: {}", brainArtifact.getAbsolutePath());
            } catch (Exception ignored) {}
        }
    }

    private static String computeSha256(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("altitude.screenshots", "true");
        FontLoader.loadFonts();

        PricingService pricingService = new PricingService();
        BookingService bookingService = new BookingService();
        Clock clock = Clock.systemUTC();

        // 1. Ensure at least 4 demo bookings exist
        ensureDemoBookingsExist(bookingService, pricingService, clock);

        User demoUser = new User();
        demoUser.setUserId(12L);
        demoUser.setEmail("demo.traveller@example.com");
        demoUser.setFullName("Demo Traveller");
        demoUser.setRole(UserRole.CUSTOMER);

        BookingContext ctx = new BookingContext();
        ctx.setCurrentUser(demoUser);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/my-bookings.fxml"));
        Parent root = loader.load();

        MyBookingsController controller = loader.getController();
        controller.init(ctx, () -> {});

        Scene scene = new Scene(root, 1280, 800);
        ThemeManager.getInstance().applyTheme(scene);
        stage.setScene(scene);
        stage.setTitle("Glass My Bookings Screenshot");
        stage.setResizable(true);
        stage.setWidth(1280);
        stage.setHeight(800);
        stage.show();

        Path outputDir = getOutputDir();
        Files.createDirectories(outputDir);
        File brainDir = new File("C:/Users/tttttttttttttttttttt/.gemini/antigravity/brain/dd68c2a6-b6fe-4136-9f98-988170ed66c9");

        // Sequence:
        // 1. Populated at 1280x800 -> glass-bookings-populated.png
        // 2. Populated at 900x680 -> glass-bookings-populated-900.png
        // 3. Empty at 1280x800 -> glass-bookings-empty.png
        // 4. Error at 1280x800 -> glass-bookings-error.png

        PauseTransition p1 = new PauseTransition(Duration.millis(900));
        p1.setOnFinished(e1 -> {
            try {
                controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                captureSnapshot(scene, "glass-bookings-populated.png", outputDir, brainDir);

                // Resize to 900x680
                stage.setWidth(900);
                stage.setHeight(680);

                PauseTransition p2 = new PauseTransition(Duration.millis(700));
                p2.setOnFinished(e2 -> {
                    try {
                        controller.updateResponsiveLayout(scene.getWidth(), scene.getHeight());
                        captureSnapshot(scene, "glass-bookings-populated-900.png", outputDir, brainDir);

                        // Resize back to 1280x800 and show empty state
                        stage.setWidth(1280);
                        stage.setHeight(800);
                        controller.updateResponsiveLayout(1280, 800);
                        controller.showEmptyState();

                        PauseTransition p3 = new PauseTransition(Duration.millis(600));
                        p3.setOnFinished(e3 -> {
                            try {
                                captureSnapshot(scene, "glass-bookings-empty.png", outputDir, brainDir);

                                // Show error state
                                controller.showErrorState("We encountered an issue retrieving your booking history. Please try again.");

                                PauseTransition p4 = new PauseTransition(Duration.millis(600));
                                p4.setOnFinished(e4 -> {
                                    try {
                                        captureSnapshot(scene, "glass-bookings-error.png", outputDir, brainDir);

                                        System.out.println("=== SCREENSHOT HASH REPORT ===");
                                        File popFile = outputDir.resolve("glass-bookings-populated.png").toFile();
                                        File pop900File = outputDir.resolve("glass-bookings-populated-900.png").toFile();
                                        File emptyFile = outputDir.resolve("glass-bookings-empty.png").toFile();
                                        File errFile = outputDir.resolve("glass-bookings-error.png").toFile();

                                        System.out.println("glass-bookings-populated.png SHA256: " + computeSha256(popFile));
                                        System.out.println("glass-bookings-populated-900.png SHA256: " + computeSha256(pop900File));
                                        System.out.println("glass-bookings-empty.png SHA256: " + computeSha256(emptyFile));
                                        System.out.println("glass-bookings-error.png SHA256: " + computeSha256(errFile));
                                        System.out.println("==============================");

                                        logger.info("All 4 My Bookings screenshots captured successfully.");
                                        Platform.exit();
                                    } catch (Exception ex) {
                                        logger.error("Failed to capture error screenshot", ex);
                                        Platform.exit();
                                    }
                                });
                                p4.play();
                            } catch (Exception ex) {
                                logger.error("Failed to capture empty screenshot", ex);
                                Platform.exit();
                            }
                        });
                        p3.play();
                    } catch (Exception ex) {
                        logger.error("Failed to capture 900x680 screenshot", ex);
                        Platform.exit();
                    }
                });
                p2.play();
            } catch (Exception ex) {
                logger.error("Failed to capture populated screenshot", ex);
                Platform.exit();
            }
        });
        p1.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
