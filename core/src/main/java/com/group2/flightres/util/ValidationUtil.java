package com.group2.flightres.util;

import com.group2.flightres.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Validates domain inputs (names, emails, passport numbers, dates, routes)
 * and logs validation failures with SLF4J.
 */
public final class ValidationUtil {

    private static final Logger logger = LoggerFactory.getLogger(ValidationUtil.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    // Standard alphanumeric passport pattern (6 to 20 characters)
    private static final Pattern PASSPORT_PATTERN = Pattern.compile(
            "^[A-Z0-9]{6,20}$"
    );

    // Passenger name allowing letters, spaces, hyphens, periods, and apostrophes
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "^[A-Za-zÀ-ÖØ-öø-ÿ'\\-.]+(?:\\s+[A-Za-zÀ-ÖØ-öø-ÿ'\\-.]+)+$"
    );

    private ValidationUtil() {}

    /**
     * Validates passenger full name (first and last name required).
     */
    public static String validatePassengerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            logger.warn("Validation failed: Passenger name is empty");
            throw new ValidationException("passengerName", "Passenger name is required.");
        }
        String trimmed = name.trim();
        if (trimmed.length() < 3 || trimmed.length() > 120) {
            logger.warn("Validation failed: Passenger name length {} is invalid", trimmed.length());
            throw new ValidationException("passengerName", "Passenger name must be between 3 and 120 characters.");
        }
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            logger.warn("Validation failed: Passenger name '{}' contains invalid characters or lacks full name", trimmed);
            throw new ValidationException("passengerName", "Please enter both first and last name using valid characters.");
        }
        return trimmed;
    }

    /**
     * Validates passenger email address format.
     */
    public static String validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            logger.warn("Validation failed: Email address is empty");
            throw new ValidationException("email", "Email address is required.");
        }
        String trimmed = email.trim();
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            logger.warn("Validation failed: Invalid email format '{}'", trimmed);
            throw new ValidationException("email", "Please enter a valid email address (e.g. user@example.com).");
        }
        return trimmed;
    }

    /**
     * Validates passport number format.
     */
    public static String validatePassportNumber(String passport) {
        if (passport == null || passport.trim().isEmpty()) {
            logger.warn("Validation failed: Passport number is empty");
            throw new ValidationException("passportNumber", "Passport number is required.");
        }
        String normalized = passport.trim().toUpperCase();
        if (!PASSPORT_PATTERN.matcher(normalized).matches()) {
            logger.warn("Validation failed: Invalid passport number format '{}'", normalized);
            throw new ValidationException("passportNumber", "Passport number must be 6 to 20 alphanumeric characters.");
        }
        return normalized;
    }

    /**
     * Validates flight search criteria (origin, destination, departure date).
     */
    public static void validateFlightSearch(String origin, String destination, LocalDate departureDate) {
        if (origin == null || origin.trim().isEmpty()) {
            logger.warn("Validation failed: Search origin airport/city is empty");
            throw new ValidationException("origin", "Origin departure city or airport code is required.");
        }
        if (destination == null || destination.trim().isEmpty()) {
            logger.warn("Validation failed: Search destination airport/city is empty");
            throw new ValidationException("destination", "Destination arrival city or airport code is required.");
        }

        String normOrigin = extractAirportCodeOrCity(origin);
        String normDest = extractAirportCodeOrCity(destination);
        if (normOrigin.equalsIgnoreCase(normDest)) {
            logger.warn("Validation failed: Origin '{}' and destination '{}' are the same", origin, destination);
            throw new ValidationException("route", "Origin and destination cannot be the same airport.");
        }

        if (departureDate == null) {
            logger.warn("Validation failed: Departure date is null");
            throw new ValidationException("departureDate", "Departure date is required.");
        }
    }

    /**
     * Helper to extract 3-letter IATA code if in parentheses, or trimmed city string.
     */
    public static String extractAirportCodeOrCity(String input) {
        if (input == null) return "";
        String s = input.trim();
        int openParen = s.lastIndexOf('(');
        int closeParen = s.lastIndexOf(')');
        if (openParen != -1 && closeParen > openParen) {
            String code = s.substring(openParen + 1, closeParen).trim();
            if (code.length() == 3) {
                return code;
            }
        }
        return s;
    }
}
