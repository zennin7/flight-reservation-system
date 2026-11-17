// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBookingsSourceGuardTest {

    private static final Pattern COLOR_LITERAL_PATTERN = Pattern.compile(
            "(?i)(#[0-9a-f]{3,8}\\b|rgb\\s*\\(|rgba\\s*\\()"
    );

    private static Path resolve(String rel) {
        Path p = Path.of(rel);
        return Files.exists(p) ? p : Path.of("desktop").resolve(rel);
    }

    @Test
    @DisplayName("R11 Source Guard: No hex/rgb/rgba colour literals in admin-bookings.fxml or AdminBookingsController.java")
    void testNoColorLiteralsOutsideGlassCss() throws IOException {
        List<Path> files = List.of(
                resolve("src/main/resources/com/group2/flightres/fxml/admin-bookings.fxml"),
                resolve("src/main/java/com/group2/flightres/controller/AdminBookingsController.java")
        );

        for (Path file : files) {
            assertTrue(Files.exists(file), "Missing file: " + file);
            String content = Files.readString(file);
            Matcher m = COLOR_LITERAL_PATTERN.matcher(content);
            assertFalse(m.find(), "R11 violation: colour literal '" + (m.find(0) ? m.group() : "")
                    + "' found in " + file);
            assertFalse(content.contains("setStyle("), "R11 violation: inline setStyle() found in " + file);
        }
    }

    @Test
    @DisplayName("Credit & No-Cancel/No-Fabrication Guard")
    void testCreditAndNoCancelButton() throws IOException {
        String fxml = Files.readString(resolve("src/main/resources/com/group2/flightres/fxml/admin-bookings.fxml"));
        String ctrl = Files.readString(resolve("src/main/java/com/group2/flightres/controller/AdminBookingsController.java"));

        assertTrue(ctrl.contains("Original concept: Petja / Luyanda. Integrated by David."),
                "Exact header credit must be present in AdminBookingsController");
        assertFalse(fxml.toLowerCase().contains("cancel booking") || ctrl.toLowerCase().contains("cancel booking"),
                "Cancel booking button must NOT be faked on Admin Bookings screen");
        assertFalse(fxml.contains("K7QX2M") || fxml.contains("1,522"),
                "Sample board values must not appear in admin-bookings.fxml");
    }
}
