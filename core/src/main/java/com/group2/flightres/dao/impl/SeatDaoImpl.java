package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.config.SeatHoldPolicy;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SeatDaoImpl implements SeatDao {

    private static final Logger logger = LoggerFactory.getLogger(SeatDaoImpl.class);
    private static final Map<String, Boolean> HELD_BY_COLUMN_CACHE = new ConcurrentHashMap<>();

    private static final String SEAT_MAP_QUERY = """
        SELECT spv.flight_seat_id, spv.flight_id, spv.seat_row, spv.seat_col, spv.seat_class,
               spv.display_row, spv.display_col, spv.status, spv.calculated_price, fs.held_until
        FROM seat_pricing_view spv
        JOIN flight_seats fs ON fs.flight_seat_id = spv.flight_seat_id
        WHERE spv.flight_id = ?
        ORDER BY spv.display_row, spv.display_col
        """;

    private static final String SEAT_MAP_WITH_OWNER_QUERY = """
        SELECT spv.flight_seat_id, spv.flight_id, spv.seat_row, spv.seat_col, spv.seat_class,
               spv.display_row, spv.display_col, spv.status, spv.calculated_price, fs.held_until, fs.held_by
        FROM seat_pricing_view spv
        JOIN flight_seats fs ON fs.flight_seat_id = spv.flight_seat_id
        WHERE spv.flight_id = ?
        ORDER BY spv.display_row, spv.display_col
        """;

    // Optimistic concurrency: only holds if the seat is still AVAILABLE.
    // Generates held_until server-side using make_interval with the policy duration parameter.
    private static final String HOLD_SEAT_QUERY = """
        UPDATE flight_seats
        SET status = 'HELD', held_until = NOW() + make_interval(secs => ?)
        WHERE flight_seat_id = ? AND status = 'AVAILABLE'
        RETURNING held_until
        """;

    private static final String HOLD_SEAT_WITH_OWNER_QUERY = """
        UPDATE flight_seats
        SET status = 'HELD', held_until = NOW() + make_interval(secs => ?), held_by = ?
        WHERE flight_seat_id = ? AND (status = 'AVAILABLE' OR (status = 'HELD' AND held_by = ?))
        RETURNING held_until
        """;

    private static final String RELEASE_SEAT_QUERY = """
        UPDATE flight_seats
        SET status = 'AVAILABLE', held_until = NULL
        WHERE flight_seat_id = ? AND status = 'HELD'
        """;

    private static final String RELEASE_SEAT_WITH_OWNER_QUERY = """
        UPDATE flight_seats
        SET status = 'AVAILABLE', held_until = NULL, held_by = NULL
        WHERE flight_seat_id = ? AND status = 'HELD' AND (held_by IS NULL OR held_by = ?)
        """;

    private static final String RELEASE_SEAT_CLEAR_OWNER_QUERY = """
        UPDATE flight_seats
        SET status = 'AVAILABLE', held_until = NULL, held_by = NULL
        WHERE flight_seat_id = ? AND status = 'HELD'
        """;

    private static final String BOOK_SEAT_QUERY = """
        UPDATE flight_seats
        SET status = 'BOOKED', held_until = NULL
        WHERE flight_seat_id = ? AND (status = 'HELD' OR status = 'AVAILABLE')
        """;

    private static final String BOOK_SEAT_CLEAR_OWNER_QUERY = """
        UPDATE flight_seats
        SET status = 'BOOKED', held_until = NULL, held_by = NULL
        WHERE flight_seat_id = ? AND (status = 'HELD' OR status = 'AVAILABLE')
        """;

    private static final String RELEASE_EXPIRED_HOLDS_QUERY = """
        UPDATE flight_seats
        SET status = 'AVAILABLE', held_until = NULL
        WHERE status = 'HELD' AND held_until < NOW()
        """;

    private static final String RELEASE_EXPIRED_HOLDS_CLEAR_OWNER_QUERY = """
        UPDATE flight_seats
        SET status = 'AVAILABLE', held_until = NULL, held_by = NULL
        WHERE status = 'HELD' AND held_until < NOW()
        """;

    private static final String FIND_SEAT_BY_ID_QUERY = """
        SELECT spv.flight_seat_id, spv.flight_id, spv.seat_row, spv.seat_col, spv.seat_class,
               spv.display_row, spv.display_col, spv.status, spv.calculated_price, fs.held_until
        FROM seat_pricing_view spv
        JOIN flight_seats fs ON fs.flight_seat_id = spv.flight_seat_id
        WHERE spv.flight_seat_id = ?
        """;

    private static final String FIND_SEAT_BY_ID_WITH_OWNER_QUERY = """
        SELECT spv.flight_seat_id, spv.flight_id, spv.seat_row, spv.seat_col, spv.seat_class,
               spv.display_row, spv.display_col, spv.status, spv.calculated_price, fs.held_until, fs.held_by
        FROM seat_pricing_view spv
        JOIN flight_seats fs ON fs.flight_seat_id = spv.flight_seat_id
        WHERE spv.flight_seat_id = ?
        """;

    private static final String SEAT_CLASS_PRICING_QUERY = """
        SELECT seat_class, price_multiplier
        FROM seat_class_pricing
        """;

    private static boolean hasHeldByColumn(Connection conn) {
        try {
            var meta = conn.getMetaData();
            String url = meta.getURL();
            String cacheKey = url != null ? url : "";
            if (Boolean.TRUE.equals(HELD_BY_COLUMN_CACHE.get(cacheKey))) {
                return true;
            }
            try (ResultSet rs = meta.getColumns(null, null, "flight_seats", "held_by")) {
                if (rs.next()) {
                    HELD_BY_COLUMN_CACHE.put(cacheKey, Boolean.TRUE);
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    @Override
    public int releaseExpiredHolds() {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            boolean hasOwner = hasHeldByColumn(conn);
            String sql = hasOwner ? RELEASE_EXPIRED_HOLDS_CLEAR_OWNER_QUERY : RELEASE_EXPIRED_HOLDS_QUERY;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    logger.info("Released {} expired seat holds back to AVAILABLE", updated);
                }
                return updated;
            }
        } catch (SQLException e) {
            logger.warn("Could not release expired holds: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public List<FlightSeat> getSeatMapForFlight(long flightId) {
        releaseExpiredHolds();

        List<FlightSeat> seats = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            boolean hasOwner = hasHeldByColumn(conn);
            String sql = hasOwner ? SEAT_MAP_WITH_OWNER_QUERY : SEAT_MAP_QUERY;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, flightId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        seats.add(mapSeat(rs, hasOwner));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load seat map for flight {}: {}", flightId, e.getMessage(), e);
            throw new RuntimeException("Failed to load seat map for flight " + flightId, e);
        }

        return seats;
    }

    @Override
    public Optional<Instant> holdSeat(long flightSeatId, int holdSeconds) {
        return holdSeat(flightSeatId, holdSeconds, null);
    }

    @Override
    public Optional<Instant> holdSeat(long flightSeatId, int holdSeconds, String holdOwner) {
        releaseExpiredHolds();

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            boolean hasOwner = hasHeldByColumn(conn);
            String cleanOwner = (holdOwner != null && !holdOwner.isBlank()) ? holdOwner.trim() : null;
            if (hasOwner && cleanOwner != null) {
                try (PreparedStatement stmt = conn.prepareStatement(HOLD_SEAT_WITH_OWNER_QUERY)) {
                    stmt.setInt(1, holdSeconds);
                    stmt.setString(2, cleanOwner);
                    stmt.setLong(3, flightSeatId);
                    stmt.setString(4, cleanOwner);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Timestamp ts = rs.getTimestamp("held_until");
                            return Optional.ofNullable(ts != null ? ts.toInstant() : null);
                        }
                    }
                }
                return Optional.empty();
            }

            try (PreparedStatement stmt = conn.prepareStatement(HOLD_SEAT_QUERY)) {
                stmt.setInt(1, holdSeconds);
                stmt.setLong(2, flightSeatId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp ts = rs.getTimestamp("held_until");
                        return Optional.ofNullable(ts != null ? ts.toInstant() : null);
                    }
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to hold seat {}: {}", flightSeatId, e.getMessage(), e);
            throw new RuntimeException("Failed to hold seat " + flightSeatId, e);
        }
    }

    @Override
    public boolean releaseSeat(long flightSeatId) {
        return releaseSeat(flightSeatId, null);
    }

    @Override
    public boolean releaseSeat(long flightSeatId, String holdOwner) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            boolean hasOwner = hasHeldByColumn(conn);
            String cleanOwner = (holdOwner != null && !holdOwner.isBlank()) ? holdOwner.trim() : null;
            if (hasOwner && cleanOwner != null) {
                try (PreparedStatement stmt = conn.prepareStatement(RELEASE_SEAT_WITH_OWNER_QUERY)) {
                    stmt.setLong(1, flightSeatId);
                    stmt.setString(2, cleanOwner);
                    return stmt.executeUpdate() == 1;
                }
            }
            String sql = hasOwner ? RELEASE_SEAT_CLEAR_OWNER_QUERY : RELEASE_SEAT_QUERY;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, flightSeatId);
                return stmt.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            logger.error("Failed to release seat {}: {}", flightSeatId, e.getMessage(), e);
            throw new RuntimeException("Failed to release seat " + flightSeatId, e);
        }
    }

    @Override
    public boolean bookSeat(long flightSeatId) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            boolean hasOwner = hasHeldByColumn(conn);
            String sql = hasOwner ? BOOK_SEAT_CLEAR_OWNER_QUERY : BOOK_SEAT_QUERY;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, flightSeatId);
                return stmt.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            logger.error("Failed to book seat {}: {}", flightSeatId, e.getMessage(), e);
            throw new RuntimeException("Failed to book seat " + flightSeatId, e);
        }
    }

    @Override
    public Optional<FlightSeat> findSeatById(long flightSeatId) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            boolean hasOwner = hasHeldByColumn(conn);
            String sql = hasOwner ? FIND_SEAT_BY_ID_WITH_OWNER_QUERY : FIND_SEAT_BY_ID_QUERY;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, flightSeatId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapSeat(rs, hasOwner));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find seat by ID {}: {}", flightSeatId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
        Map<SeatClass, BigDecimal> map = new EnumMap<>(SeatClass.class);
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SEAT_CLASS_PRICING_QUERY);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                SeatClass sc = SeatClass.valueOf(rs.getString("seat_class"));
                BigDecimal mult = rs.getBigDecimal("price_multiplier");
                map.put(sc, mult);
            }
        } catch (SQLException e) {
            logger.error("Failed to load seat class pricing: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load seat class pricing", e);
        }
        return map;
    }

    private FlightSeat mapSeat(ResultSet rs, boolean includeHeldBy) throws SQLException {
        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(rs.getLong("flight_seat_id"));
        seat.setFlightId(rs.getLong("flight_id"));
        seat.setSeatRow(rs.getInt("seat_row"));
        seat.setSeatCol(rs.getString("seat_col").charAt(0));
        seat.setSeatClass(SeatClass.valueOf(rs.getString("seat_class")));
        seat.setDisplayRow(rs.getInt("display_row"));
        seat.setDisplayCol(rs.getInt("display_col"));
        seat.setStatus(SeatStatus.valueOf(rs.getString("status")));
        seat.setCalculatedPrice(rs.getBigDecimal("calculated_price"));

        Timestamp heldUntilTs = rs.getTimestamp("held_until");
        if (heldUntilTs != null) {
            seat.setHeldUntil(heldUntilTs.toInstant());
        }
        if (includeHeldBy) {
            seat.setHeldBy(rs.getString("held_by"));
        }
        return seat;
    }
}
