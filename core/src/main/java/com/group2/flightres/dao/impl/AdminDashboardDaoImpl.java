// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.AdminDashboardDao;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.DailyBookingsPoint;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.AdminDashboardData.RouteRevenuePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PostgreSQL DAO for Admin Dashboard aggregates using parameterized PreparedStatements.
 */
public class AdminDashboardDaoImpl implements AdminDashboardDao {

    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardDaoImpl.class);
    public static final ZoneId SA_ZONE = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private static final String KPI_QUERY = """
        SELECT
            (SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED') AS bookings_count,
            (SELECT COALESCE(SUM(total_amount), 0) FROM bookings WHERE status = 'CONFIRMED') AS total_revenue,
            (SELECT COUNT(bs.booking_seat_id)
             FROM booking_seats bs
             JOIN bookings b ON b.booking_id = bs.booking_id
             WHERE b.status = 'CONFIRMED') AS tickets_sold
        """;

    private static final String BOOKINGS_PER_DAY_14_SAST_QUERY = """
        SELECT (created_at AT TIME ZONE 'Africa/Johannesburg')::date AS sast_date,
               COUNT(*) AS day_count
        FROM bookings
        WHERE status = 'CONFIRMED'
          AND created_at >= ?
        GROUP BY sast_date
        ORDER BY sast_date ASC
        """;

    private static final String REVENUE_PER_ROUTE_QUERY = """
        SELECT CONCAT(orig.iata_code, ' -> ', dest.iata_code) AS route_code,
               COALESCE(SUM(b.total_amount), 0) AS route_revenue,
               COUNT(b.booking_id) AS route_bookings
        FROM bookings b
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN airports orig ON orig.airport_id = f.origin_airport_id
        JOIN airports dest ON dest.airport_id = f.dest_airport_id
        WHERE b.status = 'CONFIRMED'
        GROUP BY orig.iata_code, dest.iata_code
        ORDER BY route_revenue DESC, route_code ASC
        """;

    private static final String UPCOMING_FLIGHTS_LOAD_QUERY = """
        SELECT f.flight_id, f.flight_number,
               CONCAT(orig.iata_code, ' -> ', dest.iata_code) AS route_code,
               orig.timezone AS origin_tz,
               f.departure_time,
               COUNT(fs.flight_seat_id) AS total_seats,
               COUNT(fs.flight_seat_id) FILTER (WHERE fs.status = 'BOOKED') AS booked_seats,
               COUNT(fs.flight_seat_id) FILTER (
                   WHERE fs.status = 'AVAILABLE'
                      OR (fs.status = 'HELD' AND fs.held_until <= ?)
               ) AS available_seats
        FROM flights f
        JOIN airports orig ON orig.airport_id = f.origin_airport_id
        JOIN airports dest ON dest.airport_id = f.dest_airport_id
        LEFT JOIN flight_seats fs ON fs.flight_id = f.flight_id
        WHERE f.status = 'SCHEDULED'
          AND f.departure_time > ?
        GROUP BY f.flight_id, f.flight_number, orig.iata_code, dest.iata_code, orig.timezone, f.departure_time
        ORDER BY f.departure_time ASC
        """;

    @Override
    public AdminDashboardData loadDashboardData(Clock clock, int lowSeatThreshold) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            return loadDashboardData(conn, clock, lowSeatThreshold);
        } catch (SQLException e) {
            logger.error("Failed to load admin dashboard data: {}", e.getMessage(), e);
            throw new IllegalStateException("Unable to load admin dashboard metrics", e);
        }
    }

    @Override
    public AdminDashboardData loadDashboardData(Connection conn, Clock clock, int lowSeatThreshold) throws SQLException {
        Clock refClock = clock != null ? clock : Clock.systemUTC();

        long bookingsCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long ticketsSold = 0;

        try (PreparedStatement stmt = conn.prepareStatement(KPI_QUERY);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                bookingsCount = rs.getLong("bookings_count");
                totalRevenue = rs.getBigDecimal("total_revenue");
                ticketsSold = rs.getLong("tickets_sold");
            }
        }

        List<DailyBookingsPoint> dailyPoints = queryBookingsPerDay14(conn, refClock);
        List<RouteRevenuePoint> routePoints = queryRevenuePerRoute(conn);
        List<FlightLoadFactor> upcoming = queryUpcomingFlights(conn, refClock);
        List<FlightLoadFactor> lowSeatAlerts = upcoming.stream()
                .filter(f -> f.availableSeats() < lowSeatThreshold)
                .toList();

        return new AdminDashboardData(
                totalRevenue,
                bookingsCount,
                ticketsSold,
                dailyPoints,
                routePoints,
                upcoming,
                lowSeatAlerts
        );
    }

    private List<DailyBookingsPoint> queryBookingsPerDay14(Connection conn, Clock clock) throws SQLException {
        LocalDate todaySast = LocalDate.now(clock.withZone(SA_ZONE));
        LocalDate startSast = todaySast.minusDays(13);
        Instant startInstant = startSast.atStartOfDay(SA_ZONE).toInstant();

        Map<LocalDate, Long> countsByDate = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(BOOKINGS_PER_DAY_14_SAST_QUERY)) {
            stmt.setTimestamp(1, Timestamp.from(startInstant));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("sast_date");
                    if (d != null) {
                        countsByDate.put(d.toLocalDate(), rs.getLong("day_count"));
                    }
                }
            }
        }

        List<DailyBookingsPoint> list = new ArrayList<>(14);
        for (int i = 13; i >= 0; i--) {
            LocalDate date = todaySast.minusDays(i);
            long count = countsByDate.getOrDefault(date, 0L);
            list.add(new DailyBookingsPoint(date, date.format(DAY_LABEL_FORMAT), count));
        }
        return list;
    }

    private List<RouteRevenuePoint> queryRevenuePerRoute(Connection conn) throws SQLException {
        List<RouteRevenuePoint> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(REVENUE_PER_ROUTE_QUERY);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new RouteRevenuePoint(
                        rs.getString("route_code"),
                        rs.getBigDecimal("route_revenue"),
                        rs.getLong("route_bookings")
                ));
            }
        }
        return list;
    }

    private List<FlightLoadFactor> queryUpcomingFlights(Connection conn, Clock clock) throws SQLException {
        Timestamp nowTs = Timestamp.from(clock.instant());
        List<FlightLoadFactor> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(UPCOMING_FLIGHTS_LOAD_QUERY)) {
            stmt.setTimestamp(1, nowTs);
            stmt.setTimestamp(2, nowTs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int totalSeats = rs.getInt("total_seats");
                    int bookedSeats = rs.getInt("booked_seats");
                    int availableSeats = rs.getInt("available_seats");
                    double loadFactorPct = totalSeats > 0 ? (bookedSeats * 100.0) / totalSeats : 0.0;
                    String tz = rs.getString("origin_tz");
                    ZoneId zone = (tz != null && !tz.isBlank()) ? ZoneId.of(tz) : SA_ZONE;
                    Timestamp depTs = rs.getTimestamp("departure_time");
                    Instant depInstant = depTs != null ? depTs.toInstant() : clock.instant();

                    list.add(new FlightLoadFactor(
                            rs.getLong("flight_id"),
                            rs.getString("flight_number"),
                            rs.getString("route_code"),
                            depInstant,
                            zone,
                            bookedSeats,
                            totalSeats,
                            availableSeats,
                            loadFactorPct
                    ));
                }
            }
        }
        return list;
    }
}
