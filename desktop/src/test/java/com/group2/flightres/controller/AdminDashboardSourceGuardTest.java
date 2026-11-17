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

class AdminDashboardSourceGuardTest {

    private static final Pattern COLOR_LITERAL_PATTERN = Pattern.compile(
            "(?i)(#[0-9a-f]{3,8}\\b|rgb\\s*\\(|rgba\\s*\\()"
    );

    private static Path resolve(String rel) {
        Path p = Path.of(rel);
        return Files.exists(p) ? p : Path.of("desktop").resolve(rel);
    }

    @Test
    @DisplayName("R11 Source Guard: No hex/rgb/rgba colour literals in admin-dashboard.fxml or AdminDashboardController.java")
    void testNoColorLiteralsOutsideGlassCss() throws IOException {
        List<Path> files = List.of(
                resolve("src/main/resources/com/group2/flightres/fxml/admin-dashboard.fxml"),
                resolve("src/main/java/com/group2/flightres/controller/AdminDashboardController.java")
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
    @DisplayName("Credit & No-Fabrication Guard: header credit present and no sample board numbers")
    void testCreditAndNoFabricatedNumbers() throws IOException {
        String fxml = Files.readString(resolve("src/main/resources/com/group2/flightres/fxml/admin-dashboard.fxml"));
        String ctrl = Files.readString(resolve("src/main/java/com/group2/flightres/controller/AdminDashboardController.java"));

        assertTrue(ctrl.contains("Original concept: Petja / Luyanda. Integrated by David."),
                "Exact header credit must be present in AdminDashboardController");
        assertFalse(fxml.contains("148k") || fxml.contains("412") || fxml.contains("1,522"),
                "Sample board figures must not appear in admin-dashboard.fxml");
        assertFalse(ctrl.contains("148k") || ctrl.contains("412") || ctrl.contains("1,522"),
                "Sample board figures must not appear in AdminDashboardController.java");
    }

    @Test
    @DisplayName("Git Hygiene Guard: git ls-files contains no .bak, .orig, or .log artefacts")
    void testNoBuildArtefactsTrackedInGit() throws Exception {
        Path repoRoot = Files.exists(Path.of(".git")) ? Path.of(".") : Path.of("..");
        Process process = new ProcessBuilder("git", "ls-files")
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        assertTrue(exit == 0, "git ls-files failed with exit code " + exit);
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            assertFalse(trimmed.endsWith(".bak") || trimmed.endsWith(".orig") || trimmed.endsWith(".log"),
                    "Tracked build/backup artefact found in git ls-files: " + trimmed);
        }
    }
}
