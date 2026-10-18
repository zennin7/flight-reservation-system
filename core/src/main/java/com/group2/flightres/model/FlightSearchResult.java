package com.group2.flightres.model;

import java.math.BigDecimal;

/**
 * Model representing a flight search result entry.
 */
public class FlightSearchResult {

    private final Long flightId;
    private final String flightNumber;
    private final String departureTime;
    private final String arrivalTime;
    private final String duration;
    private final BigDecimal basePrice;
    private final String priceFormatted;

    public FlightSearchResult(Long flightId, String flightNumber, String departureTime,
                              String arrivalTime, String duration, BigDecimal basePrice,
                              String priceFormatted) {
        this.flightId = flightId;
        this.flightNumber = flightNumber;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.duration = duration;
        this.basePrice = basePrice;
        this.priceFormatted = priceFormatted;
    }

    public Long getFlightId() { return flightId; }
    public String getFlightNumber() { return flightNumber; }
    public String getDepartureTime() { return departureTime; }
    public String getArrivalTime() { return arrivalTime; }
    public String getDuration() { return duration; }
    public BigDecimal getBasePrice() { return basePrice; }
    public String getPriceFormatted() { return priceFormatted; }
}
