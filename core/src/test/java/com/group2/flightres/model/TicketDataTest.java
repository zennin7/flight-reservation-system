package com.group2.flightres.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
        ctx.setDepartureInstant(Instant.parse("2026-11-14T04:30:00Z"));
        ctx.setArrivalInstant(Instant.parse("2026-11-14T06:35:00Z"));
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
        rec.setDepartureInstant(Instant.parse("2026-11-14T04:30:00Z"));
        rec.setArrivalInstant(Instant.parse("2026-11-14T06:35:00Z"));
        rec.setBookingReference("ZT6RL2");
        rec.setSeatLabel("9E");
        rec.setSeatClass(SeatClass.ECONOMY);

        TicketData data = TicketData.from(rec);
        assertEquals("Amahle Mokoena", data.passenger());
        assertEquals("ZT6RL2", data.bookingReference());
        assertEquals("Sat, 14 Nov 2026", data.formattedDate());
        assertEquals("2h 05m", data.duration());
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
        rec.setDepartureInstant(Instant.parse("2026-11-14T04:30:00Z"));
        rec.setArrivalInstant(Instant.parse("2026-11-14T06:35:00Z"));
        rec.setBookingReference("ZT6RL2");
        rec.setSeatLabel("9E");
        // departureDate is null

        assertThrows(IllegalStateException.class, () -> TicketData.from(rec));
    }

    @Test
    @DisplayName("TicketData throws IllegalStateException when departure or arrival instant is missing")
    void testMissingInstantsThrows() {
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
        ctx.setSelectedSeats(List.of(seat));
        // Departure and arrival instants are null

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> TicketData.from(ctx));
        assertTrue(ex.getMessage().contains("instants are required"));
    }

    @Test
    @DisplayName("TicketData uses originCity and destinationCity from record/context when provided")
    void testOriginDestinationCityFromData() {
        BookingContext ctx = new BookingContext();
        ctx.setPassengerName("Amahle Mokoena");
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("JNB");
        ctx.setOriginCity("Johannesburg");
        ctx.setDestination("DUR");
        ctx.setDestinationCity("Durban");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("07:55");
        ctx.setDepartureInstant(Instant.parse("2026-11-14T04:30:00Z"));
        ctx.setArrivalInstant(Instant.parse("2026-11-14T05:55:00Z"));
        ctx.setBookingReference("ZT6RL2");

        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        ctx.setSelectedSeats(List.of(seat));

        TicketData data = TicketData.from(ctx);
        assertEquals("Johannesburg", data.originCity());
        assertEquals("Durban", data.destinationCity());
        assertEquals("1h 25m", data.duration());
    }

    @Test
    @DisplayName("TicketData computes duration across time zones from Instants (JNB to DXB: 8h 15m)")
    void testDurationAcrossTimezonesJnbToDxb() {
        // JNB is UTC+2 (SAST), DXB is UTC+4 (GST)
        // Departs JNB at 13:00 SAST -> 11:00 UTC
        // Flight duration is 8h 15m
        // Arrives DXB at 19:15 UTC -> 23:15 GST
        // String subtraction (23:15 - 13:00) would erroneously give 10h 15m
        // Instant duration between 11:00 UTC and 19:15 UTC gives exact 8h 15m
        Instant depInstant = Instant.parse("2026-11-14T11:00:00Z");
        Instant arrInstant = Instant.parse("2026-11-14T19:15:00Z");

        BookingContext ctx = new BookingContext();
        ctx.setPassengerName("Fatima Al-Mansoor");
        ctx.setFlightNumber("EK 762");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setOriginCity("Johannesburg");
        ctx.setDestination("Dubai (DXB)");
        ctx.setDestinationCity("Dubai");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("13:00");
        ctx.setArrivalTime("23:15");
        ctx.setDepartureInstant(depInstant);
        ctx.setArrivalInstant(arrInstant);
        ctx.setBookingReference("DXB999");

        FlightSeat seat = new FlightSeat();
        seat.setSeatRow(3);
        seat.setSeatCol('A');
        seat.setSeatClass(SeatClass.FIRST);
        ctx.setSelectedSeats(List.of(seat));

        TicketData data = TicketData.from(ctx);
        assertEquals("13:00", data.departureTime(), "Departure time in origin zone SAST");
        assertEquals("23:15", data.arrivalTime(), "Arrival time in destination zone GST");
        assertEquals("8h 15m", data.duration(), "Duration correctly computed from Instants across time zones");
        assertEquals("First Class", data.cabin());
        assertEquals("3A", data.seatLabel());
    }
}
