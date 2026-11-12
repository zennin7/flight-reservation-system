package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generates fallback reference seats when the database is offline or empty.
 * Never fabricates prices: if no quotes exist in context, calculated prices remain null
 * causing the UI to show an honest state and disable booking.
 */
class SeatMapFallback {

    private SeatMapFallback() {}

    static List<FlightSeat> generateSampleSeats(long flightId, BookingContext bookingContext) {
        List<FlightSeat> list = new ArrayList<>();
        long id = 100L;

        BigDecimal firstPrice = resolvePrice(bookingContext, SeatClass.FIRST);
        BigDecimal econPrice = resolvePrice(bookingContext, SeatClass.ECONOMY);

        // First Class: 1A(A), 1B(B), 1C(A), 1D(A), 2A(A), 2B(A), 2C(B), 2D(B)
        Set<String> firstBooked = Set.of("1B", "2C", "2D");
        int[] firstRows = {1, 2};
        char[] firstCols = {'A', 'B', 'C', 'D'};
        for (int r : firstRows) {
            for (char c : firstCols) {
                String label = r + String.valueOf(c);
                SeatStatus status = firstBooked.contains(label) ? SeatStatus.BOOKED : SeatStatus.AVAILABLE;
                FlightSeat seat = new FlightSeat();
                seat.setFlightSeatId(id++);
                seat.setFlightId(flightId);
                seat.setSeatRow(r);
                seat.setSeatCol(c);
                seat.setSeatClass(SeatClass.FIRST);
                seat.setStatus(status);
                seat.setCalculatedPrice(firstPrice);
                list.add(seat);
            }
        }

        // Economy Booked: 6A, 6B, 7C, 7D, 7F, 8A, 9B, 10C, 10D, 11A, 11F
        Set<String> econBooked = Set.of("6A", "6B", "7C", "7D", "7F", "8A", "9B", "10C", "10D", "11A", "11F");
        int[] econRows = {6, 7, 8, 9, 10, 11};
        char[] econCols = {'A', 'B', 'C', 'D', 'E', 'F'};
        for (int r : econRows) {
            for (char c : econCols) {
                String label = r + String.valueOf(c);
                SeatStatus status = econBooked.contains(label) ? SeatStatus.BOOKED : SeatStatus.AVAILABLE;
                FlightSeat seat = new FlightSeat();
                seat.setFlightSeatId(id++);
                seat.setFlightId(flightId);
                seat.setSeatRow(r);
                seat.setSeatCol(c);
                seat.setSeatClass(SeatClass.ECONOMY);
                seat.setStatus(status);
                seat.setCalculatedPrice(econPrice);
                list.add(seat);
            }
        }

        return list;
    }

    static FlightSeat createFallbackSeat(long flightId, String label, SeatStatus status, BookingContext bookingContext) {
        FlightSeat s = new FlightSeat();
        s.setFlightSeatId(System.currentTimeMillis());
        s.setFlightId(flightId);
        s.setStatus(status);
        s.setSeatClass(SeatClass.ECONOMY);
        s.setCalculatedPrice(resolvePrice(bookingContext, SeatClass.ECONOMY));
        return s;
    }

    private static BigDecimal resolvePrice(BookingContext bookingContext, SeatClass seatClass) {
        if (bookingContext != null && bookingContext.getPriceQuotes() != null) {
            for (PriceQuote q : bookingContext.getPriceQuotes()) {
                if (q != null && q.total() != null) {
                    return q.total();
                }
            }
        }
        return null;
    }
}
