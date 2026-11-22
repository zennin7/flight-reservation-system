package com.group2.flightres.model;

import com.group2.flightres.service.PaymentResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared mutable state passed between wizard steps (Search -> Seat
 * Selection -> Checkout -> Confirmation). One instance lives for the
 * whole booking session, owned by MainShellController.
 */
public class BookingContext {

    private Long flightId;
    private String flightNumber;
    private String origin;
    private String destination;
    private String originCode;
    private String originAirport;
    private String originCity;
    private String destinationCode;
    private String destinationAirport;
    private String destinationCity;
    private String duration;
    private String aircraftModel;
    private String departureDate;
    private String departureTime;
    private String arrivalTime;
    private Instant departureInstant;
    private Instant arrivalInstant;

    private List<FlightSeat> selectedSeats = new ArrayList<>();
    private List<PriceQuote> priceQuotes = new ArrayList<>();
    private BigDecimal baseFare = BigDecimal.ZERO;
    private BigDecimal totalPrice = BigDecimal.ZERO;

    private String passengerName;
    private String passengerEmail;
    private String passportNumber;
    private String bookingReference;
    private LocalDate dateOfBirth;
    private Instant holdExpiresAt;
    private String cardBrand;
    private String cardLast4;
    private PaymentResult paymentResult;
    private String idempotencyKey;
    private String holdOwner;

    private User currentUser;

    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getOriginCode() { return originCode; }
    public void setOriginCode(String originCode) { this.originCode = originCode; }

    public String getOriginAirport() { return originAirport; }
    public void setOriginAirport(String originAirport) { this.originAirport = originAirport; }

    public String getOriginCity() { return originCity; }
    public void setOriginCity(String originCity) { this.originCity = originCity; }

    public String getDestinationCode() { return destinationCode; }
    public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }

    public String getDestinationAirport() { return destinationAirport; }
    public void setDestinationAirport(String destinationAirport) { this.destinationAirport = destinationAirport; }

    public String getDestinationCity() { return destinationCity; }
    public void setDestinationCity(String destinationCity) { this.destinationCity = destinationCity; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getAircraftModel() { return aircraftModel; }
    public void setAircraftModel(String aircraftModel) { this.aircraftModel = aircraftModel; }

    public String getDepartureDate() { return departureDate; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }

    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }

    public Instant getDepartureInstant() { return departureInstant; }
    public void setDepartureInstant(Instant departureInstant) { this.departureInstant = departureInstant; }

    public Instant getArrivalInstant() { return arrivalInstant; }
    public void setArrivalInstant(Instant arrivalInstant) { this.arrivalInstant = arrivalInstant; }

    public List<FlightSeat> getSelectedSeats() { return selectedSeats; }
    public void setSelectedSeats(List<FlightSeat> selectedSeats) {
        this.selectedSeats = selectedSeats != null ? new ArrayList<>(selectedSeats) : new ArrayList<>();
    }

    public List<PriceQuote> getPriceQuotes() { return priceQuotes; }
    public void setPriceQuotes(List<PriceQuote> priceQuotes) {
        this.priceQuotes = priceQuotes != null ? new ArrayList<>(priceQuotes) : new ArrayList<>();
        recalculateTotals();
    }

    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
    }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getPassengerEmail() { return passengerEmail; }
    public void setPassengerEmail(String passengerEmail) { this.passengerEmail = passengerEmail; }

    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User currentUser) { this.currentUser = currentUser; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Instant getHoldExpiresAt() {
        if (holdExpiresAt != null) {
            return holdExpiresAt;
        }
        if (selectedSeats != null && !selectedSeats.isEmpty()) {
            FlightSeat primary = selectedSeats.get(0);
            if (primary != null && primary.getHeldUntil() != null) {
                return primary.getHeldUntil();
            }
        }
        return null;
    }

    public void setHoldExpiresAt(Instant holdExpiresAt) { this.holdExpiresAt = holdExpiresAt; }

    public long getRemainingHoldSeconds() {
        Instant exp = getHoldExpiresAt();
        if (exp == null) return 0;
        long rem = java.time.Duration.between(Instant.now(), exp).toSeconds();
        return Math.max(0, rem);
    }

    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }

    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    public PaymentResult getPaymentResult() { return paymentResult; }
    public void setPaymentResult(PaymentResult paymentResult) {
        this.paymentResult = paymentResult;
        if (paymentResult != null) {
            this.cardBrand = paymentResult.getCardBrand();
            this.cardLast4 = paymentResult.getCardLast4();
        }
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getHoldOwner() { return holdOwner; }
    public void setHoldOwner(String holdOwner) { this.holdOwner = holdOwner; }

    /**
     * Recalculates total price from priceQuotes summing over all selected seats.
     */
    public void recalculateTotals() {
        if (priceQuotes == null || priceQuotes.isEmpty()) {
            totalPrice = BigDecimal.ZERO;
            return;
        }
        totalPrice = priceQuotes.stream()
                .map(PriceQuote::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String generateBookingReference() {
        if (bookingReference != null && !bookingReference.isBlank()) {
            return bookingReference;
        }
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        bookingReference = sb.toString();
        return bookingReference;
    }

    public String getSeatLabel() {
        if (selectedSeats != null && !selectedSeats.isEmpty()) {
            return selectedSeats.get(0).getSeatLabel();
        }
        return "—";
    }

    public SeatClass getSeatClass() {
        if (selectedSeats != null && !selectedSeats.isEmpty()) {
            return selectedSeats.get(0).getSeatClass();
        }
        return SeatClass.ECONOMY;
    }

    public String getSeatDisplay() {
        if (selectedSeats != null && !selectedSeats.isEmpty()) {
            FlightSeat s = selectedSeats.get(0);
            String className = (s.getSeatClass() == SeatClass.FIRST) ? "First Class" : "Economy";
            return s.getSeatLabel() + " · " + className;
        }
        return "None";
    }

    public String getRouteDisplay() {
        if (origin == null || destination == null) {
            return "";
        }
        return extractCode(origin) + " to " + extractCode(destination);
    }

    public String getFlightRouteDisplay() {
        String route = getRouteDisplay();
        if (flightNumber == null || flightNumber.isBlank()) {
            return route;
        }
        return route.isEmpty() ? flightNumber : flightNumber + " · " + route;
    }

    public String getDepartureDisplay() {
        if (departureDate == null && departureTime == null) {
            return "";
        }
        if (departureDate != null && departureTime != null) {
            return departureDate + " · " + departureTime;
        }
        return departureDate != null ? departureDate : departureTime;
    }

    private String extractCode(String val) {
        if (val == null) return "";
        int open = val.lastIndexOf('(');
        int close = val.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return val.substring(open + 1, close);
        }
        return val.trim();
    }
}
