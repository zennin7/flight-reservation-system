package com.group2.flightres.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Pure utility functions for formatting flight times, durations, and time zone abbreviations.
 */
public final class FlightTimes {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
    private static final DateTimeFormatter ZONE_FORMATTER = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH);

    private FlightTimes() {}

    /**
     * Formats an absolute instant to local time "HH:mm" in the given airport time zone.
     */
    public static String formatLocalTime(Instant instant, ZoneId zone) {
        if (instant == null || zone == null) {
            return "";
        }
        return instant.atZone(zone).format(TIME_FORMATTER);
    }

    /**
     * Formats an absolute instant to local date "yyyy-MM-dd" in the given airport time zone.
     */
    public static String formatLocalDate(Instant instant, ZoneId zone) {
        if (instant == null || zone == null) {
            return "";
        }
        return instant.atZone(zone).format(DATE_FORMATTER);
    }

    /**
     * Formats elapsed flight duration between departure and arrival instants as "Xh YYm"
     * with zero-padded minutes (e.g. "2h 10m" or "2h 05m").
     */
    public static String formatDuration(Instant dep, Instant arr) {
        if (dep == null || arr == null) {
            return "";
        }
        Duration d = Duration.between(dep, arr);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return String.format(Locale.ENGLISH, "%dh %02dm", hours, minutes);
    }

    /**
     * Formats the timezone abbreviation for the given zone and instant (e.g. "SAST" or "GST").
     */
    public static String zoneAbbreviation(ZoneId zone, Instant instant) {
        if (zone == null || instant == null) {
            return "";
        }
        return instant.atZone(zone).format(ZONE_FORMATTER);
    }
}
