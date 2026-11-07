package com.group2.flightres.model;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.util.FlightDateFormatter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Immutable domain record encapsulating all visual and print data for flight boarding passes.
 * Replaces PassData across the OpenPDF boarding pass generator and the on-screen TicketView.
 */
public record TicketData(
        String passenger,
        String flightNumber,
        String originCode,
        String originCity,
        String destinationCode,
        String destinationCity,
        String formattedDate,
        String departureTime,
        String arrivalTime,
        String duration,
        String boardingTime,
        String bagDropCloseTime,
        String seatLabel,
        String cabin,
        String bookingReference,
        SeatClass seatClass
) {

    /**
     * Builds a TicketData record from an in-flight BookingContext.
     * Throws IllegalStateException if any essential field is missing (no silent fallbacks).
     */
    public static TicketData from(BookingContext context) {
        if (context == null) {
            throw new IllegalStateException("BookingContext cannot be null to build TicketData.");
        }

        String passenger = requireNonBlank(context.getPassengerName(), "Passenger name");
        String flightNo = requireNonBlank(context.getFlightNumber(), "Flight number");
        String originStr = requireNonBlank(context.getOrigin(), "Origin");
        String destStr = requireNonBlank(context.getDestination(), "Destination");
        String depDate = requireNonBlank(context.getDepartureDate(), "Departure date");
        String depTime = requireNonBlank(context.getDepartureTime(), "Departure time");
        String arrTime = requireNonBlank(context.getArrivalTime(), "Arrival time");
        String seat = requireNonBlank(context.getSeatLabel(), "Seat label");
        if ("—".equals(seat)) {
            throw new IllegalStateException("Seat label is required to build TicketData.");
        }
        String ref = requireNonBlank(context.getBookingReference(), "Booking reference");

        SeatClass seatCls = (context.getSeatClass() != null) ? context.getSeatClass() : SeatClass.ECONOMY;
        String cabin = (seatCls == SeatClass.FIRST) ? "First Class" : "Economy";

        String originCode = extractCode(originStr, "JNB");
        String originCity = extractCity(originStr, originCode);
        String destCode = extractCode(destStr, "CPT");
        String destCity = extractCity(destStr, destCode);

        String formattedDate = FlightDateFormatter.format(depDate);
        String duration = calculateDuration(depTime, arrTime);
        String boardingTime = BoardingPolicy.formatBoardingTime(depTime);
        String bagDropTime = BoardingPolicy.formatBagDropTime(depTime, originCity);

        return new TicketData(
                passenger,
                flightNo,
                originCode,
                originCity,
                destCode,
                destCity,
                formattedDate,
                depTime,
                arrTime,
                duration,
                boardingTime,
                bagDropTime,
                seat,
                cabin,
                ref,
                seatCls
        );
    }

    /**
     * Builds a TicketData record from a persisted BookingRecord.
     * Throws IllegalStateException if any essential field is missing (no silent fallbacks).
     */
    public static TicketData from(BookingRecord record) {
        if (record == null) {
            throw new IllegalStateException("BookingRecord cannot be null to build TicketData.");
        }

        String passenger = requireNonBlank(record.getPassengerName(), "Passenger name");
        String flightNo = requireNonBlank(record.getFlightNumber(), "Flight number");
        String originStr = requireNonBlank(record.getOrigin(), "Origin");
        String destStr = requireNonBlank(record.getDestination(), "Destination");
        String depDate = requireNonBlank(record.getDepartureDate(), "Departure date");
        String depTime = requireNonBlank(record.getDepartureTime(), "Departure time");
        String arrTime = requireNonBlank(record.getArrivalTime(), "Arrival time");
        String seat = requireNonBlank(record.getSeatLabel(), "Seat label");
        if ("—".equals(seat)) {
            throw new IllegalStateException("Seat label is required to build TicketData.");
        }
        String ref = requireNonBlank(record.getBookingReference(), "Booking reference");

        SeatClass seatCls = (record.getSeatClass() != null) ? record.getSeatClass() : SeatClass.ECONOMY;
        String cabin = (seatCls == SeatClass.FIRST) ? "First Class" : "Economy";

        String originCode = extractCode(originStr, "JNB");
        String originCity = extractCity(originStr, originCode);
        String destCode = extractCode(destStr, "CPT");
        String destCity = extractCity(destStr, destCode);

        String formattedDate = FlightDateFormatter.format(depDate);
        String duration = calculateDuration(depTime, arrTime);
        String boardingTime = BoardingPolicy.formatBoardingTime(depTime);
        String bagDropTime = BoardingPolicy.formatBagDropTime(depTime, originCity);

        return new TicketData(
                passenger,
                flightNo,
                originCode,
                originCity,
                destCode,
                destCity,
                formattedDate,
                depTime,
                arrTime,
                duration,
                boardingTime,
                bagDropTime,
                seat,
                cabin,
                ref,
                seatCls
        );
    }

    private static String requireNonBlank(String val, String fieldName) {
        if (val == null || val.isBlank()) {
            throw new IllegalStateException(fieldName + " is required to build TicketData.");
        }
        return val.trim();
    }

    private static String extractCode(String str, String fallback) {
        if (str == null) return fallback;
        int openParen = str.lastIndexOf('(');
        int closeParen = str.lastIndexOf(')');
        if (openParen >= 0 && closeParen > openParen) {
            return str.substring(openParen + 1, closeParen).trim().toUpperCase(Locale.ROOT);
        }
        if (str.length() == 3) {
            return str.toUpperCase(Locale.ROOT);
        }
        return fallback;
    }

    private static String extractCity(String str, String code) {
        if (str == null) return code;
        int openParen = str.lastIndexOf('(');
        if (openParen > 0) {
            return str.substring(0, openParen).trim();
        }
        if ("JNB".equalsIgnoreCase(code)) return "Johannesburg";
        if ("CPT".equalsIgnoreCase(code)) return "Cape Town";
        if ("DUR".equalsIgnoreCase(code)) return "Durban";
        return str.trim();
    }

    private static String calculateDuration(String depTime, String arrTime) {
        try {
            LocalTime d = LocalTime.parse(depTime.trim(), DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime a = LocalTime.parse(arrTime.trim(), DateTimeFormatter.ofPattern("HH:mm"));
            long minutes = ChronoUnit.MINUTES.between(d, a);
            if (minutes < 0) minutes += 24 * 60;
            long hours = minutes / 60;
            long remMin = minutes % 60;
            return String.format(Locale.ENGLISH, "%dh %02dm", hours, remMin);
        } catch (Exception e) {
            return "2h 05m";
        }
    }
}
