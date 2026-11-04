package com.group2.flightres.util;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IcsCalendarGeneratorTest {

    @Test
    void testGenerateIcsContent() {
        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setPassengerName("Amahle Mokoena");

        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.BOOKED);
        ctx.setSelectedSeats(List.of(seat));

        String ics = IcsCalendarGenerator.generateIcsContent(ctx);
        assertNotNull(ics);
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("END:VCALENDAR"));
        assertTrue(ics.contains("UID:FLIGHT-K7QX2M-20261114T063000@skyvoyage.com"));
        assertTrue(ics.contains("DTSTART:20261114T063000"));
        assertTrue(ics.contains("DTEND:20261114T083500"));
        assertTrue(ics.contains("SUMMARY:Flight FL 204: Johannesburg (JNB) to Cape Town (CPT)"));
        assertTrue(ics.contains("Booking Reference: K7QX2M"));
        assertTrue(ics.contains("Seat: 9E"));
        assertTrue(ics.contains("STATUS:CONFIRMED"));
    }

    @Test
    void testMissingBookingReferenceThrows() {
        BookingContext ctx = new BookingContext();
        assertThrows(IllegalStateException.class, () -> IcsCalendarGenerator.generateIcsContent(ctx));
    }
}
