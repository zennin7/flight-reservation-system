package com.group2.flightres.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Transparent dynamic pricing policy rules for Altitude flight reservations.
 *
 * Demand multipliers based on flight occupancy (booked + currently held seats / total seats):
 *   - < 40%: 1.00
 *   - 40% to < 60%: 1.05
 *   - 60% to < 80%: 1.12
 *   - >= 80%: 1.20
 *
 * Booking-time multipliers based on remaining hours until departure:
 *   - >= 240 hours (10+ days): 0.95 (early bird)
 *   - >= 120 hours (5-10 days): 1.00 (standard)
 *   - >= 48 hours (2-5 days): 1.07 (moderate surge)
 *   - >= 24 hours (1-2 days): 1.12 (peak surge)
 *   - < 24 hours (last minute): 1.20 (urgent surge)
 *
 * VAT Rate: 15% standard rate in South Africa.
 * All quoted fares are VAT-inclusive; VAT_RATE is used solely for the informational
 * "Includes VAT" line breakdown.
 */
public final class PricingPolicy {

    public static final BigDecimal DEMAND_TIER_UNDER_40 = new BigDecimal("1.00");
    public static final BigDecimal DEMAND_TIER_40_TO_60 = new BigDecimal("1.05");
    public static final BigDecimal DEMAND_TIER_60_TO_80 = new BigDecimal("1.12");
    public static final BigDecimal DEMAND_TIER_80_PLUS = new BigDecimal("1.20");

    public static final BigDecimal TIMING_TIER_240_PLUS = new BigDecimal("0.95");
    public static final BigDecimal TIMING_TIER_120_TO_240 = new BigDecimal("1.00");
    public static final BigDecimal TIMING_TIER_48_TO_120 = new BigDecimal("1.07");
    public static final BigDecimal TIMING_TIER_24_TO_48 = new BigDecimal("1.12");
    public static final BigDecimal TIMING_TIER_UNDER_24 = new BigDecimal("1.20");

    public static final BigDecimal VAT_RATE = new BigDecimal("0.15");

    private PricingPolicy() {
        // Prevent instantiation
    }

    /**
     * Resolves the demand multiplier based on seat occupancy percentage [0.0, 1.0].
     */
    public static BigDecimal getDemandMultiplier(double occupancy) {
        if (occupancy < 0.40) {
            return DEMAND_TIER_UNDER_40;
        } else if (occupancy < 0.60) {
            return DEMAND_TIER_40_TO_60;
        } else if (occupancy < 0.80) {
            return DEMAND_TIER_60_TO_80;
        } else {
            return DEMAND_TIER_80_PLUS;
        }
    }

    /**
     * Resolves the booking-time multiplier based on hours until flight departure.
     */
    public static BigDecimal getTimingMultiplier(double hoursToDeparture) {
        if (hoursToDeparture >= 240.0) {
            return TIMING_TIER_240_PLUS;
        } else if (hoursToDeparture >= 120.0) {
            return TIMING_TIER_120_TO_240;
        } else if (hoursToDeparture >= 48.0) {
            return TIMING_TIER_48_TO_120;
        } else if (hoursToDeparture >= 24.0) {
            return TIMING_TIER_24_TO_48;
        } else {
            return TIMING_TIER_UNDER_24;
        }
    }

    /**
     * Convenience method to calculate timing multiplier from departure instant and current instant.
     */
    public static BigDecimal getTimingMultiplier(Instant now, Instant departure) {
        if (now == null || departure == null) {
            return TIMING_TIER_120_TO_240;
        }
        Duration diff = Duration.between(now, departure);
        double hours = diff.toSeconds() / 3600.0;
        return getTimingMultiplier(hours);
    }
}
