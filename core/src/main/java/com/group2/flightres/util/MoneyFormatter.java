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

    public static String formatSigned(BigDecimal amount) {
        BigDecimal safe = amount != null ? amount : BigDecimal.ZERO;
        if (safe.compareTo(BigDecimal.ZERO) < 0) {
            return "-" + format(safe.abs());
        }
        return "+" + format(safe);
    }

    public static String formatPercent(double pct) {
        return String.format(Locale.US, "%.1f%%", pct);
    }
}
