package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.DashboardStatsDao;
import com.group2.flightres.model.DashboardStats;
import com.group2.flightres.model.DashboardStats.DailyBookingStat;
import com.group2.flightres.model.DashboardStats.DestinationStat;
import com.group2.flightres.model.SeatClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardStatsDaoImpl implements DashboardStatsDao {

    private static final Logger logger = LoggerFactory.getLogger(DashboardStatsDaoImpl.class);
    private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH);

    private static final String TOTAL_BOOKINGS_REVENUE_QUERY = """
        SELECT COUNT(*) AS total_bookings, COALESCE(SUM(total_amount), 0) AS total_revenue
        FROM bookings
        WHERE status = 'CONFIRMED'
        """;

    private static final String TOTAL_FLIGHTS_QUERY = """
        SELECT COUNT(*) AS total_flights FROM flights
        """;

    private static final String SEATS_SOLD_PCT_QUERY = """
        SELECT
            COUNT(*) AS total_seats,
            COUNT(*) FILTER (WHERE status = 'BOOKED') AS booked_seats,
            ROUND(
                (COUNT(*) FILTER (WHERE status = 'BOOKED')::NUMERIC / NULLIF(COUNT(*), 0)::NUMERIC) * 100,
                1
            ) AS seats_sold_pct
        FROM flight_seats
        """;

    private static final String BOOKINGS_PER_DAY_QUERY = """
        SELECT CAST(created_at AS DATE) AS b_date, COUNT(*) AS b_count
        FROM bookings
        WHERE status = 'CONFIRMED'
          AND created_at >= CURRENT_DATE - INTERVAL '6 days'
        GROUP BY CAST(created_at AS DATE)
        ORDER BY b_date ASC
        """;

    private static final String BOOKINGS_BY_CLASS_QUERY = """
        SELECT st.seat_class, COUNT(bs.booking_seat_id) AS class_count
        FROM booking_seats bs
        JOIN flight_seats fs ON fs.flight_seat_id = bs.flight_seat_id
        JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
        JOIN bookings b ON b.booking_id = bs.booking_id
        WHERE b.status = 'CONFIRMED'
        GROUP BY st.seat_class
        """;

    private static final String TOP_DESTINATIONS_QUERY = """
        SELECT a.city, a.iata_code, COUNT(b.booking_id) AS dest_count
        FROM bookings b
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN airports a ON a.airport_id = f.dest_airport_id
        WHERE b.status = 'CONFIRMED'
        GROUP BY a.city, a.iata_code
        ORDER BY dest_count DESC
        LIMIT 5
        """;

    @Override
    public DashboardStats getDashboardStats() {
        DashboardStats stats = new DashboardStats();

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {

            // 1. Total Bookings and Revenue
            try (PreparedStatement stmt = conn.prepareStatement(TOTAL_BOOKINGS_REVENUE_QUERY);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.setTotalBookings(rs.getLong("total_bookings"));
                    stats.setTotalRevenue(rs.getBigDecimal("total_revenue"));
                }
            }

            // 2. Total Flights
            try (PreparedStatement stmt = conn.prepareStatement(TOTAL_FLIGHTS_QUERY);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.setTotalFlights(rs.getLong("total_flights"));
                }
            }

            // 3. Seats Sold Percentage
            try (PreparedStatement stmt = conn.prepareStatement(SEATS_SOLD_PCT_QUERY);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal pct = rs.getBigDecimal("seats_sold_pct");
                    stats.setSeatsSoldPercentage(pct != null ? pct.doubleValue() : 0.0);
                }
            }

            // 4. Bookings per day (last 7 days continuous series)
            Map<LocalDate, Long> dayCounts = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(BOOKINGS_PER_DAY_QUERY);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("b_date");
                    if (d != null) {
                        dayCounts.put(d.toLocalDate(), rs.getLong("b_count"));
                    }
                }
            }

            LocalDate today = LocalDate.now();
            List<DailyBookingStat> dailyList = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                long count = dayCounts.getOrDefault(date, 0L);
                String label = date.format(DAY_LABEL_FORMAT);
                dailyList.add(new DailyBookingStat(date, label, count));
            }
            stats.setBookingsPerDay(dailyList);

            // 5. Bookings by seat class
            Map<SeatClass, Long> classMap = new LinkedHashMap<>();
            classMap.put(SeatClass.FIRST, 0L);
            classMap.put(SeatClass.ECONOMY, 0L);
            try (PreparedStatement stmt = conn.prepareStatement(BOOKINGS_BY_CLASS_QUERY);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String clsStr = rs.getString("seat_class");
                    if (clsStr != null) {
                        SeatClass sc = SeatClass.valueOf(clsStr);
                        classMap.put(sc, rs.getLong("class_count"));
                    }
                }
            }
            stats.setBookingsByClass(classMap);

            // 6. Top destinations
            List<DestinationStat> topDests = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(TOP_DESTINATIONS_QUERY);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String city = rs.getString("city");
                    String iata = rs.getString("iata_code");
                    long count = rs.getLong("dest_count");
                    topDests.add(new DestinationStat(city, iata, count));
                }
            }
            stats.setTopDestinations(topDests);

            logger.info("Loaded dashboard stats: totalBookings={}, revenue={}, flights={}, seatsSold={}%",
                    stats.getTotalBookings(), stats.getTotalRevenue(), stats.getTotalFlights(), stats.getSeatsSoldPercentage());

        } catch (SQLException e) {
            logger.error("Failed to query dashboard statistics from database: {}", e.getMessage(), e);
        }

        return stats;
    }
}
