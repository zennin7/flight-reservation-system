package com.group2.flightres.util;

import com.group2.flightres.model.FlightSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlassFlightResultsBridgeTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private FlightSearchResult createFlight(
            long id,
            String num,
            Instant dep,
            Instant arr,
            BigDecimal price,
            int totalSeats,
            int econSeats,
            int firstSeats) {
        return new FlightSearchResult(
                id,
                num,
                "JNB",
                "O. R. Tambo Intl",
                "Johannesburg",
                SAST,
                "CPT",
                "Cape Town Intl",
                "Cape Town",
                SAST,
                dep,
                arr,
                price,
                "Airbus A320",
                totalSeats,
                econSeats,
                firstSeats
        );
    }

    @Test
    @DisplayName("Price bound: filters flights above max price threshold")
    void testPriceBoundFilter() {
        GlassFlightResultsBridge bridge = new GlassFlightResultsBridge();
        FlightSearchResult cheap = createFlight(1L, "F1", Instant.parse("2026-11-02T04:30:00Z"), Instant.parse("2026-11-02T06:40:00Z"), new BigDecimal("1200.00"), 162, 150, 12);
        FlightSearchResult expensive = createFlight(2L, "F2", Instant.parse("2026-11-02T04:30:00Z"), Instant.parse("2026-11-02T06:40:00Z"), new BigDecimal("3500.00"), 162, 150, 12);

        bridge.setMaxPriceFilter(2000.0);

        assertTrue(bridge.matchesPrice(cheap), "Cheap flight should match price <= 2000");
        assertFalse(bridge.matchesPrice(expensive), "Expensive flight should be filtered out by price <= 2000");
    }

    @Test
    @DisplayName("Duration bound: filters flights exceeding max duration minutes calculated from Instants")
    void testDurationBoundFilter() {
        GlassFlightResultsBridge bridge = new GlassFlightResultsBridge();
        // 2h 10m = 130 minutes
        FlightSearchResult shortFlight = createFlight(1L, "F1", Instant.parse("2026-11-02T04:30:00Z"), Instant.parse("2026-11-02T06:40:00Z"), new BigDecimal("1200.00"), 162, 150, 12);
        // 4h 00m = 240 minutes
        FlightSearchResult longFlight = createFlight(2L, "F2", Instant.parse("2026-11-02T04:30:00Z"), Instant.parse("2026-11-02T08:30:00Z"), new BigDecimal("1200.00"), 162, 150, 12);

        bridge.setMaxDurationFilter(180.0);

        assertTrue(bridge.matchesDuration(shortFlight), "130-minute flight should match <= 180 min");
        assertFalse(bridge.matchesDuration(longFlight), "240-minute flight should be filtered out by <= 180 min");
    }

    @Test
    @DisplayName("Cabin filter: filters by availableEconomy and availableFirst with zero-availability cases")
    void testCabinAvailabilityFilter() {
        GlassFlightResultsBridge bridge = new GlassFlightResultsBridge();
        // Flight A: Economy only (150 econ, 0 first)
        FlightSearchResult econOnly = createFlight(1L, "EA", Instant.parse("2026-11-02T04:30:00Z"), Instant.parse("2026-11-02T06:40:00Z"), new BigDecimal("1000.00"), 150, 150, 0);
        // Flight B: First class only (0 econ, 12 first)
        FlightSearchResult firstOnly = createFlight(2L, "FB", Instant.parse("2026-11-02T04:30:00Z"), Instant.parse("2026-11-02T06:40:00Z"), new BigDecimal("2500.00"), 12, 0, 12);
        // Flight C: Mixed (150 econ, 12 first)
        FlightSearchResult mixed = createFlight(3L, "MC", Instant.parse("2026-11-02T04:30:00Z"), Instant.parse("2026-11-02T06:40:00Z"), new BigDecimal("1500.00"), 162, 150, 12);

        // 1. Neither active -> all match
        bridge.resetCabinFilter();
        assertTrue(bridge.matchesCabin(econOnly));
        assertTrue(bridge.matchesCabin(firstOnly));
        assertTrue(bridge.matchesCabin(mixed));

        // 2. Economy filter active only
        bridge.toggleCabinFilterEconomy();
        assertTrue(bridge.matchesCabin(econOnly), "Economy-only flight matches economy filter");
        assertFalse(bridge.matchesCabin(firstOnly), "First-only flight filtered out by economy filter");
        assertTrue(bridge.matchesCabin(mixed), "Mixed flight matches economy filter");

        // 3. First class filter active only
        bridge.resetCabinFilter();
        bridge.toggleCabinFilterFirst();
        assertFalse(bridge.matchesCabin(econOnly), "Economy-only flight filtered out by first class filter");
        assertTrue(bridge.matchesCabin(firstOnly), "First-only flight matches first class filter");
        assertTrue(bridge.matchesCabin(mixed), "Mixed flight matches first class filter");

        // 4. Both active -> no filter applied (all match)
        bridge.toggleCabinFilterEconomy();
        assertTrue(bridge.matchesCabin(econOnly));
        assertTrue(bridge.matchesCabin(firstOnly));
        assertTrue(bridge.matchesCabin(mixed));
    }

    @Test
    @DisplayName("Departure bucket: buckets by origin airport local hour across America/Los_Angeles and UTC")
    void testDepartureBucketTimezoneResilience() {
        TimeZone original = TimeZone.getDefault();
        try {
            // Flight departs at 04:30 UTC, which is 06:30 SAST in Africa/Johannesburg
            FlightSearchResult sastFlight = createFlight(
                    1L,
                    "SA0630",
                    Instant.parse("2026-11-02T04:30:00Z"),
                    Instant.parse("2026-11-02T06:40:00Z"),
                    new BigDecimal("1200.00"),
                    162, 150, 12
            );

            GlassFlightResultsBridge bridge = new GlassFlightResultsBridge();

            // Under America/Los_Angeles (where 04:30Z is 20:30 previous day)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            bridge.setDepartureFilter("6 AM to 12 PM");
            assertTrue(bridge.matchesDeparture(sastFlight), "06:30 SAST must be in '6 AM to 12 PM' under LA JVM zone");

            bridge.setDepartureFilter("Before 6 AM");
            assertFalse(bridge.matchesDeparture(sastFlight), "06:30 SAST is not 'Before 6 AM'");

            bridge.setDepartureFilter("After 6 PM");
            assertFalse(bridge.matchesDeparture(sastFlight), "06:30 SAST is not 'After 6 PM'");

            // Under UTC (where 04:30Z is 04:30)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            bridge.setDepartureFilter("6 AM to 12 PM");
            assertTrue(bridge.matchesDeparture(sastFlight), "06:30 SAST must be in '6 AM to 12 PM' under UTC JVM zone");

            bridge.setDepartureFilter("Before 6 AM");
            assertFalse(bridge.matchesDeparture(sastFlight), "06:30 SAST is not 'Before 6 AM' under UTC");
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
