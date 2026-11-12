package com.group2.flightres.integration;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.AirportDao;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.impl.AirportDaoImpl;
import com.group2.flightres.dao.impl.BookingDaoImpl;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PricingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceAlignmentTest {

    @Test
    @DisplayName("Flight search price == seat map cheapest economy quote == checkout total for a real flight")
    void testPriceAlignmentEndToEnd() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneId.of("Africa/Johannesburg"));

        SeatDao seatDao = new SeatDaoImpl();
        FlightDao flightDao = new FlightDaoImpl();
        AirportDao airportDao = new AirportDaoImpl();
        PricingService pricingService = new PricingService(seatDao, clock);
        FlightService flightService = new FlightService(flightDao, airportDao, pricingService, clock);

        LocalDate tomorrow = LocalDate.of(2026, 11, 9);
        List<FlightSearchResult> searchResults = flightService.searchFlights("Johannesburg (JNB)", "Cape Town (CPT)", tomorrow);
        assertFalse(searchResults.isEmpty(), "Must find flights for tomorrow on JNB -> CPT");

        // Pick the first result (e.g. ALT 204 or ALT 205)
        FlightSearchResult selectedFlight = searchResults.get(0);
        BigDecimal searchPrice = selectedFlight.getBasePrice();
        assertNotNull(searchPrice, "Search result must have a computed quote base price");

        // Step 2: Seat Map quotes the selected available economy seat
        PriceQuote seatMapQuote = pricingService.quote(selectedFlight.getFlightId(), SeatClass.ECONOMY, clock);
        BigDecimal seatMapEconomyPrice = seatMapQuote.total();

        // Step 3: Checkout receives BookingContext with this quote
        BookingContext context = new BookingContext();
        context.setFlightId(selectedFlight.getFlightId());
        context.setFlightNumber(selectedFlight.getFlightNumber());
        context.setBaseFare(selectedFlight.getBasePrice());

        FlightSeat seat = new FlightSeat();
        seat.setFlightId(selectedFlight.getFlightId());
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.HELD);
        context.setSelectedSeats(List.of(seat));
        context.setPriceQuotes(List.of(seatMapQuote));

        BigDecimal checkoutTotal = context.getTotalPrice();

        // PROVE: search price == seat map's cheapest economy quote == checkout total
        assertEquals(searchPrice, seatMapEconomyPrice,
                "Flight search price must exactly match seat map economy quote total");
        assertEquals(seatMapEconomyPrice, checkoutTotal,
                "Seat map economy quote total must exactly match checkout total");
    }

    @Test
    @DisplayName("Gate 5: Confirmed total == payments.amount == quote total (no fixed fees)")
    void testConfirmedTotalEqualsPaymentsAmountEqualsQuoteTotal() throws SQLException {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Africa/Johannesburg"));

        SeatDao seatDao = new SeatDaoImpl();
        FlightDao flightDao = new FlightDaoImpl();
        PricingService pricingService = new PricingService(seatDao, clock);
        BookingDao bookingDao = new BookingDaoImpl();

        FlightSearchResult flight = flightDao.findById(1L).orElseThrow();
        List<FlightSeat> seats = seatDao.getSeatMapForFlight(flight.getFlightId());
        FlightSeat seat = seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).findFirst().orElseThrow();

        // Real quote via PricingService
        PriceQuote quote = pricingService.quote(flight.getFlightId(), seat.getSeatClass(), clock);
        assertNotNull(quote);

        // Build context with authoritative quote
        BookingContext ctx = new BookingContext();
        ctx.setFlightId(flight.getFlightId());
        ctx.setFlightNumber(flight.getFlightNumber());
        ctx.setOrigin(flight.getOriginAirportName());
        ctx.setDestination(flight.getDestAirportName());
        ctx.setDepartureInstant(flight.getDeparture());
        ctx.setArrivalInstant(flight.getArrival());
        ctx.setPassengerName("Price Reconciliation Passenger");
        ctx.setPassengerEmail("recon@skyvoyage.com");
        ctx.setPassportNumber("RC123456");
        ctx.setDateOfBirth(LocalDate.of(1992, 4, 15));
        ctx.setPriceQuotes(List.of(quote));
        ctx.setBaseFare(quote.baseFare());
        ctx.setTotalPrice(quote.total());
        seat.setCalculatedPrice(quote.total());
        ctx.setSelectedSeats(new ArrayList<>(List.of(seat)));
        ctx.setPaymentResult(new PaymentResult(true, "TXN-RECON-7788", "VISA", "4242", null));

        // Create booking in database
        BookingRecord record = bookingDao.createBooking(ctx);
        try {
            assertNotNull(record);
            BigDecimal confirmedTotal = record.getTotalAmount();

            // Reconcile 1: payments.amount from DB
            BigDecimal paymentAmount;
            try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT amount FROM payments WHERE booking_id = ?")) {
                stmt.setLong(1, record.getBookingId());
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "Payment row must exist");
                    paymentAmount = rs.getBigDecimal("amount");
                }
            }

            // Reconcile 2: Confirmation screen lines (base + demand + timing)
            BigDecimal confirmationBase = ctx.getPriceQuotes().stream().map(PriceQuote::classFare).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal confirmationDemand = ctx.getPriceQuotes().stream().map(PriceQuote::demandAdjustment).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal confirmationTiming = ctx.getPriceQuotes().stream().map(PriceQuote::timingAdjustment).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal confirmationTotal = confirmationBase.add(confirmationDemand).add(confirmationTiming);

            // PROVE: confirmed total == payments.amount == quote total
            assertEquals(0, quote.total().compareTo(confirmedTotal),
                    "Confirmed total must equal quote total");
            assertEquals(0, quote.total().compareTo(paymentAmount),
                    "Database payments.amount must equal quote total");
            assertEquals(0, quote.total().compareTo(confirmationTotal),
                    "Confirmation screen breakdown total must equal quote total");
            assertEquals(confirmedTotal, paymentAmount,
                    "Confirmed total must equal payment amount");

        } finally {
            // Teardown test booking
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
    }
}
