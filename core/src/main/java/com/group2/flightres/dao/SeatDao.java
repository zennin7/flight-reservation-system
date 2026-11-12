package com.group2.flightres.dao;

import com.group2.flightres.config.SeatHoldPolicy;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SeatDao {

    List<FlightSeat> getSeatMapForFlight(long flightId);

    /**
     * Holds a seat for the specified duration in seconds.
     * Sets status = 'HELD', held_until = NOW() + make_interval(secs => ?).
     *
     * @param flightSeatId seat ID to hold
     * @param holdSeconds duration of hold in seconds from SeatHoldPolicy
     * @return DB-generated held_until instant if held successfully, or empty if seat is unavailable
     */
    default Optional<Instant> holdSeat(long flightSeatId, int holdSeconds) {
        return Optional.of(Instant.now().plusSeconds(holdSeconds));
    }

    default boolean holdSeat(long flightSeatId) {
        return holdSeat(flightSeatId, SeatHoldPolicy.HOLD_SECONDS).isPresent();
    }

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

    /**
     * Reads all seat class multipliers directly from the seat_class_pricing table.
     *
     * @return map of SeatClass to its price multiplier (e.g. FIRST -> 3.50, ECONOMY -> 1.00)
     */
    default Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
        return Map.of(SeatClass.ECONOMY, BigDecimal.ONE, SeatClass.FIRST, new BigDecimal("3.50"));
    }
}
