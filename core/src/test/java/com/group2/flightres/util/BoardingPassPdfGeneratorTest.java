package com.group2.flightres.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.TicketData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BoardingPassPdfGeneratorTest {

    private BookingContext createSampleContext() {
        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setDepartureInstant(Instant.parse("2026-11-14T04:30:00Z"));
        ctx.setArrivalInstant(Instant.parse("2026-11-14T06:35:00Z"));
        ctx.setPassengerName("Amahle Mokoena");

        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.BOOKED);
        ctx.setSelectedSeats(List.of(seat));
        return ctx;
    }

    @Test
    void testPdfGenerationWithPdfBoxAndZxingVerification(@TempDir Path tempDir) throws Exception {
        BookingContext ctx = createSampleContext();
        TicketData data = TicketData.from(ctx);

        File targetPdf = tempDir.resolve("BoardingPass_K7QX2M.pdf").toFile();
        BoardingPassPdfGenerator.generate(data, targetPdf);

        assertTrue(targetPdf.exists());
        assertTrue(targetPdf.length() > 5000, "PDF should be generated with embedded fonts and graphics");

        // 1. PDFBox: Extract text and verify required strings & prohibited placeholders
        try (PDDocument doc = PDDocument.load(targetPdf)) {
            assertEquals(1, doc.getNumberOfPages(), "Boarding pass should be a single-page document");

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            // Required strings
            assertTrue(text.contains("K7QX2M"), "Must contain booking reference");
            assertTrue(text.contains("Sat, 14 Nov 2026"), "Must contain formatted date 'Sat, 14 Nov 2026'");
            assertTrue(text.contains("Cape Town"), "Must contain destination city Cape Town");
            assertTrue(text.contains("Johannesburg"), "Must contain origin city Johannesburg");
            assertTrue(text.replace("\r", "").replace("\n", " ").contains(BoardingPolicy.GATE_NOTE), "Must contain gate note");

            // Prohibited legacy / board placeholder strings
            assertFalse(text.contains("SkyVoyage"), "Must not contain legacy brand SkyVoyage");
            assertFalse(text.contains("A12"), "Must not contain board placeholder gate A12");
            assertFalse(text.contains("B12"), "Must not contain board placeholder gate B12");
            assertFalse(text.contains("Group"), "Must not contain boarding group");
            assertFalse(text.contains("Ms "), "Must not contain board honorific 'Ms '");

            // 2. PDFBox: Render page 1 to BufferedImage at 300 DPI
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage pageImage = renderer.renderImageWithDPI(0, 300);
            assertNotNull(pageImage, "Rendered page image must not be null");

            // 3. ZXing: Decode barcode from rendered page
            LuminanceSource source = new BufferedImageLuminanceSource(pageImage);
            Map<DecodeHintType, Object> hints = Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            String decodedRef = null;
            try {
                decodedRef = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
            } catch (Exception e) {
                // Inverted decode for white bars on dark background
                decodedRef = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source.invert())), hints).getText();
            }

            assertEquals("K7QX2M", decodedRef, "Decoded barcode text must match booking reference");

            // 4. Save rendered page and sample PDF to target/ instead of docs/
            Path targetDir = Paths.get("target");
            if (!Files.exists(targetDir)) {
                targetDir = Paths.get("core", "target");
            }
            if (Files.exists(targetDir)) {
                File pngOut = targetDir.resolve("boarding-pass-pdf.png").toFile();
                ImageIO.write(pageImage, "PNG", pngOut);
                System.out.println("Wrote rendered PDF screenshot to: " + pngOut.getAbsolutePath());

                File samplePdfOut = targetDir.resolve("boarding-pass.pdf").toFile();
                Files.copy(targetPdf.toPath(), samplePdfOut.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Wrote sample PDF to: " + samplePdfOut.getAbsolutePath());
            }
        }
    }

    @Test
    void testMissingPassengerThrows(@TempDir Path tempDir) {
        BookingContext ctx = createSampleContext();
        ctx.setPassengerName(null);

        File target = tempDir.resolve("test.pdf").toFile();
        assertThrows(IllegalStateException.class, () -> BoardingPassPdfGenerator.generate(ctx, target));
    }

    @Test
    void testMissingBookingRefThrows(@TempDir Path tempDir) {
        BookingContext ctx = createSampleContext();
        ctx.setBookingReference(null);

        File target = tempDir.resolve("test.pdf").toFile();
        assertThrows(IllegalStateException.class, () -> BoardingPassPdfGenerator.generate(ctx, target));
    }
}
