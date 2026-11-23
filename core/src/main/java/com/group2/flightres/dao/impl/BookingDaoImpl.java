package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.exception.RefundFailedException;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.CancelResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.service.CancellationPolicy;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PaymentService;
import com.group2.flightres.util.FlightTimes;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BookingDaoImpl implements BookingDao {

    private static final Logger logger = LoggerFactory.getLogger(BookingDaoImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PNR_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Map<String, Boolean> COLUMN_EXISTS_CACHE = new ConcurrentHashMap<>();

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
               origin_ap.city AS origin_city, origin_ap.iata_code AS origin_code, origin_ap.timezone AS origin_timezone,
               dest_ap.city AS dest_city, dest_ap.iata_code AS dest_code, dest_ap.timezone AS dest_timezone,
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

    private static final String LIST_SUMMARIES_BY_USER_QUERY = """
        SELECT b.booking_id, b.booking_ref, b.user_id, b.status AS booking_status, b.total_amount,
               f.flight_id, f.flight_number, f.departure_time, f.arrival_time,
               act.model AS aircraft_model,
               o.iata_code AS origin_code, o.city AS origin_city, o.timezone AS origin_timezone,
               d.iata_code AS dest_code, d.city AS dest_city, d.timezone AS dest_timezone,
               MIN(bs.passenger_name) AS passenger_name, u.email AS user_email,
               string_agg(concat(st.seat_row, st.seat_col), ', ' ORDER BY st.seat_row, st.seat_col) AS seat_labels
        FROM bookings b
        JOIN flights f ON f.flight_id = b.flight_id
        JOIN aircraft_types act ON act.aircraft_type_id = f.aircraft_type_id
        JOIN airports o ON o.airport_id = f.origin_airport_id
        JOIN airports d ON d.airport_id = f.dest_airport_id
        JOIN users u ON u.user_id = b.user_id
        LEFT JOIN booking_seats bs ON bs.booking_id = b.booking_id
        LEFT JOIN flight_seats fs ON fs.flight_seat_id = bs.flight_seat_id
        LEFT JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
        WHERE b.user_id = ?
        GROUP BY b.booking_id, b.booking_ref, b.user_id, b.status, b.total_amount,
                 f.flight_id, f.flight_number, f.departure_time, f.arrival_time, act.model,
                 o.iata_code, o.city, o.timezone, d.iata_code, d.city, d.timezone, u.email
        """;

    private record BookingHeader(long bookingId, LocalDateTime createdAt) {}

    @Override
    public BookingRecord createBooking(BookingContext context) {
        if (context == null) {
            throw new IllegalArgumentException("BookingContext cannot be null");
        }

        String idemKey = (context.getIdempotencyKey() != null && !context.getIdempotencyKey().isBlank())
                ? context.getIdempotencyKey().trim()
                : null;

        Connection conn = null;
        try {
            conn = DatabaseConfig.getDataSource().getConnection();
            conn.setAutoCommit(false);

            if (idemKey != null) {
                Optional<BookingRecord> existing = findByIdempotencyKey(conn, idemKey);
                if (existing.isPresent()) {
                    conn.rollback();
                    logger.info("Idempotent replay detected for key {}; returning booking {}",
                            idemKey, existing.get().getBookingReference());
                    return existing.get();
                }
            }

            BookingRecord record = createBookingInTransaction(conn, context);

            conn.commit();
            logger.info("Successfully created booking {} (ID: {}) for flight {} in transaction",
                    context.getBookingReference(), record.getBookingId(), context.getFlightId());

            return record;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { logger.error("Rollback failed", ex); }
            }
            if ("23505".equals(e.getSQLState()) && idemKey != null) {
                Optional<BookingRecord> existing = findByIdempotencyKey(idemKey);
                if (existing.isPresent()) {
                    logger.info("Concurrent idempotency conflict resolved to existing booking {}",
                            existing.get().getBookingReference());
                    return existing.get();
                }
            }
            logger.error("Failed to execute booking transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Booking transaction failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Executes the booking transaction within an externally-managed Connection/transaction.
     * Does NOT commit or close the Connection, allowing callers (such as test suites)
     * to test against real queries and roll back cleanly without leaving persisted rows.
     */
    public BookingRecord createBookingInTransaction(Connection conn, BookingContext context) throws SQLException {
        if (context == null) {
            throw new IllegalArgumentException("BookingContext cannot be null");
        }

        String idemKey = (context.getIdempotencyKey() != null && !context.getIdempotencyKey().isBlank())
                ? context.getIdempotencyKey().trim()
                : null;
        if (idemKey != null) {
            Optional<BookingRecord> existing = findByIdempotencyKey(conn, idemKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        long userId = resolveUserId(conn, context);
        String pnr = generateUniquePnr(conn);
        context.setBookingReference(pnr);

        BookingHeader header = insertBookingHeader(conn, pnr, userId, context);
        persistBookingSeats(conn, header.bookingId(), context);
        persistPayment(conn, header.bookingId(), pnr, context);

        return buildBookingRecord(header, pnr, userId, context);
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

        String holdOwner = (context.getHoldOwner() != null && !context.getHoldOwner().isBlank())
                ? context.getHoldOwner().trim()
                : null;
        boolean hasHeldBy = hasColumn(conn, "flight_seats", "held_by");

        for (FlightSeat seat : seats) {
            int updated;
            if (holdOwner != null && hasHeldBy) {
                String sql = """
                        UPDATE flight_seats
                        SET status = 'BOOKED'::seat_status, held_until = NULL, held_by = NULL
                        WHERE flight_seat_id = ? AND status = 'HELD' AND held_by = ? AND held_until >= NOW()
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, seat.getFlightSeatId());
                    stmt.setString(2, holdOwner);
                    updated = stmt.executeUpdate();
                }
            } else {
                String sql = hasHeldBy
                        ? "UPDATE flight_seats SET status = 'BOOKED'::seat_status, held_until = NULL, held_by = NULL WHERE flight_seat_id = ? AND (status = 'HELD' OR status = 'AVAILABLE')"
                        : BOOK_FLIGHT_SEAT_QUERY;
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, seat.getFlightSeatId());
                    updated = stmt.executeUpdate();
                }
            }
            if (updated == 0) {
                logger.warn("Transaction rollback: Seat {} ({}) is no longer available",
                        seat.getSeatLabel(), seat.getFlightSeatId());
                conn.rollback();
                throw new SeatUnavailableException("Seat " + seat.getSeatLabel() + " is no longer available.");
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

    private static boolean hasColumn(Connection conn, String table, String column) {
        try {
            var meta = conn.getMetaData();
            String url = meta.getURL();
            String cacheKey = (url != null ? url : "") + "|" + table + "|" + column;
            if (Boolean.TRUE.equals(COLUMN_EXISTS_CACHE.get(cacheKey))) {
                return true;
            }
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                if (rs.next()) {
                    COLUMN_EXISTS_CACHE.put(cacheKey, Boolean.TRUE);
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

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

        boolean hasCardMeta = hasColumn(conn, "payments", "card_brand");
        String idemKey = (context.getIdempotencyKey() != null && !context.getIdempotencyKey().isBlank())
                ? context.getIdempotencyKey().trim()
                : null;
        boolean hasIdempotencyKey = idemKey != null && hasColumn(conn, "payments", "idempotency_key");

        String sql;
        if (hasCardMeta && hasIdempotencyKey) {
            sql = "INSERT INTO payments (booking_id, amount, method, status, transaction_ref, card_brand, card_last4, idempotency_key) VALUES (?, ?, 'CARD', 'COMPLETED'::payment_status, ?, ?, ?, ?)";
        } else if (hasCardMeta) {
            sql = "INSERT INTO payments (booking_id, amount, method, status, transaction_ref, card_brand, card_last4) VALUES (?, ?, 'CARD', 'COMPLETED'::payment_status, ?, ?, ?)";
        } else if (hasIdempotencyKey) {
            sql = "INSERT INTO payments (booking_id, amount, method, status, transaction_ref, idempotency_key) VALUES (?, ?, 'CARD', 'COMPLETED'::payment_status, ?, ?)";
        } else {
            sql = INSERT_PAYMENT_QUERY;
        }

        BigDecimal amount = context.getTotalPrice() != null ? context.getTotalPrice() : BigDecimal.ZERO;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            stmt.setLong(idx++, bookingId);
            stmt.setBigDecimal(idx++, amount);
            stmt.setString(idx++, txnRef);
            if (hasCardMeta) {
                stmt.setString(idx++, brand);
                stmt.setString(idx++, last4);
            }
            if (hasIdempotencyKey) {
                stmt.setString(idx, idemKey);
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
        record.setOriginCity(context.getOriginCity());
        record.setDestination(context.getDestination());
        record.setDestinationCity(context.getDestinationCity());
        record.setDepartureDate(context.getDepartureDate());
        record.setDepartureTime(context.getDepartureTime());
        record.setArrivalTime(context.getArrivalTime());
        record.setDepartureInstant(context.getDepartureInstant());
        record.setArrivalInstant(context.getArrivalInstant());
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
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            return findByReference(conn, bookingRef);
        } catch (SQLException e) {
            logger.error("Failed to query booking by reference {}: {}", bookingRef, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<BookingRecord> findByReference(Connection conn, String bookingRef) throws SQLException {
        if (bookingRef == null || bookingRef.trim().isEmpty()) {
            return Optional.empty();
        }
        String ref = bookingRef.trim().toUpperCase(Locale.ROOT);
        try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_REF_QUERY)) {
            stmt.setString(1, ref);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRecord(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<BookingRecord> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            return findByIdempotencyKey(conn, idempotencyKey.trim());
        } catch (SQLException e) {
            logger.error("Failed to query booking by idempotency key: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<BookingRecord> findByIdempotencyKey(Connection conn, String idempotencyKey) throws SQLException {
        if (!hasColumn(conn, "payments", "idempotency_key")) {
            return Optional.empty();
        }
        String sql = BOOKING_SELECT_FROM
                + " JOIN payments p ON p.booking_id = b.booking_id WHERE p.idempotency_key = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, idempotencyKey);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRecord(rs));
                }
            }
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

    @Override
    public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
        Clock refClock = clock != null ? clock : Clock.systemUTC();
        List<BookingSummary> rawList = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(LIST_SUMMARIES_BY_USER_QUERY)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long bookingId = rs.getLong("booking_id");
                    String bookingRef = rs.getString("booking_ref");
                    long uid = rs.getLong("user_id");
                    String storedStatus = rs.getString("booking_status");
                    BigDecimal totalAmount = rs.getBigDecimal("total_amount");
                    String flightNumber = rs.getString("flight_number");
                    String aircraftModel = rs.getString("aircraft_model");

                    String origTz = rs.getString("origin_timezone");
                    ZoneId origZone = (origTz != null && !origTz.isBlank()) ? ZoneId.of(origTz) : ZoneId.of("Africa/Johannesburg");
                    String destTz = rs.getString("dest_timezone");
                    ZoneId destZone = (destTz != null && !destTz.isBlank()) ? ZoneId.of(destTz) : ZoneId.of("Africa/Johannesburg");

                    Timestamp depTs = rs.getTimestamp("departure_time");
                    Instant depInstant = depTs != null ? depTs.toInstant() : Instant.EPOCH;

                    Timestamp arrTs = rs.getTimestamp("arrival_time");
                    Instant arrInstant = arrTs != null ? arrTs.toInstant() : depInstant;

                    String originCode = rs.getString("origin_code");
                    String originCity = rs.getString("origin_city");
                    String destCode = rs.getString("dest_code");
                    String destCity = rs.getString("dest_city");
                    String passengerName = rs.getString("passenger_name");
                    String userEmail = rs.getString("user_email");

                    String seatLabelsAgg = rs.getString("seat_labels");
                    List<String> seatLabels = new ArrayList<>();
                    if (seatLabelsAgg != null && !seatLabelsAgg.isBlank()) {
                        for (String s : seatLabelsAgg.split(",")) {
                            String trimmed = s.trim();
                            if (!trimmed.isEmpty()) {
                                seatLabels.add(trimmed);
                            }
                        }
                    }

                    // Status classification:
                    // UPCOMING if departure is in the future and not cancelled,
                    // COMPLETED if past,
                    // CANCELLED as stored.
                    String status;
                    if (storedStatus != null && "CANCELLED".equalsIgnoreCase(storedStatus.trim())) {
                        status = "CANCELLED";
                    } else if (depInstant.isAfter(refClock.instant())) {
                        status = "UPCOMING";
                    } else {
                        status = "COMPLETED";
                    }

                    rawList.add(new BookingSummary(
                            bookingId,
                            bookingRef,
                            uid,
                            flightNumber,
                            originCode,
                            originCity,
                            depInstant,
                            origZone,
                            destCode,
                            destCity,
                            arrInstant,
                            destZone,
                            status,
                            storedStatus,
                            seatLabels,
                            totalAmount,
                            passengerName,
                            userEmail,
                            aircraftModel
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to list booking summaries for user {}: {}", userId, e.getMessage(), e);
        }

        // Ordering requirement:
        // "Order: upcoming ascending, then past descending."
        List<BookingSummary> upcoming = new ArrayList<>();
        List<BookingSummary> past = new ArrayList<>();
        List<BookingSummary> cancelled = new ArrayList<>();

        for (BookingSummary summary : rawList) {
            if ("UPCOMING".equals(summary.getStatus())) {
                upcoming.add(summary);
            } else if ("COMPLETED".equals(summary.getStatus())) {
                past.add(summary);
            } else {
                cancelled.add(summary);
            }
        }

        upcoming.sort(Comparator.comparing(BookingSummary::getDepartureInstant));
        past.sort(Comparator.comparing(BookingSummary::getDepartureInstant).reversed());
        cancelled.sort(Comparator.comparing(BookingSummary::getDepartureInstant).reversed());

        List<BookingSummary> ordered = new ArrayList<>(rawList.size());
        ordered.addAll(upcoming);
        ordered.addAll(past);
        ordered.addAll(cancelled);
        return ordered;
    }

    @Override
    public CancelResult cancelBookingForUser(long userId,
                                             String bookingReference,
                                             CancellationPolicy policy,
                                             PaymentService paymentService) {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("Booking reference must not be null or blank");
        }
        if (policy == null) {
            throw new IllegalArgumentException("CancellationPolicy must not be null");
        }
        if (paymentService == null) {
            throw new IllegalArgumentException("PaymentService must not be null");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getDataSource().getConnection();
            conn.setAutoCommit(false);

            CancelResult result = cancelBookingInTransaction(conn, userId, bookingReference, policy, paymentService);
            conn.commit();
            return result;
        } catch (RuntimeException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { logger.error("Cancel rollback failed", ex); }
            }
            throw e;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { logger.error("Cancel rollback failed", ex); }
            }
            throw new RuntimeException("Cancellation transaction failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public CancelResult cancelBookingInTransaction(Connection conn,
                                                   long userId,
                                                   String bookingReference,
                                                   CancellationPolicy policy,
                                                   PaymentService paymentService) throws SQLException {
        String ref = bookingReference.trim().toUpperCase(Locale.ROOT);
        boolean hasRefundRef = hasColumn(conn, "payments", "refund_ref");
        boolean hasRefundedAt = hasColumn(conn, "payments", "refunded_at");
        boolean hasHeldBy = hasColumn(conn, "flight_seats", "held_by");
        boolean hasSeatActive = hasColumn(conn, "booking_seats", "is_active");

        String lockSql = hasRefundRef
                ? """
                  SELECT b.booking_id, b.booking_ref, b.user_id, b.status AS booking_status, b.total_amount,
                         f.departure_time, p.transaction_ref, p.refund_ref
                  FROM bookings b
                  JOIN flights f ON f.flight_id = b.flight_id
                  LEFT JOIN payments p ON p.booking_id = b.booking_id
                  WHERE b.booking_ref = ?
                  ORDER BY p.payment_id DESC NULLS LAST
                  FOR UPDATE OF b
                  """
                : """
                  SELECT b.booking_id, b.booking_ref, b.user_id, b.status AS booking_status, b.total_amount,
                         f.departure_time, p.transaction_ref, NULL::varchar AS refund_ref
                  FROM bookings b
                  JOIN flights f ON f.flight_id = b.flight_id
                  LEFT JOIN payments p ON p.booking_id = b.booking_id
                  WHERE b.booking_ref = ?
                  ORDER BY p.payment_id DESC NULLS LAST
                  FOR UPDATE OF b
                  """;

        long bookingId;
        long ownerUserId;
        String bookingStatus;
        BigDecimal totalAmount;
        Instant departureInstant;
        String txnRef;
        String existingRefundRef;

        try (PreparedStatement stmt = conn.prepareStatement(lockSql)) {
            stmt.setString(1, ref);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new NoSuchElementException("Booking not found: " + ref);
                }
                bookingId = rs.getLong("booking_id");
                ownerUserId = rs.getLong("user_id");
                bookingStatus = rs.getString("booking_status");
                totalAmount = rs.getBigDecimal("total_amount");
                Timestamp depTs = rs.getTimestamp("departure_time");
                departureInstant = depTs != null ? depTs.toInstant() : null;
                txnRef = rs.getString("transaction_ref");
                existingRefundRef = rs.getString("refund_ref");
            }
        }

        if (ownerUserId != userId) {
            logger.warn("Security violation: user {} attempted to cancel booking {} owned by user {}",
                    userId, ref, ownerUserId);
            throw new SecurityException("User " + userId + " is not authorized to cancel booking " + ref);
        }

        String normStatus = bookingStatus == null ? "" : bookingStatus.trim().toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(normStatus) || "REFUNDED".equals(normStatus)) {
            return new CancelResult(
                    false, true, ref, BigDecimal.ZERO, existingRefundRef,
                    "Booking " + ref + " is already cancelled.");
        }

        CancellationPolicy.Decision decision = policy.evaluate(departureInstant, bookingStatus, totalAmount);
        if (!decision.allowed()) {
            throw new IllegalStateException(decision.reason());
        }

        try (PreparedStatement updBooking = conn.prepareStatement(
                "UPDATE bookings SET status = 'CANCELLED'::booking_status WHERE booking_id = ?")) {
            updBooking.setLong(1, bookingId);
            updBooking.executeUpdate();
        }

        String releaseSeatsSql = hasHeldBy
                ? "UPDATE flight_seats SET status = 'AVAILABLE'::seat_status, held_until = NULL, held_by = NULL WHERE flight_seat_id IN (SELECT flight_seat_id FROM booking_seats WHERE booking_id = ?)"
                : "UPDATE flight_seats SET status = 'AVAILABLE'::seat_status, held_until = NULL WHERE flight_seat_id IN (SELECT flight_seat_id FROM booking_seats WHERE booking_id = ?)";
        try (PreparedStatement relSeats = conn.prepareStatement(releaseSeatsSql)) {
            relSeats.setLong(1, bookingId);
            relSeats.executeUpdate();
        }

        if (hasSeatActive) {
            try (PreparedStatement deact = conn.prepareStatement(
                    "UPDATE booking_seats SET is_active = FALSE WHERE booking_id = ?")) {
                deact.setLong(1, bookingId);
                deact.executeUpdate();
            }
        }

        String refundRef = null;
        if (txnRef != null && !txnRef.isBlank()) {
            // Invoke refund BEFORE commit so any gateway exception rolls back booking & seat updates atomically
            try {
                refundRef = paymentService.refundWithReference(txnRef);
            } catch (RuntimeException ex) {
                throw new RefundFailedException("Payment gateway refund failed for transaction " + txnRef, ex);
            }

            String updPaySql;
            if (hasRefundRef && hasRefundedAt) {
                updPaySql = "UPDATE payments SET status = 'REFUNDED'::payment_status, refund_ref = ?, refunded_at = NOW() WHERE booking_id = ?";
            } else if (hasRefundRef) {
                updPaySql = "UPDATE payments SET status = 'REFUNDED'::payment_status, refund_ref = ? WHERE booking_id = ?";
            } else {
                updPaySql = "UPDATE payments SET status = 'REFUNDED'::payment_status WHERE booking_id = ?";
            }
            try (PreparedStatement updPay = conn.prepareStatement(updPaySql)) {
                int idx = 1;
                if (hasRefundRef) {
                    updPay.setString(idx++, refundRef);
                }
                updPay.setLong(idx, bookingId);
                updPay.executeUpdate();
            }
        }

        logger.info("Cancelled booking {} (ID: {}) with refundRef={} for user {}", ref, bookingId, refundRef, userId);
        return new CancelResult(
                true, false, ref, decision.refundAmount(), refundRef,
                "Booking " + ref + " cancelled with a full refund.");
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

        String origTz = rs.getString("origin_timezone");
        ZoneId origZone = (origTz != null && !origTz.isBlank()) ? ZoneId.of(origTz) : ZoneId.of("UTC");
        String destTz = rs.getString("dest_timezone");
        ZoneId destZone = (destTz != null && !destTz.isBlank()) ? ZoneId.of(destTz) : ZoneId.of("UTC");

        Timestamp depTs = rs.getTimestamp("departure_time");
        if (depTs != null) {
            Instant depInstant = depTs.toInstant();
            rec.setDepartureInstant(depInstant);
            rec.setDepartureTime(FlightTimes.formatLocalTime(depInstant, origZone));
            rec.setDepartureDate(FlightTimes.formatLocalDate(depInstant, origZone));
        } else {
            rec.setDepartureTime("—");
            rec.setDepartureDate("—");
        }

        Timestamp arrTs = rs.getTimestamp("arrival_time");
        if (arrTs != null) {
            Instant arrInstant = arrTs.toInstant();
            rec.setArrivalInstant(arrInstant);
            rec.setArrivalTime(FlightTimes.formatLocalTime(arrInstant, destZone));
        } else {
            rec.setArrivalTime("—");
        }

        rec.setOrigin(rs.getString("origin_city") + " (" + rs.getString("origin_code") + ")");
        rec.setOriginCity(rs.getString("origin_city"));
        rec.setDestination(rs.getString("dest_city") + " (" + rs.getString("dest_code") + ")");
        rec.setDestinationCity(rs.getString("dest_city"));

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
