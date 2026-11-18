// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.AdminDashboardDao;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.CabinLoadBreakdown;
import com.group2.flightres.model.AdminDashboardData.DailySeriesPoint;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.AdminDashboardData.Range;
import com.group2.flightres.model.AdminDashboardData.RecentBookingItem;
import com.group2.flightres.model.AdminDashboardData.TopDestinationItem;
import com.group2.flightres.service.FlightSalesPolicy;
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
    private static final DateTimeFormatter SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    private static final String PERIOD_KPI_QUERY = """
        SELECT
            COUNT(*) FILTER (WHERE b.status::text = 'CONFIRMED') AS bookings_count,
            COALESCE(SUM(b.total_amount) FILTER (WHERE b.status::text = 'CONFIRMED'), 0) AS total_revenue,
            COUNT(*) FILTER (WHERE b.status::text = 'CANCELLED') AS cancellations_count,
            COALESCE((
                SELECT COUNT(*)
                FROM booking_seats bs
                JOIN bookings b2 ON b2.booking_id = bs.booking_id
                WHERE b2.status::text = 'CONFIRMED'
                  AND b2.created_at >= ?
                  AND b2.created_at < ?
            ), 0) AS seats_sold
        FROM bookings b
        WHERE b.created_at >= ?
          AND b.created_at < ?
        """;

    private static final String DAILY_SERIES_QUERY = """
        SELECT (b.created_at AT TIME ZONE 'Africa/Johannesburg')::date AS sast_date,
               COUNT(*) FILTER (WHERE b.status::text = 'CONFIRMED') AS bookings_count,
               COALESCE(SUM(b.total_amount) FILTER (WHERE b.status::text = 'CONFIRMED'), 0) AS revenue,
               COUNT(*) FILTER (WHERE b.status::text = 'CANCELLED') AS cancellations_count,
               COALESCE(SUM(CASE WHEN b.status::text = 'CONFIRMED' THEN (
                   SELECT COUNT(*) FROM booking_seats bs WHERE bs.booking_id = b.booking_id
               ) ELSE 0 END), 0) AS seats_sold
        FROM bookings b
        WHERE b.created_at >= ?
          AND b.created_at < ?
        GROUP BY sast_date
        ORDER BY sast_date ASC
        """;

    private static final String CABIN_LOAD_QUERY = """
        SELECT
            COUNT(fs.flight_seat_id) FILTER (WHERE st.seat_class::text = 'ECONOMY' AND fs.status::text = 'BOOKED') AS economy_booked,
            COUNT(fs.flight_seat_id) FILTER (WHERE st.seat_class::text = 'FIRST' AND fs.status::text = 'BOOKED') AS first_booked,
            COUNT(fs.flight_seat_id) FILTER (WHERE fs.status::text = 'AVAILABLE') AS open_seats,
            COUNT(fs.flight_seat_id) AS total_seats,
            COUNT(fs.flight_seat_id) FILTER (WHERE fs.status::text = 'HELD' AND fs.held_until > ?) AS held_now
        FROM flight_seats fs
        JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
        JOIN flights f ON f.flight_id = fs.flight_id
        WHERE f.departure_time > ?
        """;

    private static final String UPCOMING_FLIGHTS_QUERY = """
        SELECT f.flight_id, f.flight_number,
               o.iata_code AS origin_code, o.timezone AS origin_tz,
               d.iata_code AS dest_code, f.departure_time,
               COUNT(fs.flight_seat_id) AS total_seats,
               COUNT(fs.flight_seat_id) FILTER (WHERE fs.status::text = 'BOOKED') AS booked_seats,
               COUNT(fs.flight_seat_id) FILTER (WHERE fs.status::text = 'AVAILABLE') AS available_seats
        FROM flights f
        JOIN airports o ON o.airport_id = f.origin_airport_id
        JOIN airports d ON d.airport_id = f.dest_airport_id
        LEFT JOIN flight_seats fs ON fs.flight_id = f.flight_id
        WHERE f.departure_time > ?
        GROUP BY f.flight_id, f.flight_number, o.iata_code, o.timezone, d.iata_code, f.departure_time
        ORDER BY f.departure_time ASC, f.flight_id ASC
        """;

    private static final String TODAY_FLIGHTS_QUERY = """
        SELECT f.flight_id, f.flight_number,
               o.iata_code AS origin_code, o.timezone AS origin_tz,
               d.iata_code AS dest_code, f.departure_time,
               COUNT(fs.flight_seat_id) AS total_seats,
               COUNT(fs.flight_seat_id) FILTER (WHERE fs.status::text = 'BOOKED') AS booked_seats,
               COUNT(fs.flight_seat_id) FILTER (WHERE fs.status::text = 'AVAILABLE') AS available_seats
        FROM flights f
        JOIN airports o ON o.airport_id = f.origin_airport_id
        JOIN airports d ON d.airport_id = f.dest_airport_id
        LEFT JOIN flight_seats fs ON fs.flight_id = f.flight_id
        WHERE f.departure_time >= ?
          AND f.departure_time < ?
        GROUP BY f.flight_id, f.flight_number, o.iata_code, o.timezone, d.iata_code, f.departure_time
        ORDER BY f.departure_time ASC, f.flight_id ASC
        """;

    private static final String TOP_DESTINATIONS_QUERY = """
        SELECT d.iata_code AS dest_code, d.city AS dest_city, COUNT(*) AS bookings_count
        FROM bookings b
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN airports d ON d.airport_id = f.dest_airport_id
        WHERE b.status::text = 'CONFIRMED'
          AND b.created_at >= ?
          AND b.created_at < ?
        GROUP BY d.iata_code, d.city
        ORDER BY bookings_count DESC, d.iata_code ASC
        LIMIT 3
        """;

    private static final String RECENT_BOOKINGS_QUERY = """
        SELECT b.booking_ref,
               COALESCE((SELECT MIN(bs2.passenger_name) FROM booking_seats bs2 WHERE bs2.booking_id = b.booking_id),
                        u.full_name, u.username, 'Guest') AS passenger_name,
               o.iata_code || ' -> ' || d.iata_code AS route,
               COALESCE((
                   SELECT string_agg(concat(st.seat_row, st.seat_col), ', ' ORDER BY st.seat_row, st.seat_col)
                   FROM booking_seats bs
                   JOIN flight_seats fs ON fs.flight_seat_id = bs.flight_seat_id
                   JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
                   WHERE bs.booking_id = b.booking_id
               ), '') AS seat_labels,
               b.total_amount
        FROM bookings b
        JOIN users u ON u.user_id = b.user_id
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN airports o ON o.airport_id = f.origin_airport_id
        JOIN airports d ON d.airport_id = f.dest_airport_id
        WHERE b.created_at >= ?
          AND b.created_at < ?
        ORDER BY b.created_at DESC, b.booking_id DESC
        LIMIT 4
        """;

    private record PeriodMetrics(long bookings, BigDecimal revenue, long cancellations, long seatsSold) {}

    @Override
    public AdminDashboardData loadDashboardData(Clock clock, Range range, int lowFreePercent) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            return loadDashboardData(conn, clock, range, lowFreePercent);
        } catch (SQLException e) {
            logger.error("Failed to load admin dashboard analytics", e);
            throw new RuntimeException("Failed to load admin dashboard analytics", e);
        }
    }

    @Override
    public AdminDashboardData loadDashboardData(Connection conn, Clock clock, Range range, int lowFreePercent) throws SQLException {
        Range effectiveRange = range != null ? range : Range.SEVEN_DAYS;
        Clock effectiveClock = clock != null ? clock : Clock.systemUTC();
        Instant now = effectiveClock.instant();
        LocalDate todaySast = LocalDate.now(effectiveClock.withZone(SA_ZONE));

        int days = effectiveRange.getDays();
        LocalDate curStartDate = todaySast.minusDays(days - 1L);
        LocalDate curEndDateExclusive = todaySast.plusDays(1L);
        LocalDate prevStartDate = curStartDate.minusDays(days);

        Instant curStart = curStartDate.atStartOfDay(SA_ZONE).toInstant();
        Instant curEnd = curEndDateExclusive.atStartOfDay(SA_ZONE).toInstant();
        Instant prevStart = prevStartDate.atStartOfDay(SA_ZONE).toInstant();
        Instant salesCutoff = now.plus(FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE);
        Instant todayStart = todaySast.atStartOfDay(SA_ZONE).toInstant();
        Instant tomorrowStart = todaySast.plusDays(1L).atStartOfDay(SA_ZONE).toInstant();

        PeriodMetrics current = queryPeriodMetrics(conn, curStart, curEnd);
        PeriodMetrics previous = queryPeriodMetrics(conn, prevStart, curStart);

        List<DailySeriesPoint> dailySeries = queryDailySeries(conn, curStartDate, todaySast, curStart, curEnd, effectiveRange);
        List<FlightLoadFactor> upcoming = queryFlights(conn, UPCOMING_FLIGHTS_QUERY, salesCutoff, null, now);
        List<FlightLoadFactor> todayFlights = queryFlights(conn, TODAY_FLIGHTS_QUERY, todayStart, tomorrowStart, now);

        long lowFreeCount = upcoming.stream()
                .filter(f -> f.totalSeats() > 0 && (f.availableSeats() * 100.0 / f.totalSeats()) < lowFreePercent)
                .count();
        CabinLoadBreakdown cabinLoad = queryCabinLoad(conn, now, salesCutoff, lowFreeCount);
        List<TopDestinationItem> topDestinations = queryTopDestinations(conn, curStart, curEnd);
        List<RecentBookingItem> recentBookings = queryRecentBookings(conn, curStart, curEnd);

        return new AdminDashboardData(
                effectiveRange,
                current.bookings(),
                computeDeltaPercent(current.bookings(), previous.bookings()),
                current.revenue(),
                computeDeltaPercent(current.revenue().doubleValue(), previous.revenue().doubleValue()),
                current.seatsSold(),
                computeDeltaPercent(current.seatsSold(), previous.seatsSold()),
                current.cancellations(),
                computeDeltaPercent(current.cancellations(), previous.cancellations()),
                dailySeries,
                cabinLoad,
                todayFlights,
                upcoming,
                topDestinations,
                recentBookings
        );
    }

    public static Double computeDeltaPercent(double current, double previous) {
        if (previous <= 0.0) {
            return null;
        }
        return ((current - previous) / previous) * 100.0;
    }

    private PeriodMetrics queryPeriodMetrics(Connection conn, Instant start, Instant end) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(PERIOD_KPI_QUERY)) {
            Timestamp tsStart = Timestamp.from(start);
            Timestamp tsEnd = Timestamp.from(end);
            ps.setTimestamp(1, tsStart);
            ps.setTimestamp(2, tsEnd);
            ps.setTimestamp(3, tsStart);
            ps.setTimestamp(4, tsEnd);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PeriodMetrics(
                            rs.getLong("bookings_count"),
                            rs.getBigDecimal("total_revenue"),
                            rs.getLong("cancellations_count"),
                            rs.getLong("seats_sold")
                    );
                }
            }
        }
        return new PeriodMetrics(0L, BigDecimal.ZERO, 0L, 0L);
    }

    private List<DailySeriesPoint> queryDailySeries(
            Connection conn, LocalDate startDate, LocalDate endDate, Instant start, Instant end, Range range) throws SQLException {
        Map<LocalDate, DailySeriesPoint> byDate = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(DAILY_SERIES_QUERY)) {
            ps.setTimestamp(1, Timestamp.from(start));
            ps.setTimestamp(2, Timestamp.from(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("sast_date");
                    if (d != null) {
                        LocalDate ld = d.toLocalDate();
                        String lbl = range == Range.SEVEN_DAYS ? ld.format(SHORT_DAY_FORMAT) : ld.format(DAY_LABEL_FORMAT);
                        byDate.put(ld, new DailySeriesPoint(
                                ld,
                                lbl,
                                rs.getLong("bookings_count"),
                                rs.getBigDecimal("revenue"),
                                rs.getLong("seats_sold"),
                                rs.getLong("cancellations_count")
                        ));
                    }
                }
            }
        }
        List<DailySeriesPoint> series = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            DailySeriesPoint existing = byDate.get(d);
            if (existing != null) {
                series.add(existing);
            } else {
                String lbl = range == Range.SEVEN_DAYS ? d.format(SHORT_DAY_FORMAT) : d.format(DAY_LABEL_FORMAT);
                series.add(new DailySeriesPoint(d, lbl, 0L, BigDecimal.ZERO, 0L, 0L));
            }
        }
        return series;
    }

    private CabinLoadBreakdown queryCabinLoad(Connection conn, Instant now, Instant salesCutoff, long lowFreeCount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CABIN_LOAD_QUERY)) {
            ps.setTimestamp(1, Timestamp.from(now));
            ps.setTimestamp(2, Timestamp.from(salesCutoff));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CabinLoadBreakdown(
                            rs.getLong("economy_booked"),
                            rs.getLong("first_booked"),
                            rs.getLong("open_seats"),
                            rs.getLong("total_seats"),
                            rs.getLong("held_now"),
                            lowFreeCount
                    );
                }
            }
        }
        return new CabinLoadBreakdown(0, 0, 0, 0, 0, lowFreeCount);
    }

    private List<FlightLoadFactor> queryFlights(
            Connection conn, String sql, Instant firstParam, Instant secondParam, Instant now) throws SQLException {
        List<FlightLoadFactor> flights = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(firstParam));
            if (secondParam != null) {
                ps.setTimestamp(2, Timestamp.from(secondParam));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String origin = rs.getString("origin_code");
                    String dest = rs.getString("dest_code");
                    String tz = rs.getString("origin_tz");
                    ZoneId originZone = tz != null && !tz.isBlank() ? ZoneId.of(tz) : SA_ZONE;
                    Timestamp depTs = rs.getTimestamp("departure_time");
                    Instant depInstant = depTs != null ? depTs.toInstant() : Instant.EPOCH;
                    int total = rs.getInt("total_seats");
                    int booked = rs.getInt("booked_seats");
                    int avail = rs.getInt("available_seats");
                    double pct = total > 0 ? (booked * 100.0) / total : 0.0;
                    flights.add(new FlightLoadFactor(
                            rs.getLong("flight_id"),
                            rs.getString("flight_number"),
                            origin,
                            dest,
                            depInstant,
                            originZone,
                            booked,
                            total,
                            avail,
                            pct,
                            FlightLoadFactor.deriveStatus(depInstant, now)
                    ));
                }
            }
        }
        return flights;
    }

    private List<TopDestinationItem> queryTopDestinations(Connection conn, Instant start, Instant end) throws SQLException {
        List<TopDestinationItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(TOP_DESTINATIONS_QUERY)) {
            ps.setTimestamp(1, Timestamp.from(start));
            ps.setTimestamp(2, Timestamp.from(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("dest_code");
                    String city = rs.getString("dest_city");
                    items.add(new TopDestinationItem(
                            code,
                            city != null && !city.isBlank() ? city : TopDestinationItem.resolveCityName(code),
                            rs.getLong("bookings_count")
                    ));
                }
            }
        }
        return items;
    }

    private List<RecentBookingItem> queryRecentBookings(Connection conn, Instant start, Instant end) throws SQLException {
        List<RecentBookingItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(RECENT_BOOKINGS_QUERY)) {
            ps.setTimestamp(1, Timestamp.from(start));
            ps.setTimestamp(2, Timestamp.from(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new RecentBookingItem(
                            rs.getString("booking_ref"),
                            rs.getString("passenger_name"),
                            rs.getString("route"),
                            rs.getString("seat_labels"),
                            rs.getBigDecimal("total_amount")
                    ));
                }
            }
        }
        return items;
    }
}
