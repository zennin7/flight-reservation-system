package com.group2.flightres.util;

import com.group2.flightres.service.FlightSalesPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFlightFinderTest {

    @Test
    @DisplayName("TestFlightFinder finds earliest open ALT flight with all seats available")
    void testFindEarliestOpenAltFlight() {
        Clock clock = Clock.systemUTC();
        TestFlightFinder.OpenAltFlight flight = TestFlightFinder.findEarliestOpenAltFlight(clock);
        assertNotNull(flight);
        assertTrue(flight.flightNumber().startsWith("ALT"));
        assertNotNull(flight.originCode());
        assertNotNull(flight.destCode());
        assertFalse(flight.availableSeats().isEmpty());

        // Assert FlightSalesPolicy.isOpen(departure, clock) is true with that margin
        assertTrue(FlightSalesPolicy.isOpen(flight.departureInstant(), clock),
                "Earliest open ALT flight must be open for sales per FlightSalesPolicy");

        Duration requiredMargin = FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE.plusMinutes(15);
        assertTrue(!flight.departureInstant().isBefore(clock.instant().plus(requiredMargin)),
                "Flight departure must satisfy policy cutoff plus 15 minute margin");
    }
}
