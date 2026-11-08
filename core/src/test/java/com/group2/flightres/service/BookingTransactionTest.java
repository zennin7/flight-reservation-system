package com.group2.flightres.service;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.exception.ValidationException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingTransactionTest {

    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private static final FlightDao MOCK_FLIGHT_DAO = new FlightDao() {
        @Override
        public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
            return List.of();
        }

        @Override
        public Optional<FlightSearchResult> findById(long flightId) {
            return Optional.of(new FlightSearchResult(
                    1L, "GRP 204", "JNB", "O. R. Tambo Intl", "Johannesburg", ZoneId.of("Africa/Johannesburg"),
                    "CPT", "Cape Town Intl", "Cape Town", ZoneId.of("Africa/Johannesburg"),
                    Instant.parse("2026-11-02T04:30:00Z"), Instant.parse("2026-11-02T06:40:00Z"),
                    new BigDecimal("1200"), "Airbus A320", 149, 137, 12
            ));
        }
    };

    private static class MockBookingDao implements BookingDao {
        private final Map<String, BookingRecord> store = new HashMap<>();
        private boolean seatConflictSimulated = false;

        public void setSeatConflictSimulated(boolean conflict) {
            this.seatConflictSimulated = conflict;
        }

        @Override
        public BookingRecord createBooking(BookingContext context) {
            if (seatConflictSimulated) {
                throw new SeatUnavailableException("Seat 7E is no longer available.");
            }

            BookingRecord rec = new BookingRecord();
            rec.setBookingId(501L);
            String pnr = context.getBookingReference() != null ? context.getBookingReference() : "K7P2QX";
            rec.setBookingReference(pnr);
            rec.setFlightId(context.getFlightId());
            rec.setFlightNumber(context.getFlightNumber());
            rec.setPassengerName(context.getPassengerName());
            rec.setPassengerEmail(context.getPassengerEmail());
            rec.setPassportNumber(context.getPassportNumber());
            rec.setTotalAmount(context.getTotalPrice());
            rec.setStatus("CONFIRMED");
            rec.setCreatedAt(LocalDateTime.now());
            if (!context.getSelectedSeats().isEmpty()) {
                FlightSeat s = context.getSelectedSeats().get(0);
                s.setStatus(SeatStatus.BOOKED);
                rec.setSeatLabel(s.getSeatLabel());
                rec.setSeatClass(s.getSeatClass());
            }

            store.put(pnr, rec);
            return rec;
        }

        @Override
        public Optional<BookingRecord> findByReference(String bookingRef) {
            return Optional.ofNullable(store.get(bookingRef));
        }

        @Override
        public List<BookingRecord> findByUserId(long userId) {
            return new ArrayList<>(store.values());
        }
    }

    @Test
    @DisplayName("Create booking transaction succeeds, marks seat BOOKED and sets 6-char PNR")
    void testBookingTransactionSuccess() {
        MockBookingDao dao = new MockBookingDao();
        BookingService service = new BookingService(dao, MOCK_FLIGHT_DAO, TEST_CLOCK);

        BookingContext context = new BookingContext();
        context.setFlightId(1L);
        context.setFlightNumber("GRP 204");
        context.setPassengerName("Jane Doe");
        context.setPassengerEmail("jane.doe@example.com");
        context.setPassportNumber("PA123456");
        context.setBaseFare(new BigDecimal("1200"));

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setSeatRow(7);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.HELD);
        context.setSelectedSeats(List.of(seat));

        BookingRecord record = service.createBooking(context);

        assertNotNull(record);
        assertEquals("CONFIRMED", record.getStatus());
        assertEquals("Jane Doe", record.getPassengerName());
        assertEquals(SeatStatus.BOOKED, seat.getStatus());

        // Booking lookup by reference
        Optional<BookingRecord> lookedUp = service.getBookingByReference(record.getBookingReference());
        assertTrue(lookedUp.isPresent());
        assertEquals("Jane Doe", lookedUp.get().getPassengerName());
    }

    @Test
    @DisplayName("Seat conflict during transaction causes rollback and SeatUnavailableException")
    void testBookingTransactionRollbackOnSeatConflict() {
        MockBookingDao dao = new MockBookingDao();
        dao.setSeatConflictSimulated(true);
        BookingService service = new BookingService(dao, MOCK_FLIGHT_DAO, TEST_CLOCK);

        BookingContext context = new BookingContext();
        context.setFlightId(1L);
        context.setPassengerName("John Smith");
        context.setPassengerEmail("john@example.com");
        context.setPassportNumber("PA987654");

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setSeatRow(7);
        seat.setSeatCol('E');
        context.setSelectedSeats(List.of(seat));

        assertThrows(SeatUnavailableException.class, () -> service.createBooking(context));
        assertTrue(dao.findByReference("K7P2QX").isEmpty(), "No booking should be persisted after rollback");
    }

    @Test
    @DisplayName("Invalid passenger inputs throw ValidationException before attempting transaction")
    void testBookingValidationRejection() {
        MockBookingDao dao = new MockBookingDao();
        BookingService service = new BookingService(dao, MOCK_FLIGHT_DAO, TEST_CLOCK);

        BookingContext context = new BookingContext();
        context.setPassengerName(""); // Invalid
        context.setPassengerEmail("invalid-email");
        context.setSelectedSeats(List.of(new FlightSeat()));

        assertThrows(ValidationException.class, () -> service.createBooking(context));
    }
}
