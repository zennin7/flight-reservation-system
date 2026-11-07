package com.group2.flightres.service;

import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.exception.ValidationException;
import com.group2.flightres.model.FlightSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightServiceTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final Instant DEP1 = Instant.parse("2026-11-02T04:30:00Z");
    private static final Instant ARR1 = Instant.parse("2026-11-02T06:40:00Z");
    private static final Instant DEP2 = Instant.parse("2026-11-02T07:15:00Z");
    private static final Instant ARR2 = Instant.parse("2026-11-02T09:25:00Z");
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);

    private static final FlightSearchResult FLIGHT_1 = new FlightSearchResult(
            1L, "GRP 204", "JNB", "O. R. Tambo International Airport", "Johannesburg", SAST,
            "CPT", "Cape Town International Airport", "Cape Town", SAST,
            DEP1, ARR1, new BigDecimal("1200"), "Airbus A320", 149
    );

    private static final FlightSearchResult FLIGHT_2 = new FlightSearchResult(
            2L, "GRP 310", "JNB", "O. R. Tambo International Airport", "Johannesburg", SAST,
            "CPT", "Cape Town International Airport", "Cape Town", SAST,
            DEP2, ARR2, new BigDecimal("1450"), "Airbus A320", 147
    );

    private static class MockFlightDao implements FlightDao {
        @Override
        public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
            if ("JNB".equals(origin) || origin.contains("Johannesburg")) {
                return List.of(FLIGHT_1, FLIGHT_2);
            }
            return Collections.emptyList();
        }

        @Override
        public Optional<FlightSearchResult> findById(long flightId) {
            if (flightId == 1L) {
                return Optional.of(FLIGHT_1);
            }
            return Optional.empty();
        }

        @Override
        public Optional<LocalDate> findNextAvailableDate(String origin, String destination, LocalDate fromDate, Instant minDepartureInstant) {
            if ("JNB".equals(origin) || origin.contains("Johannesburg")) {
                return Optional.of(LocalDate.of(2026, 11, 2));
            }
            return Optional.empty();
        }
    }

    @Test
    @DisplayName("Search flights returns matching list when flights exist")
    void testSearchFlightsReturnsResults() {
        FlightService service = new FlightService(new MockFlightDao(), TEST_CLOCK);
        List<FlightSearchResult> results = service.searchFlights("Johannesburg (JNB)", "Cape Town (CPT)", LocalDate.of(2026, 11, 2));

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("GRP 204", results.get(0).getFlightNumber());
        assertEquals("06:30", results.get(0).getDepartureTime());
        assertEquals("08:40", results.get(0).getArrivalTime());
        assertEquals("2h 10m", results.get(0).getDuration());
    }

    @Test
    @DisplayName("Search flights returns empty list when no flights exist")
    void testSearchFlightsHandlesNoResults() {
        FlightService service = new FlightService(new MockFlightDao(), TEST_CLOCK);
        List<FlightSearchResult> results = service.searchFlights("Durban (DUR)", "Cape Town (CPT)", LocalDate.of(2026, 11, 2));

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Search flights validates input and rejects same origin and destination")
    void testSearchFlightsValidatesRoute() {
        FlightService service = new FlightService(new MockFlightDao(), TEST_CLOCK);
        assertThrows(ValidationException.class, () ->
                service.searchFlights("JNB", "JNB", LocalDate.of(2026, 11, 2))
        );
    }

    @Test
    @DisplayName("nextAvailableDate returns earliest date with open flights")
    void testNextAvailableDate() {
        FlightService service = new FlightService(new MockFlightDao(), TEST_CLOCK);
        Optional<LocalDate> next = service.nextAvailableDate("JNB", "CPT", LocalDate.of(2026, 11, 1));
        assertTrue(next.isPresent());
        assertEquals(LocalDate.of(2026, 11, 2), next.get());
    }
}
