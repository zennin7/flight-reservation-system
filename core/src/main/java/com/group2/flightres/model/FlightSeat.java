package com.group2.flightres.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class FlightSeat {

    private long flightSeatId;
    private long flightId;
    private int seatRow;
    private char seatCol;
    private SeatClass seatClass;
    private int displayRow;
    private int displayCol;
    private SeatStatus status;
    private BigDecimal calculatedPrice;
    private Instant heldUntil;
    private String heldBy;

    public String getSeatLabel() {
        return seatRow + String.valueOf(seatCol);
    }

    public long getFlightSeatId() { return flightSeatId; }
    public void setFlightSeatId(long flightSeatId) { this.flightSeatId = flightSeatId; }

    public long getFlightId() { return flightId; }
    public void setFlightId(long flightId) { this.flightId = flightId; }

    public int getSeatRow() { return seatRow; }
    public void setSeatRow(int seatRow) { this.seatRow = seatRow; }

    public char getSeatCol() { return seatCol; }
    public void setSeatCol(char seatCol) { this.seatCol = seatCol; }

    public SeatClass getSeatClass() { return seatClass; }
    public void setSeatClass(SeatClass seatClass) { this.seatClass = seatClass; }

    public int getDisplayRow() { return displayRow; }
    public void setDisplayRow(int displayRow) { this.displayRow = displayRow; }

    public int getDisplayCol() { return displayCol; }
    public void setDisplayCol(int displayCol) { this.displayCol = displayCol; }

    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }

    public BigDecimal getCalculatedPrice() { return calculatedPrice; }
    public void setCalculatedPrice(BigDecimal calculatedPrice) { this.calculatedPrice = calculatedPrice; }

    public Instant getHeldUntil() { return heldUntil; }
    public void setHeldUntil(Instant heldUntil) { this.heldUntil = heldUntil; }

    public String getHeldBy() { return heldBy; }
    public void setHeldBy(String heldBy) { this.heldBy = heldBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlightSeat)) return false;
        FlightSeat other = (FlightSeat) o;
        return flightSeatId == other.flightSeatId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(flightSeatId);
    }
}
