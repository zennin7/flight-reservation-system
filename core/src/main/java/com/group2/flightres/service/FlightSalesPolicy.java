package com.group2.flightres.service;

import com.group2.flightres.config.BoardingPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Single source of truth for flight sales cutoff policy.
 * Sales close strictly 60 minutes before departure time.
 */
public final class FlightSalesPolicy {

    public static final Duration SALES_CLOSE_BEFORE_DEPARTURE =
            Duration.ofMinutes(BoardingPolicy.SALES_CLOSE_MINUTES_BEFORE);
    public static final int LOW_SEATS_THRESHOLD = 10;

    private FlightSalesPolicy() {}

    /**
     * Checks whether a flight is open for sales given its departure instant and reference clock.
     * A flight is open if the clock's current instant is strictly before (departure - 60 minutes).
     *
     * Boundaries:
     * - 61 minutes before departure: OPEN (true)
     * - 60 minutes before departure: CLOSED (false)
     * - 59 minutes before departure: CLOSED (false)
     *
     * @param departure departure instant (UTC)
     * @param clock reference clock
     * @return true if open for sales, false if closed or departed
     */
    public static boolean isOpen(Instant departure, Clock clock) {
        if (departure == null || clock == null) {
            return false;
        }
        Instant cutoff = departure.minus(SALES_CLOSE_BEFORE_DEPARTURE);
        return clock.instant().isBefore(cutoff);
    }
}
