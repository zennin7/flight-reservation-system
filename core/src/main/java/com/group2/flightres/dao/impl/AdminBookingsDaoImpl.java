// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.AdminBookingsDao;
import com.group2.flightres.model.AdminBookingRow;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.SeatClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PostgreSQL DAO for Admin Bookings using one joined query (no N+1) and escaped LIKE search.
 */
public class AdminBookingsDaoImpl implements AdminBookingsDao {

    private static final Logger logger = LoggerFactory.getLogger(AdminBookingsDaoImpl.class);
    public static final ZoneId SA_ZONE = ZoneId.of("Africa/Johannesburg");

    private static final String FILTER_WHERE = """
        WHERE (? = 'ALL' OR b.status::text = ?)
          AND (? = '' OR b.booking_ref ILIKE ? ESCAPE '\\'
                      OR u.email ILIKE ? ESCAPE '\\'
                      OR u.full_name ILIKE ? ESCAPE '\\'
                      OR EXISTS (
                          SELECT 1 FROM booking_seats bs2
                          WHERE bs2.booking_id = b.booking_id
                            AND bs2.passenger_name ILIKE ? ESCAPE '\\'
                      ))
          AND (CAST(? AS timestamptz) IS NULL OR f.departure_time >= CAST(? AS timestamptz))
          AND (CAST(? AS timestamptz) IS NULL OR f.departure_time < CAST(? AS timestamptz))
          AND (? = 'ALL' OR EXISTS (
              SELECT 1 FROM booking_seats bs3
              JOIN flight_seats fs3 ON fs3.flight_seat_id = bs3.flight_seat_id
              JOIN seat_templates st3 ON st3.seat_template_id = fs3.seat_template_id
              WHERE bs3.booking_id = b.booking_id
                AND st3.seat_class::text = ?
          ))
          AND (CAST(? AS bigint) IS NULL OR f.flight_id = CAST(? AS bigint))
        """;

    private static final String COUNT_AND_SUM_QUERY = """
        SELECT COUNT(*) AS total_count,
               COALESCE(SUM(b.total_amount), 0) AS sum_total
        FROM bookings b
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN users u ON u.user_id = b.user_id
        """ + FILTER_WHERE;

    private static final String SEARCH_JOINED_QUERY = """
        SELECT b.booking_id, b.booking_ref, b.status::text AS booking_status, b.total_amount,
               f.flight_id, f.flight_number, f.departure_time, f.arrival_time,
               o.iata_code AS origin_code, o.city AS origin_city, o.timezone AS origin_tz,
               d.iata_code AS dest_code, d.city AS dest_city, d.timezone AS dest_tz,
               COALESCE(MIN(bs.passenger_name), u.full_name) AS passenger_name,
               u.email AS user_email, u.full_name AS user_full_name,
               COALESCE(string_agg(concat(st.seat_row, st.seat_col), ', ' ORDER BY st.seat_row, st.seat_col), '') AS seat_labels,
               COALESCE(MIN(st.seat_class::text), 'ECONOMY') AS seat_class
        FROM bookings b
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN airports o ON o.airport_id = f.origin_airport_id
        JOIN airports d ON d.airport_id = f.dest_airport_id
        JOIN users u ON u.user_id = b.user_id
        LEFT JOIN booking_seats bs ON bs.booking_id = b.booking_id
        LEFT JOIN flight_seats fs ON fs.flight_seat_id = bs.flight_seat_id
        LEFT JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
        """ + FILTER_WHERE + """
        GROUP BY b.booking_id, b.booking_ref, b.status, b.total_amount,
                 f.flight_id, f.flight_number, f.departure_time, f.arrival_time,
                 o.iata_code, o.city, o.timezone, d.iata_code, d.city, d.timezone,
                 u.email, u.full_name
        ORDER BY b.created_at DESC, b.booking_id DESC
        LIMIT ? OFFSET ?
        """;

    private static final String LIST_FLIGHT_OPTIONS_QUERY = """
        SELECT f.flight_id, f.flight_number, o.iata_code AS origin_code, d.iata_code AS dest_code
        FROM flights f
        JOIN airports o ON o.airport_id = f.origin_airport_id
        JOIN airports d ON d.airport_id = f.dest_airport_id
        ORDER BY f.departure_time DESC, f.flight_id DESC
        """;

    public static String escapeLikeWildcards(String input) {
        if (input == null) return "";
        return input.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Override
    public AdminBookingsPage searchBookings(AdminBookingsFilter filter, int page, int pageSize) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            return searchBookings(conn, filter, page, pageSize);
        } catch (SQLException e) {
            logger.error("Failed to query admin bookings", e);
            throw new RuntimeException("Failed to query admin bookings", e);
        }
    }

    @Override
    public AdminBookingsPage searchBookings(Connection conn, AdminBookingsFilter filter, int page, int pageSize) throws SQLException {
        AdminBookingsFilter f = filter != null ? filter : AdminBookingsFilter.defaults();
        int safePageSize = pageSize > 0 ? pageSize : 25;
        int safePage = Math.max(1, page);

        long totalCount = 0;
        BigDecimal totalRevenueSum = BigDecimal.ZERO;

        try (PreparedStatement ps = conn.prepareStatement(COUNT_AND_SUM_QUERY)) {
            bindFilterParameters(ps, f);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalCount = rs.getLong("total_count");
                    totalRevenueSum = rs.getBigDecimal("sum_total");
                }
            }
        }

        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / safePageSize);
        if (safePage > totalPages) {
            safePage = totalPages;
        }
        int offset = (safePage - 1) * safePageSize;

        List<AdminBookingRow> rows = queryRows(conn, f, safePageSize, offset);
        return new AdminBookingsPage(rows, totalCount, totalRevenueSum, safePage, safePageSize);
    }

    @Override
    public List<AdminBookingRow> findAllMatching(AdminBookingsFilter filter) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            return findAllMatching(conn, filter);
        } catch (SQLException e) {
            logger.error("Failed to export admin bookings", e);
            throw new RuntimeException("Failed to export admin bookings", e);
        }
    }

    @Override
    public List<AdminBookingRow> findAllMatching(Connection conn, AdminBookingsFilter filter) throws SQLException {
        AdminBookingsFilter f = filter != null ? filter : AdminBookingsFilter.defaults();
        return queryRows(conn, f, 10000, 0);
    }

    @Override
    public List<FlightFilterOption> listFlightOptions() {
        List<FlightFilterOption> options = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(LIST_FLIGHT_OPTIONS_QUERY);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("flight_id");
                String label = rs.getString("flight_number") + " ("
                        + rs.getString("origin_code") + " -> " + rs.getString("dest_code") + ")";
                options.add(new FlightFilterOption(id, label));
            }
        } catch (SQLException e) {
            logger.warn("Could not list flight filter options", e);
        }
        return options;
    }

    private List<AdminBookingRow> queryRows(Connection conn, AdminBookingsFilter f, int limit, int offset) throws SQLException {
        List<AdminBookingRow> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SEARCH_JOINED_QUERY)) {
            int idx = bindFilterParameters(ps, f);
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        }
        return rows;
    }

    private int bindFilterParameters(PreparedStatement ps, AdminBookingsFilter f) throws SQLException {
        String normStatus = normalizeFilter(f.statusFilter());
        String normCabin = normalizeFilter(f.cabinFilter());
        String escaped = escapeLikeWildcards(f.searchTerm());
        String likePattern = escaped.isEmpty() ? "" : "%" + escaped + "%";

        Timestamp depStart = null;
        Timestamp depEnd = null;
        String dateFilter = normalizeFilter(f.dateFilter());
        LocalDate todaySast = LocalDate.now(SA_ZONE);
        if ("NEXT_7_DAYS".equals(dateFilter)) {
            depStart = Timestamp.from(todaySast.atStartOfDay(SA_ZONE).toInstant());
            depEnd = Timestamp.from(todaySast.plusDays(8).atStartOfDay(SA_ZONE).toInstant());
        } else if ("NEXT_30_DAYS".equals(dateFilter)) {
            depStart = Timestamp.from(todaySast.atStartOfDay(SA_ZONE).toInstant());
            depEnd = Timestamp.from(todaySast.plusDays(31).atStartOfDay(SA_ZONE).toInstant());
        } else if ("CUSTOM".equals(dateFilter)) {
            if (f.customFrom() != null) {
                depStart = Timestamp.from(f.customFrom().atStartOfDay(SA_ZONE).toInstant());
            }
            if (f.customTo() != null) {
                depEnd = Timestamp.from(f.customTo().plusDays(1).atStartOfDay(SA_ZONE).toInstant());
            }
        }

        int i = 1;
        ps.setString(i++, normStatus);
        ps.setString(i++, normStatus);
        ps.setString(i++, escaped);
        ps.setString(i++, likePattern);
        ps.setString(i++, likePattern);
        ps.setString(i++, likePattern);
        ps.setString(i++, likePattern);
        setNullableTimestamp(ps, i++, depStart);
        setNullableTimestamp(ps, i++, depStart);
        setNullableTimestamp(ps, i++, depEnd);
        setNullableTimestamp(ps, i++, depEnd);
        ps.setString(i++, normCabin);
        ps.setString(i++, normCabin);
        if (f.flightId() != null) {
            ps.setLong(i++, f.flightId());
            ps.setLong(i++, f.flightId());
        } else {
            ps.setNull(i++, Types.BIGINT);
            ps.setNull(i++, Types.BIGINT);
        }
        return i;
    }

    private void setNullableTimestamp(PreparedStatement ps, int index, Timestamp ts) throws SQLException {
        if (ts != null) {
            ps.setTimestamp(index, ts);
        } else {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) return "ALL";
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private AdminBookingRow mapRow(ResultSet rs) throws SQLException {
        Timestamp depTs = rs.getTimestamp("departure_time");
        Timestamp arrTs = rs.getTimestamp("arrival_time");
        String originTz = rs.getString("origin_tz");
        String destTz = rs.getString("dest_tz");
        String seatClassStr = rs.getString("seat_class");
        SeatClass seatClass = "FIRST".equalsIgnoreCase(seatClassStr) ? SeatClass.FIRST : SeatClass.ECONOMY;
        String paxName = rs.getString("passenger_name");
        if (paxName == null || paxName.isBlank()) {
            paxName = rs.getString("user_full_name");
        }
        return new AdminBookingRow(
                rs.getLong("booking_id"),
                rs.getString("booking_ref"),
                paxName != null ? paxName : "Guest",
                rs.getString("user_email"),
                rs.getString("user_full_name"),
                rs.getLong("flight_id"),
                rs.getString("flight_number"),
                rs.getString("origin_code"),
                rs.getString("origin_city"),
                rs.getString("dest_code"),
                rs.getString("dest_city"),
                depTs != null ? depTs.toInstant() : Instant.EPOCH,
                originTz != null ? ZoneId.of(originTz) : SA_ZONE,
                arrTs != null ? arrTs.toInstant() : Instant.EPOCH,
                destTz != null ? ZoneId.of(destTz) : SA_ZONE,
                rs.getString("seat_labels"),
                seatClass,
                rs.getBigDecimal("total_amount"),
                rs.getString("booking_status")
        );
    }
}
