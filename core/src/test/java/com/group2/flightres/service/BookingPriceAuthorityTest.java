package com.group2.flightres.service;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.exception.PriceChangedException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
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

class BookingPriceAuthorityTest {

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
                    new BigDecimal("1200.00"), "Airbus A320", 149, 137, 12
            ));
        }
    };

    private static class TrackingBookingDao implements BookingDao {
        private final Map<String, BookingRecord> store = new HashMap<>();
        private int createBookingCalls = 0;

        @Override
        public BookingRecord createBooking(BookingContext context) {
            createBookingCalls++;
            BookingRecord rec = new BookingRecord();
            rec.setBookingId(999L);
            rec.setBookingReference(context.getBookingReference() != null ? context.getBookingReference() : "TST123");
            rec.setTotalAmount(context.getTotalPrice());
            rec.setStatus("CONFIRMED");
            rec.setCreatedAt(LocalDateTime.now());
            for (FlightSeat seat : context.getSelectedSeats()) {
                seat.setStatus(SeatStatus.BOOKED);
            }
            store.put(rec.getBookingReference(), rec);
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

        public int getCreateBookingCalls() {
            return createBookingCalls;
        }
    }

    @Test
    @DisplayName("PriceChangedException path charges nothing, inserts no booking, and changes no seat status")
    void testPriceChangedExceptionChargesNothingAndChangesNoSeat() {
        TrackingBookingDao bookingDao = new TrackingBookingDao();
        PricingService pricingService = new PricingService() {
            @Override
            public PriceQuote quote(long flightId, SeatClass seatClass, Clock c) {
                // Return total of 1440.00 for economy
                return PriceQuote.calculate(new BigDecimal("1200.00"), BigDecimal.ONE, new BigDecimal("1.00"), new BigDecimal("1.20"));
            }
        };

        BookingService service = new BookingService(bookingDao, MOCK_FLIGHT_DAO, pricingService, TEST_CLOCK);

        BookingContext context = new BookingContext();
        context.setFlightId(1L);
        context.setFlightNumber("GRP 204");
        context.setPassengerName("Jane Doe");
        context.setPassengerEmail("jane@example.com");
        context.setPassportNumber("A12345678");

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.HELD);
        context.setSelectedSeats(List.of(seat));

        // Client expects outdated price of 1200.00, but server recomputes to 1440.00
        context.setTotalPrice(new BigDecimal("1200.00"));

        PriceChangedException thrown = assertThrows(PriceChangedException.class, () -> service.createBooking(context));

        assertEquals(new BigDecimal("1200.00"), thrown.getExpectedTotal());
        assertEquals(new BigDecimal("1440.00"), thrown.getActualTotal());

        // Zero booking DAO calls executed
        assertEquals(0, bookingDao.getCreateBookingCalls(), "No DB booking creation must be attempted on price mismatch");
        assertTrue(bookingDao.store.isEmpty(), "No booking records must be stored");

        // Seat status remained HELD, never changed to BOOKED
        assertEquals(SeatStatus.HELD, seat.getStatus(), "Seat status must remain unchanged when price mismatch occurs");
    }

    @Test
    @DisplayName("Two-seat total: sum across multiple seats is authoritative and accurately persisted")
    void testTwoSeatAuthoritativeTotal() {
        TrackingBookingDao bookingDao = new TrackingBookingDao();
        PricingService pricingService = new PricingService() {
            @Override
            public PriceQuote quote(long flightId, SeatClass seatClass, Clock c) {
                if (seatClass == SeatClass.FIRST) {
                    // First class: 1200 * 3.50 * 1.00 * 1.20 = 5040.00
                    return PriceQuote.calculate(new BigDecimal("1200.00"), new BigDecimal("3.50"), new BigDecimal("1.00"), new BigDecimal("1.20"));
                } else {
                    // Economy: 1200 * 1.00 * 1.00 * 1.20 = 1440.00
                    return PriceQuote.calculate(new BigDecimal("1200.00"), BigDecimal.ONE, new BigDecimal("1.00"), new BigDecimal("1.20"));
                }
            }
        };

        BookingService service = new BookingService(bookingDao, MOCK_FLIGHT_DAO, pricingService, TEST_CLOCK);

        BookingContext context = new BookingContext();
        context.setFlightId(1L);
        context.setFlightNumber("GRP 204");
        context.setPassengerName("John Smith");
        context.setPassengerEmail("john@example.com");
        context.setPassportNumber("B98765432");

        FlightSeat seat1 = new FlightSeat();
        seat1.setFlightSeatId(1L);
        seat1.setSeatRow(1);
        seat1.setSeatCol('A');
        seat1.setSeatClass(SeatClass.FIRST);
        seat1.setStatus(SeatStatus.HELD);

        FlightSeat seat2 = new FlightSeat();
        seat2.setFlightSeatId(101L);
        seat2.setSeatRow(9);
        seat2.setSeatCol('E');
        seat2.setSeatClass(SeatClass.ECONOMY);
        seat2.setStatus(SeatStatus.HELD);

        context.setSelectedSeats(List.of(seat1, seat2));

        // Sum of both seats: 5040.00 + 1440.00 = 6480.00
        BigDecimal expectedSum = new BigDecimal("6480.00");
        context.setTotalPrice(expectedSum);

        BookingRecord record = service.createBooking(context);

        assertNotNull(record);
        assertEquals(expectedSum, record.getTotalAmount());
        assertEquals(expectedSum, context.getTotalPrice());
        assertEquals(2, context.getPriceQuotes().size());
        assertEquals(new BigDecimal("5040.00"), context.getPriceQuotes().get(0).total());
        assertEquals(new BigDecimal("1440.00"), context.getPriceQuotes().get(1).total());

        // Both seats are now BOOKED
        assertEquals(SeatStatus.BOOKED, seat1.getStatus());
        assertEquals(SeatStatus.BOOKED, seat2.getStatus());
    }
}
