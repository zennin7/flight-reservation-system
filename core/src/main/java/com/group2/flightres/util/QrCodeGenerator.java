package com.group2.flightres.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates high-contrast QR codes (Error Correction M, 4-module quiet zone, >= 320px).
 */
public final class QrCodeGenerator {

    public static final int MIN_SIZE_PX = 320;
    public static final int QUIET_ZONE_MODULES = 4;

    private QrCodeGenerator() {}

    public static BufferedImage generateQrImage(String content) {
        return generateQrImage(content, MIN_SIZE_PX);
    }

    public static BufferedImage generateQrImage(String content, int requestedSizePx) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QR code content must not be null or blank");
        }
        int size = Math.max(MIN_SIZE_PX, requestedSizePx);
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            MatrixToImageConfig config = new MatrixToImageConfig(0xFF000000, 0xFFFFFFFF);
            return MatrixToImageWriter.toBufferedImage(matrix, config);
        } catch (WriterException e) {
            throw new IllegalStateException("Failed to encode QR code", e);
        }
    }

    public static byte[] generateQrPng(String content) throws IOException {
        return generateQrPng(content, MIN_SIZE_PX);
    }

    public static byte[] generateQrPng(String content, int requestedSizePx) throws IOException {
        BufferedImage img = generateQrImage(content, requestedSizePx);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }
}
