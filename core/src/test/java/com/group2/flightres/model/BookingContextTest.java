package com.group2.flightres.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingContextTest {

    @Test
    @DisplayName("Booking reference generates a 6-character alphanumeric uppercase code")
    void testBookingReferenceGeneration() {
        BookingContext context = new BookingContext();
        String ref = context.generateBookingReference();

        assertNotNull(ref);
        assertEquals(6, ref.length());
        assertTrue(ref.matches("[A-Z0-9]{6}"));
        assertEquals(ref, context.getBookingReference());
    }

    @Test
    @DisplayName("Total price calculation reflects base fare, multiplier, demand and taxes")
    void testPriceCalculation() {
        BookingContext context = new BookingContext();
        context.setBaseFare(new BigDecimal("1200"));

        FlightSeat seat = new FlightSeat();
        seat.setSeatClass(SeatClass.FIRST);
        context.setSelectedSeats(List.of(seat));

        // 1200 * 2.0 (First class) + 180 (demand) + 142 (taxes) = 2722
        BigDecimal total = context.getTotalPrice();
        assertEquals(new BigDecimal("2722.0"), total);
    }
}
