package com.group2.flightres.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationThrottleTest {

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration delta) {
            current.updateAndGet(now -> now.plus(delta));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    @Test
    @DisplayName("Allows 5 registration attempts per IP and throttles the 6th for 10 minutes")
    void locksOnSixthAttemptPerIpWithinTenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-21T10:00:00Z"));
        RegistrationThrottle throttle = new RegistrationThrottle(clock);

        String ipA = "203.0.113.10";
        String ipB = "203.0.113.11";

        for (int i = 0; i < RegistrationThrottle.MAX_ATTEMPTS; i++) {
            assertTrue(throttle.remainingLockout(ipA).isEmpty(), "Attempt " + (i + 1) + " should be allowed");
            throttle.recordAttempt(ipA);
            clock.advance(Duration.ofSeconds(10));
        }

        var lockout = throttle.remainingLockout(ipA);
        assertTrue(lockout.isPresent(), "6th attempt within 10 minutes should be locked out");
        assertEquals(Duration.ofMinutes(10).minusSeconds(50), lockout.get());

        // Different IP remains independent
        assertTrue(throttle.remainingLockout(ipB).isEmpty());
    }

    @Test
    @DisplayName("Lockout expires once the oldest attempt ages past 10 minutes")
    void expiresAfterTenMinuteWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-21T10:00:00Z"));
        RegistrationThrottle throttle = new RegistrationThrottle(clock);

        for (int i = 0; i < RegistrationThrottle.MAX_ATTEMPTS; i++) {
            throttle.recordAttempt("198.51.100.7");
        }
        assertTrue(throttle.remainingLockout("198.51.100.7").isPresent());

        clock.advance(Duration.ofMinutes(10));
        assertTrue(throttle.remainingLockout("198.51.100.7").isEmpty());
    }
}
