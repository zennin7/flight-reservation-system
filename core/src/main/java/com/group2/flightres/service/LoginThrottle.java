package com.group2.flightres.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for authentication endpoints.
 * Locks an identifier + client IP pair for 5 minutes after 5 failed attempts.
 */
public class LoginThrottle {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration WINDOW = Duration.ofMinutes(5);

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    public LoginThrottle(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Optional<Duration> remainingLockout(String identifier, String clientIp) {
        String key = keyFor(identifier, clientIp);
        Deque<Instant> timestamps = attemptsByKey.get(key);
        if (timestamps == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            if (timestamps.size() < MAX_FAILED_ATTEMPTS) {
                if (timestamps.isEmpty()) {
                    attemptsByKey.remove(key, timestamps);
                }
                return Optional.empty();
            }
            Instant oldest = timestamps.peekFirst();
            Duration remaining = Duration.between(now, oldest.plus(WINDOW));
            if (remaining.isZero() || remaining.isNegative()) {
                timestamps.clear();
                attemptsByKey.remove(key, timestamps);
                return Optional.empty();
            }
            return Optional.of(remaining.getSeconds() < 1 ? Duration.ofSeconds(1) : remaining);
        }
    }

    public void recordFailure(String identifier, String clientIp) {
        String key = keyFor(identifier, clientIp);
        Instant now = clock.instant();
        Deque<Instant> timestamps = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            timestamps.addLast(now);
        }
    }

    public void recordSuccess(String identifier, String clientIp) {
        attemptsByKey.remove(keyFor(identifier, clientIp));
    }

    public static String formatWait(Duration wait) {
        long totalSeconds = Math.max(1L, (wait.toMillis() + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0 && seconds > 0) {
            return minutes + (minutes == 1 ? " minute and " : " minutes and ")
                    + seconds + (seconds == 1 ? " second" : " seconds");
        }
        if (minutes > 0) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    private static void pruneExpired(Deque<Instant> timestamps, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
            timestamps.pollFirst();
        }
    }

    private static String keyFor(String identifier, String clientIp) {
        String normId = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        String normIp = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        return normId + "|" + normIp;
    }
}
