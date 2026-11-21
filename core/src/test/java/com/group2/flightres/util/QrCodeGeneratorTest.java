package com.group2.flightres.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeGeneratorTest {

    @Test
    @DisplayName("QR PNG meets >= 320px, quiet zone, and decodes back to expected URL via ZXing")
    void generatesAndDecodesQrPngAccurately() throws Exception {
        String url = "http://192.168.1.42:8080/verify/K7QX2M?sig=AbCdEf1234567890_-xyz";
        byte[] png = QrCodeGenerator.generateQrPng(url, 200);
        assertTrue(png.length > 100);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(img.getWidth() >= 320, "Requested size below 320px must clamp up to >= 320px");
        assertTrue(img.getHeight() >= 320);

        // Verify white quiet-zone corners
        assertEquals(0xFFFFFFFF, img.getRGB(0, 0));
        assertEquals(0xFFFFFFFF, img.getRGB(img.getWidth() - 1, img.getHeight() - 1));

        LuminanceSource source = new BufferedImageLuminanceSource(img);
        Result decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)));
        assertEquals(url, decoded.getText());
    }
}
