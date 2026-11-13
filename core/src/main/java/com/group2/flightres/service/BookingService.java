package com.group2.flightres.service;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.BookingDaoImpl;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.exception.PriceChangedException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.BookingSummary;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Service managing flight booking transactions and lookups.
 * Shared between JavaFX desktop and web modules.
 */
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private final BookingDao bookingDao;
    private final FlightDao flightDao;
    private final PricingService pricingService;
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
        this(bookingDao, flightDao, new PricingService(new SeatDaoImpl(), clock != null ? clock : Clock.systemUTC()), clock);
    }

    public BookingService(BookingDao bookingDao, FlightDao flightDao, PricingService pricingService, Clock clock) {
        this.bookingDao = bookingDao;
        this.flightDao = flightDao != null ? flightDao : new FlightDaoImpl();
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.pricingService = pricingService != null ? pricingService : new PricingService(new SeatDaoImpl(), this.clock);
    }

    /**
     * Confirms and persists a booking in a single database transaction.
     * Recomputes the quote for every selected seat server-side.
     * If the recomputed total differs from the expected total shown to the client,
     * throws PriceChangedException BEFORE any payment, booking, or seat status changes.
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

        // Server-side authority: recompute quote for every selected seat
        long flightId = context.getFlightId() != null ? context.getFlightId() : 0L;
        List<PriceQuote> quotes = new ArrayList<>();
        BigDecimal recomputedTotal = BigDecimal.ZERO;
        for (FlightSeat seat : context.getSelectedSeats()) {
            long fId = seat.getFlightId() > 0 ? seat.getFlightId() : flightId;
            PriceQuote quote = pricingService.quote(fId, seat.getSeatClass(), clock);
            quotes.add(quote);
            recomputedTotal = recomputedTotal.add(quote.total());
            seat.setCalculatedPrice(quote.total());
        }

        BigDecimal clientExpected = context.getTotalPrice();
        if (clientExpected != null && clientExpected.compareTo(BigDecimal.ZERO) > 0
                && clientExpected.compareTo(recomputedTotal) != 0) {
            logger.warn("Booking price mismatch: client expected {} vs server recomputed {}",
                    clientExpected, recomputedTotal);
            throw new PriceChangedException(clientExpected, recomputedTotal);
        }

        // Apply authoritative server-side quotes to context
        context.setPriceQuotes(quotes);
        context.setTotalPrice(recomputedTotal);

        logger.info("Initiating booking transaction for passenger '{}' on flight ID {} (total: R {})",
                context.getPassengerName(), context.getFlightId(), recomputedTotal);

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
     * Lists booking summaries for a user using default service clock.
     */
    public List<BookingSummary> listBookingsForUser(long userId) {
        return listBookingsForUser(userId, this.clock);
    }

    /**
     * Lists booking summaries for a user via a single join query,
     * computing status (UPCOMING, COMPLETED, CANCELLED) using the provided Clock
     * and ordering upcoming ascending, then past descending.
     */
    public List<BookingSummary> listBookingsForUser(long userId, Clock clock) {
        return bookingDao.listSummariesForUser(userId, clock != null ? clock : this.clock);
    }

    /**
     * Retrieves a booking record for a user, enforcing strict ownership.
     * Throws SecurityException if the booking belongs to another user.
     */
    public BookingRecord getBookingForUser(long userId, String bookingReference) {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("Booking reference must not be null or blank");
        }
        String ref = bookingReference.trim().toUpperCase(Locale.ROOT);
        BookingRecord record = bookingDao.findByReference(ref)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + ref));
        if (record.getUserId() == null || record.getUserId() != userId) {
            logger.warn("Security violation: user {} attempted to access booking {} owned by user {}",
                    userId, ref, record.getUserId());
            throw new SecurityException("User " + userId + " is not authorized to access booking " + ref);
        }
        return record;
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

    public PricingService getPricingService() {
        return pricingService;
    }
}
