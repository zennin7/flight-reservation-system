package com.group2.flightres.model;

import com.group2.flightres.util.MoneyFormatter;

import java.math.BigDecimal;

/**
 * Aggregated live route summary for the home page (real DB schedules and fares).
 */
public class RouteSummary {

    private final String originCode;
    private final String originCity;
    private final String originCountry;
    private final String destCode;
    private final String destCity;
    private final String destCountry;
    private final long durationMinutes;
    private final BigDecimal cheapestFare;
    private final int flightCount;

    public RouteSummary(String originCode, String originCity, String originCountry,
                        String destCode, String destCity, String destCountry,
                        long durationMinutes, BigDecimal cheapestFare, int flightCount) {
        this.originCode = originCode;
        this.originCity = originCity;
        this.originCountry = originCountry;
        this.destCode = destCode;
        this.destCity = destCity;
        this.destCountry = destCountry;
        this.durationMinutes = durationMinutes;
        this.cheapestFare = cheapestFare;
        this.flightCount = flightCount;
    }

    public String getOriginCode() { return originCode; }
    public String getOriginCity() { return originCity; }
    public String getOriginCountry() { return originCountry; }
    public String getDestCode() { return destCode; }
    public String getDestCity() { return destCity; }
    public String getDestCountry() { return destCountry; }
    public long getDurationMinutes() { return durationMinutes; }
    public BigDecimal getCheapestFare() { return cheapestFare; }
    public int getFlightCount() { return flightCount; }

    public boolean isInternational() {
        return destCountry != null && !"South Africa".equalsIgnoreCase(destCountry.trim());
    }

    public String getDurationFormatted() {
        long hrs = durationMinutes / 60;
        long mins = durationMinutes % 60;
        return String.format("%dh %02dm", hrs, mins);
    }

    public String getCheapestFareFormatted() {
        return MoneyFormatter.format(cheapestFare);
    }

    public String getOriginParam() {
        return originCity + " (" + originCode + ")";
    }

    public String getDestParam() {
        return destCity + " (" + destCode + ")";
    }
}
