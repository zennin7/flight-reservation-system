package com.group2.flightres.model;

import com.group2.flightres.util.FlightTimes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable model representing a flight search result entry with full origin/destination airport,
 * timezone, aircraft model, and seat availability metadata.
 */
public class FlightSearchResult {

    private final Long flightId;
    private final String flightNumber;
    private final String originCode;
    private final String originAirportName;
    private final String originCity;
    private final ZoneId originZone;
    private final String destCode;
    private final String destAirportName;
    private final String destCity;
    private final ZoneId destZone;
    private final Instant departure;
    private final Instant arrival;
    private final BigDecimal basePrice;
    private final String aircraftModel;
    private final int availableSeats;
    private final int availableEconomy;
    private final int availableFirst;

    public FlightSearchResult(
            Long flightId,
            String flightNumber,
            String originCode,
            String originAirportName,
            String originCity,
            ZoneId originZone,
            String destCode,
            String destAirportName,
            String destCity,
            ZoneId destZone,
            Instant departure,
            Instant arrival,
            BigDecimal basePrice,
            String aircraftModel,
            int availableSeats,
            int availableEconomy,
            int availableFirst) {
        this.flightId = flightId;
        this.flightNumber = flightNumber;
        this.originCode = originCode;
        this.originAirportName = originAirportName;
        this.originCity = originCity;
        this.originZone = originZone;
        this.destCode = destCode;
        this.destAirportName = destAirportName;
        this.destCity = destCity;
        this.destZone = destZone;
        this.departure = departure;
        this.arrival = arrival;
        this.basePrice = basePrice;
        this.aircraftModel = aircraftModel;
        this.availableSeats = availableSeats;
        this.availableEconomy = availableEconomy;
        this.availableFirst = availableFirst;
    }

    /**
     * Backward-compatible convenience constructor defaulting available economy/first breakdown.
     */
    public FlightSearchResult(
            Long flightId,
            String flightNumber,
            String originCode,
            String originAirportName,
            String originCity,
            ZoneId originZone,
            String destCode,
            String destAirportName,
            String destCity,
            ZoneId destZone,
            Instant departure,
            Instant arrival,
            BigDecimal basePrice,
            String aircraftModel,
            int availableSeats) {
        this(flightId, flightNumber, originCode, originAirportName, originCity, originZone,
                destCode, destAirportName, destCity, destZone, departure, arrival, basePrice, aircraftModel,
                availableSeats, availableSeats, 0);
    }

    public Long getFlightId() { return flightId; }
    public String getFlightNumber() { return flightNumber; }
    public String getOriginCode() { return originCode; }
    public String getOriginAirportName() { return originAirportName; }
    public String getOriginCity() { return originCity; }
    public ZoneId getOriginZone() { return originZone; }
    public String getDestCode() { return destCode; }
    public String getDestAirportName() { return destAirportName; }
    public String getDestCity() { return destCity; }
    public ZoneId getDestZone() { return destZone; }
    public Instant getDeparture() { return departure; }
    public Instant getArrival() { return arrival; }
    public BigDecimal getBasePrice() { return basePrice; }
    public String getAircraftModel() { return aircraftModel; }
    public int getAvailableSeats() { return availableSeats; }
    public int getAvailableEconomy() { return availableEconomy; }
    public int getAvailableFirst() { return availableFirst; }

    public FlightSearchResult withBasePrice(BigDecimal newPrice) {
        return new FlightSearchResult(
                flightId, flightNumber, originCode, originAirportName, originCity, originZone,
                destCode, destAirportName, destCity, destZone, departure, arrival,
                newPrice, aircraftModel, availableSeats, availableEconomy, availableFirst
        );
    }

    // Derived getters via FlightTimes for backward compatibility and formatted presentation
    public String getDepartureTime() {
        return FlightTimes.formatLocalTime(departure, originZone);
    }

    public String getArrivalTime() {
        return FlightTimes.formatLocalTime(arrival, destZone);
    }

    public String getDuration() {
        return FlightTimes.formatDuration(departure, arrival);
    }

    public String getPriceFormatted() {
        return basePrice != null
                ? String.format(Locale.US, "R %,d", basePrice.longValue())
                : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlightSearchResult that = (FlightSearchResult) o;
        return availableSeats == that.availableSeats
                && availableEconomy == that.availableEconomy
                && availableFirst == that.availableFirst
                && Objects.equals(flightId, that.flightId)
                && Objects.equals(flightNumber, that.flightNumber)
                && Objects.equals(originCode, that.originCode)
                && Objects.equals(destCode, that.destCode)
                && Objects.equals(departure, that.departure)
                && Objects.equals(arrival, that.arrival)
                && Objects.equals(basePrice, that.basePrice)
                && Objects.equals(aircraftModel, that.aircraftModel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                flightId, flightNumber, originCode, originAirportName, originCity, originZone,
                destCode, destAirportName, destCity, destZone, departure, arrival, basePrice, aircraftModel,
                availableSeats, availableEconomy, availableFirst
        );
    }

    @Override
    public String toString() {
        return "FlightSearchResult{" +
                "flightNumber='" + flightNumber + '\'' +
                ", originCode='" + originCode + '\'' +
                ", destCode='" + destCode + '\'' +
                ", departure=" + departure +
                ", basePrice=" + basePrice +
                ", availableSeats=" + availableSeats +
                '}';
    }
}
