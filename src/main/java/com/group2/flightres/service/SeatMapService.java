package com.group2.flightres.service;

import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.FlightSeat;

import java.util.List;

public class SeatMapService {

    private final SeatDao seatDao = new SeatDaoImpl();

    public List<FlightSeat> loadSeatMap(long flightId) {
        return seatDao.getSeatMapForFlight(flightId);
    }

    public void selectSeat(FlightSeat seat) {
        boolean success = seatDao.holdSeat(seat.getFlightSeatId());
        if (!success) {
            throw new SeatUnavailableException(
                    "Seat " + seat.getSeatLabel() + " was just taken by another user. Please choose a different seat.");
        }
    }

    public void deselectSeat(FlightSeat seat) {
        seatDao.releaseSeat(seat.getFlightSeatId());
    }

    public void confirmBooking(FlightSeat seat) {
        boolean success = seatDao.bookSeat(seat.getFlightSeatId());
        if (!success) {
            throw new SeatUnavailableException(
                    "Seat " + seat.getSeatLabel() + " could not be booked. Its hold may have expired.");
        }
    }
}
