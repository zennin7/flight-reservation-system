package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.config.SeatHoldPolicy;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SeatDaoImpl implements SeatDao {

    private static final Logger logger = LoggerFactory.getLogger(SeatDaoImpl.class);

    private static final String SEAT_MAP_QUERY = """
        SELECT spv.flight_seat_id, spv.flight_id, spv.seat_row, spv.seat_col, spv.seat_class,
               spv.display_row, spv.display_col, spv.status, spv.calculated_price, fs.held_until
        FROM seat_pricing_view spv
        JOIN flight_seats fs ON fs.flight_seat_id = spv.flight_seat_id
        WHERE spv.flight_id = ?
        ORDER BY spv.display_row, spv.display_col
        """;

    // Optimistic concurrency: only holds if the seat is still AVAILABLE.
    // If another user grabbed it first, rowsAffected will be 0.
    private static final String HOLD_SEAT_QUERY = """
        UPDATE flight_seats
        SET status = 'HELD', held_until = ?
        WHERE flight_seat_id = ? AND status = 'AVAILABLE'
        """;

    private static final String RELEASE_SEAT_QUERY = """
        UPDATE flight_seats
        SET status = 'AVAILABLE', held_until = NULL
        WHERE flight_seat_id = ? AND status = 'HELD'
        """;

    private static final String BOOK_SEAT_QUERY = """
        UPDATE flight_seats
        SET status = 'BOOKED', held_until = NULL
        WHERE flight_seat_id = ? AND (status = 'HELD' OR status = 'AVAILABLE')
        """;

    private static final String RELEASE_EXPIRED_HOLDS_QUERY = """
        UPDATE flight_seats
        SET status = 'AVAILABLE', held_until = NULL
        WHERE status = 'HELD' AND held_until < NOW()
        """;

    private static final String FIND_SEAT_BY_ID_QUERY = """
        SELECT spv.flight_seat_id, spv.flight_id, spv.seat_row, spv.seat_col, spv.seat_class,
               spv.display_row, spv.display_col, spv.status, spv.calculated_price, fs.held_until
        FROM seat_pricing_view spv
        JOIN flight_seats fs ON fs.flight_seat_id = spv.flight_seat_id
        WHERE spv.flight_seat_id = ?
        """;

    @Override
    public int releaseExpiredHolds() {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(RELEASE_EXPIRED_HOLDS_QUERY)) {
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.info("Released {} expired seat holds back to AVAILABLE", updated);
            }
            return updated;
        } catch (SQLException e) {
            logger.warn("Could not release expired holds: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public List<FlightSeat> getSeatMapForFlight(long flightId) {
        releaseExpiredHolds();

        List<FlightSeat> seats = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SEAT_MAP_QUERY)) {

            stmt.setLong(1, flightId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    seats.add(mapSeat(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load seat map for flight {}: {}", flightId, e.getMessage(), e);
            throw new RuntimeException("Failed to load seat map for flight " + flightId, e);
        }

        return seats;
    }

    @Override
    public boolean holdSeat(long flightSeatId) {
        releaseExpiredHolds();

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(HOLD_SEAT_QUERY)) {

            // Soft-lock for 5 minutes while the passenger is in checkout
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().plusSeconds(SeatHoldPolicy.HOLD_SECONDS)));
            stmt.setLong(2, flightSeatId);

            int updated = stmt.executeUpdate();
            return updated == 1;
        } catch (SQLException e) {
            logger.error("Failed to hold seat {}: {}", flightSeatId, e.getMessage(), e);
            throw new RuntimeException("Failed to hold seat " + flightSeatId, e);
        }
    }

    @Override
    public boolean releaseSeat(long flightSeatId) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(RELEASE_SEAT_QUERY)) {

            stmt.setLong(1, flightSeatId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.error("Failed to release seat {}: {}", flightSeatId, e.getMessage(), e);
            throw new RuntimeException("Failed to release seat " + flightSeatId, e);
        }
    }

    @Override
    public boolean bookSeat(long flightSeatId) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(BOOK_SEAT_QUERY)) {

            stmt.setLong(1, flightSeatId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.error("Failed to book seat {}: {}", flightSeatId, e.getMessage(), e);
            throw new RuntimeException("Failed to book seat " + flightSeatId, e);
        }
    }

    @Override
    public Optional<FlightSeat> findSeatById(long flightSeatId) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_SEAT_BY_ID_QUERY)) {

            stmt.setLong(1, flightSeatId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSeat(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find seat by ID {}: {}", flightSeatId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    private FlightSeat mapSeat(ResultSet rs) throws SQLException {
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
        return seat;
    }
}
