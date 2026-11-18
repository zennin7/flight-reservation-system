package com.group2.flightres.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FlightResultSummaryTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    @Test
    @DisplayName("FlightResultSummary computes min/max base price and min/max duration from Instants for 3 synthetic flights")
    void testSummaryBoundsWithThreeSyntheticFlights() {
        FlightSearchResult f1 = new FlightSearchResult(
                1L, "GRP 101", "JNB", "O. R. Tambo Intl", "Johannesburg", SAST,
                "CPT", "Cape Town Intl", "Cape Town", SAST,
                Instant.parse("2026-11-02T06:00:00Z"),
                Instant.parse("2026-11-02T08:15:00Z"), // 135 minutes (2h 15m)
                new BigDecimal("1200.00"),
                "Airbus A320",
                150, 138, 12
        );

        FlightSearchResult f2 = new FlightSearchResult(
                2L, "GRP 202", "JNB", "O. R. Tambo Intl", "Johannesburg", SAST,
                "CPT", "Cape Town Intl", "Cape Town", SAST,
                Instant.parse("2026-11-02T10:00:00Z"),
                Instant.parse("2026-11-02T12:05:00Z"), // 125 minutes (2h 05m)
                new BigDecimal("950.00"),
                "Airbus A320",
                120, 110, 10
        );

        FlightSearchResult f3 = new FlightSearchResult(
                3L, "GRP 303", "JNB", "O. R. Tambo Intl", "Johannesburg", SAST,
                "CPT", "Cape Town Intl", "Cape Town", SAST,
                Instant.parse("2026-11-02T14:00:00Z"),
                Instant.parse("2026-11-02T16:40:00Z"), // 160 minutes (2h 40m)
                new BigDecimal("2100.00"),
                "Airbus A320",
                160, 148, 12
        );

        FlightResultSummary summary = FlightResultSummary.fromResults(List.of(f1, f2, f3));

        assertNotNull(summary);
        assertEquals(new BigDecimal("950.00"), summary.minPrice());
        assertEquals(new BigDecimal("2100.00"), summary.maxPrice());
        assertEquals(125L, summary.minDurationMinutes());
        assertEquals(160L, summary.maxDurationMinutes());
    }

    @Test
    @DisplayName("FlightResultSummary handles empty list cleanly")
    void testSummaryBoundsWithEmptyList() {
        FlightResultSummary summary = FlightResultSummary.fromResults(List.of());

        assertNotNull(summary);
        assertEquals(BigDecimal.ZERO, summary.minPrice());
        assertEquals(BigDecimal.ZERO, summary.maxPrice());
        assertEquals(0L, summary.minDurationMinutes());
        assertEquals(0L, summary.maxDurationMinutes());
    }
}
