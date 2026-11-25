package com.group2.flightres.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopTokenGuardTest {

    private static final Pattern GOLD_HEX_OR_RGBA = Pattern.compile(
            "(?i)(#(a87d22|b98a2e|c4952f|cb9c37|cfa038|d4a63c|d9ac47|e0b44f|e8c46a|f0cf74|f3d98b)\\b|rgba\\s*\\(\\s*212\\s*,\\s*166\\s*,\\s*60)"
    );

    private static final Pattern LEGACY_BRAND_OR_FL = Pattern.compile(
            "(?i)(altitude|skyvoyage|\\bFL-\\d+)"
    );

    private static final Pattern SERIF_FONT = Pattern.compile(
            "(?i)(\\bLora\\b|\\bGeorgia\\b|,\\s*serif\\b)"
    );

    private static Path desktopRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("src/main/resources/com/group2/flightres/css/glass.css"))) {
            return cwd;
        }
        return cwd.resolve("desktop");
    }

    @Test
    @DisplayName("No gold hex or gold rgba values remain anywhere in desktop/src/main (CSS, FXML, Java)")
    void noGoldColorsInDesktopMain() throws IOException {
        Path mainDir = desktopRoot().resolve("src/main");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainDir)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".css") || n.endsWith(".fxml") || n.endsWith(".java");
                    })
                    .toList();
            for (Path f : files) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = GOLD_HEX_OR_RGBA.matcher(lines.get(i));
                    if (m.find()) {
                        hits.add(mainDir.relativize(f) + ":" + (i + 1) + " -> " + m.group());
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "Gold color leftovers found in desktop/src/main:\n" + String.join("\n", hits));
    }

    @Test
    @DisplayName("No legacy brand (Altitude, skyvoyage, FL- prefix) in desktop/src/main")
    void noLegacyBrandInDesktopMain() throws IOException {
        Path mainDir = desktopRoot().resolve("src/main");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainDir)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".css") || n.endsWith(".fxml") || n.endsWith(".java");
                    })
                    .toList();
            for (Path f : files) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = LEGACY_BRAND_OR_FL.matcher(lines.get(i));
                    if (m.find()) {
                        hits.add(mainDir.relativize(f) + ":" + (i + 1) + " -> " + m.group());
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "Legacy brand/prefix found:\n" + String.join("\n", hits));
    }

    @Test
    @DisplayName("No serif fonts (Lora, Georgia, serif) remain in desktop/src/main (CSS, FXML, Java)")
    void noSerifFontsInDesktopMain() throws IOException {
        Path mainDir = desktopRoot().resolve("src/main");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainDir)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".css") || n.endsWith(".fxml") || n.endsWith(".java");
                    })
                    .toList();
            for (Path f : files) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.contains("sans-serif")) {
                        line = line.replace("sans-serif", "");
                    }
                    Matcher m = SERIF_FONT.matcher(line);
                    if (m.find()) {
                        hits.add(mainDir.relativize(f) + ":" + (i + 1) + " -> " + m.group());
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "Serif font leftovers found in desktop/src/main:\n" + String.join("\n", hits));
    }

    @Test
    @DisplayName("Bundled web aerial, cabin, and destination photos exist in desktop resources and are <= 350 KB")
    void bundledWebPhotosExistAndWithinSizeCap() throws IOException {
        Path photosDir = desktopRoot().resolve("src/main/resources/com/group2/flightres/photos");
        List<String> required = List.of(
                "aerial-1.jpg", "aerial-2.jpg", "aerial-3.jpg",
                "cabin-economy-1.jpg", "cabin-first-1.jpg",
                "dest-capetown.jpg", "dest-durban.jpg", "dest-johannesburg.jpg",
                "dest-dubai.jpg", "dest-monaco.jpg", "dest-rome.jpg", "dest-santorini.jpg",
                "sky-wing-1.jpg", "cabin-mood-1.jpg", "sky-engine-1.jpg",
                "sky-window-peaks.jpg", "sky-jet-clouds.jpg", "sky-jet-ocean.jpg",
                "cabin-suite-1.jpg", "tarmac-jet-1.jpg", "sky-jet-dark.jpg"
        );
        Path webPhotosDir = desktopRoot().getParent().resolve("web/src/main/resources/public/photos");
        for (String name : required) {
            Path p = photosDir.resolve(name);
            assertTrue(Files.exists(p), "Missing desktop photo: " + name);
            long size = Files.size(p);
            assertTrue(size > 0 && size <= 358_400L,
                    "Photo " + name + " exceeds 350 KB cap (" + size + " bytes)");
            Path webCopy = webPhotosDir.resolve(name);
            if (Files.exists(webCopy)) {
                assertTrue(java.util.Arrays.equals(Files.readAllBytes(p), Files.readAllBytes(webCopy)),
                        "Desktop and web photo bytes differ for " + name);
            }
        }
    }
}
