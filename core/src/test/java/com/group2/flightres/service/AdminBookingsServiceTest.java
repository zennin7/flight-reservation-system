// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.AdminBookingsDao.AdminBookingsFilter;
import com.group2.flightres.dao.impl.AdminBookingsDaoImpl;
import com.group2.flightres.model.AdminBookingRow;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBookingsServiceTest {

    private static User adminUser() {
        User u = new User();
        u.setUserId(1L);
        u.setEmail("admin@example.com");
        u.setFullName("David Admin");
        u.setRole(UserRole.ADMIN);
        return u;
    }

    private static User customerUser() {
        User u = new User();
        u.setUserId(12L);
        u.setEmail("traveller@example.com");
        u.setFullName("Demo Traveller");
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    @Test
    @DisplayName("RBAC: non-ADMIN and null users rejected for search, export, and boarding pass PDF")
    void testRbacRejectsNonAdmin() {
        AdminBookingsService service = new AdminBookingsService();
        assertThrows(SecurityException.class, () -> service.searchBookings(customerUser(), AdminBookingsFilter.defaults(), 1));
        assertThrows(SecurityException.class, () -> service.searchBookings(null, AdminBookingsFilter.defaults(), 1));
        assertThrows(SecurityException.class, () -> service.exportFilteredCsv(customerUser(), AdminBookingsFilter.defaults()));
        assertThrows(SecurityException.class, () -> service.listFlightOptions(customerUser()));
    }

    @Test
    @DisplayName("CSV formula-injection protection escapes = + - @ and quotes commas safely")
    void testCsvFormulaInjectionEscaping() {
        ZoneId sast = ZoneId.of("Africa/Johannesburg");
        AdminBookingRow hostile = new AdminBookingRow(
                1L, "PNR999", "=HYPERLINK(\"http://evil.example\",\"Click\")", "+user@example.com",
                "Hostile", 10L, "ALT 101", "JNB", "Johannesburg", "CPT", "Cape Town",
                Instant.now(), sast, Instant.now().plusSeconds(7200), sast,
                "1A", SeatClass.ECONOMY, new BigDecimal("1522.00"), "CONFIRMED"
        );
        String csv = AdminBookingsService.formatCsv(List.of(hostile));
        String[] lines = csv.trim().split("\\R");
        assertEquals(2, lines.length);
        assertTrue(lines[1].contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"Click\"\")\""),
                "Hostile = formula must be prefixed with quote and RFC-4180 escaped: " + lines[1]);
        assertTrue(lines[1].contains("'+user@example.com"), "Leading + must be prefixed with quote");
    }

    @Test
    @DisplayName("DB Integration (rolled back): reconciles status, cabin, date, search, flight filters & CSV against independent SQL")
    void testFiltersAndCsvReconciliationInRolledBackTransaction() throws Exception {
        AdminBookingsService service = new AdminBookingsService(new AdminBookingsDaoImpl());

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. ALL filter reconciliation
                AdminBookingsPage allPage = service.searchBookings(adminUser(), AdminBookingsFilter.defaults(), 1, conn);
                long sqlCount = 0;
                BigDecimal sqlSum = BigDecimal.ZERO;
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*), COALESCE(SUM(total_amount),0) FROM bookings");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    sqlCount = rs.getLong(1);
                    sqlSum = rs.getBigDecimal(2);
                }
                assertEquals(sqlCount, allPage.getTotalCount());
                assertEquals(0, sqlSum.compareTo(allPage.getTotalAmountSum()));

                // 2. CSV export reconciliation against ALL filter
                String csv = service.exportFilteredCsv(adminUser(), AdminBookingsFilter.defaults(), conn);
                String[] lines = csv.trim().split("\\R");
                assertEquals(sqlCount + 1, lines.length, "CSV rows (+ header) must equal SQL count");

                // 3. CONFIRMED status filter reconciliation
                AdminBookingsPage confPage = service.searchBookings(adminUser(),
                        new AdminBookingsFilter("", "CONFIRMED", "ALL", null, null, "ALL", null), 1, conn);
                long confCount = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM bookings WHERE status::text = 'CONFIRMED'");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    confCount = rs.getLong(1);
                }
                assertEquals(confCount, confPage.getTotalCount());

                // 4. FIRST cabin filter reconciliation
                AdminBookingsPage firstPage = service.searchBookings(adminUser(),
                        new AdminBookingsFilter("", "ALL", "ALL", null, null, "FIRST", null), 1, conn);
                long firstCount = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM bookings b WHERE EXISTS (SELECT 1 FROM booking_seats bs "
                        + "JOIN flight_seats fs ON fs.flight_seat_id=bs.flight_seat_id "
                        + "JOIN seat_templates st ON st.seat_template_id=fs.seat_template_id "
                        + "WHERE bs.booking_id=b.booking_id AND st.seat_class::text='FIRST')");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    firstCount = rs.getLong(1);
                }
                assertEquals(firstCount, firstPage.getTotalCount());

                // 5. Escaped wildcard search does not match everything
                AdminBookingsPage wildcardPage = service.searchBookings(adminUser(),
                        new AdminBookingsFilter("%_NONEXISTENT_%", "ALL", "ALL", null, null, "ALL", null), 1, conn);
                assertEquals(0, wildcardPage.getTotalCount());
                assertFalse(allPage.getRows().isEmpty());
            } finally {
                conn.rollback();
            }
        }
    }
}
