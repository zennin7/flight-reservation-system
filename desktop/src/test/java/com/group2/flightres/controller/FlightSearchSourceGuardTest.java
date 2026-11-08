package com.group2.flightres.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightSearchSourceGuardTest {

    record AllowlistEntry(String fileName, String pattern, int maxCount, String owner, String reason) {}

    // Words with NO legitimate hit (built by concatenation to avoid matching this source file)
    static final List<String> BANNED_PATTERNS = List.of(
            "checked" + " bag",
            "Tambo" + " Intl",
            "Cape" + " Town" + " Intl",
            "Via" + " ",
            "Sn" + "ack",
            "Free" + " meal",
            "3h" + " 50m",
            "FL" + " 301",
            "FL" + " 512",
            "GRP" + " 318",
            "GRP" + " 422",
            "GRP" + " 507",
            "Via" + " Durban",
            "LocalDate" + ".of(2026",
            "King" + " Shaka",
            "lay" + "over",
            "4," + "850",
            "48" + "50",
            "6h" + " 40m",
            "1," + "290",
            "2," + "722"
    );

    // Words with legitimate hits guarded by file + pattern + maxCount allowlist
    static final List<String> GUARDED_PATTERNS = List.of(
            "Dur" + "ban",
            "23" + " kg",
            "Airbus" + " A320",
            "2h" + " 05m"
    );

    // Explicit allowlist of legitimate occurrences in desktop/src/main (all checkout, seatmap or verifier owned)
    static final List<AllowlistEntry> ALLOWLIST = List.of(
            new AllowlistEntry("GlassConfirmationVerifier.java", "Durban", 1, "verifier", "Verification test fixture synthetic destination"),
            new AllowlistEntry("GlassSeatMapBridge.java", "Airbus A320", 1, "seatmap", "Screen 3 Seat map header fallback aircraft label"),
            new AllowlistEntry("checkout.fxml", "23 kg", 1, "checkout", "Screen 5 Checkout screen static baggage allowance label"),
            new AllowlistEntry("checkout.fxml", "2h 05m", 1, "checkout", "Screen 5 Checkout screen static duration placeholder")
    );

    // Banned literals specific to flight search UI and controller
    static final List<String> FLIGHT_SEARCH_BANNED = List.of(
            "Prem" + "ium",
            "Busi" + "ness",
            "Non" + "stop",
            "Coming" + " soon",
            "Coming" + " Soon"
    );

    static List<String> checkFileContent(String fileName, List<String> lines,
                                         List<String> bannedPatterns,
                                         List<String> guardedPatterns,
                                         List<AllowlistEntry> allowlist) {
        List<String> violations = new ArrayList<>();

        // 1. Check unconditionally banned patterns
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;
            for (String banned : bannedPatterns) {
                if (line.contains(banned)) {
                    violations.add(fileName + ":" + lineNum + " contains banned literal: '" + banned + "'");
                }
            }
        }

        // 2. Check guarded patterns against allowlist (file + pattern + maxCount)
        for (String guarded : guardedPatterns) {
            int count = 0;
            for (String line : lines) {
                if (line.contains(guarded)) {
                    count++;
                }
            }
            if (count > 0) {
                AllowlistEntry match = allowlist.stream()
                        .filter(e -> fileName.equals(e.fileName()) && guarded.equals(e.pattern()))
                        .findFirst()
                        .orElse(null);
                if (match == null) {
                    violations.add(fileName + " contains un-allowlisted literal: '" + guarded + "' (" + count + " occurrences)");
                } else if (count > match.maxCount()) {
                    violations.add(fileName + " contains " + count + " occurrences of '" + guarded
                            + "', exceeding allowlist maxCount of " + match.maxCount() + " [owner: " + match.owner() + "]");
                }
            }
        }

        // 3. Check flight search specific banned literals
        if ("flight-search.fxml".equals(fileName) || "FlightSearchController.java".equals(fileName)) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lineNum = i + 1;
                for (String banned : FLIGHT_SEARCH_BANNED) {
                    if (line.contains(banned)) {
                        violations.add(fileName + ":" + lineNum + " contains banned flight search literal: '" + banned + "'");
                    }
                }
            }
        }

        return violations;
    }

    @Test
    @DisplayName("Source Guard: desktop/src/main contains no hard-coded flight search placeholders")
    void testNoHardcodedFlightSearchLiterals() throws IOException {
        Path mainDir = Path.of("src", "main");
        if (!Files.exists(mainDir)) {
            mainDir = Path.of("desktop", "src", "main");
        }
        assertTrue(Files.exists(mainDir), "desktop/src/main directory must exist");

        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(mainDir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString();
                if (fileName.endsWith(".java") || fileName.endsWith(".fxml") || fileName.endsWith(".css")) {
                    try {
                        List<String> lines = Files.readAllLines(path);
                        violations.addAll(checkFileContent(fileName, lines, BANNED_PATTERNS, GUARDED_PATTERNS, ALLOWLIST));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed reading " + path, e);
                    }
                }
            });
        }

        assertTrue(violations.isEmpty(), "Found banned hardcoded literals in desktop/src/main:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("Source Guard: Failing-case check proves un-allowlisted hit is properly reported")
    void testFailingCaseReported() {
        // Test un-allowlisted file with guarded pattern
        List<String> guardedViolations = checkFileContent("UnexpectedService.java",
                List.of("String destination = \"Durban\";"),
                BANNED_PATTERNS, GUARDED_PATTERNS, ALLOWLIST);
        assertFalse(guardedViolations.isEmpty(), "Un-allowlisted literal in unexpected file must trigger a violation");
        assertTrue(guardedViolations.get(0).contains("contains un-allowlisted literal: 'Durban'"),
                "Violation must name the un-allowlisted pattern: " + guardedViolations.get(0));

        // Test unconditionally banned literal
        List<String> bannedViolations = checkFileContent("SomeComponent.java",
                List.of("String perk = \"Free meal\";"),
                BANNED_PATTERNS, GUARDED_PATTERNS, ALLOWLIST);
        assertFalse(bannedViolations.isEmpty(), "Banned literal must trigger a violation");
        assertTrue(bannedViolations.get(0).contains("contains banned literal: 'Free meal'"),
                "Violation must name the banned literal: " + bannedViolations.get(0));

        // Test flight-search banned literal
        List<String> fsViolations = checkFileContent("flight-search.fxml",
                List.of("<Label text=\"Premium\"/>"),
                BANNED_PATTERNS, GUARDED_PATTERNS, ALLOWLIST);
        assertFalse(fsViolations.isEmpty(), "Banned flight search literal must trigger a violation");
        assertTrue(fsViolations.get(0).contains("contains banned flight search literal: 'Premium'"),
                "Violation must name the banned literal: " + fsViolations.get(0));
    }
}
