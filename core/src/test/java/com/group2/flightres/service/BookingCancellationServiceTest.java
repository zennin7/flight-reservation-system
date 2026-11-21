package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.BookingDaoImpl;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.CancelResult;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingCancellationServiceTest {

    @Test
    @DisplayName("Service enforces ownership and cancels/refunds atomically inside rollback-safe Postgres transaction")
    void enforcesOwnershipAndAtomicRollbackInRealTransaction() throws Exception {
        BookingDaoImpl bookingDao = new BookingDaoImpl();
        FlightDao flightDao = new FlightDaoImpl();
        BookingService service = new BookingService(bookingDao, flightDao, Clock.systemUTC());

        // Pick a real future flight (>48h away) and an AVAILABLE seat without mutating DB permanently
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long flightId = -1;
                long seatId = -1;
                int seatRow = 0;
                char seatCol = 'A';
                SeatClass seatClass = SeatClass.ECONOMY;
                BigDecimal basePrice = new BigDecimal("1200.00");

                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT f.flight_id, f.base_price, fs.flight_seat_id, st.seat_row, st.seat_col, st.seat_class
                        FROM flights f
                        JOIN flight_seats fs ON fs.flight_id = f.flight_id
                        JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
                        WHERE f.departure_time > NOW() + INTERVAL '48 hours'
                          AND fs.status = 'AVAILABLE'
                        LIMIT 1
                        """);
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Seeded DB must have an available seat on a flight >48h ahead");
                    flightId = rs.getLong("flight_id");
                    basePrice = rs.getBigDecimal("base_price");
                    seatId = rs.getLong("flight_seat_id");
                    seatRow = rs.getInt("seat_row");
                    seatCol = rs.getString("seat_col").charAt(0);
                    seatClass = SeatClass.valueOf(rs.getString("seat_class"));
                }

                BookingContext ctx = new BookingContext();
                ctx.setFlightId(flightId);
                ctx.setFlightNumber("GRP 999");
                ctx.setOrigin("Johannesburg (JNB)");
                ctx.setDestination("Cape Town (CPT)");
                ctx.setDepartureDate("2026-11-28");
                ctx.setDepartureTime("09:00");
                ctx.setArrivalTime("11:10");
                ctx.setPassengerName("Cancel Test Passenger");
                ctx.setPassengerEmail("cancel-tx@example.com");
                ctx.setPassportNumber("P11223344");
                ctx.setDateOfBirth(LocalDate.of(1993, 4, 15));
                FlightSeat seat = new FlightSeat();
                seat.setFlightSeatId(seatId);
                seat.setFlightId(flightId);
                seat.setSeatRow(seatRow);
                seat.setSeatCol(seatCol);
                seat.setSeatClass(seatClass);
                seat.setStatus(SeatStatus.HELD);
                seat.setCalculatedPrice(basePrice);
                ctx.setSelectedSeats(List.of(seat));
                ctx.setTotalPrice(basePrice);
                ctx.setPaymentResult(PaymentResult.approved("TXN-ROLLBACK1", "VISA", "4242"));

                BookingRecord created = bookingDao.createBookingInTransaction(conn, ctx);
                long ownerId = created.getUserId();
                String ref = created.getBookingReference();
                assertEquals("CONFIRMED", created.getStatus());

                // 1. Wrong user gets SecurityException
                CancellationPolicy openPolicy = new CancellationPolicy(Clock.systemUTC());
                PaymentService failingGateway = new PaymentService() {
                    @Override
                    public PaymentResult processPayment(String c, String e, String v, BigDecimal a) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void refund(String reference) {
                        throw new IllegalStateException("Simulated gateway refund outage");
                    }
                };
                assertThrows(SecurityException.class,
                        () -> bookingDao.cancelBookingInTransaction(conn, ownerId + 999, ref, openPolicy, failingGateway));

                // 2. Gateway refund failure throws and caller rollbacks via savepoint leaving booking CONFIRMED & seat BOOKED
                var sp = conn.setSavepoint("before_failed_cancel");
                assertThrows(com.group2.flightres.exception.RefundFailedException.class,
                        () -> bookingDao.cancelBookingInTransaction(conn, ownerId, ref, openPolicy, failingGateway));
                conn.rollback(sp);

                BookingRecord stillActive = bookingDao.findByReference(conn, ref).orElseThrow();
                assertEquals("CONFIRMED", stillActive.getStatus());

                // 3. Valid cancel succeeds and frees seat back to AVAILABLE
                AtomicInteger refundCalls = new AtomicInteger(0);
                SimulatedPaymentGateway okGateway = new SimulatedPaymentGateway() {
                    @Override
                    public void refund(String reference) {
                        refundCalls.incrementAndGet();
                        super.refund(reference);
                    }
                };
                CancelResult r1 = bookingDao.cancelBookingInTransaction(conn, ownerId, ref, openPolicy, okGateway);
                assertTrue(r1.cancelled());
                assertFalse(r1.alreadyCancelled());
                assertNotNull(r1.refundReference());
                assertEquals(1, refundCalls.get());

                BookingRecord nowCancelled = bookingDao.findByReference(conn, ref).orElseThrow();
                assertEquals("CANCELLED", nowCancelled.getStatus());
                try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM flight_seats WHERE flight_seat_id = ?")) {
                    ps.setLong(1, seatId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("AVAILABLE", rs.getString("status"));
                    }
                }

                // 4. Second cancel returns alreadyCancelled and never refunds twice
                CancelResult r2 = bookingDao.cancelBookingInTransaction(conn, ownerId, ref, openPolicy, okGateway);
                assertFalse(r2.cancelled());
                assertTrue(r2.alreadyCancelled());
                assertEquals(1, refundCalls.get());
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    @Test
    @DisplayName("Service wrapper delegates cancellation cleanly and rejects blank reference")
    void serviceEnforcesArgumentsAndDelegation() {
        AtomicInteger daoCalls = new AtomicInteger(0);
        BookingDao mockDao = new BookingDao() {
            @Override
            public BookingRecord createBooking(BookingContext context) { return null; }

            @Override
            public Optional<BookingRecord> findByReference(String bookingRef) { return Optional.empty(); }

            @Override
            public List<BookingRecord> findByUserId(long userId) { return List.of(); }

            @Override
            public CancelResult cancelBookingForUser(long userId, String bookingReference, CancellationPolicy policy, PaymentService paymentService) {
                daoCalls.incrementAndGet();
                return new CancelResult(true, false, bookingReference, new BigDecimal("1000"), "REF-TXN", "Cancelled");
            }
        };
        FlightDao emptyFlightDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String o, String d, LocalDate dt) { return List.of(); }

            @Override
            public Optional<FlightSearchResult> findById(long flightId) { return Optional.empty(); }
        };
        BookingService svc = new BookingService(mockDao, emptyFlightDao);

        assertThrows(IllegalArgumentException.class,
                () -> svc.cancelBookingForUser(1L, " ", new SimulatedPaymentGateway()));
        CancelResult res = svc.cancelBookingForUser(1L, " ab12cd ", new SimulatedPaymentGateway());
        assertEquals("AB12CD", res.bookingReference());
        assertEquals(1, daoCalls.get());
    }
}
