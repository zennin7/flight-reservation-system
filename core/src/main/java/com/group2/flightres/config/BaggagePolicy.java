package com.group2.flightres.config;

/**
 * Standard baggage policies across SkyVoyage services, tickets, and UI.
 */
public final class BaggagePolicy {

    /**
     * Standard checked baggage allowance in kilograms for Altitude fares.
     */
    public static final int CHECKED_BAGGAGE_KG = 23;

    private BaggagePolicy() {
        // Prevent instantiation
    }
}
