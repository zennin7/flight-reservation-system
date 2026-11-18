package com.group2.flightres.config;

/**
 * Shared seat hold policy across core DAOs, business services, and UI countdown timers.
 */
public final class SeatHoldPolicy {

    /**
     * Seat hold duration in seconds (5 minutes = 300 seconds).
     */
    public static final int HOLD_SECONDS = 300;

    private SeatHoldPolicy() {
        // Prevent instantiation
    }
}
