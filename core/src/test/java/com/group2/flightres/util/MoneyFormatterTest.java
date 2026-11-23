package com.group2.flightres.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyFormatterTest {

    @Test
    @DisplayName("Currency and percentages format with dot decimals and comma thousands regardless of JVM Locale")
    void formatsIndependentlyOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            for (Locale testLocale : new Locale[]{
                    Locale.forLanguageTag("en-ZA"),
                    Locale.FRANCE,
                    Locale.GERMANY,
                    Locale.US
            }) {
                Locale.setDefault(testLocale);
                assertEquals("R 1,180.00", MoneyFormatter.format(new BigDecimal("1180")));
                assertEquals("R 0.00", MoneyFormatter.format(null));
                assertEquals("+R 250.50", MoneyFormatter.formatSigned(new BigDecimal("250.5")));
                assertEquals("-R 250.50", MoneyFormatter.formatSigned(new BigDecimal("-250.5")));
                assertEquals("0.0%", MoneyFormatter.formatPercent(0.0));
                assertEquals("7.4%", MoneyFormatter.formatPercent(7.42));
            }
        } finally {
            Locale.setDefault(previous);
        }
    }
}
