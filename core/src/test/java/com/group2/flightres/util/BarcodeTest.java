package com.group2.flightres.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class BarcodeTest {

    @Test
    @DisplayName("Generate Code 128 as AWT Image with white on transparent background")
    void testBarcode128WhiteOnTransparent() {
        Barcode128 code128 = new Barcode128();
        code128.setCode("K7QX2M");
        code128.setCodeType(Barcode128.CODE128);
        code128.setBarHeight(40f);
        Image img = code128.createAwtImage(Color.WHITE, new Color(0, 0, 0, 0));
        assertNotNull(img);
        assertTrue(img.getWidth(null) > 0);
    }

    @Test
    @DisplayName("Generate Code 128 as OpenPDF element in PDF stream")
    void testBarcode128PdfImage() throws Exception {
        Document doc = new Document(new Rectangle(300, 300));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        doc.open();
        PdfContentByte cb = writer.getDirectContent();

        Barcode128 code128 = new Barcode128();
        code128.setCode("K7QX2M");
        code128.setCodeType(Barcode128.CODE128);
        com.lowagie.text.Image pdfImg = code128.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
        assertNotNull(pdfImg);
        doc.add(pdfImg);
        doc.close();
        assertTrue(baos.size() > 0);
    }

    @Test
    @DisplayName("Real round-trip decode: render BarcodeGenerator image and decode back via ZXing")
    void testBarcode128RoundTripDecode() throws Exception {
        String originalBookingRef = "K7QX2M";
        BufferedImage image = BarcodeGenerator.generateCode128Image(
                originalBookingRef, 50, 3.0f, Color.BLACK, Color.WHITE);
        assertNotNull(image, "Rendered barcode image must not be null");

        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Result result = new MultiFormatReader().decode(bitmap);
        assertNotNull(result, "ZXing decoder must successfully decode the barcode image");
        assertEquals(BarcodeFormat.CODE_128, result.getBarcodeFormat(), "Format must be Code 128");
        assertEquals(originalBookingRef, result.getText(), "Decoded text must match original booking reference");
    }
}
