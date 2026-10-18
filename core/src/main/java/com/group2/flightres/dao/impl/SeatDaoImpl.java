package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SeatDaoImpl implements SeatDao {

    private static final String SEAT_MAP_QUERY = """
        SELECT flight_seat_id, flight_id, seat_row, seat_col, seat_class,
               display_row, display_col, status, calculated_price
        FROM seat_pricing_view
        WHERE flight_id = ?
        ORDER BY display_row, display_col
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
        WHERE flight_seat_id = ? AND status = 'HELD'
        """;

    @Override
    public List<FlightSeat> getSeatMapForFlight(long flightId) {
        List<FlightSeat> seats = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SEAT_MAP_QUERY)) {

            stmt.setLong(1, flightId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
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
                    seats.add(seat);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load seat map for flight " + flightId, e);
        }

        return seats;
    }

    @Override
    public boolean holdSeat(long flightSeatId) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(HOLD_SEAT_QUERY)) {

            // Soft-lock for 5 minutes while the passenger is in checkout
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));
            stmt.setLong(2, flightSeatId);

            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
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
            throw new RuntimeException("Failed to book seat " + flightSeatId, e);
        }
    }
}
