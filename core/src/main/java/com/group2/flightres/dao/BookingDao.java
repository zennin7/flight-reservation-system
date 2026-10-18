package com.group2.flightres.dao;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;

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
     * Retrieves all bookings for a user.
     */
    List<BookingRecord> findByUserId(long userId);
}
