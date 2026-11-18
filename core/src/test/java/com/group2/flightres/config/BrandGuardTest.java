package com.group2.flightres.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandGuardTest {

    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".fxml", ".css", ".html", ".sql", ".md", ".properties", ".xml", ".txt"
    );

    private static final Pattern FORBIDDEN_BRAND = Pattern.compile(
            "(?i)(altitude|skyvoyage|sky\\s+voyage)|(?<!fx:constant=)\"APP_NAME\"|>APP_NAME<"
    );

    @Test
    @DisplayName("AppInfo.NAME and BrandConfig.APP_NAME are Sky Royal")
    void testBrandIsSkyRoyal() {
        assertEquals("Sky Royal", AppInfo.NAME);
        assertEquals("Sky Royal", BrandConfig.APP_NAME);
    }

    @Test
    @DisplayName("No source or template contains legacy brand names Altitude, skyvoyage, or literal APP_NAME")
    void testNoLegacyBrandNames() throws IOException {
        Path repoRoot = resolveRepoRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(repoRoot)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> !isExcluded(repoRoot.relativize(p)))
                    .filter(p -> hasAllowedExt(p.getFileName().toString()))
                    .toList();

            for (Path file : files) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = FORBIDDEN_BRAND.matcher(lines.get(i));
                    if (m.find()) {
                        violations.add(repoRoot.relativize(file) + ":" + (i + 1) + " -> " + m.group());
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), "Legacy brand references found:\n" + String.join("\n", violations));
    }

    private static boolean isExcluded(Path rel) {
        String s = rel.toString().replace('\\', '/');
        if (s.startsWith(".git/") || s.startsWith("docs/") || s.startsWith("design-glass/")
                || s.startsWith("design-v2/") || s.contains("/target/")) {
            return true;
        }
        return s.endsWith("BrandGuardTest.java") || s.endsWith(".log");
    }

    private static boolean hasAllowedExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot).toLowerCase());
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("core")) && Files.exists(cwd.resolve("web"))) {
            return cwd;
        }
        if (cwd.getParent() != null && Files.exists(cwd.getParent().resolve("core"))) {
            return cwd.getParent();
        }
        return cwd;
    }
}
