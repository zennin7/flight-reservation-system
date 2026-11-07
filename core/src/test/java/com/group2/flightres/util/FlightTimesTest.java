package com.group2.flightres.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightTimesTest {

    @Test
    @DisplayName("FlightTimes: JNB to CPT domestic route time formatting and SAST zone")
    void testJnbToCptDomesticRoute() {
        Instant dep = Instant.parse("2026-11-02T04:30:00Z");
        Instant arr = Instant.parse("2026-11-02T06:40:00Z");
        ZoneId jnb = ZoneId.of("Africa/Johannesburg");
        ZoneId cpt = ZoneId.of("Africa/Johannesburg");

        assertEquals("06:30", FlightTimes.formatLocalTime(dep, jnb));
        assertEquals("08:40", FlightTimes.formatLocalTime(arr, cpt));
        assertEquals("2h 10m", FlightTimes.formatDuration(dep, arr));
        assertEquals("SAST", FlightTimes.zoneAbbreviation(jnb, dep));
        assertEquals("SAST", FlightTimes.zoneAbbreviation(cpt, arr));
    }

    @Test
    @DisplayName("FlightTimes: JNB to DXB international route across time zones")
    void testJnbToDxbInternationalRoute() {
        Instant dep = Instant.parse("2026-11-02T11:00:00Z");
        Instant arr = Instant.parse("2026-11-02T19:15:00Z");
        ZoneId jnb = ZoneId.of("Africa/Johannesburg");
        ZoneId dxb = ZoneId.of("Asia/Dubai");

        assertEquals("13:00", FlightTimes.formatLocalTime(dep, jnb));
        assertEquals("23:15", FlightTimes.formatLocalTime(arr, dxb));
        assertEquals("8h 15m", FlightTimes.formatDuration(dep, arr));
        assertEquals("SAST", FlightTimes.zoneAbbreviation(jnb, dep));
        assertEquals("GST", FlightTimes.zoneAbbreviation(dxb, arr));
    }

    @Test
    @DisplayName("FlightTimes: Duration minutes are zero-padded (e.g. 2h 05m)")
    void testDurationZeroPaddedMinutes() {
        Instant dep = Instant.parse("2026-11-02T06:30:00Z");
        Instant arr = Instant.parse("2026-11-02T08:35:00Z");

        assertEquals("2h 05m", FlightTimes.formatDuration(dep, arr));
    }
}
