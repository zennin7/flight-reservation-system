package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.BookingDaoImpl;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingServiceMyBookingsTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("Status classification: future is UPCOMING, past is COMPLETED, cancelled is CANCELLED")
    void testStatusClassificationAndSorting() {
        // Arrange
        BookingDao stubDao = new BookingDao() {
            @Override
            public BookingRecord createBooking(BookingContext context) { return null; }
            @Override
            public Optional<BookingRecord> findByReference(String bookingRef) { return Optional.empty(); }
            @Override
            public List<BookingRecord> findByUserId(long userId) { return List.of(); }

            @Override
            public List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
                Instant now = clock.instant();
                List<BookingSummary> list = new ArrayList<>();

                // Item 1: 2 hours in future
                Instant tFuture1 = now.plusSeconds(7200);
                list.add(new BookingSummary(
                        1L, "UPC001", userId, "SR 101",
                        "JNB", "Johannesburg", tFuture1, ZoneId.of("Africa/Johannesburg"),
                        "CPT", "Cape Town", tFuture1.plusSeconds(7200), ZoneId.of("Africa/Johannesburg"),
                        "UPCOMING", "CONFIRMED", List.of("1A"), new BigDecimal("1200.00"),
                        "Test Traveller", "test@example.com", "Airbus A320"
                ));

                // Item 2: 24 hours in future
                Instant tFuture2 = now.plusSeconds(86400);
                list.add(new BookingSummary(
                        2L, "UPC002", userId, "SR 102",
                        "CPT", "Cape Town", tFuture2, ZoneId.of("Africa/Johannesburg"),
                        "DUR", "Durban", tFuture2.plusSeconds(7200), ZoneId.of("Africa/Johannesburg"),
                        "UPCOMING", "CONFIRMED", List.of("2B"), new BigDecimal("1500.00"),
                        "Test Traveller", "test@example.com", "Airbus A320"
                ));

                // Item 3: 2 days in past
                Instant tPast1 = now.minusSeconds(172800);
                list.add(new BookingSummary(
                        3L, "PST001", userId, "SR 201",
                        "DUR", "Durban", tPast1, ZoneId.of("Africa/Johannesburg"),
                        "JNB", "Johannesburg", tPast1.plusSeconds(7200), ZoneId.of("Africa/Johannesburg"),
                        "COMPLETED", "CONFIRMED", List.of("3C"), new BigDecimal("900.00"),
                        "Test Traveller", "test@example.com", "Airbus A320"
                ));

                // Item 4: 10 days in past
                Instant tPast2 = now.minusSeconds(864000);
                list.add(new BookingSummary(
                        4L, "PST002", userId, "SR 202",
                        "JNB", "Johannesburg", tPast2, ZoneId.of("Africa/Johannesburg"),
                        "CPT", "Cape Town", tPast2.plusSeconds(7200), ZoneId.of("Africa/Johannesburg"),
                        "COMPLETED", "CONFIRMED", List.of("4D"), new BigDecimal("1100.00"),
                        "Test Traveller", "test@example.com", "Airbus A320"
                ));

                // Item 5: Cancelled in future
                Instant tCancelled = now.plusSeconds(3600);
                list.add(new BookingSummary(
                        5L, "CAN001", userId, "SR 301",
                        "CPT", "Cape Town", tCancelled, ZoneId.of("Africa/Johannesburg"),
                        "JNB", "Johannesburg", tCancelled.plusSeconds(7200), ZoneId.of("Africa/Johannesburg"),
                        "CANCELLED", "CANCELLED", List.of("5E"), new BigDecimal("1300.00"),
                        "Test Traveller", "test@example.com", "Airbus A320"
                ));

                return list;
            }
        };

        BookingService service = new BookingService(stubDao, new FlightDaoImpl(), FIXED_CLOCK);

        // Act
        List<BookingSummary> bookings = service.listBookingsForUser(42L, FIXED_CLOCK);

        // Assert
        assertEquals(5, bookings.size());
        assertEquals("UPC001", bookings.get(0).getBookingReference());
        assertEquals("UPCOMING", bookings.get(0).getStatus());

        assertEquals("UPC002", bookings.get(1).getBookingReference());
        assertEquals("UPCOMING", bookings.get(1).getStatus());

        assertEquals("PST001", bookings.get(2).getBookingReference());
        assertEquals("COMPLETED", bookings.get(2).getStatus());

        assertEquals("PST002", bookings.get(3).getBookingReference());
        assertEquals("COMPLETED", bookings.get(3).getStatus());

        assertEquals("CAN001", bookings.get(4).getBookingReference());
        assertEquals("CANCELLED", bookings.get(4).getStatus());
    }

    @Test
    @DisplayName("Ownership security: User A cannot open User B booking; non-existent booking throws NoSuchElementException")
    void testOwnershipSecurityEnforcement() {
        BookingRecord recordUserA = new BookingRecord();
        recordUserA.setBookingReference("PNR111");
        recordUserA.setUserId(100L);

        BookingRecord recordUserB = new BookingRecord();
        recordUserB.setBookingReference("PNR222");
        recordUserB.setUserId(200L);

        BookingDao stubDao = new BookingDao() {
            @Override
            public BookingRecord createBooking(BookingContext context) { return null; }
            @Override
            public Optional<BookingRecord> findByReference(String bookingRef) {
                if ("PNR111".equalsIgnoreCase(bookingRef)) return Optional.of(recordUserA);
                if ("PNR222".equalsIgnoreCase(bookingRef)) return Optional.of(recordUserB);
                return Optional.empty();
            }
            @Override
            public List<BookingRecord> findByUserId(long userId) { return List.of(); }
        };

        BookingService service = new BookingService(stubDao, new FlightDaoImpl(), FIXED_CLOCK);

        // Authorized access
        BookingRecord foundA = service.getBookingForUser(100L, "PNR111");
        assertNotNull(foundA);
        assertEquals(100L, foundA.getUserId());
        assertEquals("PNR111", foundA.getBookingReference());

        // Unauthorized access (User A tries to access User B's booking)
        SecurityException secEx = assertThrows(SecurityException.class, () ->
                service.getBookingForUser(100L, "PNR222"));
        assertTrue(secEx.getMessage().contains("not authorized"));

        // Non-existent booking
        assertThrows(NoSuchElementException.class, () ->
                service.getBookingForUser(100L, "NONEXIST"));
    }

    @Test
    @DisplayName("DB Integration & SQL Reconciliation: query real DB for demo user 12")
    void testDbIntegrationRealBookingSummary() {
        BookingDaoImpl dao = new BookingDaoImpl();
        List<BookingSummary> list = dao.listSummariesForUser(12L, Clock.systemUTC());
        assertNotNull(list);
        if (!list.isEmpty()) {
            BookingSummary s = list.get(0);
            assertEquals(12L, s.getUserId());
            assertNotNull(s.getBookingReference());
            assertNotNull(s.getFlightNumber());
            assertNotNull(s.getOriginCode());
            assertNotNull(s.getDestinationCode());
            assertNotNull(s.getDepartureInstant());
            assertNotNull(s.getStatus());
            assertTrue(s.getTotalPaid().compareTo(BigDecimal.ZERO) >= 0);
        }
    }
}
