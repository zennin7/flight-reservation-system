package com.group2.flightres.exception;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Thrown when the server-side recalculated booking price differs from the price expected by the client.
 */
public class PriceChangedException extends RuntimeException {

    private final BigDecimal oldTotal;
    private final BigDecimal newTotal;

    public PriceChangedException(BigDecimal oldTotal, BigDecimal newTotal) {
        super(String.format(Locale.US, "The fare changed from R %,d to R %,d.",
                oldTotal != null ? oldTotal.longValue() : 0,
                newTotal != null ? newTotal.longValue() : 0));
        this.oldTotal = oldTotal;
        this.newTotal = newTotal;
    }

    public BigDecimal getOldTotal() {
        return oldTotal;
    }

    public BigDecimal getNewTotal() {
        return newTotal;
    }
    public BigDecimal getExpectedTotal() {
        return oldTotal;
    }

    public BigDecimal getActualTotal() {
        return newTotal;
    }
}