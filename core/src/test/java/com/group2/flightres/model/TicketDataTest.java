package com.group2.flightres.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TicketDataTest {

    @Test
    @DisplayName("TicketData successfully created from valid BookingContext")
    void testFromBookingContext() {
        BookingContext ctx = new BookingContext();
        ctx.setPassengerName("Amahle Mokoena");
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:35");
        ctx.setBookingReference("ZT6RL2");

        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        TicketData data = TicketData.from(ctx);
        assertEquals("Amahle Mokoena", data.passenger());
        assertEquals("FL 204", data.flightNumber());
        assertEquals("JNB", data.originCode());
        assertEquals("Johannesburg", data.originCity());
        assertEquals("CPT", data.destinationCode());
        assertEquals("Cape Town", data.destinationCity());
        assertEquals("Sat, 14 Nov 2026", data.formattedDate());
        assertEquals("06:30", data.departureTime());
        assertEquals("08:35", data.arrivalTime());
        assertEquals("2h 05m", data.duration());
        assertEquals("06:00", data.boardingTime());
        assertEquals("9E", data.seatLabel());
        assertEquals("Economy", data.cabin());
        assertEquals("ZT6RL2", data.bookingReference());
    }

    @Test
    @DisplayName("TicketData throws IllegalStateException when context lacks fields")
    void testFromBookingContextMissingFields() {
        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("ZT6RL2");
        assertThrows(IllegalStateException.class, () -> TicketData.from(ctx));

        ctx.setPassengerName("Amahle Mokoena");
        assertThrows(IllegalStateException.class, () -> TicketData.from(ctx));
    }

    @Test
    @DisplayName("TicketData successfully created from BookingRecord")
    void testFromBookingRecord() {
        BookingRecord rec = new BookingRecord();
        rec.setPassengerName("Amahle Mokoena");
        rec.setFlightNumber("FL 204");
        rec.setOrigin("Johannesburg (JNB)");
        rec.setDestination("Cape Town (CPT)");
        rec.setDepartureDate("2026-11-14");
        rec.setDepartureTime("06:30");
        rec.setArrivalTime("08:35");
        rec.setBookingReference("ZT6RL2");
        rec.setSeatLabel("9E");
        rec.setSeatClass(SeatClass.ECONOMY);

        TicketData data = TicketData.from(rec);
        assertEquals("Amahle Mokoena", data.passenger());
        assertEquals("ZT6RL2", data.bookingReference());
        assertEquals("Sat, 14 Nov 2026", data.formattedDate());
    }

    @Test
    @DisplayName("TicketData throws when BookingRecord lacks departureDate")
    void testFromBookingRecordMissingDepartureDate() {
        BookingRecord rec = new BookingRecord();
        rec.setPassengerName("Amahle Mokoena");
        rec.setFlightNumber("FL 204");
        rec.setOrigin("Johannesburg (JNB)");
        rec.setDestination("Cape Town (CPT)");
        rec.setDepartureTime("06:30");
        rec.setArrivalTime("08:35");
        rec.setBookingReference("ZT6RL2");
        rec.setSeatLabel("9E");
        // departureDate is null

        assertThrows(IllegalStateException.class, () -> TicketData.from(rec));
    }
}
