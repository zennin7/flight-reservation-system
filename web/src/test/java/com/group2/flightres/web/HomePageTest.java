package com.group2.flightres.web;

import com.group2.flightres.config.AppInfo;
import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.config.SeatHoldPolicy;
import com.group2.flightres.util.MoneyFormatter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomePageTest {

    private static WebApplication webApp;
    private static String baseUrl;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() {
        webApp = new WebApplication();
        webApp.start(0);
        baseUrl = "http://localhost:" + webApp.getApp().port();
    }

    @AfterAll
    static void stopServer() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    @Test
    @DisplayName("Home page renders Sky Royal brand, 5 min seat hold, and no APP_NAME")
    void testHomePageBrandAndFactsStrip() throws Exception {
        HttpResponse<String> resp = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode());
        String html = resp.body();

        assertTrue(html.contains(AppInfo.NAME), "Home page must render Sky Royal brand");
        String legacyToken = "APP_" + "NAME"; assertFalse(html.contains(legacyToken), "Home page must never contain " + legacyToken);
        String expectedHold = (SeatHoldPolicy.HOLD_SECONDS / 60) + " min";
        assertTrue(html.contains(expectedHold), "Facts strip must show " + expectedHold);
        assertTrue(html.contains("Original website: Nikki and Thaledi. Integrated by David."));
    }

    @Test
    @DisplayName("Popular routes cheapest fares match independent SQL oracle")
    void testPopularRoutesMatchSqlOracle() throws Exception {
        HttpResponse<String> resp = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode());
        String html = resp.body();

        String sql = """
                SELECT dest_code, dest_city, min_fare FROM (
                    SELECT DISTINCT ON (a.iata_code)
                           a.iata_code AS dest_code, a.city AS dest_city,
                           MIN(f.base_price) AS min_fare, COUNT(*) AS cnt
                    FROM flights f
                    JOIN airports o ON o.airport_id = f.origin_airport_id
                    JOIN airports a ON a.airport_id = f.dest_airport_id
                    WHERE f.status = 'SCHEDULED'
                      AND f.departure_time >= ?
                      AND a.iata_code IN ('CPT', 'DUR', 'JNB')
                    GROUP BY o.iata_code, a.iata_code, a.city
                    ORDER BY a.iata_code, COUNT(*) DESC, MIN(f.base_price) ASC, o.iata_code ASC
                ) sub
                ORDER BY dest_code
                """;
        Instant minDep = Instant.now().plusSeconds(3600);
        int checked = 0;
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(minDep));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String city = rs.getString("dest_city");
                    BigDecimal minFare = rs.getBigDecimal("min_fare");
                    String formatted = MoneyFormatter.format(minFare);
                    assertTrue(html.contains(city), "Home page missing popular destination: " + city);
                    assertTrue(html.contains(formatted),
                            "Home page missing oracle fare " + formatted + " for " + city);
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "Expected at least 1 popular domestic route in DB");
    }

    @Test
    @DisplayName("No hex/rgb literals or gold/navy tokens in home template or CSS outside glass-web.css")
    void testNoColorLiteralsOrGoldNavyTokens() throws IOException {
        Path webRoot = resolveWebRoot();
        Path homeHtml = webRoot.resolve("src/main/resources/templates/home.html");
        Path glassWebCss = webRoot.resolve("src/main/resources/static/css/glass-web.css");

        Pattern colorLiteral = Pattern.compile("(#[0-9a-fA-F]{3,8}\\b|(?<![A-Za-z])rgba?\\s*\\()");
        Pattern goldOrNavy = Pattern.compile("(?i)(--glass-gold|--glass-navy|-glass-gold|-glass-navy)");

        List<String> violations = new ArrayList<>();
        checkFile(homeHtml, colorLiteral, violations, "hex/rgb literal in home.html");
        checkFile(homeHtml, goldOrNavy, violations, "gold/navy token in home.html");
        checkFile(glassWebCss, goldOrNavy, violations, "gold/navy token in glass-web.css");

        Path staticCssDir = webRoot.resolve("src/main/resources/static/css");
        if (Files.exists(staticCssDir)) {
            try (Stream<Path> walk = Files.walk(staticCssDir)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    if (!p.getFileName().toString().equals("glass-web.css")) {
                        checkFile(p, colorLiteral, violations, "hex/rgb literal outside glass-web.css");
                        checkFile(p, goldOrNavy, violations, "gold/navy token outside glass-web.css");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), "CSS/template violations:\n" + String.join("\n", violations));
    }

    private static void checkFile(Path file, Pattern pattern, List<String> violations, String label) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = pattern.matcher(lines.get(i));
            if (m.find()) {
                violations.add(label + " at " + file.getFileName() + ":" + (i + 1) + " -> " + m.group());
            }
        }
    }

    private static Path resolveWebRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("src/main/resources/templates/home.html"))) {
            return cwd;
        }
        return cwd.resolve("web");
    }
}
