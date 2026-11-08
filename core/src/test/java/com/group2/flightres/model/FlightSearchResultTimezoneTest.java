package com.group2.flightres.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightSearchResultTimezoneTest {

    @Test
    @DisplayName("FlightSearchResult: Formatting is resilient to JVM default time zone changes")
    void testTimezoneResilienceAcrossJvmTimezones() {
        TimeZone original = TimeZone.getDefault();
        try {
            // Synthetic JNB -> CPT (dep 04:30Z in Africa/Johannesburg = 06:30 SAST, arr 06:40Z = 08:40 SAST, 2h 10m)
            FlightSearchResult jnbCpt = new FlightSearchResult(
                    1L,
                    "GRP 204",
                    "JNB",
                    "O. R. Tambo International Airport",
                    "Johannesburg",
                    ZoneId.of("Africa/Johannesburg"),
                    "CPT",
                    "Cape Town International Airport",
                    "Cape Town",
                    ZoneId.of("Africa/Johannesburg"),
                    Instant.parse("2026-11-02T04:30:00Z"),
                    Instant.parse("2026-11-02T06:40:00Z"),
                    new BigDecimal("1200.00"),
                    "Airbus A320",
                    149,
                    137,
                    12
            );

            // Synthetic JNB -> DXB (dep 11:00Z in Africa/Johannesburg = 13:00 SAST, arr 19:15Z in Asia/Dubai = 23:15 GST, 8h 15m)
            FlightSearchResult jnbDxb = new FlightSearchResult(
                    2L,
                    "DXB 777",
                    "JNB",
                    "O. R. Tambo International Airport",
                    "Johannesburg",
                    ZoneId.of("Africa/Johannesburg"),
                    "DXB",
                    "Dubai International Airport",
                    "Dubai",
                    ZoneId.of("Asia/Dubai"),
                    Instant.parse("2026-11-02T11:00:00Z"),
                    Instant.parse("2026-11-02T19:15:00Z"),
                    new BigDecimal("4500.00"),
                    "Boeing 777-300ER",
                    50,
                    40,
                    10
            );

            // 1. Verify under America/Los_Angeles
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertEquals("06:30", jnbCpt.getDepartureTime());
            assertEquals("08:40", jnbCpt.getArrivalTime());
            assertEquals("2h 10m", jnbCpt.getDuration());

            assertEquals("13:00", jnbDxb.getDepartureTime());
            assertEquals("23:15", jnbDxb.getArrivalTime());
            assertEquals("8h 15m", jnbDxb.getDuration());

            // 2. Verify under UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals("06:30", jnbCpt.getDepartureTime());
            assertEquals("08:40", jnbCpt.getArrivalTime());
            assertEquals("2h 10m", jnbCpt.getDuration());

            assertEquals("13:00", jnbDxb.getDepartureTime());
            assertEquals("23:15", jnbDxb.getArrivalTime());
            assertEquals("8h 15m", jnbDxb.getDuration());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
