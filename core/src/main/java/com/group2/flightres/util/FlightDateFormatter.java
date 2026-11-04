package com.group2.flightres.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Centralized flight date presentation formatter.
 * Produces standard display strings formatted as "EEE, d MMM yyyy" (e.g. "Sat, 14 Nov 2026").
 */
public final class FlightDateFormatter {

    public static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US);

    private FlightDateFormatter() {
        // Prevent instantiation
    }

    /**
     * Formats a LocalDate into "EEE, d MMM yyyy" (e.g., "Sat, 14 Nov 2026").
     */
    public static String format(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DISPLAY_FORMAT);
    }

    /**
     * Formats a raw date string (e.g., "2026-11-14") into "EEE, d MMM yyyy" (e.g., "Sat, 14 Nov 2026").
     * If already formatted, returns the string trimmed.
     */
    public static String format(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return "";
        }
        String clean = dateStr.trim();
        // Check if already in "EEE, d MMM yyyy" or "EEE, dd MMM yyyy" format
        if (clean.matches("^[A-Za-z]{3},\\s+\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}$")) {
            return clean;
        }
        try {
            LocalDate parsed = LocalDate.parse(clean);
            return format(parsed);
        } catch (DateTimeParseException e) {
            return clean;
        }
    }
}
