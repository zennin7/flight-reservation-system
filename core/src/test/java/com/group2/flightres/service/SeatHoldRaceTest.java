package com.group2.flightres.service;

import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatHoldRaceTest {

    /**
     * In-memory thread-safe DAO simulating the SQL:
     * UPDATE flight_seats SET status='HELD', held_until=? WHERE flight_seat_id=? AND status='AVAILABLE'
     */
    private static class AtomicSeatDao implements SeatDao {
        private final AtomicBoolean isHeld = new AtomicBoolean(false);
        private final AtomicBoolean isBooked = new AtomicBoolean(false);
        private final AtomicBoolean isExpired = new AtomicBoolean(false);

        @Override
        public List<FlightSeat> getSeatMapForFlight(long flightId) {
            return Collections.emptyList();
        }

        @Override
        public boolean holdSeat(long flightSeatId) {
            // Expired holds become AVAILABLE again
            if (isExpired.get()) {
                isHeld.set(false);
                isExpired.set(false);
            }
            // Atomically transition from false (AVAILABLE) to true (HELD)
            return isHeld.compareAndSet(false, true);
        }

        @Override
        public boolean releaseSeat(long flightSeatId) {
            return isHeld.compareAndSet(true, false);
        }

        @Override
        public boolean bookSeat(long flightSeatId) {
            if (isHeld.get() && isBooked.compareAndSet(false, true)) {
                isHeld.set(false);
                return true;
            }
            return false;
        }

        @Override
        public int releaseExpiredHolds() {
            if (isHeld.get() && isExpired.get()) {
                isHeld.set(false);
                isExpired.set(false);
                return 1;
            }
            return 0;
        }

        @Override
        public Optional<FlightSeat> findSeatById(long flightSeatId) {
            FlightSeat s = new FlightSeat();
            s.setFlightSeatId(flightSeatId);
            s.setSeatRow(7);
            s.setSeatCol('E');
            s.setSeatClass(SeatClass.ECONOMY);
            s.setStatus(isBooked.get() ? SeatStatus.BOOKED : (isHeld.get() ? SeatStatus.HELD : SeatStatus.AVAILABLE));
            return Optional.of(s);
        }

        public void expireHold() {
            this.isExpired.set(true);
        }
    }

    @Test
    @DisplayName("Race condition: Two concurrent users selecting the same seat results in exactly 1 winner and 1 rejected")
    void testTwoUsersOneSeatHoldRace() throws InterruptedException {
        AtomicSeatDao dao = new AtomicSeatDao();
        SeatMapService service = new SeatMapService(dao);

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setSeatRow(7);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setCalculatedPrice(new BigDecimal("1380"));

        int threadCount = 2;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    service.selectSeat(seat);
                    successCount.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    conflictCount.incrementAndGet();
                    assertTrue(e.getMessage().contains("was just taken"));
                } catch (Exception e) {
                    // unexpected
                } finally {
                    endGate.countDown();
                }
            }).start();
        }

        // Release the latch so both threads compete simultaneously
        startGate.countDown();
        endGate.await();

        assertEquals(1, successCount.get(), "Exactly one user must win the hold race");
        assertEquals(1, conflictCount.get(), "The second user must receive SeatUnavailableException");
    }

    @Test
    @DisplayName("Expired holds become available again and can be re-held")
    void testExpiredHoldBecomesAvailable() {
        AtomicSeatDao dao = new AtomicSeatDao();
        SeatMapService service = new SeatMapService(dao);

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(102L);
        seat.setSeatRow(9);
        seat.setSeatCol('A');

        // First user holds seat
        service.selectSeat(seat);

        // Second user cannot hold
        boolean secondFailed = false;
        try {
            service.selectSeat(seat);
        } catch (SeatUnavailableException e) {
            secondFailed = true;
        }
        assertTrue(secondFailed, "Seat should be unavailable while held");

        // Expire the hold
        dao.expireHold();

        // Release expired holds
        int released = service.releaseExpiredHolds();
        assertEquals(1, released, "Should release 1 expired hold");

        // Now seat can be held again
        service.selectSeat(seat);
    }
}
