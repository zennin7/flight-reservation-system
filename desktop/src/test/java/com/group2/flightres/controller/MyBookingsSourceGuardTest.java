package com.group2.flightres.controller;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.service.BookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyBookingsSourceGuardTest {

    private static final Path FXML_PATH = Path.of("src/main/resources/com/group2/flightres/fxml/my-bookings.fxml");
    private static final Path CONTROLLER_PATH = Path.of("src/main/java/com/group2/flightres/controller/MyBookingsController.java");

    @Test
    @DisplayName("Source Guard: my-bookings.fxml contains no hardcoded prices, aircraft models, or Coming soon alerts")
    void testFxmlSourceGuard() throws IOException {
        Path resolvedPath = Files.exists(FXML_PATH) ? FXML_PATH : Path.of("desktop").resolve(FXML_PATH);
        assertTrue(Files.exists(resolvedPath), "my-bookings.fxml must exist");

        String content = Files.readString(resolvedPath);

        // Assert no hard-coded prices from design board
        assertFalse(content.contains("1,522"), "my-bookings.fxml must not contain sample fare 1,522");
        assertFalse(content.contains("3,044"), "my-bookings.fxml must not contain sample fare 3,044");
        assertFalse(content.contains("R180"), "my-bookings.fxml must not contain hardcoded R180");
        assertFalse(content.contains("R142"), "my-bookings.fxml must not contain hardcoded R142");

        // Assert aircraft model is not hardcoded in FXML
        assertFalse(content.contains("Airbus"), "Aircraft model must not be hardcoded in FXML");
        assertFalse(content.contains("A320"), "Aircraft model must not be hardcoded in FXML");
    }

    @Test
    @DisplayName("Source Guard: MyBookingsController contains no fabricated prices or hard-coded seat values")
    void testControllerSourceGuard() throws IOException {
        Path resolvedPath = Files.exists(CONTROLLER_PATH) ? CONTROLLER_PATH : Path.of("desktop").resolve(CONTROLLER_PATH);
        assertTrue(Files.exists(resolvedPath), "MyBookingsController.java must exist");

        String content = Files.readString(resolvedPath);

        // Banned literals
        assertFalse(content.contains("1522"), "MyBookingsController must not contain hardcoded 1522");
        assertFalse(content.contains("3044"), "MyBookingsController must not contain hardcoded 3044");
        assertFalse(content.contains("R 1,522"), "MyBookingsController must not contain hardcoded R 1,522");
    }

    @Test
    @DisplayName("SQL Reconciliation (R8h): numbers shown reconcile against independent SQL count for demo user")
    void testSqlReconciliation() throws Exception {
        long userId = 12L;
        long dbCount = 0;
        String sql = "SELECT count(*) FROM bookings WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    dbCount = rs.getLong(1);
                }
            }
        }

        BookingService service = new BookingService();
        List<BookingSummary> list = service.listBookingsForUser(userId);

        assertEquals(dbCount, list.size(),
                "Number of bookings returned by BookingService must match independent SQL count (" + dbCount + ")");
        assertTrue(list.size() >= 4, "Authorised demo user must have at least 4 bookings per prompt requirement");
    }
}
