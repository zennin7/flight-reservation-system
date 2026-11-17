// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.impl.AdminBookingsDaoImpl;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.DatabaseAssumption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBookingsServiceTest {

    private static User adminUser() {
        User u = new User();
        u.setUserId(1L);
        u.setEmail("testadmin@example.com");
        u.setFullName("Test Admin");
        u.setRole(UserRole.ADMIN);
        return u;
    }

    private static User customerUser() {
        User u = new User();
        u.setUserId(12L);
        u.setEmail("demo.traveller@example.com");
        u.setFullName("Demo Traveller");
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    @Test
    @DisplayName("RBAC: ADMIN succeeds while CUSTOMER and null user throw SecurityException")
    void testRbacEnforcement() {
        AdminBookingsService service = new AdminBookingsService((s, st, p, sz) ->
                new AdminBookingsPage(List.of(), 0L, BigDecimal.ZERO, p, sz));

        assertThrows(SecurityException.class, () -> service.searchBookings(null, "", "ALL", 1));
        assertThrows(SecurityException.class, () -> service.searchBookings(customerUser(), "", "ALL", 1));

        AdminBookingsPage page = service.searchBookings(adminUser(), "", "ALL", 1);
        assertNotNull(page);
        assertEquals(25, page.getPageSize());
    }

    @Test
    @DisplayName("Wildcard escaping escapes backslash, percent, and underscore for parameterized LIKE")
    void testWildcardEscaping() {
        assertEquals("", AdminBookingsDaoImpl.escapeLikeWildcards(null));
        assertEquals("\\%\\_\\\\", AdminBookingsDaoImpl.escapeLikeWildcards(" %_\\ "));
        assertEquals("FCT79R", AdminBookingsDaoImpl.escapeLikeWildcards("FCT79R"));
    }

    @Test
    @DisplayName("DB Integration & SQL Reconciliation: row count, sum of totals, PNR search, and wildcard safety in rolled-back tx")
    void testDbSearchPaginationAndSqlReconciliation() throws SQLException {
        DatabaseAssumption.assumeDatabaseAvailable();
        AdminBookingsService service = new AdminBookingsService(new AdminBookingsDaoImpl());

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. All bookings reconciled against independent SQL
                AdminBookingsPage allPage = service.searchBookings(adminUser(), "", "ALL", 1, conn);
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) AS cnt, COALESCE(SUM(total_amount), 0) AS total_sum FROM bookings");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(rs.getLong("cnt"), allPage.getTotalCount());
                    assertEquals(0, rs.getBigDecimal("total_sum").compareTo(allPage.getTotalAmountSum()));
                }

                // 2. Wildcard injection '%' must NOT return all bookings
                AdminBookingsPage wildcardPage = service.searchBookings(adminUser(), "%", "ALL", 1, conn);
                assertEquals(0L, wildcardPage.getTotalCount(), "Escaped '%' search must not act as wildcard");

                // 3. Server-side search by known email 'demo.traveller@example.com'
                AdminBookingsPage demoPage = service.searchBookings(adminUser(), "demo.traveller", "CONFIRMED", 1, conn);
                assertFalse(demoPage.getRows().isEmpty());
                assertTrue(demoPage.getRows().stream().allMatch(r -> "CONFIRMED".equals(r.status())));
            } finally {
                conn.rollback();
            }
        }
    }
}
