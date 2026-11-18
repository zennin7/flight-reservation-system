package com.group2.flightres.service;

import com.group2.flightres.dao.DashboardStatsDao;
import com.group2.flightres.model.DashboardStats;
import com.group2.flightres.model.DashboardStats.DailyBookingStat;
import com.group2.flightres.model.DashboardStats.DestinationStat;
import com.group2.flightres.model.SeatClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardStatsTest {

    private static class MockDashboardStatsDao implements DashboardStatsDao {
        @Override
        public DashboardStats getDashboardStats() {
            DashboardStats stats = new DashboardStats();
            stats.setTotalBookings(312);
            stats.setTotalRevenue(new BigDecimal("458200"));
            stats.setTotalFlights(24);
            stats.setSeatsSoldPercentage(86.5);

            LocalDate today = LocalDate.now();
            stats.setBookingsPerDay(List.of(
                    new DailyBookingStat(today.minusDays(6), "Day 1", 30),
                    new DailyBookingStat(today.minusDays(5), "Day 2", 45),
                    new DailyBookingStat(today.minusDays(4), "Day 3", 40),
                    new DailyBookingStat(today.minusDays(3), "Day 4", 55),
                    new DailyBookingStat(today.minusDays(2), "Day 5", 60),
                    new DailyBookingStat(today.minusDays(1), "Day 6", 50),
                    new DailyBookingStat(today, "Today", 32)
            ));

            stats.setBookingsByClass(Map.of(
                    SeatClass.FIRST, 52L,
                    SeatClass.ECONOMY, 260L
            ));

            stats.setTopDestinations(List.of(
                    new DestinationStat("Cape Town", "CPT", 180),
                    new DestinationStat("Johannesburg", "JNB", 132)
            ));

            return stats;
        }
    }

    @Test
    @DisplayName("Dashboard stats service returns calculated aggregates properly")
    void testDashboardStatsRetrieval() {
        DashboardStatsService service = new DashboardStatsService(new MockDashboardStatsDao());
        DashboardStats stats = service.getStats();

        assertNotNull(stats);
        assertEquals(312, stats.getTotalBookings());
        assertEquals(new BigDecimal("458200"), stats.getTotalRevenue());
        assertEquals(24, stats.getTotalFlights());
        assertEquals(86.5, stats.getSeatsSoldPercentage());

        // 7 days trend
        assertEquals(7, stats.getBookingsPerDay().size());
        assertEquals(32, stats.getBookingsPerDay().get(6).getCount());

        // Bookings by class
        assertEquals(52L, stats.getBookingsByClass().get(SeatClass.FIRST));
        assertEquals(260L, stats.getBookingsByClass().get(SeatClass.ECONOMY));

        // Top destinations
        assertEquals(2, stats.getTopDestinations().size());
        assertEquals("Cape Town", stats.getTopDestinations().get(0).getCity());
    }

    @Test
    @DisplayName("Empty stats return valid default zero values")
    void testEmptyDashboardStats() {
        DashboardStatsService service = new DashboardStatsService(DashboardStats::new);
        DashboardStats stats = service.getStats();

        assertNotNull(stats);
        assertEquals(0, stats.getTotalBookings());
        assertEquals(BigDecimal.ZERO, stats.getTotalRevenue());
        assertEquals(0, stats.getTotalFlights());
        assertEquals(0.0, stats.getSeatsSoldPercentage());
        assertTrue(stats.getBookingsPerDay().isEmpty());
    }
}
