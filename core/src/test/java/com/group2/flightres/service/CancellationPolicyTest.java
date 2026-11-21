package com.group2.flightres.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final CancellationPolicy POLICY = new CancellationPolicy(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Exactly 24h00m before departure allows full refund; 23h59m and past block cancellation")
    void enforcesTwentyFourHourCutoffWithInjectedClock() {
        BigDecimal paid = new BigDecimal("1522.00");

        // Boundary 24h00m -> allowed
        var exact24 = POLICY.evaluate(NOW.plusSeconds(24L * 3600L), "CONFIRMED", paid);
        assertTrue(exact24.allowed());
        assertFalse(exact24.alreadyCancelled());
        assertEquals(0, paid.compareTo(exact24.refundAmount()));

        // 23h59m59s -> blocked
        var under24 = POLICY.evaluate(NOW.plusSeconds(24L * 3600L - 1L), "CONFIRMED", paid);
        assertFalse(under24.allowed());
        assertFalse(under24.alreadyCancelled());
        assertEquals(0, BigDecimal.ZERO.compareTo(under24.refundAmount()));
        assertTrue(under24.reason().contains("24 hours"));

        // Past departure -> blocked
        var departed = POLICY.evaluate(NOW.minusSeconds(60), "CONFIRMED", paid);
        assertFalse(departed.allowed());
        assertTrue(departed.reason().toLowerCase().contains("departed"));

        // Already cancelled -> flagged alreadyCancelled
        var already = POLICY.evaluate(NOW.plusSeconds(48L * 3600L), "CANCELLED", paid);
        assertFalse(already.allowed());
        assertTrue(already.alreadyCancelled());
    }
}
