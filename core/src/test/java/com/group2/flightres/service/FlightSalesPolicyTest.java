package com.group2.flightres.service;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightSalesPolicyTest {

    private static final Instant DEPARTURE = Instant.parse("2026-11-02T10:00:00Z");
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private static final FlightSearchResult TEST_FLIGHT = new FlightSearchResult(
            55L,
            "GRP 204",
            "JNB",
            "O. R. Tambo International Airport",
            "Johannesburg",
            SAST,
            "CPT",
            "Cape Town International Airport",
            "Cape Town",
            SAST,
            DEPARTURE,
            DEPARTURE.plusSeconds(7200),
            new BigDecimal("1200"),
            "Airbus A320",
            100,
            90,
            10
    );

    @Test
    @DisplayName("Flight is open strictly before 60-minute cutoff (61 minutes before)")
    void testOpenAt61MinutesBefore() {
        Instant now = DEPARTURE.minusSeconds(61 * 60);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        assertTrue(FlightSalesPolicy.isOpen(DEPARTURE, clock), "Flight should be open 61 minutes before departure");
    }

    @Test
    @DisplayName("Flight is closed exactly at 60-minute cutoff (60 minutes before)")
    void testClosedAt60MinutesBefore() {
        Instant now = DEPARTURE.minusSeconds(60 * 60);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        assertFalse(FlightSalesPolicy.isOpen(DEPARTURE, clock), "Flight should be closed exactly 60 minutes before departure");
    }

    @Test
    @DisplayName("Flight is closed inside 60-minute window (59 minutes before)")
    void testClosedAt59MinutesBefore() {
        Instant now = DEPARTURE.minusSeconds(59 * 60);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        assertFalse(FlightSalesPolicy.isOpen(DEPARTURE, clock), "Flight should be closed 59 minutes before departure");
    }

    @Test
    @DisplayName("Flight is closed if departure time has already passed")
    void testClosedAfterDeparture() {
        Instant now = DEPARTURE.plusSeconds(300);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        assertFalse(FlightSalesPolicy.isOpen(DEPARTURE, clock), "Flight should be closed after departure");
    }

    @Test
    @DisplayName("Flight is closed if departure or clock is null")
    void testNullHandling() {
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        assertFalse(FlightSalesPolicy.isOpen(null, clock));
        assertFalse(FlightSalesPolicy.isOpen(DEPARTURE, null));
    }

    @Test
    @DisplayName("FlightService.searchFlights drops closed flights from search results")
    void testSearchFlightsExcludesClosedFlights() {
        FlightDao mockDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
                return List.of(TEST_FLIGHT);
            }
            @Override
            public Optional<FlightSearchResult> findById(long flightId) {
                return Optional.of(TEST_FLIGHT);
            }
        };

        // When clock is 45 minutes before departure -> closed
        Clock closedClock = Clock.fixed(DEPARTURE.minusSeconds(45 * 60), ZoneOffset.UTC);
        FlightService closedService = new FlightService(mockDao, closedClock);
        List<FlightSearchResult> closedResults = closedService.searchFlights("JNB", "CPT", LocalDate.of(2026, 11, 2));
        assertTrue(closedResults.isEmpty(), "Closed flight must not be returned by search");

        // When clock is 2 hours before departure -> open
        Clock openClock = Clock.fixed(DEPARTURE.minusSeconds(120 * 60), ZoneOffset.UTC);
        FlightService openService = new FlightService(mockDao, openClock);
        List<FlightSearchResult> openResults = openService.searchFlights("JNB", "CPT", LocalDate.of(2026, 11, 2));
        assertEquals(1, openResults.size(), "Open flight must be returned by search");
    }

    @Test
    @DisplayName("SeatMapService.selectSeat throws clear user-presentable message when flight is closed")
    void testSeatHoldRejectedForClosedFlight() {
        FlightDao mockFlightDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
                return List.of(TEST_FLIGHT);
            }
            @Override
            public Optional<FlightSearchResult> findById(long flightId) {
                return Optional.of(TEST_FLIGHT);
            }
        };
        SeatDao mockSeatDao = new SeatDao() {
            @Override
            public List<FlightSeat> getSeatMapForFlight(long flightId) { return List.of(); }
            @Override
            public boolean holdSeat(long flightSeatId) { return true; }
            @Override
            public boolean releaseSeat(long flightSeatId) { return true; }
            @Override
            public boolean bookSeat(long flightSeatId) { return true; }
            @Override
            public int releaseExpiredHolds() { return 0; }
            @Override
            public Optional<FlightSeat> findSeatById(long flightSeatId) { return Optional.empty(); }
        };

        // 30 minutes before departure -> closed
        Clock clock = Clock.fixed(DEPARTURE.minusSeconds(30 * 60), ZoneOffset.UTC);
        SeatMapService service = new SeatMapService(mockSeatDao, mockFlightDao, clock);

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(10L);
        seat.setFlightId(TEST_FLIGHT.getFlightId());
        seat.setSeatRow(4);
        seat.setSeatCol('B');

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.selectSeat(seat));
        assertTrue(ex.getMessage().contains("Flight GRP 204 is closed for sales (sales close 60 minutes before departure)."),
                "Exception message must clearly inform that flight is closed for sales: " + ex.getMessage());
    }

    @Test
    @DisplayName("BookingService.createBooking throws clear user-presentable message when flight is closed")
    void testBookingCreationRejectedForClosedFlight() {
        FlightDao mockFlightDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
                return List.of(TEST_FLIGHT);
            }
            @Override
            public Optional<FlightSearchResult> findById(long flightId) {
                return Optional.of(TEST_FLIGHT);
            }
        };
        BookingDao mockBookingDao = new BookingDao() {
            @Override
            public BookingRecord createBooking(BookingContext context) { return new BookingRecord(); }
            @Override
            public Optional<BookingRecord> findByReference(String bookingReference) { return Optional.empty(); }
            @Override
            public List<BookingRecord> findByUserId(long userId) { return List.of(); }
        };

        // 10 minutes before departure -> closed
        Clock clock = Clock.fixed(DEPARTURE.minusSeconds(10 * 60), ZoneOffset.UTC);
        BookingService service = new BookingService(mockBookingDao, mockFlightDao, clock);

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(TEST_FLIGHT.getFlightId());
        ctx.setFlightNumber(TEST_FLIGHT.getFlightNumber());
        ctx.setPassengerName("Naledi Pandor");
        ctx.setPassengerEmail("naledi@example.com");
        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(10L);
        seat.setSeatRow(4);
        seat.setSeatCol('B');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.createBooking(ctx));
        assertTrue(ex.getMessage().contains("Flight GRP 204 is closed for sales (sales close 60 minutes before departure)."),
                "Exception message must clearly inform that flight is closed for sales: " + ex.getMessage());
    }
}
