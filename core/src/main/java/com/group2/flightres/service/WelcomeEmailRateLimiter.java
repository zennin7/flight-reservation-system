package com.group2.flightres.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Limits welcome emails to at most one dispatch per recipient address per hour.
 */
public class WelcomeEmailRateLimiter {

    public static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> lastSentByEmail = new ConcurrentHashMap<>();

    public WelcomeEmailRateLimiter() {
        this(Clock.systemUTC());
    }

    public WelcomeEmailRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Atomically reserves the 1-hour welcome-email slot for {@code email}.
     *
     * @return {@code true} if no welcome email was dispatched for this address in the past hour
     */
    public boolean tryAcquire(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String key = email.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        AtomicBoolean acquired = new AtomicBoolean(false);
        lastSentByEmail.compute(key, (k, previous) -> {
            if (previous == null || !previous.plus(WINDOW).isAfter(now)) {
                acquired.set(true);
                return now;
            }
            return previous;
        });
        return acquired.get();
    }
}
