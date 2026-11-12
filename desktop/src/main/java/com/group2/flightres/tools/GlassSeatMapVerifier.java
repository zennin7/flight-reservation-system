package com.group2.flightres.tools;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.util.FontLoader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Programmatic runtime verifier testing all DB interactions and controls for
 * Screen 3 (Seat Map):
 * 1. Click seat (7E) -> confirms status 'HELD' in PostgreSQL DB.
 * 2. Click another seat (9E) -> confirms 7E released ('AVAILABLE') and 9E held ('HELD') in DB.
 * 3. Click 'Release seat and choose another' -> confirms 9E released ('AVAILABLE') in DB.
 * 4. Click taken seat twice -> confirms seat taken error/warning alert is handled.
 * 5. Continue to checkout -> confirms BookingContext has selected seat and updated price.
 */
public class GlassSeatMapVerifier extends Application {

    private static String querySeatStatus(long flightId, int row, String col) {
        String sql = """
            SELECT fs.status
            FROM flight_seats fs
            JOIN seat_templates st ON fs.seat_template_id = st.seat_template_id
            WHERE fs.flight_id = ? AND st.seat_row = ? AND st.seat_col = ?
            """;
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, flightId);
            ps.setInt(2, row);
            ps.setString(3, col);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "UNKNOWN";
    }

    private static void resetFlight1Seats() {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE flight_seats SET status = 'AVAILABLE', held_until = NULL WHERE flight_id = 1");
            stmt.executeUpdate("UPDATE flight_seats SET status = 'BOOKED' WHERE flight_id = 1 AND flight_seat_id IN " +
                    "(SELECT fs.flight_seat_id FROM flight_seats fs JOIN seat_templates st ON fs.seat_template_id = st.seat_template_id " +
                    "WHERE fs.flight_id = 1 AND ((st.seat_row = 1 AND st.seat_col = 'C') OR (st.seat_row = 2 AND st.seat_col = 'C') " +
                    "OR (st.seat_row = 6 AND st.seat_col IN ('A','B')) OR (st.seat_row = 7 AND st.seat_col IN ('C','D','F')) " +
                    "OR (st.seat_row = 8 AND st.seat_col = 'A') OR (st.seat_row = 9 AND st.seat_col = 'B') " +
                    "OR (st.seat_row = 10 AND st.seat_col IN ('C','D')) OR (st.seat_row = 11 AND st.seat_col IN ('A','F'))))");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        FontLoader.loadFonts();
        resetFlight1Seats();

        System.out.println("===================================================================");
        System.out.println("  GLASS SEAT MAP RUNTIME & DATABASE CONCURRENCY VERIFIER");
        System.out.println("===================================================================");

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("JNB");
        ctx.setDestination("CPT");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setDepartureDate("2026-11-14");
        ctx.setBaseFare(new BigDecimal("1200"));
        PriceQuote quote = PriceQuote.calculate(new BigDecimal("1200.00"), BigDecimal.ONE, 0.0, 100.0);
        ctx.setPriceQuotes(List.of(quote));
        ctx.setTotalPrice(quote.total());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/seat-map.fxml"));
        Parent root = loader.load();

        SeatMapController controller = loader.getController();
        AtomicBoolean nextTriggered = new AtomicBoolean(false);
        controller.init(ctx, () -> nextTriggered.set(true), () -> {});

        Scene scene = new Scene(root, 1280, 860);
        stage.setScene(scene);
        stage.show();

        // 1. Initial State Check
        String initial7E = querySeatStatus(1L, 7, "E");
        String initial9E = querySeatStatus(1L, 9, "E");
        System.out.println("[INITIAL DB STATE] Seat 7E: " + initial7E + ", Seat 9E: " + initial9E);

        // STEP 1: Click available seat 7E
        System.out.println("\n--- STEP 1: Select Seat 7E ---");
        controller.selectSeatByLabel("7E");
        String dbStatusAfter7E = querySeatStatus(1L, 7, "E");
        boolean step1Passed = "HELD".equalsIgnoreCase(dbStatusAfter7E);
        System.out.println("DB Status of 7E after click: " + dbStatusAfter7E + " -> " + (step1Passed ? "PASS [HELD in DB]" : "FAIL"));

        // STEP 2: Click another seat 9E (should release 7E and hold 9E)
        System.out.println("\n--- STEP 2: Select Seat 9E (clears previous 7E) ---");
        controller.selectSeatByLabel("9E");
        String dbStatus7EAfter9E = querySeatStatus(1L, 7, "E");
        String dbStatus9EAfter9E = querySeatStatus(1L, 9, "E");
        boolean step2Passed = "AVAILABLE".equalsIgnoreCase(dbStatus7EAfter9E) && "HELD".equalsIgnoreCase(dbStatus9EAfter9E);
        System.out.println("DB Status of 7E: " + dbStatus7EAfter9E + " (expected AVAILABLE) -> " + ("AVAILABLE".equalsIgnoreCase(dbStatus7EAfter9E) ? "PASS" : "FAIL"));
        System.out.println("DB Status of 9E: " + dbStatus9EAfter9E + " (expected HELD) -> " + ("HELD".equalsIgnoreCase(dbStatus9EAfter9E) ? "PASS" : "FAIL"));
        System.out.println("Step 2 Overall: " + (step2Passed ? "PASS" : "FAIL"));

        // STEP 3: Click 'Release seat and choose another'
        System.out.println("\n--- STEP 3: Release seat and choose another ---");
        controller.handleReleaseSeat();
        String dbStatus9EAfterRelease = querySeatStatus(1L, 9, "E");
        boolean step3Passed = "AVAILABLE".equalsIgnoreCase(dbStatus9EAfterRelease);
        System.out.println("DB Status of 9E after release: " + dbStatus9EAfterRelease + " -> " + (step3Passed ? "PASS [AVAILABLE in DB]" : "FAIL"));

        // STEP 4: Click taken seat twice (seat 6A is BOOKED in DB)
        System.out.println("\n--- STEP 4: Click taken seat twice (Seat 6A) ---");
        String status6A = querySeatStatus(1L, 6, "A");
        System.out.println("DB Status of 6A before clicks: " + status6A + " (BOOKED)");
        // Click 1:
        controller.selectSeatByLabel("6A");
        // Click 2:
        controller.selectSeatByLabel("6A");
        String status6AAfter = querySeatStatus(1L, 6, "A");
        boolean step4Passed = "BOOKED".equalsIgnoreCase(status6AAfter) && controller.getSelectedSeatsList().isEmpty();
        System.out.println("DB Status of 6A after clicks: " + status6AAfter + " (remains BOOKED)");
        System.out.println("Selected seats count: " + controller.getSelectedSeatsList().size() + " (none selected)");
        System.out.println("Step 4 Error Protection: " + (step4Passed ? "PASS [Cannot hold taken seat]" : "FAIL"));

        // STEP 5: Re-select 9E and Continue to checkout
        System.out.println("\n--- STEP 5: Select 9E & Continue to checkout ---");
        controller.selectSeatByLabel("9E");
        var continueBtn = (javafx.scene.control.Button) root.lookup("#continueButton");
        boolean continueEnabled = continueBtn != null && !continueBtn.isDisable();
        System.out.println("Continue button enabled: " + continueEnabled);

        if (continueBtn != null) {
            continueBtn.fire();
        }
        boolean nextFired = nextTriggered.get();
        boolean contextHasSeat = ctx.getSelectedSeats() != null && !ctx.getSelectedSeats().isEmpty()
                && "9E".equals(ctx.getSelectedSeats().get(0).getSeatLabel());
        BigDecimal total = ctx.getTotalPrice();
        boolean priceCorrect = total != null && total.compareTo(BigDecimal.ZERO) > 0;

        System.out.println("Next wizard transition triggered: " + nextFired);
        System.out.println("BookingContext selected seat: " + (contextHasSeat ? ctx.getSelectedSeats().get(0).getSeatLabel() : "None"));
        System.out.println("BookingContext recalculated total: R " + total);
        boolean step5Passed = nextFired && contextHasSeat && priceCorrect;
        System.out.println("Step 5 Overall: " + (step5Passed ? "PASS" : "FAIL"));

        System.out.println("\n===================================================================");
        if (step1Passed && step2Passed && step3Passed && step4Passed && step5Passed) {
            System.out.println("  ALL 5 VERIFICATION STEPS PASSED SUCCESSFULLY! (5/5)");
        } else {
            System.out.println("  SOME STEPS FAILED!");
        }
        System.out.println("===================================================================");

        controller.handleReleaseSeat();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
