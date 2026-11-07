package com.group2.flightres.tools;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.group2.flightres.config.DatabaseConfig;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.group2.flightres.component.TicketView;
import com.group2.flightres.controller.BookingConfirmationController;
import com.group2.flightres.model.TicketData;
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
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.util.Duration;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import java.util.LinkedHashMap;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.text.Text;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
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

    public static class Rect {
        public final double minX, minY, maxX, maxY;

        public Rect(double minX, double minY, double maxX, double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        public static Rect fromBounds(Bounds b) {
            if (b == null) return null;
            return new Rect(b.getMinX(), b.getMinY(), b.getMaxX(), b.getMaxY());
        }

        public double getWidth() { return Math.max(0.0, maxX - minX); }
        public double getHeight() { return Math.max(0.0, maxY - minY); }

        public boolean isEmpty() {
            return minX >= maxX || minY >= maxY;
        }

        public Rect intersect(Rect other) {
            if (other == null || this.isEmpty() || other.isEmpty()) return null;
            double iMinX = Math.max(this.minX, other.minX);
            double iMaxX = Math.min(this.maxX, other.maxX);
            double iMinY = Math.max(this.minY, other.minY);
            double iMaxY = Math.min(this.maxY, other.maxY);
            if (iMinX >= iMaxX || iMinY >= iMaxY) {
                return null;
            }
            return new Rect(iMinX, iMinY, iMaxX, iMaxY);
        }

        public boolean contains(Rect inner, double eps) {
            if (inner == null || inner.isEmpty()) return false;
            return inner.minX >= this.minX - eps
                    && inner.maxX <= this.maxX + eps
                    && inner.minY >= this.minY - eps
                    && inner.maxY <= this.maxY + eps;
        }

        public boolean nearlyEquals(Rect other, double eps) {
            if (other == null) return false;
            return Math.abs(this.minX - other.minX) <= eps
                    && Math.abs(this.maxX - other.maxX) <= eps
                    && Math.abs(this.minY - other.minY) <= eps
                    && Math.abs(this.maxY - other.maxY) <= eps;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "[minX=%.1f, minY=%.1f, maxX=%.1f, maxY=%.1f, w=%.1f, h=%.1f]",
                    minX, minY, maxX, maxY, getWidth(), getHeight());
        }
    }

    @Override
    public void start(Stage stage) {
        System.setProperty("altitude.testing", "true");
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

            // CHECK 10: TicketView bound to BookingContext
            check10_ticketViewFromBookingContext(stage);

            // CHECK 11: TicketView bound to BookingRecord
            check11_ticketViewFromBookingRecord(stage);

            // CHECK 12: Destination photo shown for CPT
            check12_photoShownForCpt(stage);

            // CHECK 13: Navy/gold gradient fallback for unknown airport code
            check13_gradientForUnknownCode(stage);

            // CHECK 14: Barcode strip tightened (bars fill >= 85% of strip width, height ~44px)
            check14_barcodeWidthRatio(stage);

            // CHECK 15: Barcode image decodes with ZXing to booking reference
            check15_barcodeDecodes(stage);

            // CHECK 16: TicketView no truncation at 780x575 and scaled size
            check16_ticketViewTruncationCheck(stage);

            // CHECK 17: Destination photo cover-crop coverage and aspect ratio
            check17_photoCoverageCheck(stage);

            // CHECK 18: Responsive layout overlap and horizontal clipping check
            check18_responsiveOverlapAndBoundsCheck(stage);
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
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setSelectedSeats(List.of(seat));
        ctx.setPassengerName("Amahle Mokoena");
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
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");

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
        if (!ics.contains("SUMMARY:Flight GRP 204: Johannesburg (JNB) to Cape Town (CPT)")) {
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
            try {
                String decoded = decodeBufferedImage(bimg);
                if (!"K7QX2M".equals(decoded)) {
                    failures.add("Check 5: Decoded barcode text '" + decoded + "' does not match 'K7QX2M'");
                } else {
                    System.out.println("  ZXing Decoded barcode: " + decoded);
                }
            } catch (Exception e) {
                failures.add("Check 5: ZXing decoding failed: " + e.getMessage());
            }
        }
        System.out.println("Check 5 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check6_pdfGeneration() throws Exception {
        System.out.println("\n--- CHECK 6: Boarding Pass PDF Generation ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");

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
        missingRef.setArrivalTime("08:35");
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
                ctx.setFlightNumber("GRP 204");
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
        latch.await(15, TimeUnit.SECONDS);

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


    private void check10_ticketViewFromBookingContext(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 10: TicketView Bound To BookingContext ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");

        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        CompletableFuture<TicketView> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TicketView tv = new TicketView();
                tv.setBooking(ctx);
                future.complete(tv);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        TicketView tv = future.get(15, TimeUnit.SECONDS);
        if (!"Amahle Mokoena".equals(tv.getPassengerNameLabel().getText())) {
            failures.add("Check 10: Expected passenger 'Amahle Mokoena', got '" + tv.getPassengerNameLabel().getText() + "'");
        }
        if (!"GRP 204".equals(tv.getFlightNumberLabel().getText())) {
            failures.add("Check 10: Expected flight 'GRP 204', got '" + tv.getFlightNumberLabel().getText() + "'");
        }
        if (!"Sat, 14 Nov 2026".equals(tv.getFlightDateLabel().getText())) {
            failures.add("Check 10: Expected date 'Sat, 14 Nov 2026', got '" + tv.getFlightDateLabel().getText() + "'");
        }
        if (!"JNB".equals(tv.getOriginCodeLabel().getText())) {
            failures.add("Check 10: Expected origin 'JNB', got '" + tv.getOriginCodeLabel().getText() + "'");
        }
        if (!"CPT".equals(tv.getDestCodeLabel().getText())) {
            failures.add("Check 10: Expected dest 'CPT', got '" + tv.getDestCodeLabel().getText() + "'");
        }
        if (!"9E".equals(tv.getStubSeatLabel().getText())) {
            failures.add("Check 10: Expected seat '9E', got '" + tv.getStubSeatLabel().getText() + "'");
        }
        if (!"K7QX2M".equals(tv.getStubReferenceLabel().getText())) {
            failures.add("Check 10: Expected reference 'K7QX2M', got '" + tv.getStubReferenceLabel().getText() + "'");
        }
        System.out.println("  TicketView from Context: passenger=" + tv.getPassengerNameLabel().getText()
                + ", flight=" + tv.getFlightNumberLabel().getText() + ", seat=" + tv.getStubSeatLabel().getText());
        System.out.println("Check 10 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check11_ticketViewFromBookingRecord(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 11: TicketView Bound To BookingRecord ---");
        int preFailures = failures.size();

        BookingRecord record = new BookingRecord();
        record.setBookingId(888L);
        record.setBookingReference("REC888");
        record.setFlightNumber("GRP 310");
        record.setOrigin("Johannesburg (JNB)");
        record.setDestination("Durban (DUR)");
        record.setDepartureDate("2026-11-14");
        record.setDepartureTime("09:15");
        record.setArrivalTime("10:25");
        record.setSeatLabel("4B");
        record.setSeatClass(SeatClass.FIRST);
        record.setPassengerName("Nandi Khumalo");

        CompletableFuture<TicketView> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TicketView tv = new TicketView();
                tv.setBooking(record);
                future.complete(tv);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        TicketView tv = future.get(5, TimeUnit.SECONDS);
        if (!"Nandi Khumalo".equals(tv.getPassengerNameLabel().getText())) {
            failures.add("Check 11: Expected passenger 'Nandi Khumalo', got '" + tv.getPassengerNameLabel().getText() + "'");
        }
        if (!"GRP 310".equals(tv.getFlightNumberLabel().getText())) {
            failures.add("Check 11: Expected flight 'GRP 310', got '" + tv.getFlightNumberLabel().getText() + "'");
        }
        if (!"DUR".equals(tv.getDestCodeLabel().getText())) {
            failures.add("Check 11: Expected dest 'DUR', got '" + tv.getDestCodeLabel().getText() + "'");
        }
        if (!"4B".equals(tv.getStubSeatLabel().getText())) {
            failures.add("Check 11: Expected seat '4B', got '" + tv.getStubSeatLabel().getText() + "'");
        }
        if (!"REC888".equals(tv.getStubReferenceLabel().getText())) {
            failures.add("Check 11: Expected reference 'REC888', got '" + tv.getStubReferenceLabel().getText() + "'");
        }
        System.out.println("  TicketView from Record: passenger=" + tv.getPassengerNameLabel().getText()
                + ", flight=" + tv.getFlightNumberLabel().getText() + ", dest=" + tv.getDestCodeLabel().getText());
        System.out.println("Check 11 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check12_photoShownForCpt(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 12: Destination Photo Shown For CPT ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("CPT123");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");
        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        CompletableFuture<TicketView> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TicketView tv = new TicketView();
                tv.setBooking(ctx);
                future.complete(tv);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        TicketView tv = future.get(5, TimeUnit.SECONDS);
        if (tv.getPhotoImageView().getImage() == null) {
            failures.add("Check 12: Photo image is null for destination CPT");
        }
        if (!tv.getPhotoImageView().isVisible()) {
            failures.add("Check 12: Photo image view is not visible for destination CPT");
        }
        if (!"Cape Town".equals(tv.getWelcomeCityLabel().getText())) {
            failures.add("Check 12: Welcome city label is not 'Cape Town': " + tv.getWelcomeCityLabel().getText());
        }
        System.out.println("  CPT Photo Loaded: " + (tv.getPhotoImageView().getImage() != null)
                + ", City Label: " + tv.getWelcomeCityLabel().getText());
        System.out.println("Check 12 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check13_gradientForUnknownCode(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 13: Navy/Gold Gradient Fallback For Unknown Airport Code ---");
        int preFailures = failures.size();

        BookingRecord record = new BookingRecord();
        record.setBookingId(999L);
        record.setBookingReference("UNK789");
        record.setFlightNumber("GRP 999");
        record.setOrigin("Johannesburg (JNB)");
        record.setDestination("Nowhere (XYZ)");
        record.setDepartureDate("2026-11-14");
        record.setDepartureTime("14:00");
        record.setArrivalTime("16:00");
        record.setSeatLabel("12F");
        record.setSeatClass(SeatClass.ECONOMY);
        record.setPassengerName("Test Passenger");

        CompletableFuture<TicketView> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TicketView tv = new TicketView();
                tv.setBooking(record);
                future.complete(tv);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        TicketView tv = future.get(5, TimeUnit.SECONDS);
        if (tv.getPhotoImageView().getImage() != null) {
            failures.add("Check 13: Expected null photo image for unknown code XYZ, but got an image");
        }
        if (tv.getPhotoImageView().isVisible()) {
            failures.add("Check 13: Photo image view should be hidden for unknown code XYZ");
        }
        if (!tv.getPhotoGradientOverlay().getStyleClass().contains("ticket-photo-gradient-fallback")) {
            failures.add("Check 13: Gradient overlay should use 'ticket-photo-gradient-fallback'");
        }
        if (!"Nowhere".equals(tv.getWelcomeCityLabel().getText())) {
            failures.add("Check 13: Welcome city label should be 'Nowhere', got '" + tv.getWelcomeCityLabel().getText() + "'");
        }
        System.out.println("  Unknown Code Fallback: photoHidden=" + (!tv.getPhotoImageView().isVisible())
                + ", fallbackGradient=" + tv.getPhotoGradientOverlay().getStyleClass().contains("ticket-photo-gradient-fallback")
                + ", city=" + tv.getWelcomeCityLabel().getText());
        System.out.println("Check 13 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check14_barcodeWidthRatio(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 14: Barcode Bars Fill >= 85% Of Strip Width, Height ~44px ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");
        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        CompletableFuture<Map<String, Double>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TicketView tv = new TicketView();
                tv.setBooking(ctx);
                Scene testScene = new Scene(tv, 1280, 800);
                stage.setScene(testScene);
                tv.applyCss();
                tv.layout();

                Image fxImg = tv.getBarcodeImageView().getImage();
                int w = (int) fxImg.getWidth();
                int h = (int) fxImg.getHeight();
                int midY = h / 2;
                int minX = -1;
                int maxX = -1;
                PixelReader pr = fxImg.getPixelReader();
                for (int x = 0; x < w; x++) {
                    javafx.scene.paint.Color c = pr.getColor(x, midY);
                    if (c.getBrightness() < 0.5) {
                        if (minX == -1) minX = x;
                        maxX = x;
                    }
                }
                double barsWidth = (maxX - minX + 1);
                double stripWidth = w;
                double ratio = barsWidth / stripWidth;
                double fitH = tv.getBarcodeImageView().getFitHeight();
                int quietLeft = minX;
                int quietRight = w - 1 - maxX;
                future.complete(Map.of(
                        "ratio", ratio,
                        "stripWidth", stripWidth,
                        "barsWidth", barsWidth,
                        "fitHeight", fitH,
                        "quietLeft", (double) quietLeft,
                        "quietRight", (double) quietRight
                ));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        Map<String, Double> m = future.get(10, TimeUnit.SECONDS);
        double ratio = m.get("ratio");
        double stripWidth = m.get("stripWidth");
        double barsWidth = m.get("barsWidth");
        double fitH = m.get("fitHeight");
        double quietLeft = m.get("quietLeft");
        double quietRight = m.get("quietRight");

        System.out.printf("  Barcode on screen: stripWidth=%.1f px, barsWidth=%.1f px, fillRatio=%.3f (target >= 0.85), height=%.1f px (~44px), quietZones=[L=%.0f, R=%.0f]\n",
                stripWidth, barsWidth, ratio, fitH, quietLeft, quietRight);

        if (ratio < 0.85) {
            failures.add(String.format("Check 14: Barcode fill ratio %.3f is below required 0.85", ratio));
        }
        if (Math.abs(fitH - 44.0) > 2.0) {
            failures.add(String.format("Check 14: Barcode height %.1f px is not ~44px", fitH));
        }
        if (quietLeft < 15.0 || quietRight < 15.0) {
            failures.add(String.format("Check 14: Barcode quiet zone too small: L=%.0f, R=%.0f", quietLeft, quietRight));
        }
        System.out.println("Check 14 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check16_ticketViewTruncationCheck(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 16: TicketView No Label Truncation At Design & Scaled Sizes ---");
        System.out.println("  [Audit Note] Previous 'scale=1.154 @ 900x680' was measured by placing TicketView as standalone");
        System.out.println("  root of Scene(tv, 900, 680), taking the entire scene without shell margins or sidebar.");
        System.out.println("  In the full Confirmation layout at 900x680, available width is ~812px (scale=1.041 in stacked mode).");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");
        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        double[] testScales = {0.90, 1.00, 1.041, 1.25};
        String[] scaleLabels = {"min cap (0.90x)", "design (1.00x)", "900x680 stacked (1.041x)", "max cap (1.25x)"};

        for (int i = 0; i < testScales.length; i++) {
            double targetScale = testScales[i];
            String label = scaleLabels[i];

            CompletableFuture<int[]> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    TicketView tv = new TicketView();
                    tv.setBooking(ctx);
                    tv.setCardScale(targetScale);
                    Scene scene = new Scene(tv, 1280, 800);
                    tv.applyCss();
                    tv.layout();

                    List<Labeled> labeledNodes = new ArrayList<>();
                    collectLabeledNodes(tv, labeledNodes);

                    int truncCount = 0;
                    for (Labeled l : labeledNodes) {
                        if (l.getText() == null || l.getText().isEmpty()) continue;
                        Text textNode = (Text) l.lookup(".text");
                        if (textNode != null && textNode.getText() != null && !textNode.getText().isEmpty()) {
                            String rendered = textNode.getText();
                            String expected = l.getText();
                            if (rendered.contains("\u2026") || rendered.endsWith("...") || !rendered.equals(expected)) {
                                failures.add("Check 16 (" + label + "): Truncation in [" + l.getId() + "]: rendered='" + rendered + "', expected='" + expected + "'");
                                truncCount++;
                            }
                        }
                    }
                    future.complete(new int[]{labeledNodes.size(), truncCount});
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });

            int[] res = future.get(10, TimeUnit.SECONDS);
            System.out.printf("  Scale %s: %d Labeled nodes verified, %d truncations\n", label, res[0], res[1]);
        }

        System.out.println("Check 16 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check18_responsiveOverlapAndBoundsCheck(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 18: Responsive Layout Overlap & Viewport Clipping Check ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");
        ctx.setPassengerEmail("amahle@example.com");
        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));
        ctx.setBaseFare(new BigDecimal("1200"));
        ctx.setDemandPricing(new BigDecimal("180"));
        ctx.setTaxesAndFees(new BigDecimal("142"));
        ctx.setTotalPrice(new BigDecimal("1522"));

        // Setup stage and scene once, matching GlassConfirmationScreenshotRunner
        CompletableFuture<BookingConfirmationController> initFuture = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/booking-confirmation.fxml"));
                Parent root = loader.load();
                BookingConfirmationController ctrl = loader.getController();
                ctrl.init(ctx, () -> {});

                Scene scene = new Scene(root, 1280, 800);
                ThemeManager.getInstance().applyTheme(scene);
                stage.setScene(scene);
                stage.setTitle("Glass Confirmation Verifier");
                stage.setResizable(true);
                stage.setWidth(1280);
                stage.setHeight(800);
                stage.show();
                initFuture.complete(ctrl);
            } catch (Throwable t) {
                initFuture.completeExceptionally(t);
            }
        });

        BookingConfirmationController ctrl = initFuture.get(15, TimeUnit.SECONDS);
        Thread.sleep(400);

        class TestConfig {
            final String label;
            final boolean isOuter;
            final double targetW;
            final double targetH;
            final String expectedMode;

            TestConfig(String label, boolean isOuter, double targetW, double targetH, String expectedMode) {
                this.label = label;
                this.isOuter = isOuter;
                this.targetW = targetW;
                this.targetH = targetH;
                this.expectedMode = expectedMode;
            }
        }

        List<TestConfig> configs = List.of(
                new TestConfig("Outer 900x680", true, 900, 680, "SINGLE_COLUMN"),
                new TestConfig("Outer 1100x700", true, 1100, 700, "SINGLE_COLUMN"),
                new TestConfig("Outer 1280x800", true, 1280, 800, "TWO_COLUMN"),
                new TestConfig("Outer 1366x768", true, 1366, 768, "TWO_COLUMN"),
                new TestConfig("Outer 1920x1080", true, 1920, 1080, "TWO_COLUMN"),
                new TestConfig("Scene 1099x700 (breakpoint)", false, 1099, 700, "SINGLE_COLUMN"),
                new TestConfig("Scene 1100x700 (breakpoint)", false, 1100, 700, "TWO_COLUMN"),
                new TestConfig("Scene 1920x1080", false, 1920, 1080, "TWO_COLUMN")
        );

        for (TestConfig cfg : configs) {
            CompletableFuture<Map<String, Object>> cfgFuture = new CompletableFuture<>();

            Platform.runLater(() -> {
                try {
                    Scene scene = stage.getScene();
                    if (cfg.isOuter) {
                        stage.setWidth(cfg.targetW);
                        stage.setHeight(cfg.targetH);
                    } else {
                        double diffW = cfg.targetW - scene.getWidth();
                        double diffH = cfg.targetH - scene.getHeight();
                        stage.setWidth(stage.getWidth() + diffW);
                        stage.setHeight(stage.getHeight() + diffH);
                    }

                    ScrollPane sp = ctrl.getConfirmationScrollPane();
                    if (sp != null) {
                        sp.setVvalue(0.0);
                        sp.setHvalue(0.0);
                    }

                    PauseTransition pause = new PauseTransition(Duration.millis(350));
                    pause.setOnFinished(ev -> {
                        try {
                            Parent root = stage.getScene().getRoot();
                            root.applyCss();
                            root.layout();

                            double scW = scene.getWidth();
                            double scH = scene.getHeight();
                            ctrl.updateResponsiveLayout(scW, scH);
                            root.applyCss();
                            root.layout();

                            boolean isTwoCol = ctrl.getTwoColumnBox() != null
                                    && ctrl.getTwoColumnBox().isVisible()
                                    && ctrl.getTwoColumnBox().isManaged();
                            String activeMode = isTwoCol ? "TWO_COLUMN" : "SINGLE_COLUMN";

                            Node vpNode = (sp != null) ? sp.lookup(".viewport") : null;
                            Bounds vpBounds = (vpNode != null)
                                    ? vpNode.localToScene(vpNode.getLayoutBounds())
                                    : (sp != null ? sp.localToScene(sp.getLayoutBounds()) : scene.getRoot().localToScene(scene.getRoot().getLayoutBounds()));
                            Rect vpRect = Rect.fromBounds(vpBounds);

                            TicketView tv = ctrl.getTicketView();
                            Node barcode = tv != null ? tv.getBarcodeImageView() : null;
                            Node passengerRow = tv != null ? tv.getPassengerNameLabel() : null;
                            Node refStub = tv != null ? tv.getStubReferenceLabel() : null;
                            Node payment = ctrl.getPaymentBox();
                            Node nextSteps = ctrl.getNextStepsBox();
                            Node downloadBtn = ctrl.getDownloadPdfButton();
                            Node calendarBtn = ctrl.getAddToCalendarButton();

                            Map<String, Rect> allNodes = new LinkedHashMap<>();
                            if (tv != null) allNodes.put("Ticket", Rect.fromBounds(tv.localToScene(tv.getLayoutBounds())));
                            if (barcode != null) allNodes.put("Barcode", Rect.fromBounds(barcode.localToScene(barcode.getLayoutBounds())));
                            if (passengerRow != null) allNodes.put("PassengerRow", Rect.fromBounds(passengerRow.localToScene(passengerRow.getLayoutBounds())));
                            if (refStub != null) allNodes.put("Reference", Rect.fromBounds(refStub.localToScene(refStub.getLayoutBounds())));
                            if (payment != null) allNodes.put("Payment", Rect.fromBounds(payment.localToScene(payment.getLayoutBounds())));
                            if (nextSteps != null) allNodes.put("NextSteps", Rect.fromBounds(nextSteps.localToScene(nextSteps.getLayoutBounds())));
                            if (downloadBtn != null) allNodes.put("DownloadBtn", Rect.fromBounds(downloadBtn.localToScene(downloadBtn.getLayoutBounds())));
                            if (calendarBtn != null) allNodes.put("CalendarBtn", Rect.fromBounds(calendarBtn.localToScene(calendarBtn.getLayoutBounds())));

                            Map<String, Rect> majorBlocks = new LinkedHashMap<>();
                            if (tv != null) majorBlocks.put("Ticket", Rect.fromBounds(tv.localToScene(tv.getLayoutBounds())));
                            if (payment != null) majorBlocks.put("Payment", Rect.fromBounds(payment.localToScene(payment.getLayoutBounds())));
                            if (nextSteps != null) majorBlocks.put("NextSteps", Rect.fromBounds(nextSteps.localToScene(nextSteps.getLayoutBounds())));
                            if (downloadBtn != null) majorBlocks.put("DownloadBtn", Rect.fromBounds(downloadBtn.localToScene(downloadBtn.getLayoutBounds())));
                            if (calendarBtn != null) majorBlocks.put("CalendarBtn", Rect.fromBounds(calendarBtn.localToScene(calendarBtn.getLayoutBounds())));

                            // 1. Overlaps between major blocks
                            List<String> overlaps = new ArrayList<>();
                            List<String> keys = new ArrayList<>(majorBlocks.keySet());
                            for (int i = 0; i < keys.size(); i++) {
                                for (int j = i + 1; j < keys.size(); j++) {
                                    Rect b1 = majorBlocks.get(keys.get(i));
                                    Rect b2 = majorBlocks.get(keys.get(j));
                                    if (b1 != null && b2 != null && b1.intersect(b2) != null) {
                                        overlaps.add(keys.get(i) + " intersects " + keys.get(j));
                                    }
                                }
                            }

                            // 2. Vertical scrollbar in SINGLE_COLUMN
                            Node vbar = (sp != null) ? sp.lookup(".scroll-bar:vertical") : null;
                            Rect vbarRect = (vbar != null) ? Rect.fromBounds(vbar.localToScene(vbar.getLayoutBounds())) : null;
                            boolean vbarVisible = (vbar != null && vbar.isVisible());

                            // 3. Clipping checks
                            List<String> hClipped = new ArrayList<>();
                            List<String> vClipped = new ArrayList<>();
                            if (isTwoCol) {
                                for (Map.Entry<String, Rect> entry : allNodes.entrySet()) {
                                    Rect b = entry.getValue();
                                    Rect inter = b.intersect(vpRect);
                                    if (inter == null || !inter.nearlyEquals(b, 1.5)) {
                                        if (b.minX < vpRect.minX - 1.5 || b.maxX > vpRect.maxX + 1.5) {
                                            hClipped.add(String.format(Locale.US,
                                                    "%s [minX=%.1f, maxX=%.1f] outside viewport [minX=%.1f, maxX=%.1f]",
                                                    entry.getKey(), b.minX, b.maxX, vpRect.minX, vpRect.maxX));
                                        }
                                        if (b.minY < vpRect.minY - 1.5 || b.maxY > vpRect.maxY + 1.5) {
                                            vClipped.add(String.format(Locale.US,
                                                    "%s [minY=%.1f, maxY=%.1f] outside viewport [minY=%.1f, maxY=%.1f]",
                                                    entry.getKey(), b.minY, b.maxY, vpRect.minY, vpRect.maxY));
                                        }
                                    }
                                }
                            } else {
                                // SINGLE_COLUMN: assert node maxX <= viewport maxX - 16 AND node maxX < vbar minX - 4
                                for (Map.Entry<String, Rect> entry : majorBlocks.entrySet()) {
                                    Rect b = entry.getValue();
                                    if (b.maxX > vpRect.maxX - 16.0 + 1.5) {
                                        hClipped.add(String.format(Locale.US,
                                                "%s maxX=%.1f > viewport maxX - 16 (%.1f)",
                                                entry.getKey(), b.maxX, vpRect.maxX - 16.0));
                                    }
                                    if (vbarVisible && vbarRect != null && b.maxX >= vbarRect.minX - 4.0 - 1.5) {
                                        hClipped.add(String.format(Locale.US,
                                                "%s maxX=%.1f >= vbar minX - 4 (%.1f)",
                                                entry.getKey(), b.maxX, vbarRect.minX - 4.0));
                                    }
                                }
                            }

                            // 4. Single-column scroll sweep checks
                            List<String> scrollErrors = new ArrayList<>();
                            List<String> sweepLogs = new ArrayList<>();
                            Double middleSweepFound = null;
                            if (!isTwoCol && sp != null) {
                                Map<String, Node> liveNodes = new LinkedHashMap<>();
                                if (tv != null) liveNodes.put("Ticket", tv);
                                if (barcode != null) liveNodes.put("Barcode", barcode);
                                if (passengerRow != null) liveNodes.put("PassengerRow", passengerRow);
                                if (refStub != null) liveNodes.put("Reference", refStub);
                                if (payment != null) liveNodes.put("Payment", payment);
                                if (nextSteps != null) liveNodes.put("NextSteps", nextSteps);
                                if (downloadBtn != null) liveNodes.put("DownloadBtn", downloadBtn);
                                if (calendarBtn != null) liveNodes.put("CalendarBtn", calendarBtn);

                                for (double sweepV : new double[]{0.0, 0.5, 1.0}) {
                                    sp.setVvalue(sweepV);
                                    root.applyCss();
                                    root.layout();
                                    Node curVpNode = sp.lookup(".viewport");
                                    Rect curVp = Rect.fromBounds((curVpNode != null)
                                            ? curVpNode.localToScene(curVpNode.getLayoutBounds())
                                            : sp.localToScene(sp.getLayoutBounds()));

                                    List<String> intersectingNames = new ArrayList<>();
                                    for (Map.Entry<String, Node> e : liveNodes.entrySet()) {
                                        Rect nr = Rect.fromBounds(e.getValue().localToScene(e.getValue().getLayoutBounds()));
                                        if (curVp.intersect(nr) != null) {
                                            intersectingNames.add(e.getKey());
                                        }
                                    }

                                    if (Math.abs(sweepV - 0.0) < 0.01) {
                                        Node headerRow = (ctrl.getConfirmedContainer() != null && !ctrl.getConfirmedContainer().getChildren().isEmpty())
                                                ? ctrl.getConfirmedContainer().getChildren().get(0) : null;
                                        Rect headerRect = Rect.fromBounds(headerRow != null ? headerRow.localToScene(headerRow.getLayoutBounds()) : null);
                                        boolean headerVisible = headerRect != null
                                                && headerRect.minY >= -1.5 && headerRect.maxY <= scene.getHeight() + 1.5
                                                && headerRect.minX >= -1.5 && headerRect.maxX <= scene.getWidth() + 1.5;

                                        Rect curTicket = Rect.fromBounds(tv != null ? tv.localToScene(tv.getLayoutBounds()) : null);
                                        boolean ticketTopVisible = curTicket != null
                                                && curTicket.minY >= curVp.minY - 1.5 && curTicket.minY <= curVp.maxY;

                                        sweepLogs.add(String.format(Locale.US,
                                                "    Scroll sweep vvalue=0.0: intersecting=%s | ticketTopVisible=%b, headerRowVisible=%b",
                                                intersectingNames, ticketTopVisible, headerVisible));
                                        if (!ticketTopVisible) {
                                            scrollErrors.add(String.format(Locale.US,
                                                    "Ticket top minY=%.1f not visible at scroll=0.0 in viewport [minY=%.1f, maxY=%.1f]",
                                                    (curTicket != null ? curTicket.minY : -1.0), curVp.minY, curVp.maxY));
                                        }
                                        if (!headerVisible) {
                                            scrollErrors.add("Header row not fully visible in scene at scroll=0.0");
                                        }
                                    }

                                    Rect curBarcode = Rect.fromBounds(barcode != null ? barcode.localToScene(barcode.getLayoutBounds()) : null);
                                    Rect curPass = Rect.fromBounds(passengerRow != null ? passengerRow.localToScene(passengerRow.getLayoutBounds()) : null);
                                    Rect curRef = Rect.fromBounds(refStub != null ? refStub.localToScene(refStub.getLayoutBounds()) : null);
                                    boolean threeVis = curVp.contains(curBarcode, 1.5) && curVp.contains(curPass, 1.5) && curVp.contains(curRef, 1.5);
                                    if (threeVis && middleSweepFound == null) {
                                        middleSweepFound = sweepV;
                                    }
                                    if (Math.abs(sweepV - 0.5) < 0.01) {
                                        sweepLogs.add(String.format(Locale.US,
                                                "    Scroll sweep vvalue=0.5: intersecting=%s | Barcode=%b, PassengerRow=%b, Reference=%b",
                                                intersectingNames, curVp.contains(curBarcode, 1.5), curVp.contains(curPass, 1.5), curVp.contains(curRef, 1.5)));
                                    }

                                    if (Math.abs(sweepV - 1.0) < 0.01) {
                                        Rect curPay = Rect.fromBounds(payment != null ? payment.localToScene(payment.getLayoutBounds()) : null);
                                        Rect curNext = Rect.fromBounds(nextSteps != null ? nextSteps.localToScene(nextSteps.getLayoutBounds()) : null);
                                        Rect curDl = Rect.fromBounds(downloadBtn != null ? downloadBtn.localToScene(downloadBtn.getLayoutBounds()) : null);
                                        Rect curCal = Rect.fromBounds(calendarBtn != null ? calendarBtn.localToScene(calendarBtn.getLayoutBounds()) : null);
                                        boolean payVis = curVp.contains(curPay, 1.5);
                                        boolean nextVis = curVp.contains(curNext, 1.5);
                                        boolean dlVis = curVp.contains(curDl, 1.5);
                                        boolean calVis = curVp.contains(curCal, 1.5);

                                        sweepLogs.add(String.format(Locale.US,
                                                "    Scroll sweep vvalue=1.0: intersecting=%s | Payment=%b, NextSteps=%b, DownloadBtn=%b, CalendarBtn=%b [cal=%s, vp=%s]",
                                                intersectingNames, payVis, nextVis, dlVis, calVis, curCal, curVp));
                                        if (!payVis) scrollErrors.add("Payment not fully visible at scroll=1.0");
                                        if (!nextVis) scrollErrors.add("NextSteps not fully visible at scroll=1.0");
                                        if (!dlVis) scrollErrors.add("DownloadBtn not fully visible at scroll=1.0");
                                        if (!calVis) scrollErrors.add("CalendarBtn not fully visible at scroll=1.0");
                                    }
                                }

                                if (middleSweepFound == null) {
                                    scrollErrors.add("Barcode, PassengerRow and Reference were not fully visible simultaneously at any sweep value [0.0, 0.5, 1.0]");
                                } else {
                                    sweepLogs.add(String.format(Locale.US, "    Barcode, PassengerRow and Reference fully visible at vvalue=%.1f", middleSweepFound));
                                }
                                sp.setVvalue(0.0);
                            }

                            double scale = tv != null ? tv.getCardScale() : 1.0;
                            double effectiveFontSize = 11.0 * scale;

                            Bounds contentBounds = isTwoCol
                                    ? (ctrl.getTwoColumnBox() != null ? ctrl.getTwoColumnBox().localToScene(ctrl.getTwoColumnBox().getLayoutBounds()) : null)
                                    : (ctrl.getSingleColumnBox() != null ? ctrl.getSingleColumnBox().localToScene(ctrl.getSingleColumnBox().getLayoutBounds()) : null);

                            double leftMargin = contentBounds != null ? (contentBounds.getMinX() - vpRect.minX) : 0.0;
                            double rightMargin = contentBounds != null ? (vpRect.maxX - contentBounds.getMaxX()) : 0.0;
                            double marginDiff = Math.abs(leftMargin - rightMargin);

                            double topMargin = contentBounds != null ? (contentBounds.getMinY() - vpRect.minY) : 0.0;
                            double bottomMargin = contentBounds != null ? (vpRect.maxY - contentBounds.getMaxY()) : 0.0;
                            double vMarginDiff = Math.abs(topMargin - bottomMargin);

                            Map<String, Object> resMap = new LinkedHashMap<>();
                            resMap.put("config", cfg.label);
                            resMap.put("sceneW", scW);
                            resMap.put("sceneH", scH);
                            resMap.put("mode", activeMode);
                            resMap.put("expectedMode", cfg.expectedMode);
                            resMap.put("scale", scale);
                            resMap.put("effectiveFontSize", effectiveFontSize);
                            resMap.put("vpRect", vpRect);
                            resMap.put("allNodes", allNodes);
                            resMap.put("vbarRect", vbarRect);
                            resMap.put("vbarVisible", vbarVisible);
                            resMap.put("sweepLogs", sweepLogs);
                            resMap.put("contentBounds", contentBounds);
                            resMap.put("leftMargin", leftMargin);
                            resMap.put("rightMargin", rightMargin);
                            resMap.put("marginDiff", marginDiff);
                            resMap.put("topMargin", topMargin);
                            resMap.put("bottomMargin", bottomMargin);
                            resMap.put("vMarginDiff", vMarginDiff);
                            resMap.put("overlaps", overlaps);
                            resMap.put("hClipped", hClipped);
                            resMap.put("vClipped", vClipped);
                            resMap.put("scrollErrors", scrollErrors);

                            cfgFuture.complete(resMap);
                        } catch (Throwable t2) {
                            cfgFuture.completeExceptionally(t2);
                        }
                    });
                    pause.play();
                } catch (Throwable t) {
                    cfgFuture.completeExceptionally(t);
                }
            });

            Map<String, Object> r = cfgFuture.get(30, TimeUnit.SECONDS);
            String cfgLabel = (String) r.get("config");
            double scW = (Double) r.get("sceneW");
            double scH = (Double) r.get("sceneH");
            String mode = (String) r.get("mode");
            String expMode = (String) r.get("expectedMode");
            double scale = (Double) r.get("scale");
            double effFont = (Double) r.get("effectiveFontSize");
            Rect vp = (Rect) r.get("vpRect");
            @SuppressWarnings("unchecked")
            Map<String, Rect> nodes = (Map<String, Rect>) r.get("allNodes");
            Rect vbarRect = (Rect) r.get("vbarRect");
            boolean vbarVisible = Boolean.TRUE.equals(r.get("vbarVisible"));
            @SuppressWarnings("unchecked")
            List<String> sweepLogs = (List<String>) r.get("sweepLogs");
            Bounds contentBounds = (Bounds) r.get("contentBounds");
            @SuppressWarnings("unchecked")
            List<String> overlaps = (List<String>) r.get("overlaps");
            @SuppressWarnings("unchecked")
            List<String> hClipped = (List<String>) r.get("hClipped");
            @SuppressWarnings("unchecked")
            List<String> vClipped = (List<String>) r.get("vClipped");
            @SuppressWarnings("unchecked")
            List<String> scrollErrors = (List<String>) r.get("scrollErrors");

            // Compute margins directly from the printed rects (vp and nodes)
            double nodeMinX = Double.MAX_VALUE;
            double nodeMaxX = -Double.MAX_VALUE;
            double nodeMinY = Double.MAX_VALUE;
            double nodeMaxY = -Double.MAX_VALUE;
            for (Rect nr : nodes.values()) {
                if (nr != null && !nr.isEmpty()) {
                    nodeMinX = Math.min(nodeMinX, nr.minX);
                    nodeMaxX = Math.max(nodeMaxX, nr.maxX);
                    nodeMinY = Math.min(nodeMinY, nr.minY);
                    nodeMaxY = Math.max(nodeMaxY, nr.maxY);
                }
            }
            double leftMargin = (nodeMinX != Double.MAX_VALUE) ? (nodeMinX - vp.minX) : 0.0;
            double rightMargin = (nodeMaxX != -Double.MAX_VALUE) ? (vp.maxX - nodeMaxX) : 0.0;
            double marginDiff = Math.abs(leftMargin - rightMargin);

            double topMargin = (nodeMinY != Double.MAX_VALUE) ? (nodeMinY - vp.minY) : 0.0;
            double bottomMargin = (nodeMaxY != -Double.MAX_VALUE) ? (vp.maxY - nodeMaxY) : 0.0;
            double vMarginDiff = Math.abs(topMargin - bottomMargin);

            System.out.printf(Locale.US, "\n  Configuration: %s -> Scene: %.1fx%.1f | Mode: %s (expected: %s) | Scale: %.3f | EffFont: %.2fpx\n",
                    cfgLabel, scW, scH, mode, expMode, scale, effFont);
            System.out.printf(Locale.US, "  Viewport Rect: %s\n", vp);

            for (Map.Entry<String, Rect> entry : nodes.entrySet()) {
                Rect b = entry.getValue();
                Rect inter = b.intersect(vp);
                String interDesc = (inter != null)
                        ? String.format(Locale.US, "Intersect: [X=%.1f..%.1f, Y=%.1f..%.1f]", inter.minX, inter.maxX, inter.minY, inter.maxY)
                        : "Intersect: NONE (outside viewport)";
                System.out.printf(Locale.US, "    Node %-12s: sceneBounds=%s | %s\n",
                        entry.getKey(), b, interDesc);
            }

            System.out.printf(Locale.US, "    Horizontal Centering: leftMargin=%.1f px, rightMargin=%.1f px, diff=%.1f px (target <= 4.0 px)\n",
                    leftMargin, rightMargin, marginDiff);
            if ("TWO_COLUMN".equals(mode) && (vp.getHeight() - (nodeMaxY - nodeMinY) > 10.0)) {
                System.out.printf(Locale.US, "    Vertical Centering (spare height): topMargin=%.1f px, bottomMargin=%.1f px, diff=%.1f px (target <= 4.0 px)\n",
                        topMargin, bottomMargin, vMarginDiff);
            }
            if (!"TWO_COLUMN".equals(mode)) {
                if (vbarRect != null) {
                    System.out.printf(Locale.US, "    VBar Bounds: %s | visible=%b\n", vbarRect, vbarVisible);
                } else {
                    System.out.println("    VBar Bounds: NONE | visible=false");
                }
                for (String sLog : sweepLogs) {
                    System.out.println(sLog);
                }
            }

            if (!expMode.equals(mode)) {
                failures.add(String.format("Check 18 (%s): VIOLATION: Mode %s does not match expected %s", cfgLabel, mode, expMode));
            }
            if (effFont < 9.0) {
                failures.add(String.format("Check 18 (%s): VIOLATION: Effective ticket font size %.2fpx < 9.0px", cfgLabel, effFont));
            }
            if (marginDiff > 4.0) {
                failures.add(String.format(Locale.US,
                        "Check 18 (%s): VIOLATION: Content not horizontally centered in viewport (leftMargin=%.1f, rightMargin=%.1f, diff=%.1f > 4.0px)",
                        cfgLabel, leftMargin, rightMargin, marginDiff));
            }
            if ("TWO_COLUMN".equals(mode) && (vp.getHeight() - (nodeMaxY - nodeMinY) > 10.0)) {
                if (vMarginDiff > 4.0) {
                    failures.add(String.format(Locale.US,
                            "Check 18 (%s): VIOLATION: TWO_COLUMN not vertically centered in viewport with spare height (topMargin=%.1f, bottomMargin=%.1f, diff=%.1f > 4.0px)",
                            cfgLabel, topMargin, bottomMargin, vMarginDiff));
                }
            }
            for (String o : overlaps) {
                failures.add(String.format("Check 18 (%s): VIOLATION: OVERLAP: %s", cfgLabel, o));
            }
            for (String c : hClipped) {
                failures.add(String.format("Check 18 (%s): VIOLATION: HORIZONTAL CLIPPING: %s", cfgLabel, c));
            }
            for (String c : vClipped) {
                failures.add(String.format("Check 18 (%s): VIOLATION: VERTICAL CLIPPING: %s", cfgLabel, c));
            }
            for (String s : scrollErrors) {
                failures.add(String.format("Check 18 (%s): VIOLATION: SCROLL ERROR: %s", cfgLabel, s));
            }
            System.out.printf("    Summary (%s): Overlaps=%d, HClipped=%d, VClipped=%d, ScrollErrors=%d, Text>=9px=%b, Centered=%b\n",
                    cfgLabel, overlaps.size(), hClipped.size(), vClipped.size(), scrollErrors.size(), effFont >= 9.0, marginDiff <= 4.0);
        }

        System.out.println("Check 18 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private void check17_photoCoverageCheck(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 17: Destination Photo Cover-Crop Aspect Ratio & Panel Coverage ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("CPT123");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");
        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        CompletableFuture<Map<String, Double>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TicketView tv = new TicketView();
                tv.setBooking(ctx);
                Scene testScene = new Scene(tv, 1280, 800);
                tv.applyCss();
                tv.layout();
                tv.updatePhotoCoverCrop();

                double panelW = tv.getPhotoPanel().getWidth() > 0 ? tv.getPhotoPanel().getWidth() : 165.0;
                double panelH = tv.getPhotoPanel().getHeight() > 0 ? tv.getPhotoPanel().getHeight() : 575.0;
                double panelRatio = panelW / panelH;

                Rectangle2D vp = tv.getPhotoImageView().getViewport();
                double vpW = vp.getWidth();
                double vpH = vp.getHeight();
                double vpRatio = vpW / vpH;

                double aspectDiffPct = Math.abs(vpRatio - panelRatio) / panelRatio * 100.0;
                double fitW = tv.getPhotoImageView().getFitWidth();
                double fitH = tv.getPhotoImageView().getFitHeight();

                future.complete(Map.of(
                        "panelW", panelW,
                        "panelH", panelH,
                        "panelRatio", panelRatio,
                        "vpW", vpW,
                        "vpH", vpH,
                        "vpRatio", vpRatio,
                        "aspectDiffPct", aspectDiffPct,
                        "fitW", fitW,
                        "fitH", fitH
                ));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        Map<String, Double> m = future.get(10, TimeUnit.SECONDS);
        double panelW = m.get("panelW");
        double panelH = m.get("panelH");
        double panelRatio = m.get("panelRatio");
        double vpW = m.get("vpW");
        double vpH = m.get("vpH");
        double vpRatio = m.get("vpRatio");
        double aspectDiffPct = m.get("aspectDiffPct");
        double fitW = m.get("fitW");
        double fitH = m.get("fitH");

        System.out.printf("  Panel: %.1fx%.1f (aspect: %.4f), Viewport: %.1fx%.1f (aspect: %.4f, diff: %.3f%%), Fit: %.1fx%.1f\n",
                panelW, panelH, panelRatio, vpW, vpH, vpRatio, aspectDiffPct, fitW, fitH);

        if (aspectDiffPct > 2.0) {
            failures.add(String.format("Check 17: Viewport aspect ratio diff %.2f%% exceeds 2%%", aspectDiffPct));
        }
        if (Math.abs(fitW - panelW) > 1.0 || Math.abs(fitH - panelH) > 1.0) {
            failures.add(String.format("Check 17: Image does not cover whole panel bounds: fit=%.1fx%.1f, panel=%.1fx%.1f",
                    fitW, fitH, panelW, panelH));
        }
        System.out.println("Check 17 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private static void collectLabeledNodes(Node node, List<Labeled> list) {
        if (node instanceof Labeled labeled) {
            list.add(labeled);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectLabeledNodes(child, list);
            }
        }
    }

    private void check15_barcodeDecodes(Stage stage) throws Exception {
        System.out.println("\n--- CHECK 15: Barcode Image Decodes With ZXing To Real Reference ---");
        int preFailures = failures.size();

        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");
        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        CompletableFuture<javafx.scene.image.Image> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TicketView tv = new TicketView();
                tv.setBooking(ctx);
                Scene testScene = new Scene(tv, 1060, 360);
                tv.applyCss();
                tv.layout();

                javafx.scene.image.Image img = tv.getBarcodeImageView().getImage();
                future.complete(img);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        javafx.scene.image.Image fxImg = future.get(10, TimeUnit.SECONDS);
        if (fxImg == null) {
            failures.add("Check 15: Barcode image is null on TicketView");
        } else {
            String decoded = decodeFxImage(fxImg);
            System.out.println("  ZXing Decoded on-screen barcode: " + decoded);
            if (!"K7QX2M".equals(decoded)) {
                failures.add("Check 15: Decoded text '" + decoded + "' does not match expected 'K7QX2M'");
            }
        }
        System.out.println("Check 15 Result: " + (failures.size() == preFailures ? "PASS" : "FAIL"));
    }

    private static String decodeBufferedImage(BufferedImage bimg) throws Exception {
        int w = bimg.getWidth();
        int h = bimg.getHeight();
        BufferedImage opaque = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaque.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.drawImage(bimg, 0, 0, null);
        g.dispose();

        LuminanceSource source = new BufferedImageLuminanceSource(opaque);
        Map<DecodeHintType, Object> hints = Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source.invert())), hints).getText();
        } catch (Exception e) {
            return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
        }
    }

    private static String decodeFxImage(javafx.scene.image.Image fxImg) throws Exception {
        int w = (int) fxImg.getWidth();
        int h = (int) fxImg.getHeight();
        BufferedImage bimg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader pr = fxImg.getPixelReader();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                bimg.setRGB(x, y, pr.getArgb(x, y));
            }
        }
        return decodeBufferedImage(bimg);
    }
}
