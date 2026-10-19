package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class BookingDaoImpl implements BookingDao {

    private static final Logger logger = LoggerFactory.getLogger(BookingDaoImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PNR_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final String FIND_USER_BY_EMAIL = "SELECT user_id FROM users WHERE email = ?";
    private static final String INSERT_CUSTOMER_USER = """
        INSERT INTO users (username, password_hash, role, full_name, email, is_active)
        VALUES (?, ?, 'CUSTOMER'::user_role, ?, ?, TRUE)
        RETURNING user_id
        """;

    private static final String INSERT_BOOKING_QUERY = """
        INSERT INTO bookings (booking_ref, user_id, flight_id, status, total_amount)
        VALUES (?, ?, ?, 'CONFIRMED'::booking_status, ?)
        RETURNING booking_id, created_at
        """;

    private static final String INSERT_BOOKING_SEAT_QUERY = """
        INSERT INTO booking_seats (booking_id, flight_seat_id, passenger_name, passport_number, date_of_birth, price_paid)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING booking_seat_id
        """;

    private static final String BOOK_FLIGHT_SEAT_QUERY = """
        UPDATE flight_seats
        SET status = 'BOOKED'::seat_status, held_until = NULL
        WHERE flight_seat_id = ? AND (status = 'HELD' OR status = 'AVAILABLE')
        """;

    private static final String INSERT_PAYMENT_QUERY = """
        INSERT INTO payments (booking_id, amount, method, status, transaction_ref)
        VALUES (?, ?, 'CARD', 'COMPLETED'::payment_status, ?)
        """;

    private static final String FIND_BY_REF_QUERY = """
        SELECT b.booking_id, b.booking_ref, b.status AS booking_status, b.total_amount, b.created_at,
               f.flight_id, f.flight_number, f.departure_time, f.arrival_time,
               origin_ap.city AS origin_city, origin_ap.iata_code AS origin_code,
               dest_ap.city AS dest_city, dest_ap.iata_code AS dest_code,
               u.user_id, u.email AS user_email,
               bs.passenger_name, bs.passport_number,
               st.seat_row, st.seat_col, st.seat_class
        FROM bookings b
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN airports origin_ap ON origin_ap.airport_id = f.origin_airport_id
        JOIN airports dest_ap ON dest_ap.airport_id = f.dest_airport_id
        JOIN users u ON u.user_id = b.user_id
        LEFT JOIN booking_seats bs ON bs.booking_id = b.booking_id
        LEFT JOIN flight_seats fs ON fs.flight_seat_id = bs.flight_seat_id
        LEFT JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
        WHERE b.booking_ref = ?
        """;

    private static final String FIND_BY_USER_QUERY = """
        SELECT b.booking_id, b.booking_ref, b.status AS booking_status, b.total_amount, b.created_at,
               f.flight_id, f.flight_number, f.departure_time, f.arrival_time,
               origin_ap.city AS origin_city, origin_ap.iata_code AS origin_code,
               dest_ap.city AS dest_city, dest_ap.iata_code AS dest_code,
               u.user_id, u.email AS user_email,
               bs.passenger_name, bs.passport_number,
               st.seat_row, st.seat_col, st.seat_class
        FROM bookings b
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN airports origin_ap ON origin_ap.airport_id = f.origin_airport_id
        JOIN airports dest_ap ON dest_ap.airport_id = f.dest_airport_id
        JOIN users u ON u.user_id = b.user_id
        LEFT JOIN booking_seats bs ON bs.booking_id = b.booking_id
        LEFT JOIN flight_seats fs ON fs.flight_seat_id = bs.flight_seat_id
        LEFT JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
        WHERE b.user_id = ?
        ORDER BY b.created_at DESC
        """;

    @Override
    public BookingRecord createBooking(BookingContext context) {
        if (context == null) {
            throw new IllegalArgumentException("BookingContext cannot be null");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getDataSource().getConnection();
            conn.setAutoCommit(false);

            // 1. Resolve User
            long userId = resolveUserId(conn, context);

            // 2. Generate unique 6-char PNR
            String pnr = generateUniquePnr(conn);
            context.setBookingReference(pnr);

            // 3. Insert Booking
            long bookingId;
            LocalDateTime createdAt;
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_BOOKING_QUERY)) {
                stmt.setString(1, pnr);
                stmt.setLong(2, userId);
                stmt.setLong(3, context.getFlightId() != null ? context.getFlightId() : 1L);
                stmt.setBigDecimal(4, context.getTotalPrice() != null ? context.getTotalPrice() : BigDecimal.ZERO);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        bookingId = rs.getLong("booking_id");
                        Timestamp ts = rs.getTimestamp("created_at");
                        createdAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();
                    } else {
                        throw new SQLException("Failed to obtain generated booking ID");
                    }
                }
            }

            // 4. Mark seats BOOKED and insert into booking_seats
            List<FlightSeat> seats = context.getSelectedSeats();
            String passengerName = context.getPassengerName() != null ? context.getPassengerName() : "Valued Passenger";
            String passport = context.getPassportNumber() != null ? context.getPassportNumber() : "PA999999";
            LocalDate dob = LocalDate.of(1990, 1, 1);

            for (FlightSeat seat : seats) {
                // Optimistic check: must be HELD or AVAILABLE
                try (PreparedStatement stmt = conn.prepareStatement(BOOK_FLIGHT_SEAT_QUERY)) {
                    stmt.setLong(1, seat.getFlightSeatId());
                    int updated = stmt.executeUpdate();
                    if (updated == 0) {
                        logger.warn("Transaction rollback: Seat {} ({}) is no longer available",
                                seat.getSeatLabel(), seat.getFlightSeatId());
                        conn.rollback();
                        throw new SeatUnavailableException("Seat " + seat.getSeatLabel() + " is no longer available.");
                    }
                }

                // Insert into booking_seats
                try (PreparedStatement stmt = conn.prepareStatement(INSERT_BOOKING_SEAT_QUERY)) {
                    stmt.setLong(1, bookingId);
                    stmt.setLong(2, seat.getFlightSeatId());
                    stmt.setString(3, passengerName);
                    stmt.setString(4, passport);
                    stmt.setDate(5, Date.valueOf(dob));
                    stmt.setBigDecimal(6, seat.getCalculatedPrice() != null ? seat.getCalculatedPrice() : context.getTotalPrice());
                    stmt.executeQuery();
                }
            }

            // 5. Insert payment record
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_PAYMENT_QUERY)) {
                stmt.setLong(1, bookingId);
                stmt.setBigDecimal(2, context.getTotalPrice() != null ? context.getTotalPrice() : BigDecimal.ZERO);
                stmt.setString(3, "TXN-" + pnr);
                stmt.executeUpdate();
            }

            conn.commit();
            logger.info("Successfully created booking {} (ID: {}) for flight {} in transaction",
                    pnr, bookingId, context.getFlightId());

            BookingRecord record = new BookingRecord();
            record.setBookingId(bookingId);
            record.setBookingReference(pnr);
            record.setUserId(userId);
            record.setFlightId(context.getFlightId());
            record.setFlightNumber(context.getFlightNumber());
            record.setOrigin(context.getOrigin());
            record.setDestination(context.getDestination());
            record.setDepartureTime(context.getDepartureTime());
            record.setArrivalTime(context.getArrivalTime());
            record.setStatus("CONFIRMED");
            record.setTotalAmount(context.getTotalPrice());
            record.setCreatedAt(createdAt);
            record.setPassengerName(passengerName);
            record.setPassengerEmail(context.getPassengerEmail());
            record.setPassportNumber(passport);
            record.setSeatLabel(context.getSeatLabel());
            record.setSeatClass(context.getSeatClass());

            return record;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { logger.error("Rollback failed", ex); }
            }
            logger.error("Failed to execute booking transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Booking transaction failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public Optional<BookingRecord> findByReference(String bookingRef) {
        if (bookingRef == null || bookingRef.trim().isEmpty()) {
            return Optional.empty();
        }
        String ref = bookingRef.trim().toUpperCase(Locale.ROOT);

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_REF_QUERY)) {

            stmt.setString(1, ref);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query booking by reference {}: {}", ref, e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<BookingRecord> findByUserId(long userId) {
        List<BookingRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_USER_QUERY)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query bookings for user {}: {}", userId, e.getMessage(), e);
        }
        return list;
    }

    private long resolveUserId(Connection conn, BookingContext context) throws SQLException {
        if (context.getCurrentUser() != null && context.getCurrentUser().getUserId() > 0) {
            return context.getCurrentUser().getUserId();
        }

        String email = (context.getPassengerEmail() != null && !context.getPassengerEmail().isBlank())
                ? context.getPassengerEmail().trim().toLowerCase(Locale.ROOT) : "guest@skyvoyage.com";

        // Check if user already exists
        try (PreparedStatement stmt = conn.prepareStatement(FIND_USER_BY_EMAIL)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("user_id");
                }
            }
        }

        // Create on-the-fly customer user
        String fullName = context.getPassengerName() != null ? context.getPassengerName() : "Valued Passenger";
        String username = "cust_" + System.currentTimeMillis() + "_" + RANDOM.nextInt(1000);
        String dummyHash = PasswordUtil.hash(username);

        try (PreparedStatement stmt = conn.prepareStatement(INSERT_CUSTOMER_USER)) {
            stmt.setString(1, username);
            stmt.setString(2, dummyHash);
            stmt.setString(3, fullName);
            stmt.setString(4, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("user_id");
                }
            }
        }
        throw new SQLException("Could not resolve or create customer user");
    }

    private String generateUniquePnr(Connection conn) throws SQLException {
        for (int attempts = 0; attempts < 10; attempts++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(PNR_CHARS.charAt(RANDOM.nextInt(PNR_CHARS.length())));
            }
            String pnr = sb.toString();

            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM bookings WHERE booking_ref = ?")) {
                stmt.setString(1, pnr);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return pnr;
                    }
                }
            }
        }
        throw new SQLException("Failed to generate a unique PNR reference");
    }

    private BookingRecord mapRecord(ResultSet rs) throws SQLException {
        BookingRecord rec = new BookingRecord();
        rec.setBookingId(rs.getLong("booking_id"));
        rec.setBookingReference(rs.getString("booking_ref"));
        rec.setStatus(rs.getString("booking_status"));
        rec.setTotalAmount(rs.getBigDecimal("total_amount"));

        Timestamp ts = rs.getTimestamp("created_at");
        rec.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);

        rec.setFlightId(rs.getLong("flight_id"));
        rec.setFlightNumber(rs.getString("flight_number"));

        Timestamp depTs = rs.getTimestamp("departure_time");
        rec.setDepartureTime(depTs != null ? depTs.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "—");

        Timestamp arrTs = rs.getTimestamp("arrival_time");
        rec.setArrivalTime(arrTs != null ? arrTs.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "—");

        rec.setOrigin(rs.getString("origin_city") + " (" + rs.getString("origin_code") + ")");
        rec.setDestination(rs.getString("dest_city") + " (" + rs.getString("dest_code") + ")");

        rec.setUserId(rs.getLong("user_id"));
        rec.setPassengerName(rs.getString("passenger_name"));
        rec.setPassengerEmail(rs.getString("user_email"));
        rec.setPassportNumber(rs.getString("passport_number"));

        int row = rs.getInt("seat_row");
        String col = rs.getString("seat_col");
        if (col != null && !col.isEmpty()) {
            rec.setSeatLabel(row + col);
        }
        String seatCls = rs.getString("seat_class");
        if (seatCls != null) {
            rec.setSeatClass(SeatClass.valueOf(seatCls));
        }

        return rec;
    }
}
