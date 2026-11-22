package com.group2.flightres.util;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.TicketData;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Generates Sky Royal luxury glass-styled Boarding Pass PDFs via OpenPDF.
 * Renders a full ticket with destination photo panel, route overview with dashed flight line,
 * passenger metadata grid, wide Code 128 barcode on a light strip, and tear-off stub.
 */
public final class BoardingPassPdfGenerator {

    private static final Logger logger = LoggerFactory.getLogger(BoardingPassPdfGenerator.class);

    private static final Color COLOR_CARD_BG = new Color(11, 18, 32);
    private static final Color COLOR_STUB_BG = new Color(15, 23, 42);
    private static final Color COLOR_GOLD = new Color(212, 166, 60);
    private static final Color COLOR_GOLD_LIGHT = new Color(243, 217, 139);
    private static final Color COLOR_MUTED = new Color(156, 163, 175);
    private static final Color COLOR_TEXT_LIGHT = new Color(229, 231, 235);
    private static final Color COLOR_BORDER = new Color(255, 255, 255, 28);

    private static BaseFont bfInterRegular;
    private static BaseFont bfInterMedium;
    private static BaseFont bfInterSemiBold;
    private static BaseFont bfInterBold;
    private static BaseFont bfLoraMedium;
    private static BaseFont bfLoraSemiBold;

    static {
        loadStaticFonts();
    }

    private BoardingPassPdfGenerator() {}

    private static void loadStaticFonts() {
        try {
            bfInterRegular = loadTtf("/com/group2/flightres/fonts/Inter-Regular.ttf");
            bfInterMedium = loadTtf("/com/group2/flightres/fonts/Inter-Medium.ttf");
            bfInterSemiBold = loadTtf("/com/group2/flightres/fonts/Inter-SemiBold.ttf");
            bfInterBold = loadTtf("/com/group2/flightres/fonts/Inter-Bold.ttf");
            bfLoraMedium = loadTtf("/com/group2/flightres/fonts/Lora-Medium.ttf");
            bfLoraSemiBold = loadTtf("/com/group2/flightres/fonts/Lora-SemiBold.ttf");
            logger.info("Custom TTF fonts loaded successfully into OpenPDF: Inter and Lora");
        } catch (Exception e) {
            logger.warn("Could not load custom TTF fonts for OpenPDF, falling back to HELVETICA: {}", e.getMessage());
        }
    }

    private static BaseFont loadTtf(String resourcePath) throws IOException, DocumentException {
        try (InputStream is = BoardingPassPdfGenerator.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Font resource not found: " + resourcePath);
            }
            byte[] bytes = is.readAllBytes();
            return BaseFont.createFont(resourcePath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        }
    }

    public static Font fontInterRegular(float size, Color color) {
        if (bfInterRegular != null) return new Font(bfInterRegular, size, Font.NORMAL, color);
        return FontFactory.getFont(FontFactory.HELVETICA, size, Font.NORMAL, color);
    }

    public static Font fontInterMedium(float size, Color color) {
        if (bfInterMedium != null) return new Font(bfInterMedium, size, Font.NORMAL, color);
        return FontFactory.getFont(FontFactory.HELVETICA, size, Font.NORMAL, color);
    }

    public static Font fontInterSemiBold(float size, Color color) {
        if (bfInterSemiBold != null) return new Font(bfInterSemiBold, size, Font.BOLD, color);
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Font.NORMAL, color);
    }

    public static Font fontInterBold(float size, Color color) {
        if (bfInterBold != null) return new Font(bfInterBold, size, Font.BOLD, color);
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Font.BOLD, color);
    }

    public static Font fontLoraMedium(float size, Color color) {
        if (bfLoraMedium != null) return new Font(bfLoraMedium, size, Font.NORMAL, color);
        return FontFactory.getFont(FontFactory.TIMES_ROMAN, size, Font.NORMAL, color);
    }

    public static Font fontLoraSemiBold(float size, Color color) {
        if (bfLoraSemiBold != null) return new Font(bfLoraSemiBold, size, Font.BOLD, color);
        return FontFactory.getFont(FontFactory.TIMES_BOLD, size, Font.BOLD, color);
    }

    /**
     * Generates a boarding pass PDF conforming to the glass design board voucher card.
     */
    public static void generate(TicketData data, File targetFile) throws DocumentException, IOException {
        generate(data, null, targetFile);
    }

    /**
     * Generates a boarding pass PDF with an embedded scannable verification QR code in the tear-off stub.
     */
    public static void generate(TicketData data, String verificationUrl, File targetFile)
            throws DocumentException, IOException {
        if (data == null) {
            throw new IllegalStateException("TicketData cannot be null for boarding pass generation.");
        }

        logger.info("Generating boarding pass PDF for ref={} at {}", data.bookingReference(), targetFile.getAbsolutePath());

        float cardWidth = 720f;
        PdfPTable card = new PdfPTable(3);
        card.setTotalWidth(cardWidth);
        card.setLockedWidth(true);
        card.setWidths(new float[]{19f, 58f, 23f});

        // 1. Left Destination Photo Panel
        card.addCell(buildPhotoCell(data));

        // 2. Center Ticket Body
        card.addCell(buildBodyCell(data));

        // 3. Right Tear-Off Stub
        card.addCell(buildStubCell(data, verificationUrl));

        // Page size EXACTLY the card size (no white area)
        card.calculateHeights(true);
        float totalHeight = (float) Math.ceil(card.getTotalHeight());
        Rectangle pageSize = new Rectangle(cardWidth, totalHeight);
        Document document = new Document(pageSize, 0f, 0f, 0f, 0f);

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            PdfWriter writer = PdfWriter.getInstance(document, fos);
            document.open();
            document.add(card);
            document.close();
            logger.info("Successfully wrote boarding pass PDF: {} bytes (page: {}x{})", targetFile.length(), cardWidth, totalHeight);
        }
    }

    /**
     * Backward-compatible convenience overload accepting a BookingContext.
     */
    public static void generate(BookingContext context, File targetFile) throws DocumentException, IOException {
        generate(TicketData.from(context), targetFile);
    }

    // =========================================================================
    // SECTION BUILDERS
    // =========================================================================

    private static PdfPCell buildPhotoCell(TicketData data) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidth(1.2f);
        cell.setPadding(14f);
        cell.setBackgroundColor(new Color(11, 18, 32));

        Optional<byte[]> photoBytes = DestinationPhotoResolver.resolveBytes(data.destinationCode());
        cell.setCellEvent(new PhotoPanelCellEvent(photoBytes.orElse(null)));

        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100f);

        // Top Brand Header
        PdfPCell brandCell = new PdfPCell();
        brandCell.setBorder(Rectangle.NO_BORDER);
        brandCell.setPadding(0);
        Paragraph brand = new Paragraph(com.group2.flightres.config.BrandConfig.APP_NAME.toUpperCase(java.util.Locale.ROOT), fontInterBold(11f, Color.WHITE));
        brandCell.addElement(brand);
        inner.addCell(brandCell);

        // Spacer to push "WELCOME TO <City>" to bottom
        PdfPCell spacer = new PdfPCell();
        spacer.setBorder(Rectangle.NO_BORDER);
        spacer.setFixedHeight(175f);
        inner.addCell(spacer);

        // Welcome Overline
        PdfPCell overlineCell = new PdfPCell();
        overlineCell.setBorder(Rectangle.NO_BORDER);
        overlineCell.setPadding(0);
        Paragraph overline = new Paragraph("WELCOME TO", fontInterBold(8f, COLOR_MUTED));
        overlineCell.addElement(overline);
        inner.addCell(overlineCell);

        // Destination City in Lora Serif
        PdfPCell cityCell = new PdfPCell();
        cityCell.setBorder(Rectangle.NO_BORDER);
        cityCell.setPadding(0);
        Paragraph city = new Paragraph(data.destinationCity(), fontLoraSemiBold(18f, Color.WHITE));
        city.setLeading(18f);
        city.setSpacingBefore(2f);
        cityCell.addElement(city);
        inner.addCell(cityCell);

        cell.addElement(inner);
        return cell;
    }

    private static PdfPCell buildBodyCell(TicketData data) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_CARD_BG);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidth(1.2f);
        cell.setPadding(14f);
        cell.setPaddingRight(16f);

        PdfPTable body = new PdfPTable(1);
        body.setWidthPercentage(100f);

        // 1. Top row: "BOARDING PASS" overline on left, Cabin pill badge on right
        PdfPTable topRow = new PdfPTable(2);
        topRow.setWidthPercentage(100f);
        topRow.setWidths(new float[]{60f, 40f});

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPadding(0);
        Paragraph title = new Paragraph("BOARDING PASS", fontInterBold(9f, COLOR_MUTED));
        titleCell.addElement(title);
        topRow.addCell(titleCell);

        PdfPCell badgeCell = new PdfPCell();
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setPadding(0);
        badgeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph badge = new Paragraph(data.cabin().toUpperCase(), fontInterSemiBold(9f, COLOR_GOLD_LIGHT));
        badge.setAlignment(Element.ALIGN_RIGHT);
        badgeCell.addElement(badge);
        topRow.addCell(badgeCell);

        PdfPCell topRowWrapper = new PdfPCell();
        topRowWrapper.setBorder(Rectangle.NO_BORDER);
        topRowWrapper.setPadding(0);
        topRowWrapper.setPaddingBottom(10f);
        topRowWrapper.addElement(topRow);
        body.addCell(topRowWrapper);

        // 2. Route row: Origin Code + City · HH:mm   ──── ✈ ────   Dest Code + City · HH:mm
        PdfPTable routeRow = new PdfPTable(3);
        routeRow.setWidthPercentage(100f);
        routeRow.setWidths(new float[]{38f, 24f, 38f});

        // Origin
        PdfPCell origCell = new PdfPCell();
        origCell.setBorder(Rectangle.NO_BORDER);
        origCell.setPadding(0);
        Paragraph origCode = new Paragraph(data.originCode(), fontInterBold(26f, Color.WHITE));
        origCode.setLeading(26f);
        origCell.addElement(origCode);
        Paragraph origCity = new Paragraph(data.originCity() + " · " + data.departureTime(), fontInterRegular(9f, COLOR_TEXT_LIGHT));
        origCity.setSpacingBefore(2f);
        origCell.addElement(origCity);
        routeRow.addCell(origCell);

        // Middle connector with dashed route line and vector airplane
        PdfPCell connCell = new PdfPCell();
        connCell.setBorder(Rectangle.NO_BORDER);
        connCell.setPadding(0);
        connCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        connCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        connCell.setCellEvent(new RouteConnectorEvent());
        routeRow.addCell(connCell);

        // Destination
        PdfPCell destCell = new PdfPCell();
        destCell.setBorder(Rectangle.NO_BORDER);
        destCell.setPadding(0);
        destCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph destCode = new Paragraph(data.destinationCode(), fontInterBold(26f, Color.WHITE));
        destCode.setLeading(26f);
        destCode.setAlignment(Element.ALIGN_RIGHT);
        destCell.addElement(destCode);
        Paragraph destCity = new Paragraph(data.destinationCity() + " · " + data.arrivalTime(), fontInterRegular(9f, COLOR_TEXT_LIGHT));
        destCity.setSpacingBefore(2f);
        destCity.setAlignment(Element.ALIGN_RIGHT);
        destCell.addElement(destCity);
        routeRow.addCell(destCell);

        PdfPCell routeRowWrapper = new PdfPCell();
        routeRowWrapper.setBorder(Rectangle.NO_BORDER);
        routeRowWrapper.setPadding(0);
        routeRowWrapper.setPaddingBottom(10f);
        routeRowWrapper.addElement(routeRow);
        body.addCell(routeRowWrapper);

        // 3. Metadata Grid: 4 columns (Passenger, Flight, Date, Boarding)
        PdfPTable metaGrid = new PdfPTable(4);
        metaGrid.setWidthPercentage(100f);
        metaGrid.setWidths(new float[]{32f, 22f, 26f, 20f});

        addMetaCell(metaGrid, "PASSENGER", data.passenger());
        addMetaCell(metaGrid, "FLIGHT", data.flightNumber());
        addMetaCell(metaGrid, "DATE", data.formattedDate());
        addMetaCell(metaGrid, "BOARDING", data.boardingTime());

        PdfPCell metaWrapper = new PdfPCell();
        metaWrapper.setBorder(Rectangle.NO_BORDER);
        metaWrapper.setPadding(0);
        metaWrapper.setPaddingBottom(8f);
        metaWrapper.addElement(metaGrid);
        body.addCell(metaWrapper);

        // 4. Barcode on light strip with quiet zones, height ~40pt, bars filling >= 85% of strip width
        BufferedImage bimg = BarcodeGenerator.generateCode128Image(data.bookingReference(), 40, 2.4f, Color.BLACK, Color.WHITE);
        com.lowagie.text.Image barcodeImg;
        try {
            barcodeImg = com.lowagie.text.Image.getInstance(bimg, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create barcode image", e);
        }
        barcodeImg.scaleAbsolute(bimg.getWidth(), 40f);
        barcodeImg.setAlignment(Element.ALIGN_CENTER);

        PdfPCell barcodeStrip = new PdfPCell();
        barcodeStrip.setBorder(Rectangle.NO_BORDER);
        barcodeStrip.setPadding(0);
        barcodeStrip.setHorizontalAlignment(Element.ALIGN_CENTER);
        barcodeStrip.addElement(barcodeImg);
        body.addCell(barcodeStrip);

        // String under the barcode: ONLY the booking reference
        PdfPCell refBelowCell = new PdfPCell();
        refBelowCell.setBorder(Rectangle.NO_BORDER);
        refBelowCell.setPadding(0);
        Paragraph refBelow = new Paragraph(data.bookingReference(), fontInterRegular(8.5f, COLOR_MUTED));
        refBelow.setAlignment(Element.ALIGN_CENTER);
        refBelow.setSpacingBefore(2f);
        refBelowCell.addElement(refBelow);
        body.addCell(refBelowCell);

        cell.addElement(body);
        return cell;
    }

    private static PdfPCell buildStubCell(TicketData data, String verificationUrl) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_STUB_BG);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidth(1.2f);
        cell.setPadding(14f);
        cell.setCellEvent(new PerforationDividerEvent());

        boolean hasQr = verificationUrl != null && !verificationUrl.isBlank();
        float gap = hasQr ? 7f : 12f;

        PdfPTable stub = new PdfPTable(1);
        stub.setWidthPercentage(100f);

        // 1. SEAT (gold label, big gold font)
        PdfPCell seatCell = new PdfPCell();
        seatCell.setBorder(Rectangle.NO_BORDER);
        seatCell.setPadding(0);
        Paragraph seatLabel = new Paragraph("SEAT", fontInterBold(9f, COLOR_GOLD));
        seatCell.addElement(seatLabel);
        Paragraph seatVal = new Paragraph(data.seatLabel(), fontInterBold(hasQr ? 30f : 38f, COLOR_GOLD));
        seatVal.setLeading(hasQr ? 29f : 36f);
        seatVal.setSpacingBefore(2f);
        seatCell.addElement(seatVal);
        stub.addCell(seatCell);

        // Spacer
        PdfPCell s1 = new PdfPCell();
        s1.setBorder(Rectangle.NO_BORDER);
        s1.setFixedHeight(gap);
        stub.addCell(s1);

        // 2. GATE ("GATE" overline, BoardingPolicy.GATE_NOTE)
        PdfPCell gateCell = new PdfPCell();
        gateCell.setBorder(Rectangle.NO_BORDER);
        gateCell.setPadding(0);
        Paragraph gateLabel = new Paragraph("GATE", fontInterBold(8.5f, COLOR_MUTED));
        gateCell.addElement(gateLabel);
        Paragraph gateVal = new Paragraph(BoardingPolicy.GATE_NOTE, fontInterSemiBold(9.5f, Color.WHITE));
        gateVal.setSpacingBefore(2f);
        gateCell.addElement(gateVal);
        stub.addCell(gateCell);

        // Spacer
        PdfPCell s2 = new PdfPCell();
        s2.setBorder(Rectangle.NO_BORDER);
        s2.setFixedHeight(gap);
        stub.addCell(s2);

        // 3. REFERENCE ("REFERENCE" overline, booking reference)
        PdfPCell refCell = new PdfPCell();
        refCell.setBorder(Rectangle.NO_BORDER);
        refCell.setPadding(0);
        Paragraph refLabel = new Paragraph("REFERENCE", fontInterBold(8.5f, COLOR_MUTED));
        refCell.addElement(refLabel);
        Paragraph refVal = new Paragraph(data.bookingReference(), fontInterBold(hasQr ? 14f : 16f, Color.WHITE));
        refVal.setSpacingBefore(2f);
        refCell.addElement(refVal);
        stub.addCell(refCell);

        if (data.totalPrice() != null) {
            PdfPCell s3 = new PdfPCell();
            s3.setBorder(Rectangle.NO_BORDER);
            s3.setFixedHeight(gap);
            stub.addCell(s3);

            PdfPCell fareCell = new PdfPCell();
            fareCell.setBorder(Rectangle.NO_BORDER);
            fareCell.setPadding(0);
            Paragraph fareLabel = new Paragraph("TOTAL FARE", fontInterBold(8.5f, COLOR_MUTED));
            fareCell.addElement(fareLabel);
            Paragraph fareVal = new Paragraph(String.format(java.util.Locale.US, "R %,d", data.totalPrice().longValue()), fontInterBold(hasQr ? 12.5f : 14f, COLOR_GOLD_LIGHT));
            fareVal.setSpacingBefore(2f);
            fareCell.addElement(fareVal);
            stub.addCell(fareCell);
        }

        if (hasQr) {
            PdfPCell s4 = new PdfPCell();
            s4.setBorder(Rectangle.NO_BORDER);
            s4.setFixedHeight(8f);
            stub.addCell(s4);

            byte[] qrPng;
            try {
                qrPng = QrCodeGenerator.generateQrPng(verificationUrl, QrCodeGenerator.MIN_SIZE_PX);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to generate QR code for boarding pass PDF", e);
            }
            com.lowagie.text.Image qrImg;
            try {
                qrImg = com.lowagie.text.Image.getInstance(qrPng);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create PDF QR image", e);
            }
            qrImg.scaleAbsolute(82f, 82f);
            qrImg.setAlignment(Element.ALIGN_CENTER);

            PdfPTable qrWrap = new PdfPTable(1);
            qrWrap.setTotalWidth(90f);
            qrWrap.setLockedWidth(true);
            PdfPCell qrBox = new PdfPCell();
            qrBox.setBackgroundColor(Color.WHITE);
            qrBox.setBorder(Rectangle.BOX);
            qrBox.setBorderColor(COLOR_BORDER);
            qrBox.setBorderWidth(1f);
            qrBox.setPadding(4f);
            qrBox.setHorizontalAlignment(Element.ALIGN_CENTER);
            qrBox.addElement(qrImg);
            qrWrap.addCell(qrBox);

            PdfPCell qrContainer = new PdfPCell();
            qrContainer.setBorder(Rectangle.NO_BORDER);
            qrContainer.setPadding(0);
            qrContainer.setHorizontalAlignment(Element.ALIGN_CENTER);
            qrContainer.addElement(qrWrap);
            Paragraph qrCap = new Paragraph("SCAN TO VERIFY", fontInterBold(7f, COLOR_MUTED));
            qrCap.setAlignment(Element.ALIGN_CENTER);
            qrCap.setSpacingBefore(3f);
            qrContainer.addElement(qrCap);
            stub.addCell(qrContainer);
        }

        cell.addElement(stub);
        return cell;
    }

    private static void addMetaCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        cell.setPaddingRight(4f);
        Paragraph pLabel = new Paragraph(label, fontInterBold(8f, COLOR_MUTED));
        Paragraph pVal = new Paragraph(value, fontInterSemiBold(10f, Color.WHITE));
        pVal.setSpacingBefore(1f);
        cell.addElement(pLabel);
        cell.addElement(pVal);
        table.addCell(cell);
    }

    // =========================================================================
    // CELL EVENTS: PHOTO BACKGROUND, ROUTE DASHED LINE, PERFORATION DIVIDER
    // =========================================================================

    private static class PhotoPanelCellEvent implements PdfPCellEvent {
        private final byte[] imageBytes;

        public PhotoPanelCellEvent(byte[] imageBytes) {
            this.imageBytes = imageBytes;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            float left = position.getLeft();
            float bottom = position.getBottom();
            float width = position.getWidth();
            float height = position.getHeight();

            if (imageBytes != null && imageBytes.length > 0) {
                try {
                    com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(imageBytes);
                    img.setAbsolutePosition(left, bottom);
                    img.scaleAbsolute(width, height);
                    cb.addImage(img);

                    // Apply dark overlay gradient for readability
                    cb.saveState();
                    PdfGState gs = new PdfGState();
                    gs.setFillOpacity(0.58f);
                    cb.setGState(gs);
                    cb.setColorFill(new Color(11, 18, 32));
                    cb.rectangle(left, bottom, width, height);
                    cb.fill();
                    cb.restoreState();
                    return;
                } catch (Exception e) {
                    logger.warn("Failed to render background image in PDF photo panel, using gradient fallback", e);
                }
            }

            // Navy / gold gradient fallback when no photo is available
            cb.saveState();
            cb.setColorFill(new Color(15, 23, 42));
            cb.rectangle(left, bottom, width, height);
            cb.fill();
            cb.restoreState();
        }
    }

    private static class RouteConnectorEvent implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.TEXTCANVAS];
            float x1 = position.getLeft() + 2f;
            float x2 = position.getRight() - 2f;
            float y = position.getBottom() + (position.getHeight() / 2f) + 5f;
            float midX = (x1 + x2) / 2f;

            cb.saveState();
            cb.setLineWidth(1.2f);
            cb.setLineDash(new float[]{3f, 3f}, 0f);
            cb.setColorStroke(new Color(255, 255, 255, 120));
            cb.moveTo(x1, y);
            cb.lineTo(midX - 9f, y);
            cb.stroke();

            cb.moveTo(midX + 9f, y);
            cb.lineTo(x2, y);
            cb.stroke();

            // Draw gold vector airplane in the middle, pointing right
            cb.setColorFill(COLOR_GOLD);
            cb.moveTo(midX + 7f, y);
            cb.lineTo(midX + 2.5f, y + 1.5f);
            cb.lineTo(midX - 2.5f, y + 6f);
            cb.lineTo(midX - 4f, y + 5.5f);
            cb.lineTo(midX - 1.8f, y + 1.5f);
            cb.lineTo(midX - 6f, y + 4f);
            cb.lineTo(midX - 7f, y + 3.5f);
            cb.lineTo(midX - 5.5f, y);
            cb.lineTo(midX - 7f, y - 3.5f);
            cb.lineTo(midX - 6f, y - 4f);
            cb.lineTo(midX - 1.8f, y - 1.5f);
            cb.lineTo(midX - 4f, y - 5.5f);
            cb.lineTo(midX - 2.5f, y - 6f);
            cb.lineTo(midX + 2.5f, y - 1.5f);
            cb.closePath();
            cb.fill();

            cb.restoreState();
        }
    }

    private static class PerforationDividerEvent implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.LINECANVAS];
            cb.saveState();
            cb.setLineWidth(1.4f);
            cb.setLineDash(new float[]{4f, 4f}, 0f);
            cb.setColorStroke(new Color(255, 255, 255, 75));
            cb.moveTo(position.getLeft(), position.getBottom() + 10f);
            cb.lineTo(position.getLeft(), position.getTop() - 10f);
            cb.stroke();
            cb.restoreState();
        }
    }
}
