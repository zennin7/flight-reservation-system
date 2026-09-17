package com.group2.flightres.dao;

import com.group2.flightres.model.FlightSeat;

import java.util.List;

public interface SeatDao {

    List<FlightSeat> getSeatMapForFlight(long flightId);

    boolean holdSeat(long flightSeatId);

    boolean releaseSeat(long flightSeatId);

    boolean bookSeat(long flightSeatId);
}
