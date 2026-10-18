package com.group2.flightres.model;

import java.math.BigDecimal;
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
    private String departureDate;
    private String departureTime;
    private String arrivalTime;

    private List<FlightSeat> selectedSeats = new ArrayList<>();
    private BigDecimal baseFare = BigDecimal.ZERO;
    private BigDecimal classMultiplier = BigDecimal.ONE;
    private BigDecimal demandPricing = BigDecimal.ZERO;
    private BigDecimal taxesAndFees = BigDecimal.ZERO;
    private BigDecimal totalPrice = BigDecimal.ZERO;

    private String passengerName;
    private String passengerEmail;
    private String passportNumber;
    private String bookingReference;

    private User currentUser;

    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getDepartureDate() { return departureDate; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }

    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }

    public List<FlightSeat> getSelectedSeats() { return selectedSeats; }
    public void setSelectedSeats(List<FlightSeat> selectedSeats) {
        this.selectedSeats = selectedSeats;
        recalculateTotals();
    }

    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
        recalculateTotals();
    }

    public BigDecimal getClassMultiplier() { return classMultiplier; }
    public void setClassMultiplier(BigDecimal classMultiplier) {
        this.classMultiplier = classMultiplier;
        recalculateTotals();
    }

    public BigDecimal getDemandPricing() { return demandPricing; }
    public void setDemandPricing(BigDecimal demandPricing) {
        this.demandPricing = demandPricing;
        recalculateTotals();
    }

    public BigDecimal getTaxesAndFees() { return taxesAndFees; }
    public void setTaxesAndFees(BigDecimal taxesAndFees) {
        this.taxesAndFees = taxesAndFees;
        recalculateTotals();
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

    /**
     * Recalculates total price from baseFare, classMultiplier, demandPricing and taxesAndFees
     * based on currently selected seat.
     */
    public void recalculateTotals() {
        if (selectedSeats == null || selectedSeats.isEmpty()) {
            classMultiplier = BigDecimal.ONE;
            demandPricing = BigDecimal.ZERO;
            taxesAndFees = BigDecimal.ZERO;
            totalPrice = BigDecimal.ZERO;
            return;
        }

        if (baseFare == null) {
            baseFare = BigDecimal.ZERO;
        }

        FlightSeat primarySeat = selectedSeats.get(0);
        if (primarySeat.getSeatClass() == SeatClass.FIRST) {
            classMultiplier = new BigDecimal("2.0");
        } else {
            classMultiplier = new BigDecimal("1.0");
        }

        if (demandPricing == null || demandPricing.compareTo(BigDecimal.ZERO) == 0) {
            demandPricing = new BigDecimal("180");
        }
        if (taxesAndFees == null || taxesAndFees.compareTo(BigDecimal.ZERO) == 0) {
            taxesAndFees = new BigDecimal("142");
        }

        BigDecimal adjustedBase = baseFare.multiply(classMultiplier);
        totalPrice = adjustedBase.add(demandPricing).add(taxesAndFees);
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
