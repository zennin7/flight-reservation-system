// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.impl.AdminDashboardDaoImpl;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDashboardServiceTest {

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
        AdminDashboardData stubData = new AdminDashboardData(
                BigDecimal.ZERO, 0L, 0L, List.of(), List.of(), List.of(), List.of()
        );
        AdminDashboardService service = new AdminDashboardService((clock, thr) -> stubData, Clock.systemUTC());

        assertThrows(SecurityException.class, () -> service.getDashboardData(null));
        assertThrows(SecurityException.class, () -> service.getDashboardData(customerUser()));

        AdminDashboardData result = service.getDashboardData(adminUser());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Low-seat alert list filters flights strictly below LOW_SEAT_THRESHOLD")
    void testLowSeatThresholdFiltering() {
        AdminDashboardService service = new AdminDashboardService((clock, thr) -> null, Clock.systemUTC());
        Instant now = Instant.parse("2026-11-20T06:00:00Z");
        ZoneId jhb = ZoneId.of("Africa/Johannesburg");

        FlightLoadFactor low = new FlightLoadFactor(1L, "ALT 204", "JNB -> CPT", now, jhb, 150, 162,
                AdminDashboardService.LOW_SEAT_THRESHOLD - 1, 92.6);
        FlightLoadFactor exact = new FlightLoadFactor(2L, "ALT 205", "CPT -> JNB", now, jhb, 142, 162,
                AdminDashboardService.LOW_SEAT_THRESHOLD, 87.7);
        FlightLoadFactor high = new FlightLoadFactor(3L, "ALT 602", "JNB -> DUR", now, jhb, 10, 162,
                152, 6.2);

        List<FlightLoadFactor> alerts = service.filterLowSeatAlerts(List.of(low, exact, high));
        assertEquals(1, alerts.size());
        assertEquals("ALT 204", alerts.get(0).flightNumber());
    }

    @Test
    @DisplayName("DB Integration & SQL Reconciliation inside rolled-back transaction")
    void testDbReconciliationInRolledBackTransaction() throws SQLException {
        DatabaseAssumption.assumeDatabaseAvailable();
        Clock clock = Clock.systemUTC();
        AdminDashboardService service = new AdminDashboardService(new AdminDashboardDaoImpl(), clock);

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Insert synthetic confirmed booking in rolled-back tx? Even simpler: insert a zeroed/cancelled or
                // test synthetic flight + read real metrics, then always rollback!
                AdminDashboardData data = service.getDashboardData(adminUser(), conn);
                assertNotNull(data);
                assertEquals(14, data.getBookingsPerDay().size(), "Must return 14 SAST day buckets");

                // Independent SQL oracle for total revenue, bookings count, tickets sold
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS cnt, COALESCE(SUM(total_amount), 0) AS rev FROM bookings WHERE status = 'CONFIRMED'");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(rs.getLong("cnt"), data.getBookingsCount());
                    assertEquals(0, rs.getBigDecimal("rev").compareTo(data.getTotalRevenue()));
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(bs.booking_seat_id) AS tickets FROM booking_seats bs JOIN bookings b ON b.booking_id = bs.booking_id WHERE b.status = 'CONFIRMED'");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(rs.getLong("tickets"), data.getTicketsSold());
                }

                // Independent SQL oracle for revenue per route sum == totalRevenue
                BigDecimal routeSum = data.getRevenuePerRoute().stream()
                        .map(AdminDashboardData.RouteRevenuePoint::revenue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(0, data.getTotalRevenue().compareTo(routeSum),
                        "Sum of revenuePerRoute must equal totalRevenue");

                // Verify upcoming flights count matches SQL
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS up_cnt FROM flights WHERE status = 'SCHEDULED' AND departure_time > ?")) {
                    stmt.setTimestamp(1, java.sql.Timestamp.from(clock.instant()));
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt("up_cnt"), data.getUpcomingFlights().size());
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
