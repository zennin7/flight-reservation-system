package com.group2.flightres.model;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.util.FlightDateFormatter;
import com.group2.flightres.util.FlightTimes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

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
        SeatClass seatClass,
        BigDecimal totalPrice
) {

    public TicketData(
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
        this(passenger, flightNumber, originCode, originCity, destinationCode, destinationCity,
                formattedDate, departureTime, arrivalTime, duration, boardingTime, bagDropCloseTime,
                seatLabel, cabin, bookingReference, seatClass, null);
    }

    public String departureDate() {
        return formattedDate;
    }

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
        String originCity = (context.getOriginCity() != null && !context.getOriginCity().isBlank())
                ? context.getOriginCity() : extractCity(originStr, originCode);
        String destCode = extractCode(destStr, "CPT");
        String destCity = (context.getDestinationCity() != null && !context.getDestinationCity().isBlank())
                ? context.getDestinationCity() : extractCity(destStr, destCode);

        String formattedDate = FlightDateFormatter.format(depDate);
        Instant dep = context.getDepartureInstant();
        Instant arr = context.getArrivalInstant();
        if (dep == null || arr == null) {
            throw new IllegalStateException("Departure and arrival instants are required to compute duration.");
        }
        String duration = FlightTimes.formatDuration(dep, arr);
        String boardingTime = BoardingPolicy.formatBoardingTime(depTime);
        String bagDropTime = BoardingPolicy.formatBagDropTime(depTime, originCity);
        BigDecimal totalPrice = context.getTotalPrice();

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
                seatCls,
                totalPrice
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
        String originCity = (record.getOriginCity() != null && !record.getOriginCity().isBlank())
                ? record.getOriginCity() : extractCity(originStr, originCode);
        String destCode = extractCode(destStr, "CPT");
        String destCity = (record.getDestinationCity() != null && !record.getDestinationCity().isBlank())
                ? record.getDestinationCity() : extractCity(destStr, destCode);

        String formattedDate = FlightDateFormatter.format(depDate);
        Instant dep = record.getDepartureInstant();
        Instant arr = record.getArrivalInstant();
        if (dep == null || arr == null) {
            throw new IllegalStateException("Departure and arrival instants are required to compute duration.");
        }
        String duration = FlightTimes.formatDuration(dep, arr);
        String boardingTime = BoardingPolicy.formatBoardingTime(depTime);
        String bagDropTime = BoardingPolicy.formatBagDropTime(depTime, originCity);
        BigDecimal totalPrice = record.getTotalAmount();

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
                seatCls,
                totalPrice
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
        return str.trim();
    }

}
