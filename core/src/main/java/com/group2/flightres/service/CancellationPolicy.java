package com.group2.flightres.service;

import com.group2.flightres.model.BookingRecord;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Enforces the 24-hour free-cancellation policy using an injectable Clock.
 */
public class CancellationPolicy {

    public static final Duration MINIMUM_NOTICE = Duration.ofHours(24);

    public record Decision(
            boolean allowed,
            boolean alreadyCancelled,
            BigDecimal refundAmount,
            String reason
    ) {}

    private final Clock clock;

    public CancellationPolicy() {
        this(Clock.systemUTC());
    }

    public CancellationPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Decision evaluate(BookingRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("BookingRecord must not be null");
        }
        return evaluate(record.getDepartureInstant(), record.getStatus(), record.getTotalAmount());
    }

    public Decision evaluate(Instant departureInstant, String bookingStatus, BigDecimal totalPaid) {
        String normalized = bookingStatus == null ? "" : bookingStatus.trim().toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(normalized) || "REFUNDED".equals(normalized)) {
            return new Decision(false, true, BigDecimal.ZERO, "This booking has already been cancelled.");
        }
        if (departureInstant == null) {
            return new Decision(false, false, BigDecimal.ZERO,
                    "Flight departure time is unavailable for online cancellation.");
        }

        Instant now = clock.instant();
        Duration remaining = Duration.between(now, departureInstant);
        if (remaining.isZero() || remaining.isNegative()) {
            return new Decision(false, false, BigDecimal.ZERO,
                    "This flight has already departed and cannot be cancelled.");
        }
        if (remaining.compareTo(MINIMUM_NOTICE) < 0) {
            return new Decision(false, false, BigDecimal.ZERO,
                    "Free cancellation closes 24 hours before scheduled departure.");
        }

        BigDecimal refund = totalPaid != null ? totalPaid : BigDecimal.ZERO;
        return new Decision(true, false, refund,
                "Eligible for a 100% full refund (departure is at least 24 hours away).");
    }

    public Clock getClock() {
        return clock;
    }
}
