package com.group2.flightres.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightDateFormatterTest {

    @Test
    @DisplayName("14 Nov 2026 is confirmed Saturday and formatted as Sat, 14 Nov 2026")
    void testFormatSaturdayNov14() {
        LocalDate nov14 = LocalDate.of(2026, 11, 14);
        assertEquals(DayOfWeek.SATURDAY, nov14.getDayOfWeek(), "14 Nov 2026 must be Saturday");

        String fromDate = FlightDateFormatter.format(nov14);
        assertEquals("Sat, 14 Nov 2026", fromDate, "LocalDate formatting mismatch");

        String fromString = FlightDateFormatter.format("2026-11-14");
        assertEquals("Sat, 14 Nov 2026", fromString, "String parsing formatting mismatch");
    }

    @Test
    @DisplayName("Already formatted string passes through without alteration")
    void testAlreadyFormattedPassThrough() {
        String existing = "Sat, 14 Nov 2026";
        assertEquals("Sat, 14 Nov 2026", FlightDateFormatter.format(existing));
    }

    @Test
    @DisplayName("Edge cases: null, empty, invalid strings handled gracefully")
    void testEdgeCases() {
        assertEquals("", FlightDateFormatter.format((LocalDate) null));
        assertEquals("", FlightDateFormatter.format((String) null));
        assertEquals("", FlightDateFormatter.format("   "));
        assertEquals("invalid-date", FlightDateFormatter.format("invalid-date"));
    }
}
