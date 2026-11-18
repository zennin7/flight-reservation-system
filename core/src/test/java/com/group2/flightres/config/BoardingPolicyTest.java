package com.group2.flightres.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

public class BoardingPolicyTest {

    @Test
    void testPolicyConstants() {
        assertEquals(30, BoardingPolicy.BOARDING_MINUTES_BEFORE);
        assertEquals(60, BoardingPolicy.BAG_DROP_CLOSES_MINUTES_BEFORE);
        assertEquals(24, BoardingPolicy.CHECKIN_OPENS_HOURS_BEFORE);
        assertEquals("Gate shown 60 min before departure", BoardingPolicy.GATE_NOTE);
    }

    @Test
    void testCalculations() {
        LocalTime dep = LocalTime.of(6, 30);
        assertEquals(LocalTime.of(6, 0), BoardingPolicy.calculateBoardingTime(dep));
        assertEquals(LocalTime.of(5, 30), BoardingPolicy.calculateBagDropCloseTime(dep));

        LocalDateTime depDt = LocalDateTime.of(2026, 11, 14, 6, 30);
        LocalDateTime checkin = BoardingPolicy.calculateCheckinOpens(depDt);
        assertEquals(LocalDateTime.of(2026, 11, 13, 6, 30), checkin);
    }

    @Test
    void testFormattedStrings() {
        assertEquals("06:00", BoardingPolicy.formatBoardingTime("06:30"));
        assertEquals("05:30 at O.R. Tambo", BoardingPolicy.formatBagDropTime("06:30", "O.R. Tambo"));
        assertEquals("Opens Fri, 13 Nov at 06:30", BoardingPolicy.formatCheckinOpens("2026-11-14", "06:30"));
    }
}
