package com.group2.flightres.util;

import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Shared utility for generating Code 128 barcodes for flight boarding passes
 * on both the UI canvas/images and OpenPDF boarding pass documents.
 */
public final class BarcodeGenerator {

    private BarcodeGenerator() {
        // Prevent instantiation
    }

    /**
     * Generates a Code 128 barcode as a crisp BufferedImage with standard 2.5f module width.
     */
    public static BufferedImage generateCode128Image(String code, int barHeight, Color barColor, Color bgColor) {
        return generateCode128Image(code, barHeight, 2.5f, barColor, bgColor);
    }

    /**
     * Generates a Code 128 barcode as a crisp BufferedImage with configurable module width.
     * Antialiasing is explicitly disabled and nearest-neighbor interpolation is used
     * to eliminate smoothing blur across rectangular bar edges.
     * Includes a standard 10-module quiet zone margin on left and right for decoder scanning.
     */
    public static BufferedImage generateCode128Image(String code, int barHeight, float moduleWidth, Color barColor, Color bgColor) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Barcode text cannot be null or blank");
        }
        Barcode128 barcode = new Barcode128();
        barcode.setCode(code.trim().toUpperCase());
        barcode.setCodeType(Barcode128.CODE128);
        barcode.setBarHeight(barHeight);
        barcode.setX(moduleWidth);

        java.awt.Image awtImg = barcode.createAwtImage(barColor, bgColor);
        int rawW = awtImg.getWidth(null);
        int rawH = awtImg.getHeight(null);
        if (rawW <= 0 || rawH <= 0) {
            rawW = (int) (105 * moduleWidth);
            rawH = barHeight;
        }

        // Standard Code 128 requires a quiet zone margin (min 10 modules) on both ends
        int quietZone = (int) Math.ceil(10 * moduleWidth);
        int totalW = rawW + (2 * quietZone);
        int totalH = rawH;

        BufferedImage bimg = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bimg.createGraphics();
        if (bgColor.getAlpha() > 0) {
            g2.setColor(bgColor);
            g2.fillRect(0, 0, totalW, totalH);
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(awtImg, quietZone, 0, null);
        g2.dispose();
        return bimg;
    }

    /**
     * Generates a Code 128 barcode as an OpenPDF image for PDF document inclusion.
     */
    public static com.lowagie.text.Image generatePdfBarcode(PdfContentByte cb, String code, float barHeight) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Barcode text cannot be null or blank");
        }
        Barcode128 barcode = new Barcode128();
        barcode.setCode(code.trim().toUpperCase());
        barcode.setCodeType(Barcode128.CODE128);
        barcode.setBarHeight(barHeight);
        return barcode.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
    }
}
