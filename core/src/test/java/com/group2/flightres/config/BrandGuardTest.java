package com.group2.flightres.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private static final Pattern FORBIDDEN_FLIGHT_PREFIX = Pattern.compile(
            "\\bALT\\s+\\d+|['\"]ALT(?:\\s|%)|['\"]ALT['\"]|startsWith\\s*\\(\\s*\"ALT\"|(?i)\\bALT\\s+flights?\\b"
    );

    @Test
    @DisplayName("AppInfo.NAME is Sky Royal, FLIGHT_PREFIX is SR, and BrandConfig.APP_NAME matches")
    void testBrandIsSkyRoyal() {
        assertEquals("Sky Royal", AppInfo.NAME);
        assertEquals("SR", AppInfo.FLIGHT_PREFIX);
        assertEquals("Sky Royal", BrandConfig.APP_NAME);
    }

    @Test
    @DisplayName("No source or template contains legacy brand names Altitude, skyvoyage, or literal APP_NAME")
    void testNoLegacyBrandNames() throws IOException {
        List<String> violations = scanFilesForPattern(FORBIDDEN_BRAND, path -> false);
        assertTrue(violations.isEmpty(), "Legacy brand references found:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("No source, seed, or test contains legacy flight prefix ALT")
    void testNoLegacyFlightPrefix() throws IOException {
        List<String> violations = scanFilesForPattern(
                FORBIDDEN_FLIGHT_PREFIX,
                rel -> rel.replace('\\', '/').endsWith("migration_flight_prefix_sr.sql")
        );
        assertTrue(violations.isEmpty(), "Legacy flight prefix ALT found:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("No legacy asset filename starting with alt- exists in repository")
    void testNoLegacyAltFilenames() throws IOException {
        Path repoRoot = resolveRepoRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(repoRoot)) {
            walk.filter(Files::isRegularFile)
                    .forEach(p -> {
                        String rel = repoRoot.relativize(p).toString().replace('\\', '/');
                        if (isBuildOrDocsExcluded(rel)) {
                            return;
                        }
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (name.startsWith("alt-")) {
                            violations.add(rel);
                        }
                    });
        }

        assertTrue(violations.isEmpty(), "Legacy alt-* filenames found:\n" + String.join("\n", violations));
    }

    private static List<String> scanFilesForPattern(Pattern pattern, java.util.function.Predicate<String> skipExtra)
            throws IOException {
        Path repoRoot = resolveRepoRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(repoRoot)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String rel = repoRoot.relativize(p).toString().replace('\\', '/');
                        return !isExcluded(rel) && !skipExtra.test(rel);
                    })
                    .filter(p -> hasAllowedExt(p.getFileName().toString()))
                    .toList();

            for (Path file : files) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = pattern.matcher(lines.get(i));
                    if (m.find()) {
                        violations.add(repoRoot.relativize(file) + ":" + (i + 1) + " -> " + m.group());
                    }
                }
            }
        }
        return violations;
    }

    private static boolean isBuildOrDocsExcluded(String s) {
        return s.startsWith(".git/") || s.startsWith("docs/")
                || s.startsWith("target/") || s.contains("/target/");
    }

    private static boolean isExcluded(String s) {
        if (isBuildOrDocsExcluded(s) || s.startsWith("design-glass/") || s.startsWith("design-v2/")) {
            return true;
        }
        return s.endsWith("BrandGuardTest.java") || s.endsWith(".log");
    }

    private static boolean hasAllowedExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot).toLowerCase(Locale.ROOT));
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
