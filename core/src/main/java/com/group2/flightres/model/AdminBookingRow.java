// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.model;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.util.FlightDateFormatter;
import com.group2.flightres.util.FlightTimes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Joined booking row DTO for Admin Bookings table and detail drawer.
 */
public record AdminBookingRow(
        long bookingId,
        String bookingReference,
        String passengerName,
        String passengerEmail,
        String userFullName,
        long flightId,
        String flightNumber,
        String originCode,
        String originCity,
        String destinationCode,
        String destinationCity,
        Instant departureInstant,
        ZoneId originZone,
        Instant arrivalInstant,
        ZoneId destinationZone,
        String seatLabels,
        SeatClass seatClass,
        BigDecimal totalAmount,
        String status
) {
    public String getRoute() {
        return originCode + " -> " + destinationCode;
    }

    public String getFormattedDepartureDate() {
        return FlightDateFormatter.format(LocalDate.ofInstant(departureInstant, originZone));
    }

    public String getFormattedDepartureTime() {
        return FlightTimes.formatLocalTime(departureInstant, originZone);
    }

    public String getFormattedDepartureDateTime() {
        return FlightTimes.formatLocalDate(departureInstant, originZone) + " "
                + FlightTimes.formatLocalTime(departureInstant, originZone);
    }

    public String getFormattedArrivalTime() {
        return FlightTimes.formatLocalTime(arrivalInstant, destinationZone);
    }

    public String getDuration() {
        return FlightTimes.formatDuration(departureInstant, arrivalInstant);
    }

    public TicketData toTicketData() {
        String depTime = getFormattedDepartureTime();
        String arrTime = getFormattedArrivalTime();
        String formattedDate = getFormattedDepartureDate();
        String cabin = (seatClass == SeatClass.FIRST) ? "First Class" : "Economy";
        String seatStr = (seatLabels != null && !seatLabels.isBlank()) ? seatLabels : "-";
        return new TicketData(
                passengerName,
                flightNumber,
                originCode,
                originCity,
                destinationCode,
                destinationCity,
                formattedDate,
                depTime,
                arrTime,
                getDuration(),
                BoardingPolicy.formatBoardingTime(depTime),
                BoardingPolicy.formatBagDropTime(depTime, originCity),
                seatStr,
                cabin,
                bookingReference,
                seatClass != null ? seatClass : SeatClass.ECONOMY,
                totalAmount
        );
    }
}
