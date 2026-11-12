package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.config.PricingPolicy;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingPolicyTest {

    private static final BigDecimal EPSILON = new BigDecimal("0.001");

    @Test
    @DisplayName("Occupancy tier boundaries: exactly at 40%, 60%, 80%")
    void testOccupancyTierBoundaries() {
        // Under 40% -> 1.00
        assertEquals(new BigDecimal("1.00"), PricingPolicy.getDemandMultiplier(0.00));
        assertEquals(new BigDecimal("1.00"), PricingPolicy.getDemandMultiplier(0.25));
        assertEquals(new BigDecimal("1.00"), PricingPolicy.getDemandMultiplier(0.39999));

        // 40% to < 60% -> 1.05
        assertEquals(new BigDecimal("1.05"), PricingPolicy.getDemandMultiplier(0.40));
        assertEquals(new BigDecimal("1.05"), PricingPolicy.getDemandMultiplier(0.50));
        assertEquals(new BigDecimal("1.05"), PricingPolicy.getDemandMultiplier(0.59999));

        // 60% to < 80% -> 1.12
        assertEquals(new BigDecimal("1.12"), PricingPolicy.getDemandMultiplier(0.60));
        assertEquals(new BigDecimal("1.12"), PricingPolicy.getDemandMultiplier(0.70));
        assertEquals(new BigDecimal("1.12"), PricingPolicy.getDemandMultiplier(0.79999));

        // 80%+ -> 1.20
        assertEquals(new BigDecimal("1.20"), PricingPolicy.getDemandMultiplier(0.80));
        assertEquals(new BigDecimal("1.20"), PricingPolicy.getDemandMultiplier(0.95));
        assertEquals(new BigDecimal("1.20"), PricingPolicy.getDemandMultiplier(1.00));
    }

    @Test
    @DisplayName("Timing tier boundaries: exactly at 240h, 120h, 48h, 24h with a fixed Clock")
    void testTimingTierBoundariesWithFixedClock() {
        Instant baseTime = Instant.parse("2026-11-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);

        // >= 240h -> 0.95
        assertEquals(new BigDecimal("0.95"), PricingPolicy.getTimingMultiplier(240.0));
        assertEquals(new BigDecimal("0.95"), PricingPolicy.getTimingMultiplier(300.0));
        assertEquals(new BigDecimal("0.95"), PricingPolicy.getTimingMultiplier(fixedClock.instant(), baseTime.plus(Duration.ofHours(240))));

        // 120h to <240h -> 1.00
        assertEquals(new BigDecimal("1.00"), PricingPolicy.getTimingMultiplier(239.99));
        assertEquals(new BigDecimal("1.00"), PricingPolicy.getTimingMultiplier(120.0));
        assertEquals(new BigDecimal("1.00"), PricingPolicy.getTimingMultiplier(fixedClock.instant(), baseTime.plus(Duration.ofHours(120))));

        // 48h to <120h -> 1.07
        assertEquals(new BigDecimal("1.07"), PricingPolicy.getTimingMultiplier(119.99));
        assertEquals(new BigDecimal("1.07"), PricingPolicy.getTimingMultiplier(48.0));
        assertEquals(new BigDecimal("1.07"), PricingPolicy.getTimingMultiplier(fixedClock.instant(), baseTime.plus(Duration.ofHours(48))));

        // 24h to <48h -> 1.12
        assertEquals(new BigDecimal("1.12"), PricingPolicy.getTimingMultiplier(47.99));
        assertEquals(new BigDecimal("1.12"), PricingPolicy.getTimingMultiplier(24.0));
        assertEquals(new BigDecimal("1.12"), PricingPolicy.getTimingMultiplier(fixedClock.instant(), baseTime.plus(Duration.ofHours(24))));

        // < 24h -> 1.20
        assertEquals(new BigDecimal("1.20"), PricingPolicy.getTimingMultiplier(23.99));
        assertEquals(new BigDecimal("1.20"), PricingPolicy.getTimingMultiplier(1.0));
        assertEquals(new BigDecimal("1.20"), PricingPolicy.getTimingMultiplier(0.0));
        assertEquals(new BigDecimal("1.20"), PricingPolicy.getTimingMultiplier(fixedClock.instant(), baseTime.plus(Duration.ofHours(2))));
    }

    @Test
    @DisplayName("Line items sum to total for 1000 random inputs")
    void testLinesSumToTotalFor1000RandomInputs() {
        Random rng = new Random(42);
        for (int i = 0; i < 1000; i++) {
            BigDecimal baseFare = BigDecimal.valueOf(100 + rng.nextDouble() * 5000).setScale(2, RoundingMode.HALF_UP);
            BigDecimal classMultiplier = BigDecimal.valueOf(1.0 + rng.nextDouble() * 3.0).setScale(2, RoundingMode.HALF_UP);
            double occupancy = rng.nextDouble();
            double hours = rng.nextDouble() * 300.0;

            PriceQuote quote = PriceQuote.calculate(baseFare, classMultiplier, occupancy, hours);

            BigDecimal sum = quote.classFare().add(quote.demandAdjustment()).add(quote.timingAdjustment());
            assertEquals(quote.total(), sum, "classFare + demandAdjustment + timingAdjustment must equal total for iteration " + i);
            assertEquals(2, quote.total().scale(), "Total scale must be 2");
            assertEquals(2, quote.classFare().scale(), "classFare scale must be 2");
            assertEquals(2, quote.demandAdjustment().scale(), "demandAdjustment scale must be 2");
            assertEquals(2, quote.timingAdjustment().scale(), "timingAdjustment scale must be 2");
            assertEquals(2, quote.vatIncluded().scale(), "vatIncluded scale must be 2");
        }
    }

    @Test
    @DisplayName("Monotonicity: price never decreases as occupancy rises at a fixed time")
    void testPriceMonotonicityWithOccupancy() {
        BigDecimal base = new BigDecimal("1200.00");
        BigDecimal classMult = BigDecimal.ONE;
        double fixedHours = 10.0; // Urgent (<24h)

        BigDecimal previousTotal = BigDecimal.ZERO;
        for (int occPercent = 0; occPercent <= 100; occPercent++) {
            double occ = occPercent / 100.0;
            PriceQuote quote = PriceQuote.calculate(base, classMult, occ, fixedHours);
            assertTrue(quote.total().compareTo(previousTotal) >= 0,
                    "Total price must be non-decreasing with occupancy: occ=" + occPercent + "% produced " + quote.total() + " vs prev " + previousTotal);
            previousTotal = quote.total();
        }
    }

    @Test
    @DisplayName("DB Integration: FIRST/ECONOMY class ratio equals the DB values, test in rolled-back tx")
    void testClassMultiplierRatioFromDbInRolledBackTransaction() throws Exception {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal economyMult = null;
                BigDecimal firstMult = null;

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT seat_class, price_multiplier FROM seat_class_pricing")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String sc = rs.getString("seat_class");
                            BigDecimal mult = rs.getBigDecimal("price_multiplier");
                            if ("ECONOMY".equalsIgnoreCase(sc)) economyMult = mult;
                            if ("FIRST".equalsIgnoreCase(sc)) firstMult = mult;
                        }
                    }
                }

                assertEquals(new BigDecimal("1.00"), economyMult.setScale(2, RoundingMode.HALF_UP));
                assertEquals(new BigDecimal("3.50"), firstMult.setScale(2, RoundingMode.HALF_UP));

                BigDecimal ratio = firstMult.divide(economyMult, 2, RoundingMode.HALF_UP);
                assertEquals(new BigDecimal("3.50"), ratio);

                // Verify query in rolled-back transaction
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT count(*) FROM flight_seats WHERE flight_id = 1")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next());
                        assertTrue(rs.getInt(1) > 0);
                    }
                }

            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }
}
