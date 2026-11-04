package com.group2.flightres.util;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BoardingPassPdfGeneratorTest {

    @Test
    void testSuccessfulPdfGeneration(@TempDir Path tempDir) throws Exception {
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

        File target = tempDir.resolve("BoardingPass_K7QX2M.pdf").toFile();
        BoardingPassPdfGenerator.generate(ctx, target);

        assertTrue(target.exists());
        assertTrue(target.length() > 1000);
    }

    @Test
    void testMissingPassengerThrows(@TempDir Path tempDir) {
        BookingContext ctx = new BookingContext();
        ctx.setBookingReference("K7QX2M");
        ctx.setFlightNumber("FL 204");
        ctx.setPassengerName(null);

        File target = tempDir.resolve("test.pdf").toFile();
        assertThrows(IllegalStateException.class, () -> BoardingPassPdfGenerator.generate(ctx, target));
    }

    @Test
    void testMissingBookingRefThrows(@TempDir Path tempDir) {
        BookingContext ctx = new BookingContext();
        ctx.setBookingReference(null);
        ctx.setFlightNumber("FL 204");
        ctx.setPassengerName("Amahle Mokoena");

        File target = tempDir.resolve("test.pdf").toFile();
        assertThrows(IllegalStateException.class, () -> BoardingPassPdfGenerator.generate(ctx, target));
    }
}
