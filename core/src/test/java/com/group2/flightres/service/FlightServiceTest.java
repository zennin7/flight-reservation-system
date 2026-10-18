package com.group2.flightres.service;

import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.exception.ValidationException;
import com.group2.flightres.model.FlightSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightServiceTest {

    private static class MockFlightDao implements FlightDao {
        @Override
        public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
            if ("JNB".equals(origin) || origin.contains("Johannesburg")) {
                return List.of(
                        new FlightSearchResult(1L, "GRP 204", "06:30", "08:40", "2h 10m", new BigDecimal("1200"), "R 1,200"),
                        new FlightSearchResult(2L, "GRP 310", "09:15", "11:25", "2h 10m", new BigDecimal("1450"), "R 1,450")
                );
            }
            return Collections.emptyList();
        }

        @Override
        public Optional<FlightSearchResult> findById(long flightId) {
            if (flightId == 1L) {
                return Optional.of(new FlightSearchResult(1L, "GRP 204", "06:30", "08:40", "2h 10m", new BigDecimal("1200"), "R 1,200"));
            }
            return Optional.empty();
        }
    }

    @Test
    @DisplayName("Search flights returns matching list when flights exist")
    void testSearchFlightsReturnsResults() {
        FlightService service = new FlightService(new MockFlightDao());
        List<FlightSearchResult> results = service.searchFlights("Johannesburg (JNB)", "Cape Town (CPT)", LocalDate.of(2026, 11, 2));

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("GRP 204", results.get(0).getFlightNumber());
    }

    @Test
    @DisplayName("Search flights returns empty list when no flights exist")
    void testSearchFlightsHandlesNoResults() {
        FlightService service = new FlightService(new MockFlightDao());
        List<FlightSearchResult> results = service.searchFlights("Durban (DUR)", "Cape Town (CPT)", LocalDate.of(2026, 11, 2));

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Search flights validates input and rejects same origin and destination")
    void testSearchFlightsValidatesRoute() {
        FlightService service = new FlightService(new MockFlightDao());
        assertThrows(ValidationException.class, () ->
                service.searchFlights("JNB", "JNB", LocalDate.of(2026, 11, 2))
        );
    }
}
