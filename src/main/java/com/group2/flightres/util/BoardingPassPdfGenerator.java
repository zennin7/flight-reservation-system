package com.group2.flightres.util;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.SeatClass;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility to generate SkyVoyage Boarding Pass PDFs via OpenPDF.
 */
public final class BoardingPassPdfGenerator {

    private static final Logger logger = LoggerFactory.getLogger(BoardingPassPdfGenerator.class);

    private static final Color COLOR_NAVY = new Color(15, 27, 43);
    private static final Color COLOR_GOLD = new Color(212, 166, 60);
    private static final Color COLOR_BG = new Color(248, 249, 250);
    private static final Color COLOR_BORDER = new Color(229, 231, 235);
    private static final Color COLOR_MUTED = new Color(107, 114, 128);
    private static final Color COLOR_DARK = new Color(31, 41, 55);

    private BoardingPassPdfGenerator() {}

    /**
     * Generates a boarding pass PDF from the provided BookingContext.
     */
    public static void generate(BookingContext context, File targetFile) throws DocumentException, IOException {
        logger.info("Generating boarding pass PDF at: {}", targetFile.getAbsolutePath());

        // Landscape card dimensions: 620 x 290 pt
        Rectangle pageSize = new Rectangle(620f, 290f);
        Document document = new Document(pageSize, 16f, 16f, 16f, 16f);

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            PdfWriter writer = PdfWriter.getInstance(document, fos);
            document.open();

            // Extract context values with fallbacks
            String passenger = (context != null && context.getPassengerName() != null && !context.getPassengerName().isBlank())
                    ? context.getPassengerName() : "Valued Passenger";
            String flightNo = (context != null && context.getFlightNumber() != null && !context.getFlightNumber().isBlank())
                    ? context.getFlightNumber() : "GRP 204";
            String origin = (context != null && context.getOrigin() != null) ? context.getOrigin() : "Johannesburg (JNB)";
            String destination = (context != null && context.getDestination() != null) ? context.getDestination() : "Cape Town (CPT)";
            String depDate = (context != null && context.getDepartureDate() != null) ? context.getDepartureDate() : "2 Nov 2026";
            String depTime = (context != null && context.getDepartureTime() != null) ? context.getDepartureTime() : "06:30";
            String seat = (context != null && !context.getSeatLabel().isBlank()) ? context.getSeatLabel() : "7E";
            SeatClass seatCls = (context != null) ? context.getSeatClass() : SeatClass.ECONOMY;
            String seatClassStr = (seatCls == SeatClass.FIRST) ? "FIRST CLASS" : "ECONOMY CLASS";
            String bookingRef = (context != null && context.getBookingReference() != null && !context.getBookingReference().isBlank())
                    ? context.getBookingReference() : "K7P2QX";

            // Fonts
            Font fontBrandSky = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.WHITE);
            Font fontBrandVoyage = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, COLOR_GOLD);
            Font fontPassTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_GOLD);

            Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_MUTED);
            Font fontValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_DARK);
            Font fontValueSmall = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_DARK);
            Font fontSeatBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_GOLD);
            Font fontRefBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COLOR_NAVY);
            Font fontBarcodeCode = FontFactory.getFont(FontFactory.COURIER, 8, COLOR_MUTED);

            // Outer Card Container Table (Main pass 72%, Tear-off stub 28%)
            PdfPTable card = new PdfPTable(2);
            card.setWidthPercentage(100f);
            card.setWidths(new float[]{72f, 28f});

            // ==========================================
            // LEFT SIDE: MAIN BOARDING PASS
            // ==========================================
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBackgroundColor(Color.WHITE);
            leftCell.setBorderColor(COLOR_BORDER);
            leftCell.setBorderWidth(1.2f);
            leftCell.setPadding(0);

            // Left Header Table (Navy background)
            PdfPTable leftHeader = new PdfPTable(2);
            leftHeader.setWidthPercentage(100f);
            leftHeader.setWidths(new float[]{65f, 35f});

            PdfPCell brandCell = new PdfPCell();
            brandCell.setBackgroundColor(COLOR_NAVY);
            brandCell.setPadding(8);
            brandCell.setPaddingLeft(12);
            brandCell.setBorder(Rectangle.NO_BORDER);

            Phrase brandPhrase = new Phrase();
            brandPhrase.add(new Phrase("Sky", fontBrandSky));
            brandPhrase.add(new Phrase("Voyage", fontBrandVoyage));
            brandPhrase.add(new Phrase("   BOARDING PASS", fontPassTitle));
            brandCell.addElement(brandPhrase);
            leftHeader.addCell(brandCell);

            PdfPCell classCell = new PdfPCell();
            classCell.setBackgroundColor(COLOR_NAVY);
            classCell.setPadding(8);
            classCell.setPaddingRight(12);
            classCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            classCell.setBorder(Rectangle.NO_BORDER);
            Paragraph classPara = new Paragraph(seatClassStr, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_GOLD));
            classPara.setAlignment(Element.ALIGN_RIGHT);
            classCell.addElement(classPara);
            leftHeader.addCell(classCell);

            // Left Body Details Table
            PdfPTable leftBody = new PdfPTable(4);
            leftBody.setWidthPercentage(100f);
            leftBody.setWidths(new float[]{32f, 24f, 22f, 22f});
            leftBody.setSpacingBefore(8f);

            // Row 1: Passenger Name, Flight, Seat, Class
            addDetailCell(leftBody, "PASSENGER NAME", passenger, fontLabel, fontValue);
            addDetailCell(leftBody, "FLIGHT", flightNo, fontLabel, fontValue);
            addDetailCell(leftBody, "SEAT", seat, fontLabel, fontSeatBig);
            addDetailCell(leftBody, "CLASS", (seatCls == SeatClass.FIRST ? "First" : "Economy"), fontLabel, fontValue);

            // Row 2: From, To, Gate, Boarding Time
            addDetailCell(leftBody, "FROM", origin, fontLabel, fontValueSmall);
            addDetailCell(leftBody, "TO", destination, fontLabel, fontValueSmall);
            addDetailCell(leftBody, "GATE", "A12", fontLabel, fontValue);
            addDetailCell(leftBody, "BOARDING", "45m Prior", fontLabel, fontValue);

            // Row 3: Date, Departure Time, Booking Ref, Status
            addDetailCell(leftBody, "DATE", depDate, fontLabel, fontValueSmall);
            addDetailCell(leftBody, "DEPARTS", depTime, fontLabel, fontValue);
            addDetailCell(leftBody, "BOOKING REF", bookingRef, fontLabel, fontRefBig);
            addDetailCell(leftBody, "STATUS", "CONFIRMED", fontLabel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(46, 125, 50)));

            // Left Bottom Barcode Section
            PdfPTable barcodeTable = new PdfPTable(1);
            barcodeTable.setWidthPercentage(100f);
            barcodeTable.setSpacingBefore(10f);

            PdfPCell barcodeCell = new PdfPCell();
            barcodeCell.setBorder(Rectangle.NO_BORDER);
            barcodeCell.setPaddingLeft(12);
            barcodeCell.setPaddingBottom(10);

            // Text barcode representation
            Paragraph barcodeCode = new Paragraph("* " + bookingRef + " · " + flightNo + " · " + seat + " *", fontBarcodeCode);
            barcodeCell.addElement(barcodeCode);
            barcodeTable.addCell(barcodeCell);

            PdfPTable leftContent = new PdfPTable(1);
            leftContent.setWidthPercentage(100f);
            leftContent.addCell(leftHeader);

            PdfPCell bodyWrapper = new PdfPCell();
            bodyWrapper.setBorder(Rectangle.NO_BORDER);
            bodyWrapper.setPadding(10);
            bodyWrapper.addElement(leftBody);
            bodyWrapper.addElement(barcodeTable);
            leftContent.addCell(bodyWrapper);

            leftCell.addElement(leftContent);
            card.addCell(leftCell);

            // ==========================================
            // RIGHT SIDE: TEAR-OFF STUB / RECEIPT
            // ==========================================
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBackgroundColor(COLOR_BG);
            rightCell.setBorderColor(COLOR_BORDER);
            rightCell.setBorderWidth(1.2f);
            rightCell.setPadding(0);

            PdfPTable rightHeader = new PdfPTable(1);
            rightHeader.setWidthPercentage(100f);

            PdfPCell stubHeader = new PdfPCell();
            stubHeader.setBackgroundColor(COLOR_NAVY);
            stubHeader.setPadding(8);
            stubHeader.setBorder(Rectangle.NO_BORDER);
            Paragraph stubTitle = new Paragraph("PASSENGER RECEIPT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_GOLD));
            stubTitle.setAlignment(Element.ALIGN_CENTER);
            stubHeader.addElement(stubTitle);
            rightHeader.addCell(stubHeader);

            PdfPTable rightBody = new PdfPTable(1);
            rightBody.setWidthPercentage(100f);
            rightBody.setSpacingBefore(6f);

            addStubRow(rightBody, "PASSENGER", passenger, fontLabel, fontValueSmall);
            addStubRow(rightBody, "FLIGHT / DATE", flightNo + " · " + depDate, fontLabel, fontValueSmall);
            addStubRow(rightBody, "SEAT", seat + " (" + (seatCls == SeatClass.FIRST ? "First" : "Economy") + ")", fontLabel, fontSeatBig);
            addStubRow(rightBody, "BOOKING REF", bookingRef, fontLabel, fontRefBig);

            PdfPCell stubBodyWrapper = new PdfPCell();
            stubBodyWrapper.setBorder(Rectangle.NO_BORDER);
            stubBodyWrapper.setPadding(8);
            stubBodyWrapper.addElement(rightBody);

            PdfPTable rightContent = new PdfPTable(1);
            rightContent.setWidthPercentage(100f);
            rightContent.addCell(rightHeader);
            rightContent.addCell(stubBodyWrapper);

            rightCell.addElement(rightContent);
            card.addCell(rightCell);

            document.add(card);

            // Draw graphical barcode placeholder on left side using PdfContentByte lines
            PdfContentByte cb = writer.getDirectContent();
            float startX = 36f;
            float startY = 32f;
            float barHeight = 24f;

            // Barcode pattern derived from reference & seat
            String seed = bookingRef + flightNo + seat;
            cb.setColorStroke(Color.BLACK);
            float currentX = startX;
            for (int i = 0; i < 65; i++) {
                char ch = seed.charAt(i % seed.length());
                float width = ((ch + i * 7) % 3) + 1.0f;
                cb.setLineWidth(width);
                cb.moveTo(currentX, startY);
                cb.lineTo(currentX, startY + barHeight);
                cb.stroke();
                currentX += width + ((i % 2 == 0) ? 2.5f : 1.5f);
                if (currentX > 380f) break;
            }

            // Draw mini barcode on right stub
            float stubStartX = 460f;
            float stubCurrentX = stubStartX;
            for (int i = 0; i < 30; i++) {
                char ch = seed.charAt((i * 3) % seed.length());
                float width = ((ch + i) % 2) + 1.0f;
                cb.setLineWidth(width);
                cb.moveTo(stubCurrentX, startY);
                cb.lineTo(stubCurrentX, startY + 16f);
                cb.stroke();
                stubCurrentX += width + 2f;
                if (stubCurrentX > 590f) break;
            }

            document.close();
            logger.info("Successfully wrote boarding pass PDF: {} bytes", targetFile.length());
        }
    }

    private static void addDetailCell(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valFont));
        table.addCell(cell);
    }

    private static void addStubRow(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(3);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valFont));
        table.addCell(cell);
    }
}
