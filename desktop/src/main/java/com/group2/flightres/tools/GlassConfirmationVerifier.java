package com.group2.flightres.tools;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.controller.BookingConfirmationController;
import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.controller.MainShellController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PaymentService;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.util.BarcodeGenerator;
import com.group2.flightres.util.BoardingPassPdfGenerator;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.IcsCalendarGenerator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Screen 5 (Confirmation) Runtime & Database Concurrency Verifier.
 * Executes automated end-to-end verification of real booking display, error states,
 * calendar export, barcode encoding, PDF generation, and context resets.
 */
public class GlassConfirmationVerifier extends Application {

    private static final Logger logger = LoggerFactory.getLogger(GlassConfirmationVerifier.class);

    private static final long TEST_FLIGHT_ID = 1L;
    private static final long TEST_FLIGHT_SEAT_ID = 53L; // Seat 9E on flight 1
    private static Long createdBookingId = null;
    private static final List<String> failures = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        System.setProperty("skyvoyage.testing", "true");
        FontLoader.loadFonts();

        new Thread(() -> {
            try {
                runAllChecks(stage);
            } catch (Throwable t) {
                logger.error("Verifier encountered unexpected error", t);
                failures.add("UNEXPECTED: " + t.getMessage());
            } finally {
                printFinalReport();
                Platform.exit();
            }
        }, "verifier-thread").start();
    }

    private void runAllChecks(Stage stage) throws Exception {
        System.out.println("===================================================================");
        System.out.println("  SCREEN 5 (CONFIRMATION) RUNTIME & INTEGRITY VERIFIER");
        System.out.println("===================================================================");

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            // Restore seat 9E to AVAILABLE before tests
            setFlightSeatStatus(conn, TEST_FLIGHT_SEAT_ID, "AVAILABLE", false);
        }

        try {
            // CHECK 1: Real Booking made via BookingService -> Confirmation shows real reference/seat/total
            check1_realBookingDisplay(stage);

            // CHECK 2: Null / Blank Booking Reference gives Error State
            check2_nullReferenceErrorState(stage);

            // CHECK 3: "Back to Home" resets the BookingContext
            check3_backToHomeResetsContext();

            // CHECK 4: Add to Calendar generates valid RFC 5545 .ics content
            check4_icsCalendarGeneration();

            // CHECK 5: Code 128 Barcode encodes the real booking reference
            check5_code128BarcodeEncoding();

            // CHECK 6: Boarding Pass PDF generation succeeds
            check6_pdfGeneration();

            // CHECK 7: No fabricated values in PDF generation (throws on missing data, no A12)
            check7_pdfStrictValidation();

            // CHECK 8: Checkout hold timer stopped on booking success
            check8_checkoutTimerStoppedOnSuccess();

            // CHECK 9: resetBookingContext releases held seats when no booking reference
            check9_resetContextReleasesHeldSeat();
        } finally {
            // Cleanup
            try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
                if (createdBookingId != null) {
                    cleanupCreatedTestBooking(conn, createdBookingId);
                }
                setFlightSeatStatus(conn, TEST_FLIGHT_SEAT_ID, "AVAILABLE", false);
                System.out.println("[TEARDOWN] Restored seat 9E to AVAILABLE");
            } catch (Exception e) {
                logger.error("Teardown failed", e);
            }
        }
    }

    private void check1_realBookingDisplay(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 1: Real Booking Confirmation Display ---");

        BookingService bookingService = new BookingService();
        SeatMapService seatMapService = new SeatMapService();

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(TEST_FLIGHT_SEAT_ID);
        seat.setFlightId(TEST_FLIGHT_ID);
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.AVAILABLE);

        seatMapService.selectSeat(seat);

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(TEST_FLIGHT_ID);
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setSelectedSeats(List.of(seat));
        ctx.setPassengerName("Ms Amahle Mokoena");
        ctx.setPassengerEmail("amahle@example.com");
        ctx.setPassportNumber("A91827364");
        ctx.setDateOfBirth(LocalDate.of(1998, 5, 12));
        ctx.setBaseFare(new BigDecimal("1250.00"));
        ctx.setTaxesAndFees(new BigDecimal("272.00"));
        ctx.setTotalPrice(new BigDecimal("1522.00"));
        ctx.setPaymentResult(new PaymentResult(true, "TXN-CONFIRM-001", "VISA", "4242", null));

        BookingRecord record = bookingService.createBooking(ctx);
        createdBookingId = record.getBookingId();
        ctx.setBookingReference(record.getBookingReference());

        System.out.println("  Created real DB test booking: ID=" + createdBookingId + ", Ref=" + record.getBookingReference());

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/booking-confirmation.fxml"));
                Parent root = loader.load();
                BookingConfirmationController controller = loader.getController();
                controller.init(ctx, () -> {});

                boolean errVis = controller.getErrorContainer() != null && controller.getErrorContainer().isVisible();
                boolean confVis = controller.getConfirmedContainer() != null && controller.getConfirmedContainer().isVisible();
                String displayedRef = controller.getBookingRefLabel() != null ? controller.getBookingRefLabel().getText() : "";
                String displayedSeat = controller.getStubSeatLabel() != null ? controller.getStubSeatLabel().getText() : "";
                String displayedTotal = controller.getTotalPaidLabel() != null ? controller.getTotalPaidLabel().getText() : "";
                boolean hasBarcode = controller.getBarcodeImageView() != null && controller.getBarcodeImageView().getImage() != null;

                if (errVis) failures.add("Check 1: Error container should not be visible for valid booking");
                if (!confVis) failures.add("Check 1: Confirmed container must be visible for valid booking");
                if (!record.getBookingReference().equalsIgnoreCase(displayedRef)) {
                    failures.add("Check 1: Displayed ref '" + displayedRef + "' != record ref '" + record.getBookingReference() + "'");
                }
                if (!"9E".equalsIgnoreCase(displayedSeat)) {
                    failures.add("Check 1: Displayed seat '" + displayedSeat + "' != '9E'");
                }
                if (!displayedTotal.contains("1,522")) {
                    failures.add("Check 1: Displayed total '" + displayedTotal + "' does not contain 1,522");
                }
                if (!hasBarcode) {
                    failures.add("Check 1: Barcode image view was not populated");
                }

                String gateVal = controller.getGateValueLabel() != null ? controller.getGateValueLabel().getText() : "";
                String gateNote = controller.getGateNoteLabel() != null ? controller.getGateNoteLabel().getText() : "";
                if (gateVal.toLowerCase().contains("assigned") || gateNote.toLowerCase().contains("assigned")) {
                    failures.add("Check 1: Stub contains 'assigned'! gateVal='" + gateVal + "', gateNote='" + gateNote + "'");
                }
                if (!gateVal.equalsIgnoreCase("Shown at the airport")) {
                    failures.add("Check 1: Gate value is not 'Shown at the airport', was: '" + gateVal + "'");
                }

                System.out.println("  Confirmed Container Visible: " + confVis);
                System.out.println("  Displayed Reference: " + displayedRef);
                System.out.println("  Displayed Seat: " + displayedSeat);
                System.out.println("  Displayed Total: " + displayedTotal);
                System.out.println("  Barcode Image Rendered: " + hasBarcode);

            } catch (Exception e) {
                failures.add("Check 1 FXML loading failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
        System.out.println("Check 1 Result: " + (failures.isEmpty() ? "PASS" : "FAIL"));
    }

    private void check2_nullReferenceErrorState(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 2: Null / Blank Booking Reference Error State ---");
        int preFailures = failures.size();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/booking-confirmation.fxml"));
                Parent root = loader.load();
                BookingConfirmationController controller = loader.getController();

                BookingContext emptyCtx = new BookingContext();
                controller.init(emptyCtx, () -> {});

                boolean errVis = controller.getErrorContainer() != null && controller.getErrorContainer().isVisible();
                boolean confVis = controller.getConfirmedContainer() != null && controller.getConfirmedContainer().isVisible();

                if (!errVis) failures.add("Check 2: Error container must be visible when booking reference is missing");
                if (confVis) failures.add("Check 2: Confirmed container must NOT be visible when booking reference is missing");

                System.out.println("  Empty Context -> Error Container Visible: " + errVis + ", Confirmed Container Visible: " + confVis);

                // Null context test
                controller.init(null, () -> {});
                boolean errVisNull = controller.getErrorContainer() != null && controller.getErrorContainer().isVisible();
                if (!errVisNull) failures.add("Check 2: Error container must be visible when context is null");

                System.out.println("  Null Context  -> Error Container Visible: " + errVisNull);

            } catch (Exception e) {
                failures.add("Check 2 FXML loading failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
        System.out.println("Check 2 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check3_backToHomeResetsContext() {
        System.out.println("\n--- CHECK 3: Back to Home Resets Context ---");
        int preFailures = failures.size();

        MainShellController shell = new MainShellController();
        BookingContext ctx = shell.getBookingContext();
        ctx.setBookingReference("EXISTING_REF");
        ctx.setPassengerName("Test User");

        shell.resetBookingContext();
        BookingContext freshCtx = shell.getBookingContext();

        if (freshCtx == null) {
            failures.add("Check 3: Fresh context is null");
        } else if (freshCtx.getBookingReference() != null) {
            failures.add("Check 3: Fresh context retained old booking reference: " + freshCtx.getBookingReference());
        } else if (freshCtx.getPassengerName() != null) {
            failures.add("Check 3: Fresh context retained old passenger name: " + freshCtx.getPassengerName());
        }

        System.out.println("  Old ref: EXISTING_REF -> New ref: " + (freshCtx != null ? freshCtx.getBookingReference() : "null"));
        System.out.println("Check 3 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check4_icsCalendarGeneration() {
        System.out.println("\n--- CHECK 4: RFC 5545 iCalendar Generation ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Ms Amahle Mokoena");

        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.BOOKED);
        ctx.setSelectedSeats(List.of(seat));

        String ics = IcsCalendarGenerator.generateIcsContent(ctx);

        if (!ics.contains("BEGIN:VCALENDAR")) failures.add("Check 4: Missing BEGIN:VCALENDAR");
        if (!ics.contains("END:VCALENDAR")) failures.add("Check 4: Missing END:VCALENDAR");
        if (!ics.contains("DTSTART:20261114T063000")) failures.add("Check 4: Missing or invalid DTSTART");
        if (!ics.contains("DTEND:20261114T083500")) failures.add("Check 4: Missing or invalid DTEND");
        if (!ics.contains("SUMMARY:Flight FL 204: Johannesburg (JNB) to Cape Town (CPT)")) {
            failures.add("Check 4: Missing or invalid SUMMARY");
        }
        if (!ics.contains("STATUS:CONFIRMED")) failures.add("Check 4: Missing STATUS:CONFIRMED");

        System.out.println("  Generated .ics size: " + ics.length() + " chars");
        System.out.println("Check 4 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check5_code128BarcodeEncoding() {
        System.out.println("\n--- CHECK 5: Code 128 Barcode Encoding ---");
        int preFailures = failures.size();

        BufferedImage bimg = BarcodeGenerator.generateCode128Image("K7QX2M", 38, Color.WHITE, new Color(0, 0, 0, 0));
        if (bimg == null) {
            failures.add("Check 5: Generated barcode image is null");
        } else {
            if (bimg.getWidth() <= 50 || bimg.getHeight() != 38) {
                failures.add("Check 5: Unexpected barcode dimensions: " + bimg.getWidth() + "x" + bimg.getHeight());
            }
            System.out.println("  Code 128 Image rendered: " + bimg.getWidth() + "x" + bimg.getHeight() + " px");
        }
        System.out.println("Check 5 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check6_pdfGeneration() throws Exception {
        System.out.println("\n--- CHECK 6: Boarding Pass PDF Generation ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Ms Amahle Mokoena");

        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.BOOKED);
        ctx.setSelectedSeats(List.of(seat));

        File tempPdf = File.createTempFile("BoardingPass_Verify_", ".pdf");
        tempPdf.deleteOnExit();

        BoardingPassPdfGenerator.generate(ctx, tempPdf);

        if (!tempPdf.exists() || tempPdf.length() < 1000) {
            failures.add("Check 6: Generated PDF file does not exist or is smaller than 1KB (" + tempPdf.length() + " bytes)");
        } else {
            System.out.println("  PDF successfully created: " + tempPdf.length() + " bytes");
        }
        System.out.println("Check 6 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check7_pdfStrictValidation() {
        System.out.println("\n--- CHECK 7: PDF Generator Strict Validation (No Fallbacks) ---");
        int preFailures = failures.size();

        BookingContext missingPassenger = new BookingContext();
        missingPassenger.setBookingReference("K7QX2M");
        missingPassenger.setFlightNumber("FL 204");
        File tempFile = new File("target/test_missing_pass.pdf");

        try {
            BoardingPassPdfGenerator.generate(missingPassenger, tempFile);
            failures.add("Check 7: Missing passenger must throw IllegalStateException");
        } catch (IllegalStateException e) {
            System.out.println("  Missing passenger correctly rejected: " + e.getMessage());
        } catch (Exception other) {
            failures.add("Check 7: Expected IllegalStateException but got " + other.getClass().getSimpleName());
        }

        BookingContext missingRef = new BookingContext();
        missingRef.setPassengerName("Ms Amahle Mokoena");
        missingRef.setFlightNumber("FL 204");
        missingRef.setOrigin("Johannesburg (JNB)");
        missingRef.setDestination("Cape Town (CPT)");
        missingRef.setDepartureDate("2026-11-14");
        missingRef.setDepartureTime("06:30");
        FlightSeat testSeat = new FlightSeat();
        testSeat.setSeatRow(9);
        testSeat.setSeatCol('E');
        missingRef.setSelectedSeats(List.of(testSeat));
        missingRef.setBookingReference(null);

        try {
            BoardingPassPdfGenerator.generate(missingRef, tempFile);
            failures.add("Check 7: Missing booking ref must throw IllegalStateException");
        } catch (IllegalStateException e) {
            if (!e.getMessage().toLowerCase().contains("booking reference")) {
                failures.add("Check 7: Expected 'booking reference' in error, but got: " + e.getMessage());
            } else {
                System.out.println("  Missing booking ref correctly rejected: " + e.getMessage());
            }
        } catch (Exception other) {
            failures.add("Check 7: Expected IllegalStateException but got " + other.getClass().getSimpleName());
        }

        System.out.println("Check 7 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check8_checkoutTimerStoppedOnSuccess() throws Exception {
        System.out.println("\n--- CHECK 8: Checkout Hold Timer Stopped After Success ---");
        int preFailures = failures.size();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                BookingService mockBookingService = new BookingService() {
                    @Override
                    public BookingRecord createBooking(BookingContext context) {
                        BookingRecord r = new BookingRecord();
                        r.setBookingId(9999L);
                        r.setBookingReference("SUCCESS_PNR");
                        return r;
                    }
                };

                PaymentService mockPaymentService = new PaymentService() {
                    @Override
                    public PaymentResult processPayment(String cardNumber, String expiry, String cvv, BigDecimal amount) {
                        return new PaymentResult(true, "TXN-TEST-STOP", "VISA", "4242", null);
                    }
                    @Override
                    public void refund(String transactionReference) {
                    }
                };

                CheckoutController controller = new CheckoutController(mockBookingService, mockPaymentService);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
                loader.setControllerFactory(t -> controller);
                Parent root = loader.load();

                BookingContext ctx = new BookingContext();
                ctx.setFlightId(1L);
                ctx.setFlightNumber("FL 204");
                ctx.setOrigin("JNB");
                ctx.setDestination("CPT");
                ctx.setDepartureTime("06:30");
                ctx.setArrivalTime("08:35");
                ctx.setDepartureDate("2026-11-14");
                ctx.setPassengerName("Amahle Mokoena");
                ctx.setPassengerEmail("amahle@example.com");
                ctx.setPassportNumber("A91827364");
                ctx.setDateOfBirth(LocalDate.of(1998, 5, 12));
                ctx.setHoldExpiresAt(Instant.now().plusSeconds(300));

                FlightSeat seat = new FlightSeat();
                seat.setSeatRow(9);
                seat.setSeatCol('E');
                seat.setSeatClass(SeatClass.ECONOMY);
                seat.setStatus(SeatStatus.HELD);
                ctx.setSelectedSeats(List.of(seat));

                controller.init(ctx, () -> {}, () -> {});

                if (controller.getFirstNameField() != null) controller.getFirstNameField().setText("Amahle");
                if (controller.getLastNameField() != null) controller.getLastNameField().setText("Mokoena");
                if (controller.getEmailField() != null) controller.getEmailField().setText("amahle@example.com");
                if (controller.getPassportField() != null) controller.getPassportField().setText("A91827364");
                if (controller.getDobField() != null) controller.getDobField().setText("12 May 1998");
                if (controller.getCardNumberField() != null) controller.getCardNumberField().setText("4242 4242 4242 4242");
                if (controller.getExpiryField() != null) controller.getExpiryField().setText("08/28");
                if (controller.getCvvField() != null) controller.getCvvField().setText("123");

                controller.handleConfirm();

                // Advance hold expiry past zero now
                ctx.setHoldExpiresAt(Instant.now().minusSeconds(10));
                controller.getGlassBridge().checkHoldExpiry();

                // If timer was stopped properly, no "Seat Hold Expired" alert was triggered
                String lastAlert = controller.getLastAlertMessage();
                boolean holdAlertFired = lastAlert != null && lastAlert.contains("seat hold has expired");
                if (holdAlertFired) {
                    failures.add("Check 8: Hold expired alert fired after successful confirmation!");
                }
                boolean isStopped = controller.getGlassBridge() != null && controller.getGlassBridge().isStopped();
                if (!isStopped) {
                    failures.add("Check 8: Glass bridge hold timer was not stopped after success!");
                }
                System.out.println("  Post-Success Hold Expiry Attempt -> Alert Fired: " + holdAlertFired + ", Timer Stopped: " + isStopped);

            } catch (Exception e) {
                failures.add("Check 8 failed with exception: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
        System.out.println("Check 8 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private static void setFlightSeatStatus(Connection conn, long flightSeatId, String status, boolean setHeldUntil) throws Exception {
        String sql = setHeldUntil
                ? "UPDATE flight_seats SET status = ?::seat_status, held_until = NOW() + INTERVAL '5 minutes' WHERE flight_seat_id = ?"
                : "UPDATE flight_seats SET status = ?::seat_status, held_until = NULL WHERE flight_seat_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, flightSeatId);
            ps.executeUpdate();
        }
    }

    private static void cleanupCreatedTestBooking(Connection conn, Long bookingId) {
        if (bookingId == null || bookingId <= 0) return;
        try {
            try (PreparedStatement delBs = conn.prepareStatement("DELETE FROM booking_seats WHERE booking_id = ?")) {
                delBs.setLong(1, bookingId);
                delBs.executeUpdate();
            }
            try (PreparedStatement delPay = conn.prepareStatement("DELETE FROM payments WHERE booking_id = ?")) {
                delPay.setLong(1, bookingId);
                delPay.executeUpdate();
            }
            try (PreparedStatement delB = conn.prepareStatement("DELETE FROM bookings WHERE booking_id = ?")) {
                delB.setLong(1, bookingId);
                delB.executeUpdate();
            }
            System.out.println("[CLEANUP] Deleted test booking ID " + bookingId);
        } catch (Exception e) {
            System.err.println("[CLEANUP ERROR] Failed to clean up test booking: " + e.getMessage());
        }
    }

    private static void printFinalReport() {
        System.out.println("\n===================================================================");
        if (failures.isEmpty()) {
            System.out.println("  SCREEN 5 VERIFIER: ALL CHECKS PASSED [SUCCESS]");
        } else {
            System.out.println("  SCREEN 5 VERIFIER: " + failures.size() + " FAILURES FOUND:");
            for (String f : failures) {
                System.out.println("    - [FAIL] " + f);
            }
        }
        System.out.println("===================================================================\n");
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void check9_resetContextReleasesHeldSeat() throws Exception {
        System.out.println("\n--- CHECK 9: resetBookingContext Releases Held Seat On Navigation ---");
        int preFailures = failures.size();

        // Release Check 1's test booking first so seat 9E is available for Check 9
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            if (createdBookingId != null) {
                cleanupCreatedTestBooking(conn, createdBookingId);
                createdBookingId = null;
            }
            setFlightSeatStatus(conn, TEST_FLIGHT_SEAT_ID, "AVAILABLE", false);
        }

        SeatMapService seatMapService = new SeatMapService();
        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(TEST_FLIGHT_SEAT_ID);
        seat.setFlightId(TEST_FLIGHT_ID);
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.AVAILABLE);

        // 1. Hold seat 9E
        seatMapService.selectSeat(seat);

        // Assert it is HELD in DB
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT status FROM flight_seats WHERE flight_seat_id = ?")) {
            ps.setLong(1, TEST_FLIGHT_SEAT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String statusBefore = rs.getString(1);
                    if (!"HELD".equals(statusBefore)) {
                        failures.add("Check 9: Seat was not held before reset, status=" + statusBefore);
                    }
                }
            }
        }

        // 2. Set on MainShellController context with NO booking reference, simulate clicking 'Flights'
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                MainShellController shell = new MainShellController(seatMapService);
                BookingContext ctx = shell.getBookingContext();
                ctx.setSelectedSeats(List.of(seat));
                ctx.setBookingReference(null);

                shell.handleNavigation("Flights");
            } catch (Exception e) {
                failures.add("Check 9: Navigation failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);

        // 3. Assert DB status is now AVAILABLE again
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT status FROM flight_seats WHERE flight_seat_id = ?")) {
            ps.setLong(1, TEST_FLIGHT_SEAT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String statusAfter = rs.getString(1);
                    if (!"AVAILABLE".equals(statusAfter)) {
                        failures.add("Check 9: Seat was not released back to AVAILABLE, status=" + statusAfter);
                    } else {
                        System.out.println("  Held seat 9E successfully released to AVAILABLE on reset context / navigation");
                    }
                }
            }
        }

        System.out.println("Check 9 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

}
