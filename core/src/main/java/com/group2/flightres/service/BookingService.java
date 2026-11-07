package com.group2.flightres.service;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.BookingDaoImpl;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Service managing flight booking transactions and lookups.
 * Shared between JavaFX desktop and web modules.
 */
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private final BookingDao bookingDao;
    private final FlightDao flightDao;
    private final Clock clock;

    public BookingService() {
        this(new BookingDaoImpl(), new FlightDaoImpl(), Clock.systemUTC());
    }

    public BookingService(BookingDao bookingDao) {
        this(bookingDao, new FlightDaoImpl(), Clock.systemUTC());
    }

    public BookingService(BookingDao bookingDao, FlightDao flightDao) {
        this(bookingDao, flightDao, Clock.systemUTC());
    }

    public BookingService(BookingDao bookingDao, FlightDao flightDao, Clock clock) {
        this.bookingDao = bookingDao;
        this.flightDao = flightDao != null ? flightDao : new FlightDaoImpl();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Confirms and persists a booking in a single database transaction.
     * Validates passenger details and enforces FlightSalesPolicy before committing.
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
        if (context.getDateOfBirth() != null) {
            ValidationUtil.validateDateOfBirth(context.getDateOfBirth());
        }

        if (context.getSelectedSeats() == null || context.getSelectedSeats().isEmpty()) {
            throw new IllegalStateException("At least one seat must be selected to complete a booking.");
        }

        if (context.getFlightId() != null && context.getFlightId() > 0) {
            FlightSearchResult flight = flightDao.findById(context.getFlightId()).orElse(null);
            if (flight != null && !FlightSalesPolicy.isOpen(flight.getDeparture(), clock)) {
                logger.warn("Booking rejected: Flight {} is closed for sales", flight.getFlightNumber());
                throw new IllegalStateException("Flight " + flight.getFlightNumber() + " is closed for sales (sales close 60 minutes before departure).");
            }
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

    /**
     * Resolves the official airport name by IATA code or city name.
     */
    public Optional<String> findAirportName(String code, String city) {
        return flightDao.findAirportName(code, city);
    }

    /**
     * Resolves the airport city name by IATA code.
     */
    public Optional<String> findAirportCity(String code) {
        return flightDao.findAirportCity(code);
    }
}
