package com.group2.flightres.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    private static Clock mutableClock(AtomicReference<Instant> nowRef) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return nowRef.get();
            }
        };
    }

    @Test
    void fiveFailuresTriggerFiveMinuteLockoutThenExpire() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-20T10:00:00Z"));
        LoginThrottle throttle = new LoginThrottle(mutableClock(now));

        for (int i = 0; i < 4; i++) {
            throttle.recordFailure("Customer@Example.com", "10.0.0.5");
            assertTrue(throttle.remainingLockout("customer@example.com", "10.0.0.5").isEmpty(),
                    "Attempt " + (i + 1) + " should not lock yet");
        }

        // 5th failure crosses threshold
        throttle.recordFailure("  CUSTOMER@example.com  ", "10.0.0.5");
        Optional<Duration> lockout = throttle.remainingLockout("customer@example.com", "10.0.0.5");
        assertTrue(lockout.isPresent());
        assertEquals(Duration.ofMinutes(5), lockout.get());

        // Advance 2 minutes -> ~3 minutes remaining
        now.set(now.get().plusSeconds(120));
        Optional<Duration> midLockout = throttle.remainingLockout("customer@example.com", "10.0.0.5");
        assertTrue(midLockout.isPresent());
        assertEquals(Duration.ofMinutes(3), midLockout.get());
        assertEquals("3 minutes", LoginThrottle.formatWait(midLockout.get()));

        // Different identifier or different IP is isolated
        assertTrue(throttle.remainingLockout("other@example.com", "10.0.0.5").isEmpty());
        assertTrue(throttle.remainingLockout("customer@example.com", "10.0.0.6").isEmpty());

        // Advance past 5 minutes -> unlocked
        now.set(now.get().plusSeconds(180));
        assertTrue(throttle.remainingLockout("customer@example.com", "10.0.0.5").isEmpty());
    }

    @Test
    void successfulLoginClearsFailureCounter() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-20T10:00:00Z"));
        LoginThrottle throttle = new LoginThrottle(mutableClock(now));

        for (int i = 0; i < 4; i++) {
            throttle.recordFailure("customer", "127.0.0.1");
        }
        throttle.recordSuccess("CUSTOMER", "127.0.0.1");

        // Another failure starts fresh at 1 (not locked)
        throttle.recordFailure("customer", "127.0.0.1");
        assertTrue(throttle.remainingLockout("customer", "127.0.0.1").isEmpty());
    }
}
