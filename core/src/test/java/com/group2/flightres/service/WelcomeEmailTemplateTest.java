package com.group2.flightres.service;

import com.group2.flightres.config.EmailConfig;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.service.WelcomeEmailRenderer.RenderedWelcomeEmail;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomeEmailTemplateTest {

    private static User testUser(String fullName, String username, String email) {
        User u = new User();
        u.setUserId(42L);
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setRole(UserRole.CUSTOMER);
        u.setActive(true);
        return u;
    }

    @Test
    @DisplayName("Hero image asset is ~1200x480 and strictly under 120 KB")
    void heroImageMeetsDimensionAndSizeConstraints() throws Exception {
        byte[] bytes;
        try (InputStream is = WelcomeEmailTemplateTest.class.getResourceAsStream("/email/skyroyal-hero.jpg")) {
            assertNotNull(is, "Missing /email/skyroyal-hero.jpg classpath resource");
            bytes = is.readAllBytes();
        }
        assertTrue(bytes.length > 1024 && bytes.length < 122880,
                "Hero image size must be under 120 KB (was " + bytes.length + " bytes)");

        BufferedImage img = ImageIO.read(WelcomeEmailTemplateTest.class.getResourceAsStream("/email/skyroyal-hero.jpg"));
        assertNotNull(img);
        assertEquals(1200, img.getWidth());
        assertEquals(480, img.getHeight());
    }

    @Test
    @DisplayName("Rendered HTML and plain text follow layout, dark-mode, brand, security, and content contracts")
    void renderedEmailMatchesDesignAndSecurityRules() {
        EmailConfig cfg = new EmailConfig("smtp.gmail.com", 587, null, null,
                "support@skyroyal.com", "Sky Royal", "https://skyroyal.example", Path.of("outbox"));
        WelcomeEmailRenderer renderer = new WelcomeEmailRenderer();

        RenderedWelcomeEmail email = renderer.renderWelcome(
                testUser("Zoë Müller", "zoe.muller", "zoe@example.com"),
                "",
                cfg);
        String html = email.htmlBody();
        String txt = email.textBody();

        assertTrue(html.contains("SKY ROYAL"));
        assertTrue(html.contains("#0f1318"));
        assertTrue(html.contains("src=\"cid:skyroyal-hero\""));
        assertTrue(html.contains("Welcome aboard, Zoë."));
        assertTrue(html.contains("Zoë Müller"));
        assertTrue(html.contains("zoe.muller"));
        assertTrue(html.contains("Pick your exact seat on a live seat map"));
        assertTrue(html.contains("Fares you can see the reason for"));
        assertTrue(html.contains("A boarding pass with a QR code you can verify"));
        assertTrue(html.contains("href=\"https://skyroyal.example/flights\""));
        assertTrue(html.contains("Search flights"));
        assertTrue(html.contains("Sky Royal is a university project - no real flights, payments or bookings."));

        // No remote images, tracking pixels, backdrop-filter, rgba transparency, or old brand names
        assertFalse(Pattern.compile("(?i)<img[^>]+https?://").matcher(html).find(),
                "Welcome email must not contain remote image URLs");
        assertFalse(html.toLowerCase().contains("backdrop-filter"));
        assertFalse(html.toLowerCase().contains("rgba("));
        String legacyA = "alti" + "tude";
        String legacyB = "sky" + "voyage";
        assertFalse(html.toLowerCase().contains(legacyA));
        assertFalse(html.toLowerCase().contains(legacyB));
        assertFalse(txt.toLowerCase().contains(legacyA));
        assertFalse(txt.toLowerCase().contains(legacyB));

        // Plain-text parity
        assertTrue(txt.contains("Welcome aboard, Zoë."));
        assertTrue(txt.contains("zoe.muller"));
        assertTrue(txt.contains("https://skyroyal.example/flights"));
        assertTrue(txt.contains("Pick your exact seat on a live seat map"));
        assertTrue(txt.contains("Fares you can see the reason for"));
        assertTrue(txt.contains("A boarding pass with a QR code you can verify"));
        assertTrue(txt.contains("Sky Royal is a university project - no real flights, payments or bookings."));

        // Escaping check
        RenderedWelcomeEmail scriptEmail = renderer.renderWelcome(
                testUser("<script>alert('xss')</script> Guest", "guest", "guest@example.com"),
                "http://localhost:8080",
                cfg);
        assertFalse(scriptEmail.htmlBody().contains("<script>"));
        assertTrue(scriptEmail.htmlBody().contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("MimeMessage wraps multipart/related with inline <skyroyal-hero> JPEG inside multipart/alternative")
    void mimeMessageEmbedsInlineCidHeroImage() throws Exception {
        EmailConfig cfg = new EmailConfig("127.0.0.1", 587, "user@example.com", "secret",
                "noreply@skyroyal.com", "Sky Royal", "http://localhost:8080", Path.of("outbox"));
        WelcomeEmailRenderer renderer = new WelcomeEmailRenderer();
        RenderedWelcomeEmail rendered = renderer.renderWelcome(
                testUser("Naledi Sithole", "naledi.sithole", "naledi@example.com"),
                "http://localhost:8080",
                cfg);

        try (SmtpEmailService smtp = new SmtpEmailService(cfg, renderer, new WelcomeEmailRateLimiter(), List.of(10L), false)) {
            MimeMessage msg = smtp.buildMimeMessage(rendered);
            assertTrue(msg.getContentType().toLowerCase().contains("multipart/alternative"));

            Multipart alt = (Multipart) msg.getContent();
            assertEquals(2, alt.getCount());
            BodyPart part1 = alt.getBodyPart(0);
            assertTrue(part1.isMimeType("text/plain"));

            BodyPart part2 = alt.getBodyPart(1);
            assertTrue(part2.isMimeType("multipart/related"));
            Multipart rel = (Multipart) part2.getContent();
            assertEquals(2, rel.getCount());
            assertTrue(rel.getBodyPart(0).isMimeType("text/html"));

            BodyPart imgPart = rel.getBodyPart(1);
            assertTrue(imgPart.isMimeType("image/jpeg"));
            assertEquals("<skyroyal-hero>", imgPart.getHeader("Content-ID")[0]);
            assertEquals(Part.INLINE, imgPart.getDisposition());
        }
    }
}
