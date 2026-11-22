package com.group2.flightres.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.group2.flightres.model.TicketData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrBoardingPassPdfTest {

    private static Result decodeWithFallback(BufferedImage img, BarcodeFormat format) throws Exception {
        Map<DecodeHintType, Object> hints = Map.of(
                DecodeHintType.TRY_HARDER, Boolean.TRUE,
                DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(format)
        );
        LuminanceSource src = new BufferedImageLuminanceSource(img);
        try {
            return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(src)), hints);
        } catch (Exception first) {
            return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(src.invert())), hints);
        }
    }

    @Test
    @DisplayName("PDF boarding pass raster decodes BOTH QR verification URL and Code 128 PNR at 300 DPI")
    void pdfRasterContainsDecodableQrAndCode128OnOnePage() throws Exception {
        TicketData ticket = new TicketData(
                "Naledi Sithole",
                "GRP 204",
                "CPT", "Cape Town",
                "JNB", "Johannesburg",
                "Sat, 14 Nov 2026",
                "06:30",
                "08:40",
                "2h 10m",
                "06:00",
                "05:45",
                "6A",
                "ECONOMY",
                "K7QX2M",
                com.group2.flightres.model.SeatClass.ECONOMY,
                new BigDecimal("1522")
        );
        String verifyUrl = "http://192.168.1.42:8080/verify/K7QX2M?sig=sampleSignature123456";

        File tmpPdf = Files.createTempFile("qr-pass-", ".pdf").toFile();
        tmpPdf.deleteOnExit();
        BoardingPassPdfGenerator.generate(ticket, verifyUrl, tmpPdf);

        try (PDDocument doc = PDDocument.load(tmpPdf)) {
            assertEquals(1, doc.getNumberOfPages(), "QR boarding pass PDF must fit on 1 page");
            BufferedImage pageImg = new PDFRenderer(doc).renderImageWithDPI(0, 300);

            int bodyX = (int) (pageImg.getWidth() * 0.19);
            int bodyW = (int) (pageImg.getWidth() * 0.58);
            BufferedImage bodyCrop = pageImg.getSubimage(bodyX, 0, bodyW, pageImg.getHeight());
            Result c128 = decodeWithFallback(bodyCrop, BarcodeFormat.CODE_128);
            assertEquals(BarcodeFormat.CODE_128, c128.getBarcodeFormat());
            assertEquals("K7QX2M", c128.getText());

            int stubX = (int) (pageImg.getWidth() * 0.76);
            BufferedImage stubCrop = pageImg.getSubimage(stubX, 0, pageImg.getWidth() - stubX, pageImg.getHeight());
            Result qr = decodeWithFallback(stubCrop, BarcodeFormat.QR_CODE);
            assertEquals(BarcodeFormat.QR_CODE, qr.getBarcodeFormat());
            assertEquals(verifyUrl, qr.getText());
            assertTrue(verifyUrl.contains("K7QX2M"));
        }
    }
}
