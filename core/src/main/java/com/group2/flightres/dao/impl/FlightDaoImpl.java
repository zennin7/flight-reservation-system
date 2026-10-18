package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FlightDaoImpl implements FlightDao {

    private static final Logger logger = LoggerFactory.getLogger(FlightDaoImpl.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String SEARCH_FLIGHTS_QUERY = """
        SELECT f.flight_id, f.flight_number,
               origin_ap.iata_code AS origin_code, origin_ap.city AS origin_city,
               dest_ap.iata_code AS dest_code, dest_ap.city AS dest_city,
               f.departure_time, f.arrival_time, f.base_price, f.status
        FROM flights f
        JOIN airports origin_ap ON origin_ap.airport_id = f.origin_airport_id
        JOIN airports dest_ap ON dest_ap.airport_id = f.dest_airport_id
        WHERE (origin_ap.iata_code = ? OR UPPER(origin_ap.city) LIKE UPPER(?) OR UPPER(origin_ap.name) LIKE UPPER(?))
          AND (dest_ap.iata_code = ? OR UPPER(dest_ap.city) LIKE UPPER(?) OR UPPER(dest_ap.name) LIKE UPPER(?))
          AND (?::date IS NULL OR CAST(f.departure_time AS DATE) = ?)
          AND f.status = 'SCHEDULED'
        ORDER BY f.departure_time ASC
        """;

    private static final String FIND_BY_ID_QUERY = """
        SELECT f.flight_id, f.flight_number,
               origin_ap.iata_code AS origin_code, origin_ap.city AS origin_city,
               dest_ap.iata_code AS dest_code, dest_ap.city AS dest_city,
               f.departure_time, f.arrival_time, f.base_price, f.status
        FROM flights f
        JOIN airports origin_ap ON origin_ap.airport_id = f.origin_airport_id
        JOIN airports dest_ap ON dest_ap.airport_id = f.dest_airport_id
        WHERE f.flight_id = ?
        """;

    @Override
    public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
        String normOrigin = ValidationUtil.extractAirportCodeOrCity(origin);
        String normDest = ValidationUtil.extractAirportCodeOrCity(destination);

        logger.debug("Executing flight search: origin='{}', dest='{}', date={}", normOrigin, normDest, departureDate);
        List<FlightSearchResult> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SEARCH_FLIGHTS_QUERY)) {

            stmt.setString(1, normOrigin.toUpperCase(Locale.ROOT));
            stmt.setString(2, "%" + normOrigin + "%");
            stmt.setString(3, "%" + normOrigin + "%");

            stmt.setString(4, normDest.toUpperCase(Locale.ROOT));
            stmt.setString(5, "%" + normDest + "%");
            stmt.setString(6, "%" + normDest + "%");

            if (departureDate != null) {
                Date sqlDate = Date.valueOf(departureDate);
                stmt.setDate(7, sqlDate);
                stmt.setDate(8, sqlDate);
            } else {
                stmt.setNull(7, java.sql.Types.DATE);
                stmt.setNull(8, java.sql.Types.DATE);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query flights from database: {}", e.getMessage(), e);
            return Collections.emptyList();
        }

        logger.info("Found {} flights matching route {} -> {} on {}", results.size(), normOrigin, normDest, departureDate);
        return results;
    }

    @Override
    public Optional<FlightSearchResult> findById(long flightId) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_QUERY)) {

            stmt.setLong(1, flightId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find flight by ID {}: {}", flightId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    private FlightSearchResult mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("flight_id");
        String number = rs.getString("flight_number");
        Timestamp depTs = rs.getTimestamp("departure_time");
        Timestamp arrTs = rs.getTimestamp("arrival_time");
        BigDecimal basePrice = rs.getBigDecimal("base_price");

        LocalDateTime depLdt = depTs.toLocalDateTime();
        LocalDateTime arrLdt = arrTs.toLocalDateTime();

        String depTimeStr = depLdt.toLocalTime().format(TIME_FORMAT);
        String arrTimeStr = arrLdt.toLocalTime().format(TIME_FORMAT);

        Duration duration = Duration.between(depLdt, arrLdt);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        String durationStr = hours + "h " + minutes + "m";

        String priceFormatted = String.format(Locale.US, "R %,d", basePrice.longValue());

        return new FlightSearchResult(id, number, depTimeStr, arrTimeStr, durationStr, basePrice, priceFormatted);
    }
}
