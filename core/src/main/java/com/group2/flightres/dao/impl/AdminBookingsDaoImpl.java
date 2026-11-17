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
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PostgreSQL DAO for Admin Bookings using one joined query (no N+1) and escaped LIKE search.
 */
public class AdminBookingsDaoImpl implements AdminBookingsDao {

    private static final Logger logger = LoggerFactory.getLogger(AdminBookingsDaoImpl.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Johannesburg");

    private static final String COUNT_AND_SUM_QUERY = """
        SELECT COUNT(*) AS total_count,
               COALESCE(SUM(b.total_amount), 0) AS sum_total
        FROM bookings b
        JOIN users u ON u.user_id = b.user_id
        WHERE (? = 'ALL' OR b.status::text = ?)
          AND (? = '' OR b.booking_ref ILIKE ? ESCAPE '\\'
                      OR u.email ILIKE ? ESCAPE '\\'
                      OR u.full_name ILIKE ? ESCAPE '\\'
                      OR EXISTS (
                          SELECT 1 FROM booking_seats bs2
                          WHERE bs2.booking_id = b.booking_id
                            AND bs2.passenger_name ILIKE ? ESCAPE '\\'
                      ))
        """;

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
        WHERE (? = 'ALL' OR b.status::text = ?)
          AND (? = '' OR b.booking_ref ILIKE ? ESCAPE '\\'
                      OR u.email ILIKE ? ESCAPE '\\'
                      OR u.full_name ILIKE ? ESCAPE '\\'
                      OR EXISTS (
                          SELECT 1 FROM booking_seats bs2
                          WHERE bs2.booking_id = b.booking_id
                            AND bs2.passenger_name ILIKE ? ESCAPE '\\'
                      ))
        GROUP BY b.booking_id, b.booking_ref, b.status, b.total_amount,
                 f.flight_id, f.flight_number, f.departure_time, f.arrival_time,
                 o.iata_code, o.city, o.timezone, d.iata_code, d.city, d.timezone,
                 u.email, u.full_name
        ORDER BY b.created_at DESC, b.booking_id DESC
        LIMIT ? OFFSET ?
        """;

    public static String escapeLikeWildcards(String input) {
        if (input == null) return "";
        return input.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Override
    public AdminBookingsPage searchBookings(String searchTerm, String statusFilter, int page, int pageSize) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            return searchBookings(conn, searchTerm, statusFilter, page, pageSize);
        } catch (SQLException e) {
            logger.error("Failed to search admin bookings: {}", e.getMessage(), e);
            throw new IllegalStateException("Unable to load bookings list", e);
        }
    }

    @Override
    public AdminBookingsPage searchBookings(Connection conn, String searchTerm, String statusFilter, int page, int pageSize)
            throws SQLException {
        String normalizedStatus = (statusFilter == null || statusFilter.isBlank())
                ? "ALL" : statusFilter.trim().toUpperCase(Locale.ROOT);
        String trimmedSearch = searchTerm != null ? searchTerm.trim() : "";
        String likePattern = trimmedSearch.isEmpty() ? "" : "%" + escapeLikeWildcards(trimmedSearch) + "%";
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, pageSize);
        int offset = (safePage - 1) * safeSize;

        long totalCount = 0;
        BigDecimal totalSum = BigDecimal.ZERO;

        try (PreparedStatement countStmt = conn.prepareStatement(COUNT_AND_SUM_QUERY)) {
            bindFilterParams(countStmt, normalizedStatus, trimmedSearch, likePattern);
            try (ResultSet rs = countStmt.executeQuery()) {
                if (rs.next()) {
                    totalCount = rs.getLong("total_count");
                    totalSum = rs.getBigDecimal("sum_total");
                }
            }
        }

        List<AdminBookingRow> rows = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(SEARCH_JOINED_QUERY)) {
            int idx = bindFilterParams(stmt, normalizedStatus, trimmedSearch, likePattern);
            stmt.setInt(idx++, safeSize);
            stmt.setInt(idx, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        }

        return new AdminBookingsPage(rows, totalCount, totalSum, safePage, safeSize);
    }

    private int bindFilterParams(PreparedStatement stmt, String status, String rawSearch, String likePattern) throws SQLException {
        int i = 1;
        stmt.setString(i++, status);
        stmt.setString(i++, status);
        stmt.setString(i++, rawSearch);
        stmt.setString(i++, likePattern);
        stmt.setString(i++, likePattern);
        stmt.setString(i++, likePattern);
        stmt.setString(i++, likePattern);
        return i;
    }

    private AdminBookingRow mapRow(ResultSet rs) throws SQLException {
        String origTz = rs.getString("origin_tz");
        String destTz = rs.getString("dest_tz");
        ZoneId origZone = (origTz != null && !origTz.isBlank()) ? ZoneId.of(origTz) : DEFAULT_ZONE;
        ZoneId destZone = (destTz != null && !destTz.isBlank()) ? ZoneId.of(destTz) : DEFAULT_ZONE;

        Timestamp depTs = rs.getTimestamp("departure_time");
        Instant depInstant = depTs != null ? depTs.toInstant() : Instant.EPOCH;
        Timestamp arrTs = rs.getTimestamp("arrival_time");
        Instant arrInstant = arrTs != null ? arrTs.toInstant() : depInstant;

        String seatClsStr = rs.getString("seat_class");
        SeatClass seatClass = SeatClass.ECONOMY;
        if (seatClsStr != null && !seatClsStr.isBlank()) {
            try {
                seatClass = SeatClass.valueOf(seatClsStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        return new AdminBookingRow(
                rs.getLong("booking_id"),
                rs.getString("booking_ref"),
                rs.getString("passenger_name"),
                rs.getString("user_email"),
                rs.getString("user_full_name"),
                rs.getLong("flight_id"),
                rs.getString("flight_number"),
                rs.getString("origin_code"),
                rs.getString("origin_city"),
                rs.getString("dest_code"),
                rs.getString("dest_city"),
                depInstant,
                origZone,
                arrInstant,
                destZone,
                rs.getString("seat_labels"),
                seatClass,
                rs.getBigDecimal("total_amount"),
                rs.getString("booking_status")
        );
    }
}
