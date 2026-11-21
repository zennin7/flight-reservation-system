package com.group2.flightres.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentThrottleTest {

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration d) {
            this.instant = this.instant.plus(d);
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
            return instant;
        }
    }

    @Test
    @DisplayName("Allows 10 payment attempts per IP in 10 minutes, locks 11th, and resets after window")
    void locksOutOnEleventhAttemptAndExpiresAfterTenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
        PaymentThrottle throttle = new PaymentThrottle(clock);
        String ip = "192.168.1.50";

        for (int i = 0; i < 10; i++) {
            assertTrue(throttle.remainingLockout(ip).isEmpty(), "Attempt " + (i + 1) + " should not be locked");
            throttle.recordAttempt(ip);
            clock.advance(Duration.ofSeconds(10));
        }

        var locked = throttle.remainingLockout(ip);
        assertTrue(locked.isPresent(), "11th attempt within 10 minutes must trigger lockout");
        assertTrue(locked.get().toSeconds() > 0);
        assertTrue(throttle.remainingLockout("10.0.0.99").isEmpty(), "Different IP must not be affected");

        clock.advance(Duration.ofMinutes(10));
        assertTrue(throttle.remainingLockout(ip).isEmpty(), "Lockout must expire cleanly after window");
    }
}
