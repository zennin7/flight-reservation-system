package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.config.PricingPolicy;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service calculating dynamic price quotes based on real database base fares,
 * live seat class pricing multipliers, flight occupancy, and departure timing.
 */
public class PricingService {

    private static final Logger logger = LoggerFactory.getLogger(PricingService.class);

    private final SeatDao seatDao;
    private final Clock defaultClock;

    private static final String SINGLE_FLIGHT_OCCUPANCY_QUERY = """
        SELECT f.flight_id,
               f.base_price,
               f.departure_time,
               count(fs.flight_seat_id) AS total_seats,
               count(fs.flight_seat_id) FILTER (WHERE fs.status = 'BOOKED' OR (fs.status = 'HELD' AND fs.held_until >= ?)) AS occupied_seats
        FROM flights f
        LEFT JOIN flight_seats fs ON fs.flight_id = f.flight_id
        WHERE f.flight_id = ?
        GROUP BY f.flight_id, f.base_price, f.departure_time
        """;

    private static final String BATCH_FLIGHT_OCCUPANCY_QUERY = """
        SELECT f.flight_id,
               f.base_price,
               f.departure_time,
               count(fs.flight_seat_id) AS total_seats,
               count(fs.flight_seat_id) FILTER (WHERE fs.status = 'BOOKED' OR (fs.status = 'HELD' AND fs.held_until >= ?)) AS occupied_seats
        FROM flights f
        LEFT JOIN flight_seats fs ON fs.flight_id = f.flight_id
        WHERE f.flight_id = ANY (?)
        GROUP BY f.flight_id, f.base_price, f.departure_time
        """;

    public PricingService() {
        this(new SeatDaoImpl(), Clock.systemUTC());
    }

    public PricingService(Clock clock) {
        this(new SeatDaoImpl(), clock != null ? clock : Clock.systemUTC());
    }

    public PricingService(SeatDao seatDao) {
        this(seatDao, Clock.systemUTC());
    }

    public PricingService(SeatDao seatDao, Clock clock) {
        this.seatDao = Objects.requireNonNull(seatDao, "seatDao cannot be null");
        this.defaultClock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /**
     * Calculates a price quote for a single flight and seat class using the default clock.
     */
    public PriceQuote quote(long flightId, SeatClass seatClass) {
        return quote(flightId, seatClass, defaultClock);
    }

    /**
     * Calculates a price quote for a single flight and seat class using the provided clock.
     * Computes occupancy = (BOOKED + currently HELD) / total seats in ONE query.
     */
    public PriceQuote quote(long flightId, SeatClass seatClass, Clock clock) {
        Objects.requireNonNull(seatClass, "seatClass cannot be null");
        Clock effectiveClock = clock != null ? clock : defaultClock;

        Map<SeatClass, BigDecimal> classMultipliers = seatDao.getSeatClassMultipliers();
        BigDecimal classMultiplier = classMultipliers.getOrDefault(seatClass, BigDecimal.ONE);

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SINGLE_FLIGHT_OCCUPANCY_QUERY)) {

            stmt.setTimestamp(1, Timestamp.from(effectiveClock.instant()));
            stmt.setLong(2, flightId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal basePrice = rs.getBigDecimal("base_price");
                    Timestamp depTs = rs.getTimestamp("departure_time");
                    if (depTs == null) {
                        throw new IllegalStateException("Flight " + flightId + " has no departure time.");
                    }
                    Instant departureTime = depTs.toInstant();
                    if (!departureTime.isAfter(effectiveClock.instant())) {
                        throw new IllegalStateException("Flight " + flightId + " has already departed at "
                                + departureTime + " (current time: " + effectiveClock.instant() + ")");
                    }
                    long totalSeats = rs.getLong("total_seats");
                    long occupiedSeats = rs.getLong("occupied_seats");

                    double occupancy = totalSeats > 0 ? (double) occupiedSeats / totalSeats : 0.0;
                    BigDecimal demandMultiplier = PricingPolicy.getDemandMultiplier(occupancy);
                    BigDecimal timingMultiplier = PricingPolicy.getTimingMultiplier(effectiveClock.instant(), departureTime);

                    return PriceQuote.calculate(basePrice, classMultiplier, demandMultiplier, timingMultiplier);
                } else {
                    throw new IllegalArgumentException("Flight not found with ID: " + flightId);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query price quote for flight {}: {}", flightId, e.getMessage(), e);
            throw new RuntimeException("Failed to calculate price quote for flight " + flightId, e);
        }
    }

    /**
     * Batch calculation of price quotes for multiple flights using the default clock.
     */
    public Map<Long, PriceQuote> quoteBatch(List<Long> flightIds, SeatClass seatClass) {
        return quoteBatch(flightIds, seatClass, defaultClock);
    }

    /**
     * Batch calculation of price quotes for multiple flights in ONE query (no N+1).
     */
    public Map<Long, PriceQuote> quoteBatch(List<Long> flightIds, SeatClass seatClass, Clock clock) {
        if (flightIds == null || flightIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Objects.requireNonNull(seatClass, "seatClass cannot be null");
        Clock effectiveClock = clock != null ? clock : defaultClock;

        Map<SeatClass, BigDecimal> classMultipliers = seatDao.getSeatClassMultipliers();
        BigDecimal classMultiplier = classMultipliers.getOrDefault(seatClass, BigDecimal.ONE);

        Map<Long, PriceQuote> results = new HashMap<>();
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(BATCH_FLIGHT_OCCUPANCY_QUERY)) {

            stmt.setTimestamp(1, Timestamp.from(effectiveClock.instant()));
            stmt.setArray(2, conn.createArrayOf("bigint", flightIds.toArray(new Long[0])));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long fId = rs.getLong("flight_id");
                    BigDecimal basePrice = rs.getBigDecimal("base_price");
                    Timestamp depTs = rs.getTimestamp("departure_time");
                    if (depTs != null) {
                        Instant departureTime = depTs.toInstant();
                        if (departureTime.isAfter(effectiveClock.instant())) {
                            long totalSeats = rs.getLong("total_seats");
                            long occupiedSeats = rs.getLong("occupied_seats");

                            double occupancy = totalSeats > 0 ? (double) occupiedSeats / totalSeats : 0.0;
                            BigDecimal demandMultiplier = PricingPolicy.getDemandMultiplier(occupancy);
                            BigDecimal timingMultiplier = PricingPolicy.getTimingMultiplier(effectiveClock.instant(), departureTime);

                            PriceQuote quote = PriceQuote.calculate(basePrice, classMultiplier, demandMultiplier, timingMultiplier);
                            results.put(fId, quote);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to execute batch price quote for {} flights: {}", flightIds.size(), e.getMessage(), e);
            throw new RuntimeException("Failed to execute batch price quote", e);
        }

        return results;
    }
}
