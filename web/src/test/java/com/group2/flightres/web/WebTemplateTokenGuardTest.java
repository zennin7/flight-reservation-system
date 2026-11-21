package com.group2.flightres.web;

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

class WebTemplateTokenGuardTest {

    private static final List<String> GLASS_TEMPLATES = List.of(
            "home.html",
            "login.html",
            "register.html",
            "checkout.html",
            "confirmation.html",
            "verify.html",
            "my-bookings.html",
            "cancel.html"
    );

    private static final Pattern COLOR_LITERAL = Pattern.compile("((?<!&)#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\\b|(?<![A-Za-z])rgba?\\s*\\()");
    private static final Pattern GOLD_OR_NAVY = Pattern.compile("(?i)(--glass-gold|--glass-navy|-glass-gold|-glass-navy)");

    @Test
    @DisplayName("All glass templates and CSS outside :root use design tokens only with zero color literals or gold/navy tokens")
    void allGlassTemplatesAndCssRespectDesignTokensOnly() throws IOException {
        Path webRoot = resolveWebRoot();
        List<String> violations = new ArrayList<>();

        for (String name : GLASS_TEMPLATES) {
            Path tpl = webRoot.resolve("src/main/resources/templates").resolve(name);
            assertTrue(Files.exists(tpl), "Missing expected template: " + tpl);
            List<String> lines = Files.readAllLines(tpl, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher mc = COLOR_LITERAL.matcher(line);
                if (mc.find()) {
                    violations.add("Color literal in " + name + ":" + (i + 1) + " -> " + mc.group());
                }
                Matcher mg = GOLD_OR_NAVY.matcher(line);
                if (mg.find()) {
                    violations.add("Legacy gold/navy token in " + name + ":" + (i + 1) + " -> " + mg.group());
                }
            }
        }

        Path glassCss = webRoot.resolve("src/main/resources/static/css/glass-web.css");
        assertTrue(Files.exists(glassCss));
        List<String> cssLines = Files.readAllLines(glassCss, StandardCharsets.UTF_8);
        boolean inRoot = false;
        int braceDepth = 0;
        for (int i = 0; i < cssLines.size(); i++) {
            String line = cssLines.get(i);
            Matcher mg = GOLD_OR_NAVY.matcher(line);
            if (mg.find()) {
                violations.add("Legacy gold/navy token in glass-web.css:" + (i + 1) + " -> " + mg.group());
            }
            if (line.trim().startsWith(":root")) {
                inRoot = true;
            }
            for (char ch : line.toCharArray()) {
                if (ch == '{') braceDepth++;
                else if (ch == '}') {
                    braceDepth = Math.max(0, braceDepth - 1);
                    if (braceDepth == 0) inRoot = false;
                }
            }
            if (!inRoot) {
                Matcher mc = COLOR_LITERAL.matcher(line);
                if (mc.find()) {
                    violations.add("Color literal outside :root in glass-web.css:" + (i + 1) + " -> " + mc.group());
                }
            }
        }

        Path cssDir = webRoot.resolve("src/main/resources/static/css");
        if (Files.exists(cssDir)) {
            try (Stream<Path> walk = Files.walk(cssDir)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    if (!p.getFileName().toString().equals("glass-web.css")) {
                        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            Matcher mg = GOLD_OR_NAVY.matcher(lines.get(i));
                            if (mg.find()) {
                                violations.add("Legacy gold/navy token in " + p.getFileName() + ":" + (i + 1));
                            }
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), "Token guard violations:\n" + String.join("\n", violations));
    }

    private static Path resolveWebRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("src/main/resources/templates/home.html"))) {
            return cwd;
        }
        return cwd.resolve("web");
    }
}
