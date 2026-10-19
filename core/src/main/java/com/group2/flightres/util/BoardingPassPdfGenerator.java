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

    private record PassData(
            String passenger,
            String flightNo,
            String origin,
            String destination,
            String depDate,
            String depTime,
            String seat,
            SeatClass seatCls,
            String seatClassStr,
            String bookingRef
    ) {
        static PassData from(BookingContext context) {
            String passenger = (context != null && context.getPassengerName() != null && !context.getPassengerName().isBlank())
                    ? context.getPassengerName() : "Valued Passenger";
            String flightNo = (context != null && context.getFlightNumber() != null && !context.getFlightNumber().isBlank())
                    ? context.getFlightNumber() : "—";
            String origin = (context != null && context.getOrigin() != null && !context.getOrigin().isBlank())
                    ? context.getOrigin() : "—";
            String destination = (context != null && context.getDestination() != null && !context.getDestination().isBlank())
                    ? context.getDestination() : "—";
            String depDate = (context != null && context.getDepartureDate() != null && !context.getDepartureDate().isBlank())
                    ? context.getDepartureDate() : "—";
            String depTime = (context != null && context.getDepartureTime() != null && !context.getDepartureTime().isBlank())
                    ? context.getDepartureTime() : "—";
            String seat = (context != null && context.getSeatLabel() != null && !context.getSeatLabel().isBlank())
                    ? context.getSeatLabel() : "—";
            SeatClass seatCls = (context != null && context.getSeatClass() != null) ? context.getSeatClass() : SeatClass.ECONOMY;
            String seatClassStr = (seatCls == SeatClass.FIRST) ? "FIRST CLASS" : "ECONOMY CLASS";
            String bookingRef = (context != null && context.getBookingReference() != null && !context.getBookingReference().isBlank())
                    ? context.getBookingReference() : (context != null ? context.generateBookingReference() : "—");
            return new PassData(passenger, flightNo, origin, destination, depDate, depTime, seat, seatCls, seatClassStr, bookingRef);
        }
    }

    /**
     * Generates a boarding pass PDF from the provided BookingContext.
     */
    public static void generate(BookingContext context, File targetFile) throws DocumentException, IOException {
        logger.info("Generating boarding pass PDF at: {}", targetFile.getAbsolutePath());

        Rectangle pageSize = new Rectangle(620f, 290f);
        Document document = new Document(pageSize, 16f, 16f, 16f, 16f);

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            PdfWriter writer = PdfWriter.getInstance(document, fos);
            document.open();

            PassData data = PassData.from(context);

            PdfPTable card = new PdfPTable(2);
            card.setWidthPercentage(100f);
            card.setWidths(new float[]{72f, 28f});

            card.addCell(buildMainPassCell(data));
            card.addCell(buildTearOffStubCell(data));
            document.add(card);

            drawBarcodeStrips(writer.getDirectContent(), data.bookingRef() + data.flightNo() + data.seat());

            document.close();
            logger.info("Successfully wrote boarding pass PDF: {} bytes", targetFile.length());
        }
    }

    private static PdfPCell buildMainPassCell(PassData data) {
        Font fontBrandSky = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.WHITE);
        Font fontBrandVoyage = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, COLOR_GOLD);
        Font fontPassTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_GOLD);
        Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_MUTED);
        Font fontValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_DARK);
        Font fontValueSmall = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_DARK);
        Font fontSeatBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_GOLD);
        Font fontRefBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COLOR_NAVY);
        Font fontBarcodeCode = FontFactory.getFont(FontFactory.COURIER, 8, COLOR_MUTED);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBackgroundColor(Color.WHITE);
        leftCell.setBorderColor(COLOR_BORDER);
        leftCell.setBorderWidth(1.2f);
        leftCell.setPadding(0);

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
        Paragraph classPara = new Paragraph(data.seatClassStr(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_GOLD));
        classPara.setAlignment(Element.ALIGN_RIGHT);
        classCell.addElement(classPara);
        leftHeader.addCell(classCell);

        PdfPTable leftBody = new PdfPTable(4);
        leftBody.setWidthPercentage(100f);
        leftBody.setWidths(new float[]{32f, 24f, 22f, 22f});
        leftBody.setSpacingBefore(8f);

        addDetailCell(leftBody, "PASSENGER NAME", data.passenger(), fontLabel, fontValue);
        addDetailCell(leftBody, "FLIGHT", data.flightNo(), fontLabel, fontValue);
        addDetailCell(leftBody, "SEAT", data.seat(), fontLabel, fontSeatBig);
        addDetailCell(leftBody, "CLASS", (data.seatCls() == SeatClass.FIRST ? "First" : "Economy"), fontLabel, fontValue);

        addDetailCell(leftBody, "FROM", data.origin(), fontLabel, fontValueSmall);
        addDetailCell(leftBody, "TO", data.destination(), fontLabel, fontValueSmall);
        addDetailCell(leftBody, "GATE", "A12", fontLabel, fontValue);
        addDetailCell(leftBody, "BOARDING", "45m Prior", fontLabel, fontValue);

        addDetailCell(leftBody, "DATE", data.depDate(), fontLabel, fontValueSmall);
        addDetailCell(leftBody, "DEPARTS", data.depTime(), fontLabel, fontValue);
        addDetailCell(leftBody, "BOOKING REF", data.bookingRef(), fontLabel, fontRefBig);
        addDetailCell(leftBody, "STATUS", "CONFIRMED", fontLabel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(46, 125, 50)));

        PdfPTable barcodeTable = new PdfPTable(1);
        barcodeTable.setWidthPercentage(100f);
        barcodeTable.setSpacingBefore(10f);

        PdfPCell barcodeCell = new PdfPCell();
        barcodeCell.setBorder(Rectangle.NO_BORDER);
        barcodeCell.setPaddingLeft(12);
        barcodeCell.setPaddingBottom(10);

        Paragraph barcodeCode = new Paragraph("* " + data.bookingRef() + " · " + data.flightNo() + " · " + data.seat() + " *", fontBarcodeCode);
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
        return leftCell;
    }

    private static PdfPCell buildTearOffStubCell(PassData data) {
        Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_MUTED);
        Font fontValueSmall = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_DARK);
        Font fontSeatBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_GOLD);
        Font fontRefBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COLOR_NAVY);

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

        addStubRow(rightBody, "PASSENGER", data.passenger(), fontLabel, fontValueSmall);
        addStubRow(rightBody, "FLIGHT / DATE", data.flightNo() + " · " + data.depDate(), fontLabel, fontValueSmall);
        addStubRow(rightBody, "SEAT", data.seat() + " (" + (data.seatCls() == SeatClass.FIRST ? "First" : "Economy") + ")", fontLabel, fontSeatBig);
        addStubRow(rightBody, "BOOKING REF", data.bookingRef(), fontLabel, fontRefBig);

        PdfPCell stubBodyWrapper = new PdfPCell();
        stubBodyWrapper.setBorder(Rectangle.NO_BORDER);
        stubBodyWrapper.setPadding(8);
        stubBodyWrapper.addElement(rightBody);

        PdfPTable rightContent = new PdfPTable(1);
        rightContent.setWidthPercentage(100f);
        rightContent.addCell(rightHeader);
        rightContent.addCell(stubBodyWrapper);

        rightCell.addElement(rightContent);
        return rightCell;
    }

    private static void drawBarcodeStrips(PdfContentByte cb, String seed) {
        float startX = 36f;
        float startY = 32f;
        float barHeight = 24f;

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
