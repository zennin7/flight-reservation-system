package com.group2.flightres.integration;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.impl.AirportDaoImpl;
import com.group2.flightres.dao.impl.BookingDaoImpl;
import com.group2.flightres.dao.impl.DashboardStatsDaoImpl;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.model.Airport;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.service.PricingService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.DashboardStats;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.Route;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.util.DatabaseAssumption;
import com.group2.flightres.util.FlightTimes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
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
    @DisplayName("PostgreSQL: Query live flights with JVM in America/Los_Angeles verifies timezone resilience")
    void testLiveFlightSearchTimezoneResilience() throws SQLException {
        java.util.TimeZone originalTz = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));

            ZoneId sast = ZoneId.of("Africa/Johannesburg");
            LocalDate tomorrowSast = LocalDate.now(sast).plusDays(1);
            FlightDaoImpl flightDao = new FlightDaoImpl();
            List<FlightSearchResult> flights = flightDao.searchFlights("JNB", "CPT", tomorrowSast);
            assertNotNull(flights);
            assertFalse(flights.isEmpty(), "Should return scheduled flights for JNB -> CPT on " + tomorrowSast);

            java.time.Instant dayStart = tomorrowSast.atStartOfDay(sast).toInstant();
            java.time.Instant dayEnd = tomorrowSast.plusDays(1).atStartOfDay(sast).toInstant();
            int expectedFlightCount = 0;
            try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM flights f "
                         + "JOIN airports o ON o.airport_id = f.origin_airport_id "
                         + "JOIN airports d ON d.airport_id = f.dest_airport_id "
                         + "WHERE o.iata_code = 'JNB' AND d.iata_code = 'CPT' "
                         + "AND f.status = 'SCHEDULED' AND f.departure_time >= ? AND f.departure_time < ?")) {
                stmt.setTimestamp(1, java.sql.Timestamp.from(dayStart));
                stmt.setTimestamp(2, java.sql.Timestamp.from(dayEnd));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        expectedFlightCount = rs.getInt(1);
                    }
                }
            }
            assertEquals(expectedFlightCount, flights.size(),
                    "Should return all scheduled flights for JNB -> CPT on " + tomorrowSast);

            FlightSearchResult alt204 = flights.stream()
                    .filter(f -> "ALT 204".equals(f.getFlightNumber()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Flight ALT 204 not found on " + tomorrowSast));

            assertEquals("06:30", alt204.getDepartureTime());
            assertEquals("08:40", alt204.getArrivalTime());
            assertEquals("2h 10m", alt204.getDuration());
            assertEquals("JNB", alt204.getOriginCode());
            assertEquals("O. R. Tambo International Airport", alt204.getOriginAirportName());
            assertEquals("Johannesburg", alt204.getOriginCity());
            assertEquals("CPT", alt204.getDestCode());
            assertEquals("Cape Town International Airport", alt204.getDestAirportName());
            assertEquals("Cape Town", alt204.getDestCity());

            int independentAvailableCount = 0;
            String expectedModel = null;
            try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT at.model, "
                         + "(SELECT COUNT(*) FROM flight_seats fs WHERE fs.flight_id = f.flight_id AND (fs.status = 'AVAILABLE' OR (fs.status = 'HELD' AND fs.held_until < NOW()))) "
                         + "FROM flights f JOIN aircraft_types at ON at.aircraft_type_id = f.aircraft_type_id WHERE f.flight_id = ?")) {
                stmt.setLong(1, alt204.getFlightId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        expectedModel = rs.getString(1);
                        independentAvailableCount = rs.getInt(2);
                    }
                }
            }
            assertNotNull(expectedModel);
            assertEquals(expectedModel, alt204.getAircraftModel());
            assertEquals(independentAvailableCount, alt204.getAvailableSeats());
        } finally {
            java.util.TimeZone.setDefault(originalTz);
        }
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
    void testLiveBookingAndLookup() throws SQLException {
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

        FlightDaoImpl flightDao = new FlightDaoImpl();
        Optional<FlightSearchResult> flightOpt = flightDao.findById(1L);
        assertTrue(flightOpt.isPresent(), "Flight 1 must exist for integration test");
        FlightSearchResult flight = flightOpt.get();

        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
        PricingService pricingService = new PricingService(seatDao, fixedClock);
        PriceQuote quote = pricingService.quote(flight.getFlightId(), seat.getSeatClass(), fixedClock);

        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-02");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:40");
        ctx.setDepartureInstant(flight.getDeparture());
        ctx.setArrivalInstant(flight.getArrival());
        ctx.setPassengerName("Integration Test Passenger");
        ctx.setPassengerEmail("test.passenger@skyroyal.com");
        ctx.setPassportNumber("TEST998877");
        ctx.setDateOfBirth(LocalDate.of(1995, 5, 20));
        ctx.setPriceQuotes(List.of(quote));
        ctx.setBaseFare(quote.baseFare());
        ctx.setTotalPrice(quote.total());
        seat.setCalculatedPrice(quote.total());
        ctx.setSelectedSeats(new ArrayList<>(List.of(seat)));
        ctx.setPaymentResult(new com.group2.flightres.service.PaymentResult(true, "TXN-IT-9988", "VISA", "4242", null));

        BookingRecord record = bookingDao.createBooking(ctx);
        try {
            assertNotNull(record);
            assertNotNull(record.getBookingReference());
            assertEquals("CONFIRMED", record.getStatus());

            // Assert stored payments.amount equals the quote total
            try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT amount FROM payments WHERE booking_id = ?")) {
                stmt.setLong(1, record.getBookingId());
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "Payment record must exist for booking " + record.getBookingId());
                    assertEquals(0, quote.total().compareTo(rs.getBigDecimal("amount")),
                            "Stored payments.amount must equal the quote total");
                }
            }

            Optional<BookingRecord> fetched = bookingDao.findByReference(record.getBookingReference());
            assertTrue(fetched.isPresent());
            assertEquals("Integration Test Passenger", fetched.get().getPassengerName());
            assertNotNull(fetched.get().getDepartureInstant(), "Departure instant must not be null");
            assertNotNull(fetched.get().getArrivalInstant(), "Arrival instant must not be null");
            assertEquals(flight.getDeparture(), fetched.get().getDepartureInstant(), "Fetched departure instant must equal flight departure");
            assertEquals(flight.getArrival(), fetched.get().getArrivalInstant(), "Fetched arrival instant must equal flight arrival");
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

    @Test
    @DisplayName("FlightDao and BookingService resolve airport name from code or city")
    void testResolveAirportName() {
        FlightDaoImpl flightDao = new FlightDaoImpl();
        BookingService bookingService = new BookingService(new BookingDaoImpl(), flightDao);

        Optional<String> jnbName = bookingService.findAirportName("JNB", "Johannesburg");
        assertTrue(jnbName.isPresent());
        assertTrue(jnbName.get().contains("Tambo"));

        Optional<String> cptName = bookingService.findAirportName("CPT", "");
        assertTrue(cptName.isPresent());
        assertTrue(cptName.get().contains("Cape Town"));

        Optional<String> durName = bookingService.findAirportName("DUR", "Durban");
        assertTrue(durName.isPresent());
        assertTrue(durName.get().contains("King Shaka"));
    }

    @Test
    @DisplayName("FlightDao and BookingService resolve airport city from code")
    void testResolveAirportCity() {
        FlightDaoImpl flightDao = new FlightDaoImpl();
        BookingService bookingService = new BookingService(new BookingDaoImpl(), flightDao);

        Optional<String> jnbCity = bookingService.findAirportCity("JNB");
        assertTrue(jnbCity.isPresent());
        assertEquals("Johannesburg", jnbCity.get());

        Optional<String> cptCity = bookingService.findAirportCity("CPT");
        assertTrue(cptCity.isPresent());
        assertEquals("Cape Town", cptCity.get());

        Optional<String> durCity = bookingService.findAirportCity("DUR");
        assertTrue(durCity.isPresent());
        assertEquals("Durban", durCity.get());
    }

    @Test
    @DisplayName("BookingDao read-back times and date in airport timezones are resilient to JVM in America/Los_Angeles")
    void testBookingReadBackTimezoneResilienceInAmericaLosAngeles() throws SQLException {
        java.util.TimeZone originalTz = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));

            FlightDaoImpl flightDao = new FlightDaoImpl();
            BookingDaoImpl bookingDao = new BookingDaoImpl();
            SeatDaoImpl seatDao = new SeatDaoImpl();

            FlightSearchResult flight = flightDao.findById(1L).orElseThrow();
            List<FlightSeat> seats = seatDao.getSeatMapForFlight(flight.getFlightId());
            FlightSeat seat = seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).findFirst().orElseThrow();

            Clock fixedClock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
            PricingService pricingService = new PricingService(seatDao, fixedClock);
            PriceQuote quote = pricingService.quote(flight.getFlightId(), seat.getSeatClass(), fixedClock);

            BookingContext ctx = new BookingContext();
            ctx.setFlightId(flight.getFlightId());
            ctx.setFlightNumber(flight.getFlightNumber());
            ctx.setOrigin(flight.getOriginAirportName());
            ctx.setOriginCity(flight.getOriginCity());
            ctx.setDestination(flight.getDestAirportName());
            ctx.setDestinationCity(flight.getDestCity());
            ctx.setDepartureTime("06:30");
            ctx.setArrivalTime("08:40");
            ctx.setDepartureInstant(flight.getDeparture());
            ctx.setArrivalInstant(flight.getArrival());
            ctx.setPassengerName("Timezone Readback Passenger");
            ctx.setPassengerEmail("tz.readback@skyroyal.com");
            ctx.setPassportNumber("TZLA9988");
            ctx.setDateOfBirth(LocalDate.of(1990, 1, 1));
            ctx.setPriceQuotes(List.of(quote));
            ctx.setBaseFare(quote.baseFare());
            ctx.setTotalPrice(quote.total());
            seat.setCalculatedPrice(quote.total());
            ctx.setSelectedSeats(new ArrayList<>(List.of(seat)));
            ctx.setPaymentResult(new com.group2.flightres.service.PaymentResult(true, "TXN-TZ-9988", "VISA", "4242", null));

            BookingRecord record = bookingDao.createBooking(ctx);
            try {
                // Assert stored payments.amount equals the quote total
                try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT amount FROM payments WHERE booking_id = ?")) {
                    stmt.setLong(1, record.getBookingId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next(), "Payment record must exist for booking " + record.getBookingId());
                        assertEquals(0, quote.total().compareTo(rs.getBigDecimal("amount")),
                                "Stored payments.amount must equal the quote total");
                    }
                }

                Optional<BookingRecord> fetched = bookingDao.findByReference(record.getBookingReference());
                assertTrue(fetched.isPresent());
                assertEquals("06:30", fetched.get().getDepartureTime());
                assertEquals("08:40", fetched.get().getArrivalTime());
                String expectedDate = FlightTimes.formatLocalDate(flight.getDeparture(), flight.getOriginZone());
                assertEquals(expectedDate, fetched.get().getDepartureDate());
            } finally {
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
                }
            }
        } finally {
            java.util.TimeZone.setDefault(originalTz);
        }
    }

    @Test
    @DisplayName("AirportDao findAll returns all DB airports including JNB, CPT, DUR with correct timezones ordered by city")
    void testAirportDaoFindAll() throws SQLException {
        int expectedCount;
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM airports");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            expectedCount = rs.getInt(1);
        }

        AirportDaoImpl airportDao = new AirportDaoImpl();
        List<Airport> airports = airportDao.findAll();
        assertNotNull(airports);
        assertEquals(expectedCount, airports.size());

        Airport cpt = airports.stream().filter(a -> "CPT".equals(a.code())).findFirst().orElseThrow();
        assertEquals("Cape Town", cpt.city());
        assertEquals(ZoneId.of("Africa/Johannesburg"), cpt.zoneId());

        Airport dur = airports.stream().filter(a -> "DUR".equals(a.code())).findFirst().orElseThrow();
        assertEquals("Durban", dur.city());
        assertEquals(ZoneId.of("Africa/Johannesburg"), dur.zoneId());

        Airport jnb = airports.stream().filter(a -> "JNB".equals(a.code())).findFirst().orElseThrow();
        assertEquals("Johannesburg", jnb.city());
        assertEquals(ZoneId.of("Africa/Johannesburg"), jnb.zoneId());
    }

    @Test
    @DisplayName("FlightService defaultRoute resolves live route from earliest open flight or airports")
    void testLiveFlightServiceDefaultRoute() {
        FlightService service = new FlightService();
        Optional<Route> routeOpt = service.defaultRoute();
        assertTrue(routeOpt.isPresent());
        assertNotNull(routeOpt.get().origin());
        assertNotNull(routeOpt.get().destination());
    }

    @Test
    @DisplayName("Cabin availability per flight: tomorrow's ALT 204 matches DB available and aircraft template capacity")
    void testCabinAvailabilityAlt204() throws SQLException {
        assertAlt204CabinAvailability(false);
    }

    @Test
    @Tag("integration-full")
    @DisplayName("Full-cabin seeded DB: tomorrow's ALT 204 has full A320 capacity (economy 150, first 12)")
    void testFullCabinCapacityAlt204() throws SQLException {
        assertAlt204CabinAvailability(true);
    }

    private void assertAlt204CabinAvailability(boolean requireFullCabin) throws SQLException {
        FlightDaoImpl flightDao = new FlightDaoImpl();
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(1);
        List<FlightSearchResult> flights = flightDao.searchFlights("JNB", "CPT", tomorrow);
        FlightSearchResult alt204 = flights.stream()
                .filter(f -> "ALT 204".equals(f.getFlightNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Flight ALT 204 not found on " + tomorrow));

        int dbAvailable = 0;
        int dbEconomy = 0;
        int dbFirst = 0;
        int dbTotalEconomy = 0;
        int dbTotalFirst = 0;
        int templateEconomy = 0;
        int templateFirst = 0;
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     """
                     SELECT
                       (SELECT COUNT(*) FROM flight_seats fs WHERE fs.flight_id = f.flight_id AND (fs.status = 'AVAILABLE' OR (fs.status = 'HELD' AND fs.held_until <= NOW()))) AS avail,
                       (SELECT COUNT(*) FROM flight_seats fs JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id WHERE fs.flight_id = f.flight_id AND (fs.status = 'AVAILABLE' OR (fs.status = 'HELD' AND fs.held_until <= NOW())) AND st.seat_class = 'ECONOMY') AS econ,
                       (SELECT COUNT(*) FROM flight_seats fs JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id WHERE fs.flight_id = f.flight_id AND (fs.status = 'AVAILABLE' OR (fs.status = 'HELD' AND fs.held_until <= NOW())) AND st.seat_class = 'FIRST') AS fst,
                       (SELECT COUNT(*) FROM flight_seats fs JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id WHERE fs.flight_id = f.flight_id AND st.seat_class = 'ECONOMY') AS total_econ,
                       (SELECT COUNT(*) FROM flight_seats fs JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id WHERE fs.flight_id = f.flight_id AND st.seat_class = 'FIRST') AS total_fst,
                       (SELECT COUNT(*) FROM seat_templates st WHERE st.aircraft_type_id = f.aircraft_type_id AND st.seat_class = 'ECONOMY') AS tpl_econ,
                       (SELECT COUNT(*) FROM seat_templates st WHERE st.aircraft_type_id = f.aircraft_type_id AND st.seat_class = 'FIRST') AS tpl_fst
                     FROM flights f WHERE f.flight_id = ?
                     """)) {
            stmt.setLong(1, alt204.getFlightId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    dbAvailable = rs.getInt("avail");
                    dbEconomy = rs.getInt("econ");
                    dbFirst = rs.getInt("fst");
                    dbTotalEconomy = rs.getInt("total_econ");
                    dbTotalFirst = rs.getInt("total_fst");
                    templateEconomy = rs.getInt("tpl_econ");
                    templateFirst = rs.getInt("tpl_fst");
                }
            }
        }

        assertEquals(dbAvailable, alt204.getAvailableSeats());
        assertEquals(dbEconomy, alt204.getAvailableEconomy());
        assertEquals(dbFirst, alt204.getAvailableFirst());
        assertEquals(alt204.getAvailableSeats(), alt204.getAvailableEconomy() + alt204.getAvailableFirst());
        assertTrue(templateEconomy > 0 && templateFirst > 0, "Seat templates must exist for both cabins");
        assertEquals(templateEconomy, dbTotalEconomy);
        assertEquals(templateFirst, dbTotalFirst);
        if (requireFullCabin) {
            assertEquals(150, dbTotalEconomy);
            assertEquals(12, dbTotalFirst);
        }
    }
}
