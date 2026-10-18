package com.group2.flightres.dao;

import com.group2.flightres.model.FlightSeat;

import java.util.List;
import java.util.Optional;

public interface SeatDao {

    List<FlightSeat> getSeatMapForFlight(long flightId);

    boolean holdSeat(long flightSeatId);

    boolean releaseSeat(long flightSeatId);

    boolean bookSeat(long flightSeatId);

    /**
     * Resets any HELD seats whose hold duration has elapsed back to AVAILABLE.
     *
     * @return number of seats returned to AVAILABLE status
     */
    int releaseExpiredHolds();

    /**
     * Finds a flight seat by its ID.
     */
    Optional<FlightSeat> findSeatById(long flightSeatId);
}
