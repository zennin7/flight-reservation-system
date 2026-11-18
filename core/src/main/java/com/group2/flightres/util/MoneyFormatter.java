package com.group2.flightres.util;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Single shared currency formatter across all desktop screens.
 */
public final class MoneyFormatter {

    private MoneyFormatter() {}

    public static String format(BigDecimal amount) {
        BigDecimal safe = amount != null ? amount : BigDecimal.ZERO;
        return String.format(Locale.US, "R %,.2f", safe);
    }
}
