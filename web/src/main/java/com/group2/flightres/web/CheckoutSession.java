package com.group2.flightres.web;

import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Server-side checkout state held between POST /book, GET /checkout, and POST /pay.
 * Intentionally excludes raw card numbers and CVVs so sensitive card details are never retained in session.
 */
public class CheckoutSession implements Serializable {

    public static final int MAX_PAYMENT_ATTEMPTS = 3;

    private final long flightId;
    private final String flightNumber;
    private final String origin;
    private final String destination;
    private final String originCode;
    private final String destinationCode;
    private final String originCity;
    private final String destinationCity;
    private final String departureDate;
    private final String departureTime;
    private final String arrivalTime;
    private final Instant departureInstant;
    private final Instant arrivalInstant;

    private final FlightSeat seat;
    private String passengerName;
    private String passengerEmail;
    private String passportNumber;
    private LocalDate dateOfBirth;

    private PriceQuote quote;
    private final String idempotencyKey;
    private final String holdOwner;
    private final Instant holdExpiresAt;

    private int failedAttempts;
    private volatile String completedBookingRef;

    public CheckoutSession(FlightSearchResult flight,
                           String origin,
                           String destination,
                           String departureDate,
                           FlightSeat seat,
                           String passengerName,
                           String passengerEmail,
                           String passportNumber,
                           LocalDate dateOfBirth,
                           PriceQuote quote,
                           String idempotencyKey,
                           String holdOwner,
                           Instant holdExpiresAt) {
        this.flightId = flight.getFlightId();
        this.flightNumber = flight.getFlightNumber();
        this.origin = origin;
        this.destination = destination;
        this.originCode = resolveCode(flight.getOriginCode(), origin, "JNB");
        this.destinationCode = resolveCode(flight.getDestCode(), destination, "CPT");
        this.originCity = resolveCity(flight.getOriginCity(), origin);
        this.destinationCity = resolveCity(flight.getDestCity(), destination);
        this.departureDate = departureDate;
        this.departureTime = flight.getDepartureTime();
        this.arrivalTime = flight.getArrivalTime();
        this.departureInstant = flight.getDeparture();
        this.arrivalInstant = flight.getArrival();
        this.seat = Objects.requireNonNull(seat, "seat must not be null");
        this.passengerName = passengerName != null ? passengerName : "";
        this.passengerEmail = passengerEmail != null ? passengerEmail : "";
        this.passportNumber = passportNumber != null ? passportNumber : "";
        this.dateOfBirth = dateOfBirth;
        this.quote = Objects.requireNonNull(quote, "quote must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.holdOwner = Objects.requireNonNull(holdOwner, "holdOwner must not be null");
        this.holdExpiresAt = Objects.requireNonNull(holdExpiresAt, "holdExpiresAt must not be null");
    }

    public long getFlightId() { return flightId; }
    public String getFlightNumber() { return flightNumber; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getOriginCode() { return originCode; }
    public String getDestinationCode() { return destinationCode; }
    public String getOriginCity() { return originCity; }
    public String getDestinationCity() { return destinationCity; }
    public String getDepartureDate() { return departureDate; }
    public String getDepartureTime() { return departureTime; }
    public String getArrivalTime() { return arrivalTime; }
    public Instant getDepartureInstant() { return departureInstant; }
    public Instant getArrivalInstant() { return arrivalInstant; }

    public FlightSeat getSeat() { return seat; }
    public long getFlightSeatId() { return seat.getFlightSeatId(); }
    public String getSeatLabel() { return seat.getSeatLabel(); }
    public String getCabinDisplay() {
        return seat.getSeatClass() == SeatClass.FIRST ? "First Class" : "Economy";
    }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName != null ? passengerName : ""; }
    public String getPassengerEmail() { return passengerEmail; }
    public void setPassengerEmail(String passengerEmail) { this.passengerEmail = passengerEmail != null ? passengerEmail : ""; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber != null ? passportNumber : ""; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getDateOfBirthFormatted() { return dateOfBirth != null ? dateOfBirth.toString() : ""; }

    public PriceQuote getQuote() { return quote; }
    public void setQuote(PriceQuote quote) { this.quote = Objects.requireNonNull(quote); }
    public BigDecimal getTotalPrice() { return quote.total(); }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getHoldOwner() { return holdOwner; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }

    public int getFailedAttempts() { return failedAttempts; }
    public int incrementFailedAttempts() { return ++failedAttempts; }
    public int getRemainingAttempts() { return Math.max(0, MAX_PAYMENT_ATTEMPTS - failedAttempts); }

    public String getCompletedBookingRef() { return completedBookingRef; }
    public void setCompletedBookingRef(String completedBookingRef) { this.completedBookingRef = completedBookingRef; }

    private static String resolveCode(String code, String label, String fallback) {
        if (code != null && !code.isBlank()) {
            return code.trim();
        }
        if (label != null) {
            int open = label.lastIndexOf('(');
            int close = label.lastIndexOf(')');
            if (open >= 0 && close > open + 1) {
                return label.substring(open + 1, close).trim();
            }
        }
        return fallback;
    }

    private static String resolveCity(String city, String label) {
        if (city != null && !city.isBlank()) {
            return city.trim();
        }
        if (label != null) {
            int open = label.lastIndexOf('(');
            if (open > 0) {
                return label.substring(0, open).trim();
            }
            return label.trim();
        }
        return "";
    }
}
