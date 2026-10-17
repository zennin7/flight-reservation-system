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
    private List<FlightSeat> selectedSeats = new ArrayList<>();
    private BigDecimal totalPrice = BigDecimal.ZERO;
    private User currentUser;

    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }

    public List<FlightSeat> getSelectedSeats() { return selectedSeats; }
    public void setSelectedSeats(List<FlightSeat> selectedSeats) { this.selectedSeats = selectedSeats; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User currentUser) { this.currentUser = currentUser; }
}

