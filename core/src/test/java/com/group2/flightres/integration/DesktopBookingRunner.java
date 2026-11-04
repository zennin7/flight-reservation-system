package com.group2.flightres.integration;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.DashboardStatsService;
import com.group2.flightres.model.DashboardStats;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DesktopBookingRunner {
    public static void main(String[] args) {
        System.out.println("=== Desktop Booking Initiation ===");
        BookingService bookingService = new BookingService();
        DashboardStatsService statsService = new DashboardStatsService();

        // 1. Create a Desktop Booking Context
        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("GRP 204");
        ctx.setOrigin("Johannesburg (JNB)");
        ctx.setDestination("Cape Town (CPT)");
        ctx.setDepartureDate("2026-11-02");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:40");

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(12L); // Seat 6D
        seat.setFlightId(1L);
        seat.setSeatRow(6);
        seat.setSeatCol('D');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setCalculatedPrice(new BigDecimal("1200.00"));

        ctx.setSelectedSeats(new ArrayList<>(List.of(seat)));
        ctx.setBaseFare(new BigDecimal("1200.00"));
        ctx.setClassMultiplier(BigDecimal.ONE);
        ctx.setDemandPricing(new BigDecimal("180.00"));
        ctx.setTaxesAndFees(new BigDecimal("142.00"));
        ctx.setTotalPrice(new BigDecimal("1522.00"));

        ctx.setPassengerName("Thabo Ndlovu");
        ctx.setPassengerEmail("thabo.ndlovu@skyvoyage.com");
        ctx.setPassportNumber("TB11223344");
        ctx.setDateOfBirth(java.time.LocalDate.of(1990, 5, 14));
        ctx.setPaymentResult(new com.group2.flightres.service.PaymentResult(true, "TXN-RUNNER-1", "VISA", "4242", null));

        // Execute booking transaction (same method Desktop CheckoutController calls)
        BookingRecord record = bookingService.createBooking(ctx);
        System.out.println("DESKTOP_BOOKING_SUCCESS:" + record.getBookingReference());
        System.out.println("PASSENGER:" + record.getPassengerName());
        System.out.println("TOTAL_AMOUNT:" + record.getTotalAmount());

        // Check updated platform stats
        DashboardStats stats = statsService.getStats();
        System.out.println("REFRESHED_TOTAL_BOOKINGS:" + stats.getTotalBookings());
        System.out.println("REFRESHED_TOTAL_REVENUE:" + stats.getTotalRevenue());
    }
}
