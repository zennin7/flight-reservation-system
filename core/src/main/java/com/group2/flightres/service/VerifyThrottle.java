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
 * Sliding-window IP rate limiter for public /verify/{pnr} endpoint.
 * Allows at most 30 requests per client IP per 1 minute.
 */
public class VerifyThrottle {

    public static final int MAX_REQUESTS = 30;
    public static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> requestsByIp = new ConcurrentHashMap<>();

    public VerifyThrottle() {
        this(Clock.systemUTC());
    }

    public VerifyThrottle(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Optional<Duration> remainingLockout(String clientIp) {
        String key = normalizeIp(clientIp);
        Deque<Instant> timestamps = requestsByIp.get(key);
        if (timestamps == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            if (timestamps.size() < MAX_REQUESTS) {
                if (timestamps.isEmpty()) {
                    requestsByIp.remove(key, timestamps);
                }
                return Optional.empty();
            }
            Instant oldest = timestamps.peekFirst();
            Duration remaining = Duration.between(now, oldest.plus(WINDOW));
            if (remaining.isZero() || remaining.isNegative()) {
                timestamps.clear();
                requestsByIp.remove(key, timestamps);
                return Optional.empty();
            }
            return Optional.of(remaining.getSeconds() < 1 ? Duration.ofSeconds(1) : remaining);
        }
    }

    public void recordRequest(String clientIp) {
        String key = normalizeIp(clientIp);
        Instant now = clock.instant();
        Deque<Instant> timestamps = requestsByIp.computeIfAbsent(key, k -> new ArrayDeque<>());
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
