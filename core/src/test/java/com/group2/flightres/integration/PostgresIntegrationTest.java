package com.group2.flightres.integration;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.impl.BookingDaoImpl;
import com.group2.flightres.dao.impl.DashboardStatsDaoImpl;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.DashboardStats;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.util.DatabaseAssumption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests exercising live PostgreSQL DAO operations.
 * Automatically skips itself when PostgreSQL is offline or app.json is missing.
 */
class PostgresIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(PostgresIntegrationTest.class);

    @BeforeEach
    void setUp() {
        DatabaseAssumption.assumeDatabaseAvailable();
    }

    @Test
    @DisplayName("PostgreSQL: Query live flights between major South African corridors")
    void testLiveFlightSearch() {
        FlightDaoImpl flightDao = new FlightDaoImpl();
        List<FlightSearchResult> flights = flightDao.searchFlights("JNB", "CPT", LocalDate.of(2026, 11, 2));
        assertNotNull(flights);
        assertFalse(flights.isEmpty(), "Should return seeded flights for JNB -> CPT corridor");
        assertTrue(flights.stream().anyMatch(f -> "GRP 204".equals(f.getFlightNumber())));
    }

    @Test
    @DisplayName("PostgreSQL: Query cabin seat map layout for scheduled flight")
    void testLiveSeatMap() {
        SeatDaoImpl seatDao = new SeatDaoImpl();
        List<FlightSeat> seats = seatDao.getSeatMapForFlight(1L);
        assertNotNull(seats);
        assertFalse(seats.isEmpty(), "Flight 1 should have pre-seeded cabin seats");
        assertTrue(seats.stream().anyMatch(s -> s.getSeatClass() == SeatClass.FIRST));
        assertTrue(seats.stream().anyMatch(s -> s.getSeatClass() == SeatClass.ECONOMY));
    }

    @Test
    @DisplayName("PostgreSQL: Aggregate dashboard KPIs directly from live database")
    void testLiveDashboardStats() {
        DashboardStatsDaoImpl statsDao = new DashboardStatsDaoImpl();
        DashboardStats stats = statsDao.getDashboardStats();
        assertNotNull(stats);
        assertTrue(stats.getTotalBookings() >= 0);
        assertNotNull(stats.getTotalRevenue());
    }

    @Test
    @DisplayName("PostgreSQL: Atomic booking insertion and lookup by PNR reference")
    void testLiveBookingAndLookup() {
        BookingDaoImpl bookingDao = new BookingDaoImpl();
        SeatDaoImpl seatDao = new SeatDaoImpl();

        // Find an available seat on flight 1
        List<FlightSeat> seats = seatDao.getSeatMapForFlight(1L);
        Optional<FlightSeat> availableSeat = seats.stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .findFirst();

        assertTrue(availableSeat.isPresent(), "No available seats found on flight 1 to execute booking integration test");

        FlightSeat seat = availableSeat.get();
        boolean held = seatDao.holdSeat(seat.getFlightSeatId());
        assertTrue(held, "Failed to hold available seat " + seat.getFlightSeatId() + " on flight 1");

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-02");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:40");
        ctx.setPassengerName("Integration Test Passenger");
        ctx.setPassengerEmail("test.passenger@skyvoyage.com");
        ctx.setPassportNumber("TEST998877");
        ctx.setBaseFare(seat.getCalculatedPrice() != null ? seat.getCalculatedPrice() : new BigDecimal("1200.00"));
        ctx.setSelectedSeats(new ArrayList<>(List.of(seat)));

        BookingRecord record = bookingDao.createBooking(ctx);
        try {
            assertNotNull(record);
            assertNotNull(record.getBookingReference());
            assertEquals("CONFIRMED", record.getStatus());

            Optional<BookingRecord> fetched = bookingDao.findByReference(record.getBookingReference());
            assertTrue(fetched.isPresent());
            assertEquals("Integration Test Passenger", fetched.get().getPassengerName());
        } finally {
            // Teardown: delete test booking records and restore flight seat to AVAILABLE
            try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement delBS = conn.prepareStatement("DELETE FROM booking_seats WHERE booking_id = ?");
                     PreparedStatement delPay = conn.prepareStatement("DELETE FROM payments WHERE booking_id = ?");
                     PreparedStatement delB = conn.prepareStatement("DELETE FROM bookings WHERE booking_id = ?");
                     PreparedStatement resetSeat = conn.prepareStatement("UPDATE flight_seats SET status = 'AVAILABLE', held_until = NULL WHERE flight_seat_id = ?")) {

                    delBS.setLong(1, record.getBookingId());
                    delBS.executeUpdate();

                    delPay.setLong(1, record.getBookingId());
                    delPay.executeUpdate();

                    delB.setLong(1, record.getBookingId());
                    delB.executeUpdate();

                    resetSeat.setLong(1, seat.getFlightSeatId());
                    resetSeat.executeUpdate();

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (Exception e) {
                logger.error("Failed to clean up test booking records: {}", e.getMessage(), e);
                fail("Cleanup failed after testLiveBookingAndLookup: " + e.getMessage(), e);
            }
        }
    }
}
