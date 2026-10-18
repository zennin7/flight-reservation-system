package com.group2.flightres.service;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.impl.BookingDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service managing flight booking transactions and lookups.
 * Shared between JavaFX desktop and web modules.
 */
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private final BookingDao bookingDao;

    public BookingService() {
        this(new BookingDaoImpl());
    }

    public BookingService(BookingDao bookingDao) {
        this.bookingDao = bookingDao;
    }

    /**
     * Confirms and persists a booking in a single database transaction.
     * Validates passenger name, email, and passport before committing.
     *
     * @param context active booking context
     * @return created BookingRecord with reference code
     */
    public BookingRecord createBooking(BookingContext context) {
        if (context == null) {
            throw new IllegalArgumentException("BookingContext cannot be null");
        }

        // Validate passenger details
        ValidationUtil.validatePassengerName(context.getPassengerName());
        ValidationUtil.validateEmail(context.getPassengerEmail());
        if (context.getPassportNumber() != null && !context.getPassportNumber().isBlank()) {
            ValidationUtil.validatePassportNumber(context.getPassportNumber());
        }

        if (context.getSelectedSeats() == null || context.getSelectedSeats().isEmpty()) {
            throw new IllegalStateException("At least one seat must be selected to complete a booking.");
        }

        logger.info("Initiating booking transaction for passenger '{}' on flight ID {}",
                context.getPassengerName(), context.getFlightId());

        BookingRecord record = bookingDao.createBooking(context);
        logger.info("Booking confirmed with reference '{}' (ID: {})",
                record.getBookingReference(), record.getBookingId());

        return record;
    }

    /**
     * Looks up a booking by reference code (PNR).
     */
    public Optional<BookingRecord> getBookingByReference(String bookingRef) {
        if (bookingRef == null || bookingRef.trim().isEmpty()) {
            return Optional.empty();
        }
        logger.debug("Looking up booking for reference '{}'", bookingRef);
        return bookingDao.findByReference(bookingRef.trim().toUpperCase());
    }

    /**
     * Retrieves all bookings for a user.
     */
    public List<BookingRecord> getBookingsByUserId(long userId) {
        return bookingDao.findByUserId(userId);
    }
}
