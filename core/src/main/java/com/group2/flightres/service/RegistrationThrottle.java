package com.group2.flightres.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window IP rate limiter for customer account registration.
 * Allows at most 5 registration attempts per IP within 10 minutes.
 */
public class RegistrationThrottle {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByIp = new ConcurrentHashMap<>();

    public RegistrationThrottle() {
        this(Clock.systemUTC());
    }

    public RegistrationThrottle(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Optional<Duration> remainingLockout(String clientIp) {
        String key = normalizeIp(clientIp);
        Deque<Instant> timestamps = attemptsByIp.get(key);
        if (timestamps == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            if (timestamps.size() < MAX_ATTEMPTS) {
                if (timestamps.isEmpty()) {
                    attemptsByIp.remove(key, timestamps);
                }
                return Optional.empty();
            }
            Instant oldest = timestamps.peekFirst();
            Duration remaining = Duration.between(now, oldest.plus(WINDOW));
            if (remaining.isZero() || remaining.isNegative()) {
                timestamps.clear();
                attemptsByIp.remove(key, timestamps);
                return Optional.empty();
            }
            return Optional.of(remaining.getSeconds() < 1 ? Duration.ofSeconds(1) : remaining);
        }
    }

    public void recordAttempt(String clientIp) {
        String key = normalizeIp(clientIp);
        Instant now = clock.instant();
        Deque<Instant> timestamps = attemptsByIp.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            timestamps.addLast(now);
        }
    }

    private static void pruneExpired(Deque<Instant> timestamps, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
            timestamps.pollFirst();
        }
    }

    private static String normalizeIp(String clientIp) {
        return (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
    }
}
