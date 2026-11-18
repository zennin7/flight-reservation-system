package com.group2.flightres.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared flight boarding and check-in policy across core services, PDF generator, and UI.
 */
public final class BoardingPolicy {

    public static final int BOARDING_MINUTES_BEFORE = 30;
    public static final int BAG_DROP_CLOSES_MINUTES_BEFORE = 60;
    public static final int CHECKIN_OPENS_HOURS_BEFORE = 24;
    public static final String GATE_NOTE = "Gate shown 60 min before departure";

    private BoardingPolicy() {
        // Prevent instantiation
    }

    public static LocalTime calculateBoardingTime(LocalTime depTime) {
        if (depTime == null) return null;
        return depTime.minusMinutes(BOARDING_MINUTES_BEFORE);
    }

    public static LocalTime calculateBagDropCloseTime(LocalTime depTime) {
        if (depTime == null) return null;
        return depTime.minusMinutes(BAG_DROP_CLOSES_MINUTES_BEFORE);
    }

    public static LocalDateTime calculateCheckinOpens(LocalDateTime depDateTime) {
        if (depDateTime == null) return null;
        return depDateTime.minusHours(CHECKIN_OPENS_HOURS_BEFORE);
    }

    public static String formatBoardingTime(String depTimeStr) {
        if (depTimeStr == null || depTimeStr.isBlank()) return "—";
        try {
            LocalTime t = parseTime(depTimeStr);
            LocalTime b = calculateBoardingTime(t);
            return b.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return "—";
        }
    }

    public static String formatBagDropTime(String depTimeStr, String originAirport) {
        if (depTimeStr == null || depTimeStr.isBlank()) return "—";
        try {
            LocalTime t = parseTime(depTimeStr);
            LocalTime b = calculateBagDropCloseTime(t);
            String time = b.format(DateTimeFormatter.ofPattern("HH:mm"));
            if (originAirport != null && !originAirport.isBlank()) {
                return time + " at " + originAirport;
            }
            return time;
        } catch (Exception e) {
            return "—";
        }
    }

    public static String formatCheckinOpens(String depDateStr, String depTimeStr) {
        if (depDateStr == null || depDateStr.isBlank() || depTimeStr == null || depTimeStr.isBlank()) return "—";
        try {
            LocalDate d = parseDate(depDateStr);
            LocalTime t = parseTime(depTimeStr);
            LocalDateTime dt = LocalDateTime.of(d, t);
            LocalDateTime opens = calculateCheckinOpens(dt);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, d MMM 'at' HH:mm", Locale.ENGLISH);
            return "Opens " + opens.format(fmt);
        } catch (Exception e) {
            return "—";
        }
    }

    public static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        String clean = s.trim();
        if (clean.length() > 5) {
            clean = clean.substring(0, 5);
        }
        return LocalTime.parse(clean, DateTimeFormatter.ofPattern("HH:mm"));
    }

    public static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String clean = s.trim();
        try {
            return LocalDate.parse(clean);
        } catch (Exception ignored) {}
        DateTimeFormatter[] fmts = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        };
        for (DateTimeFormatter fmt : fmts) {
            try {
                return LocalDate.parse(clean, fmt);
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Cannot parse date: " + s);
    }
}
