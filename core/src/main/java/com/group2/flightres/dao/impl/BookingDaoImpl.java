package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.service.PaymentResult;
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

    private static final String BOOKING_SELECT_FROM = """
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
        """;

    private static final String FIND_BY_REF_QUERY = BOOKING_SELECT_FROM + " WHERE b.booking_ref = ?\n";

    private static final String FIND_BY_USER_QUERY = BOOKING_SELECT_FROM + " WHERE b.user_id = ?\nORDER BY b.created_at DESC\n";

    private record BookingHeader(long bookingId, LocalDateTime createdAt) {}

    @Override
    public BookingRecord createBooking(BookingContext context) {
        if (context == null) {
            throw new IllegalArgumentException("BookingContext cannot be null");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getDataSource().getConnection();
            conn.setAutoCommit(false);

            long userId = resolveUserId(conn, context);
            String pnr = generateUniquePnr(conn);
            context.setBookingReference(pnr);

            BookingHeader header = insertBookingHeader(conn, pnr, userId, context);
            persistBookingSeats(conn, header.bookingId(), context);
            persistPayment(conn, header.bookingId(), pnr, context);

            conn.commit();
            logger.info("Successfully created booking {} (ID: {}) for flight {} in transaction",
                    pnr, header.bookingId(), context.getFlightId());

            return buildBookingRecord(header, pnr, userId, context);
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

    private BookingHeader insertBookingHeader(Connection conn, String pnr, long userId, BookingContext context)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_BOOKING_QUERY)) {
            stmt.setString(1, pnr);
            stmt.setLong(2, userId);
            if (context.getFlightId() == null) {
                throw new IllegalStateException("Flight ID is required to create a booking.");
            }
            stmt.setLong(3, context.getFlightId());
            stmt.setBigDecimal(4, context.getTotalPrice() != null ? context.getTotalPrice() : BigDecimal.ZERO);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long bookingId = rs.getLong("booking_id");
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime createdAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();
                    return new BookingHeader(bookingId, createdAt);
                } else {
                    throw new SQLException("Failed to obtain generated booking ID");
                }
            }
        }
    }

    private void persistBookingSeats(Connection conn, long bookingId, BookingContext context)
            throws SQLException, SeatUnavailableException {
        List<FlightSeat> seats = context.getSelectedSeats();
        String passengerName = context.getPassengerName();
        if (passengerName == null || passengerName.isBlank()) {
            throw new IllegalStateException("Passenger name is required to create a booking.");
        }
        String passport = context.getPassportNumber();
        if (passport == null || passport.isBlank()) {
            throw new IllegalStateException("Passport number is required to create a booking.");
        }
        LocalDate dob = context.getDateOfBirth();
        if (dob == null) {
            throw new IllegalStateException("Passenger date of birth is required to create a booking.");
        }

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
    }

    private static volatile Boolean paymentsHasCardMeta = null;

    private boolean checkPaymentsHasCardMeta(Connection conn) {
        if (paymentsHasCardMeta != null) {
            return paymentsHasCardMeta;
        }
        try {
            var meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "payments", "card_brand")) {
                paymentsHasCardMeta = rs.next();
                return paymentsHasCardMeta;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // TODO: Remove column-exists check after migration_payments_card_meta.sql is applied to team DB
    private void persistPayment(Connection conn, long bookingId, String pnr, BookingContext context)
            throws SQLException {
        PaymentResult payResult = context.getPaymentResult();
        if (payResult == null) {
            throw new IllegalStateException("PaymentResult is required to persist payment.");
        }
        String txnRef = payResult.getTransactionReference();
        if (txnRef == null || txnRef.isBlank()) {
            throw new IllegalStateException("Payment transaction reference is required to persist payment.");
        }
        String brand = payResult.getCardBrand();
        if (brand == null || brand.isBlank()) {
            throw new IllegalStateException("Payment card brand is required to persist payment.");
        }
        String last4 = payResult.getCardLast4();
        if (last4 == null || last4.isBlank()) {
            throw new IllegalStateException("Payment card last 4 digits are required to persist payment.");
        }

        boolean hasCardMeta = checkPaymentsHasCardMeta(conn);
        String sql = hasCardMeta
                ? "INSERT INTO payments (booking_id, amount, method, status, transaction_ref, card_brand, card_last4) VALUES (?, ?, 'CARD', 'COMPLETED'::payment_status, ?, ?, ?)"
                : "INSERT INTO payments (booking_id, amount, method, status, transaction_ref) VALUES (?, ?, 'CARD', 'COMPLETED'::payment_status, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, bookingId);
            stmt.setBigDecimal(2, context.getTotalPrice() != null ? context.getTotalPrice() : BigDecimal.ZERO);
            stmt.setString(3, txnRef);
            if (hasCardMeta) {
                stmt.setString(4, brand);
                stmt.setString(5, last4);
            }
            stmt.executeUpdate();
        }
    }

    private BookingRecord buildBookingRecord(BookingHeader header, String pnr, long userId, BookingContext context) {
        String passengerName = context.getPassengerName();
        if (passengerName == null || passengerName.isBlank()) {
            throw new IllegalStateException("Passenger name is required to build booking record.");
        }
        String passport = context.getPassportNumber();
        if (passport == null || passport.isBlank()) {
            throw new IllegalStateException("Passport number is required to build booking record.");
        }

        BookingRecord record = new BookingRecord();
        record.setBookingId(header.bookingId());
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
        record.setCreatedAt(header.createdAt());
        record.setPassengerName(passengerName);
        record.setPassengerEmail(context.getPassengerEmail());
        record.setPassportNumber(passport);
        record.setSeatLabel(context.getSeatLabel());
        record.setSeatClass(context.getSeatClass());

        return record;
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

        String email = context.getPassengerEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Passenger email is required to create a booking.");
        }
        email = email.trim().toLowerCase(Locale.ROOT);

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
        String fullName = context.getPassengerName();
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalStateException("Passenger name is required to create a booking.");
        }
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
