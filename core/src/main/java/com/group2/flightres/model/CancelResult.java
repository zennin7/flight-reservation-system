package com.group2.flightres.model;

import java.math.BigDecimal;

/**
 * Outcome of an idempotent booking cancellation and refund attempt.
 */
public record CancelResult(
        boolean cancelled,
        boolean alreadyCancelled,
        String bookingReference,
        BigDecimal refundAmount,
        String refundReference,
        String message
) {}
