package com.group2.flightres.model;

import com.group2.flightres.util.FlightDateFormatter;
import com.group2.flightres.util.FlightTimes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain model representing a flight booking summary for traveller dashboards and booking listings.
 */
public class BookingSummary {

    private final long bookingId;
    private final String bookingReference;
    private final long userId;
    private final String flightNumber;
    private final String originCode;
    private final String originCity;
    private final String destinationCode;
    private final String destinationCity;
    private final Instant departureInstant;
    private final ZoneId departureZone;
    private final Instant arrivalInstant;
    private final ZoneId arrivalZone;
    private final String status;
    private final String storedStatus;
    private final List<String> seatLabels;
    private final BigDecimal totalPaid;
    private final String passengerName;
    private final String passengerEmail;
    private final String aircraftModel;

    public BookingSummary(
            long bookingId,
            String bookingReference,
            long userId,
            String flightNumber,
            String originCode,
            String originCity,
            Instant departureInstant,
            ZoneId departureZone,
            String destinationCode,
            String destinationCity,
            Instant arrivalInstant,
            ZoneId arrivalZone,
            String status,
            String storedStatus,
            List<String> seatLabels,
            BigDecimal totalPaid,
            String passengerName,
            String passengerEmail,
            String aircraftModel
    ) {
        this.bookingId = bookingId;
        this.bookingReference = Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        this.userId = userId;
        this.flightNumber = Objects.requireNonNull(flightNumber, "flightNumber must not be null");
        this.originCode = Objects.requireNonNull(originCode, "originCode must not be null");
        this.originCity = Objects.requireNonNull(originCity, "originCity must not be null");
        this.departureInstant = Objects.requireNonNull(departureInstant, "departureInstant must not be null");
        this.departureZone = departureZone != null ? departureZone : ZoneId.of("UTC");
        this.destinationCode = Objects.requireNonNull(destinationCode, "destinationCode must not be null");
        this.destinationCity = Objects.requireNonNull(destinationCity, "destinationCity must not be null");
        this.arrivalInstant = Objects.requireNonNull(arrivalInstant, "arrivalInstant must not be null");
        this.arrivalZone = arrivalZone != null ? arrivalZone : ZoneId.of("UTC");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.storedStatus = storedStatus != null ? storedStatus : "CONFIRMED";
        this.seatLabels = seatLabels != null ? Collections.unmodifiableList(new ArrayList<>(seatLabels)) : Collections.emptyList();
        this.totalPaid = totalPaid != null ? totalPaid : BigDecimal.ZERO;
        this.passengerName = passengerName != null ? passengerName : "";
        this.passengerEmail = passengerEmail != null ? passengerEmail : "";
        this.aircraftModel = aircraftModel != null ? aircraftModel : "";
    }

    public long getBookingId() { return bookingId; }
    public String getBookingReference() { return bookingReference; }
    public long getUserId() { return userId; }
    public String getFlightNumber() { return flightNumber; }
    public String getOriginCode() { return originCode; }
    public String getOriginCity() { return originCity; }
    public Instant getDepartureInstant() { return departureInstant; }
    public ZoneId getDepartureZone() { return departureZone; }
    public String getDestinationCode() { return destinationCode; }
    public String getDestinationCity() { return destinationCity; }
    public Instant getArrivalInstant() { return arrivalInstant; }
    public ZoneId getArrivalZone() { return arrivalZone; }
    public String getStatus() { return status; }
    public String getStoredStatus() { return storedStatus; }
    public List<String> getSeatLabels() { return seatLabels; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public String getPassengerName() { return passengerName; }
    public String getPassengerEmail() { return passengerEmail; }
    public String getAircraftModel() { return aircraftModel; }

    public String getFormattedDepartureDate() {
        return FlightDateFormatter.format(LocalDate.ofInstant(departureInstant, departureZone));
    }

    public String getFormattedDepartureTime() {
        return FlightTimes.formatLocalTime(departureInstant, departureZone);
    }

    public String getFormattedArrivalTime() {
        return FlightTimes.formatLocalTime(arrivalInstant, arrivalZone);
    }

    public String getDuration() {
        return FlightTimes.formatDuration(departureInstant, arrivalInstant);
    }

    public String getSeatLabelsString() {
        return seatLabels.isEmpty() ? "—" : String.join(", ", seatLabels);
    }

    public String getRoute() {
        return originCode + " → " + destinationCode;
    }

    public String getRouteCities() {
        return originCity + " to " + destinationCity;
    }

    @Override
    public String toString() {
        return "BookingSummary{" +
                "bookingReference='" + bookingReference + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", route='" + getRoute() + '\'' +
                ", status='" + status + '\'' +
                ", departure=" + departureInstant +
                ", seats=" + seatLabels +
                ", totalPaid=" + totalPaid +
                '}';
    }
}
