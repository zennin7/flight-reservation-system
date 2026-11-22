// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.impl.AdminDashboardDaoImpl;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.AdminDashboardData.Range;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDashboardServiceTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private static User adminUser() {
        User u = new User();
        u.setUserId(1);
        u.setEmail("admin@example.com");
        u.setFullName("David Admin");
        u.setRole(UserRole.ADMIN);
        return u;
    }

    private static User customerUser() {
        User u = new User();
        u.setUserId(2);
        u.setEmail("traveller@example.com");
        u.setFullName("Amahle Mokoena");
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    @Test
    @DisplayName("RBAC: non-ADMIN and null users are rejected with SecurityException")
    void testRbacRejectsNonAdmin() {
        AdminDashboardService service = new AdminDashboardService((c, r, p) -> {
            throw new AssertionError("DAO must not be called for non-admin");
        }, Clock.systemUTC());

        assertThrows(SecurityException.class, () -> service.getDashboardData(customerUser(), Range.SEVEN_DAYS));
        assertThrows(SecurityException.class, () -> service.getDashboardData(null, Range.TODAY));
    }

    @Test
    @DisplayName("LOW_FREE_PERCENT=20, delta null on 0 previous, and BoardingPolicy status derivation")
    void testPoliciesAndConstants() {
        assertEquals(20, AdminDashboardService.LOW_FREE_PERCENT);
        assertNull(AdminDashboardDaoImpl.computeDeltaPercent(10.0, 0.0), "Zero previous period must return null, not Infinity/NaN");
        assertEquals(50.0, AdminDashboardDaoImpl.computeDeltaPercent(15.0, 10.0), 0.001);

        Instant now = Instant.parse("2026-11-17T08:00:00Z");
        assertEquals("Scheduled", FlightLoadFactor.deriveStatus(now.plusSeconds(3600), now));
        assertEquals("Boarding", FlightLoadFactor.deriveStatus(now.plusSeconds(1800), now));
        assertEquals("Departed", FlightLoadFactor.deriveStatus(now, now));

        AdminDashboardService service = new AdminDashboardService();
        FlightLoadFactor low = new FlightLoadFactor(1, "SR 100", "JNB", "CPT", now.plusSeconds(7200), SAST, 81, 100, 19, 81.0, "Scheduled");
        FlightLoadFactor ok = new FlightLoadFactor(2, "SR 101", "JNB", "CPT", now.plusSeconds(7200), SAST, 80, 100, 20, 80.0, "Scheduled");
        List<FlightLoadFactor> filtered = service.filterLowFreeFlights(List.of(low, ok));
        assertEquals(1, filtered.size());
        assertEquals("SR 100", filtered.get(0).flightNumber());
    }

    @Test
    @DisplayName("DB Integration (rolled back): reconciles KPIs, seats sold, cancellations and upcoming flights against independent SQL")
    void testDatabaseReconciliationInRolledBackTransaction() throws Exception {
        Clock clock = Clock.systemUTC();
        AdminDashboardService service = new AdminDashboardService(new AdminDashboardDaoImpl(), clock);

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                AdminDashboardData data = service.getDashboardData(adminUser(), Range.THIRTY_DAYS, conn);
                assertNotNull(data);
                assertEquals(30, data.getDailySeries().size());

                LocalDate todaySast = LocalDate.now(clock.withZone(SAST));
                Instant curStart = todaySast.minusDays(29).atStartOfDay(SAST).toInstant();
                Instant curEnd = todaySast.plusDays(1).atStartOfDay(SAST).toInstant();
                Instant cutoff = clock.instant().plus(FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE);

                long expectedConfirmed = 0;
                BigDecimal expectedRevenue = BigDecimal.ZERO;
                long expectedCancelled = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FILTER (WHERE status::text='CONFIRMED'), "
                        + "COALESCE(SUM(total_amount) FILTER (WHERE status::text='CONFIRMED'),0), "
                        + "COUNT(*) FILTER (WHERE status::text='CANCELLED') "
                        + "FROM bookings WHERE created_at >= ? AND created_at < ?")) {
                    ps.setTimestamp(1, Timestamp.from(curStart));
                    ps.setTimestamp(2, Timestamp.from(curEnd));
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        expectedConfirmed = rs.getLong(1);
                        expectedRevenue = rs.getBigDecimal(2);
                        expectedCancelled = rs.getLong(3);
                    }
                }
                assertEquals(expectedConfirmed, data.getTotalBookings());
                assertEquals(0, expectedRevenue.compareTo(data.getTotalRevenue()));
                assertEquals(expectedCancelled, data.getCancellationsCount());

                long expectedSeatsSold = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM booking_seats bs JOIN bookings b ON b.booking_id=bs.booking_id "
                        + "WHERE b.status::text='CONFIRMED' AND b.created_at >= ? AND b.created_at < ?")) {
                    ps.setTimestamp(1, Timestamp.from(curStart));
                    ps.setTimestamp(2, Timestamp.from(curEnd));
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        expectedSeatsSold = rs.getLong(1);
                    }
                }
                assertEquals(expectedSeatsSold, data.getSeatsSold());

                long expectedUpcoming = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM flights WHERE departure_time > ?")) {
                    ps.setTimestamp(1, Timestamp.from(cutoff));
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        expectedUpcoming = rs.getLong(1);
                    }
                }
                assertEquals(expectedUpcoming, data.getUpcomingFlights().size());

                AdminDashboardData todayData = service.getDashboardData(adminUser(), Range.TODAY, conn);
                assertEquals(1, todayData.getDailySeries().size());
                assertFalse(data.getDailySeries().isEmpty());
            } finally {
                conn.rollback();
            }
        }
    }
}
