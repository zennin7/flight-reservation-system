package com.group2.flightres.dao;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public interface BookingDao {

    /**
     * Creates a new booking in a single atomic database transaction.
     * Inserts into bookings, booking_seats, payments, and marks flight_seats BOOKED.
     *
     * @param context the complete booking context
     * @return the created BookingRecord
     */
    BookingRecord createBooking(BookingContext context);

    /**
     * Looks up a booking by its unique 6-character PNR reference.
     */
    Optional<BookingRecord> findByReference(String bookingRef);

    /**
     * Looks up a booking within an existing connection/transaction.
     */
    default Optional<BookingRecord> findByReference(java.sql.Connection conn, String bookingRef) throws java.sql.SQLException {
        return findByReference(bookingRef);
    }

    /**
     * Looks up a booking by reference and user id.
     */
    default Optional<BookingRecord> findByReferenceAndUserId(String bookingRef, long userId) {
        return findByReference(bookingRef).filter(b -> b.getUserId() != null && b.getUserId() == userId);
    }

    /**
     * Retrieves all bookings for a user.
     */
    List<BookingRecord> findByUserId(long userId);

    /**
     * Retrieves booking summaries for a user via a single join query,
     * computing status (UPCOMING, COMPLETED, CANCELLED) using the provided Clock
     * and ordering upcoming ascending, then past descending.
     */
    default List<BookingSummary> listSummariesForUser(long userId, Clock clock) {
        return List.of();
    }
}
