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
    @DisplayName("Total price calculation reflects sum of price quotes over all seats")
    void testPriceCalculation() {
        BookingContext context = new BookingContext();
        context.setBaseFare(new BigDecimal("1200.00"));

        PriceQuote q1 = new PriceQuote(
                new BigDecimal("1200.00"),
                new BigDecimal("1.00"),
                new BigDecimal("1200.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("1.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("1200.00"),
                new BigDecimal("156.52")
        );
        PriceQuote q2 = new PriceQuote(
                new BigDecimal("1200.00"),
                new BigDecimal("3.50"),
                new BigDecimal("4200.00"),
                new BigDecimal("1.05"),
                new BigDecimal("210.00"),
                new BigDecimal("1.12"),
                new BigDecimal("529.20"),
                new BigDecimal("4939.20"),
                new BigDecimal("644.24")
        );

        context.setPriceQuotes(List.of(q1, q2));
        assertEquals(new BigDecimal("6139.20"), context.getTotalPrice());
    }
}
