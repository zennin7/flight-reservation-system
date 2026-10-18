package com.group2.flightres.service;

import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.FlightSeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service managing seat map availability, holds, and bookings.
 * Shared between JavaFX desktop and web modules.
 */
public class SeatMapService {

    private static final Logger logger = LoggerFactory.getLogger(SeatMapService.class);

    private final SeatDao seatDao;

    public SeatMapService() {
        this(new SeatDaoImpl());
    }

    public SeatMapService(SeatDao seatDao) {
        this.seatDao = seatDao;
    }

    /**
     * Loads the seat map for a flight, automatically releasing any expired holds.
     */
    public List<FlightSeat> loadSeatMap(long flightId) {
        logger.debug("Loading seat map for flight {}", flightId);
        return seatDao.getSeatMapForFlight(flightId);
    }

    /**
     * Attempts an optimistic hold on the specified seat.
     * Throws SeatUnavailableException if the seat was already taken or held by someone else.
     */
    public void selectSeat(FlightSeat seat) {
        if (seat == null) {
            throw new IllegalArgumentException("Cannot select null seat.");
        }
        boolean success = seatDao.holdSeat(seat.getFlightSeatId());
        if (!success) {
            logger.warn("Seat hold failed: Seat {} (ID: {}) was just taken by another user",
                    seat.getSeatLabel(), seat.getFlightSeatId());
            throw new SeatUnavailableException(
                    "Seat " + seat.getSeatLabel() + " was just taken by another user. Please choose a different seat.");
        }
        logger.info("Seat {} (ID: {}) held successfully for 5 minutes", seat.getSeatLabel(), seat.getFlightSeatId());
    }

    /**
     * Releases a held seat back to AVAILABLE status.
     */
    public void deselectSeat(FlightSeat seat) {
        if (seat == null) return;
        boolean released = seatDao.releaseSeat(seat.getFlightSeatId());
        logger.info("Released hold for seat {} (ID: {}): {}", seat.getSeatLabel(), seat.getFlightSeatId(), released);
    }

    /**
     * Confirms the seat booking during checkout.
     */
    public void confirmBooking(FlightSeat seat) {
        if (seat == null) {
            throw new IllegalArgumentException("Cannot book null seat.");
        }
        boolean success = seatDao.bookSeat(seat.getFlightSeatId());
        if (!success) {
            logger.warn("Booking failed for seat {} (ID: {}). Hold expired or seat taken",
                    seat.getSeatLabel(), seat.getFlightSeatId());
            throw new SeatUnavailableException(
                    "Seat " + seat.getSeatLabel() + " could not be booked. Its hold may have expired.");
        }
        logger.info("Seat {} (ID: {}) successfully booked", seat.getSeatLabel(), seat.getFlightSeatId());
    }

    /**
     * Releases expired holds across all flights.
     */
    public int releaseExpiredHolds() {
        return seatDao.releaseExpiredHolds();
    }
}
