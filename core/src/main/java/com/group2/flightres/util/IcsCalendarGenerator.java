package com.group2.flightres.util;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.model.BookingContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utility to generate standard iCalendar (.ics) calendar files for flight reservations.
 * Uses pure RFC 5545 format without third-party dependencies.
 */
public final class IcsCalendarGenerator {

    private static final String CRLF = "\r\n";

    private IcsCalendarGenerator() {
        // Prevent instantiation
    }

    public static String generateIcsContent(BookingContext context) {
        if (context == null) {
            throw new IllegalArgumentException("BookingContext cannot be null to generate calendar invite.");
        }

        String ref = context.getBookingReference();
        if (ref == null || ref.isBlank()) {
            throw new IllegalStateException("Booking reference is required for calendar invite.");
        }

        String flightNo = context.getFlightNumber() != null ? context.getFlightNumber() : "Flight";
        String origin = context.getOrigin() != null ? context.getOrigin() : "Origin";
        String dest = context.getDestination() != null ? context.getDestination() : "Destination";
        String passenger = context.getPassengerName() != null ? context.getPassengerName() : "Passenger";
        String seat = context.getSeatLabel() != null ? context.getSeatLabel() : "—";
        String depTimeStr = context.getDepartureTime();
        String arrTimeStr = context.getArrivalTime();
        String depDateStr = context.getDepartureDate();
        if (depDateStr == null || depDateStr.isBlank()) {
            throw new IllegalArgumentException("Departure date is required for calendar invite.");
        }
        LocalDate depDate = BoardingPolicy.parseDate(depDateStr);
        LocalTime depTime = (depTimeStr != null && !depTimeStr.isBlank())
                ? BoardingPolicy.parseTime(depTimeStr) : LocalTime.of(6, 30);
        LocalTime arrTime = (arrTimeStr != null && !arrTimeStr.isBlank())
                ? BoardingPolicy.parseTime(arrTimeStr) : depTime.plusHours(2);

        LocalDate arrDate = depDate;
        if (arrTime.isBefore(depTime)) {
            arrDate = depDate.plusDays(1);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String dtStart = depDate.atTime(depTime).format(dtf);
        String dtEnd = arrDate.atTime(arrTime).format(dtf);

        String boardingTime = BoardingPolicy.formatBoardingTime(depTimeStr);

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR").append(CRLF);
        sb.append("VERSION:2.0").append(CRLF);
        sb.append("PRODID:-//").append(BrandConfig.APP_NAME).append("//Flight Reservation//EN").append(CRLF);
        sb.append("CALSCALE:GREGORIAN").append(CRLF);
        sb.append("METHOD:PUBLISH").append(CRLF);
        sb.append("BEGIN:VEVENT").append(CRLF);
        sb.append("UID:FLIGHT-").append(ref).append("-").append(dtStart).append("@skyvoyage.com").append(CRLF);
        sb.append("SUMMARY:Flight ").append(flightNo).append(": ").append(origin).append(" to ").append(dest).append(CRLF);
        sb.append("DESCRIPTION:Booking Reference: ").append(ref)
                .append("\\nFlight: ").append(flightNo)
                .append("\\nSeat: ").append(seat)
                .append("\\nPassenger: ").append(passenger)
                .append("\\nBoarding: ").append(boardingTime)
                .append("\\n").append(BoardingPolicy.GATE_NOTE).append(CRLF);
        sb.append("LOCATION:").append(origin).append(CRLF);
        sb.append("DTSTART:").append(dtStart).append(CRLF);
        sb.append("DTEND:").append(dtEnd).append(CRLF);
        sb.append("STATUS:CONFIRMED").append(CRLF);
        sb.append("END:VEVENT").append(CRLF);
        sb.append("END:VCALENDAR").append(CRLF);

        return sb.toString();
    }
}
