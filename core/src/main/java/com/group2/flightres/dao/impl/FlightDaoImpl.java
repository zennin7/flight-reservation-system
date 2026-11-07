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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FlightDaoImpl implements FlightDao {

    private static final Logger logger = LoggerFactory.getLogger(FlightDaoImpl.class);

    private static final String SEARCH_FLIGHTS_QUERY = """
        SELECT f.flight_id, f.flight_number,
               orig.iata_code AS origin_code, orig.name AS origin_name, orig.city AS origin_city, orig.timezone AS origin_timezone,
               dest.iata_code AS dest_code, dest.name AS dest_name, dest.city AS dest_city, dest.timezone AS dest_timezone,
               f.departure_time, f.arrival_time, f.base_price, f.status,
               act.model AS aircraft_model,
               (SELECT COUNT(*) FROM flight_seats fs WHERE fs.flight_id = f.flight_id AND (fs.status = 'AVAILABLE' OR (fs.status = 'HELD' AND fs.held_until < NOW()))) AS available_seats
        FROM flights f
        JOIN airports orig ON orig.airport_id = f.origin_airport_id
        JOIN airports dest ON dest.airport_id = f.dest_airport_id
        JOIN aircraft_types act ON act.aircraft_type_id = f.aircraft_type_id
        WHERE (orig.iata_code = ? OR UPPER(orig.city) LIKE UPPER(?) OR UPPER(orig.name) LIKE UPPER(?))
          AND (dest.iata_code = ? OR UPPER(dest.city) LIKE UPPER(?) OR UPPER(dest.name) LIKE UPPER(?))
          AND (?::date IS NULL OR (
              f.departure_time >= (?::date)::timestamp AT TIME ZONE orig.timezone
              AND f.departure_time < ((?::date) + 1)::timestamp AT TIME ZONE orig.timezone
          ))
          AND f.status = 'SCHEDULED'
        ORDER BY f.departure_time ASC
        """;

    private static final String FIND_BY_ID_QUERY = """
        SELECT f.flight_id, f.flight_number,
               orig.iata_code AS origin_code, orig.name AS origin_name, orig.city AS origin_city, orig.timezone AS origin_timezone,
               dest.iata_code AS dest_code, dest.name AS dest_name, dest.city AS dest_city, dest.timezone AS dest_timezone,
               f.departure_time, f.arrival_time, f.base_price, f.status,
               act.model AS aircraft_model,
               (SELECT COUNT(*) FROM flight_seats fs WHERE fs.flight_id = f.flight_id AND (fs.status = 'AVAILABLE' OR (fs.status = 'HELD' AND fs.held_until < NOW()))) AS available_seats
        FROM flights f
        JOIN airports orig ON orig.airport_id = f.origin_airport_id
        JOIN airports dest ON dest.airport_id = f.dest_airport_id
        JOIN aircraft_types act ON act.aircraft_type_id = f.aircraft_type_id
        WHERE f.flight_id = ?
        """;

    private static final String FIND_NEXT_AVAILABLE_DATE_QUERY = """
        SELECT f.departure_time, orig.timezone
        FROM flights f
        JOIN airports orig ON orig.airport_id = f.origin_airport_id
        JOIN airports dest ON dest.airport_id = f.dest_airport_id
        WHERE (orig.iata_code = ? OR UPPER(orig.city) LIKE UPPER(?) OR UPPER(orig.name) LIKE UPPER(?))
          AND (dest.iata_code = ? OR UPPER(dest.city) LIKE UPPER(?) OR UPPER(dest.name) LIKE UPPER(?))
          AND f.status = 'SCHEDULED'
          AND f.departure_time >= ?
        ORDER BY f.departure_time ASC
        LIMIT 1
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
                stmt.setDate(9, sqlDate);
            } else {
                stmt.setNull(7, java.sql.Types.DATE);
                stmt.setNull(8, java.sql.Types.DATE);
                stmt.setNull(9, java.sql.Types.DATE);
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

    @Override
    public Optional<LocalDate> findNextAvailableDate(String origin, String destination, LocalDate fromDate, Instant minDepartureInstant) {
        String normOrigin = ValidationUtil.extractAirportCodeOrCity(origin);
        String normDest = ValidationUtil.extractAirportCodeOrCity(destination);

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_NEXT_AVAILABLE_DATE_QUERY)) {

            stmt.setString(1, normOrigin.toUpperCase(Locale.ROOT));
            stmt.setString(2, "%" + normOrigin + "%");
            stmt.setString(3, "%" + normOrigin + "%");

            stmt.setString(4, normDest.toUpperCase(Locale.ROOT));
            stmt.setString(5, "%" + normDest + "%");
            stmt.setString(6, "%" + normDest + "%");

            Instant threshold = minDepartureInstant;
            if (fromDate != null) {
                Instant fromInstant = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                if (threshold == null || fromInstant.isAfter(threshold)) {
                    threshold = fromInstant;
                }
            }
            if (threshold == null) {
                threshold = Instant.now();
            }

            stmt.setTimestamp(7, Timestamp.from(threshold));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    OffsetDateTime odt = rs.getObject("departure_time", OffsetDateTime.class);
                    String tz = rs.getString("timezone");
                    ZoneId zone = ZoneId.of(tz != null ? tz : "Africa/Johannesburg");
                    return Optional.of(odt.atZoneSameInstant(zone).toLocalDate());
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query next available flight date: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    private FlightSearchResult mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("flight_id");
        String number = rs.getString("flight_number");

        String originCode = rs.getString("origin_code");
        String originName = rs.getString("origin_name");
        String originCity = rs.getString("origin_city");
        String originTz = rs.getString("origin_timezone");
        ZoneId originZone = ZoneId.of(originTz != null ? originTz : "Africa/Johannesburg");

        String destCode = rs.getString("dest_code");
        String destName = rs.getString("dest_name");
        String destCity = rs.getString("dest_city");
        String destTz = rs.getString("dest_timezone");
        ZoneId destZone = ZoneId.of(destTz != null ? destTz : "Africa/Johannesburg");

        OffsetDateTime depOdt = rs.getObject("departure_time", OffsetDateTime.class);
        OffsetDateTime arrOdt = rs.getObject("arrival_time", OffsetDateTime.class);
        Instant depInstant = depOdt != null ? depOdt.toInstant() : Instant.EPOCH;
        Instant arrInstant = arrOdt != null ? arrOdt.toInstant() : Instant.EPOCH;

        BigDecimal basePrice = rs.getBigDecimal("base_price");
        String aircraftModel = rs.getString("aircraft_model");
        int availableSeats = rs.getInt("available_seats");

        return new FlightSearchResult(
                id,
                number,
                originCode,
                originName,
                originCity,
                originZone,
                destCode,
                destName,
                destCity,
                destZone,
                depInstant,
                arrInstant,
                basePrice,
                aircraftModel,
                availableSeats
        );
    }

    private static final String FIND_AIRPORT_NAME_QUERY =
            "SELECT name FROM airports WHERE UPPER(iata_code) = UPPER(?) OR UPPER(city) = UPPER(?) LIMIT 1";

    private static final String FIND_AIRPORT_CITY_QUERY =
            "SELECT city FROM airports WHERE UPPER(iata_code) = UPPER(?) OR UPPER(city) = UPPER(?) LIMIT 1";

    @Override
    public Optional<String> findAirportName(String code, String city) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_AIRPORT_NAME_QUERY)) {
            ps.setString(1, code != null ? code.trim() : "");
            ps.setString(2, city != null ? city.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.isBlank()) {
                        return Optional.of(name.trim());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding airport name for code='{}', city='{}'", code, city, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> findAirportCity(String code) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_AIRPORT_CITY_QUERY)) {
            ps.setString(1, code != null ? code.trim() : "");
            ps.setString(2, code != null ? code.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String city = rs.getString(1);
                    if (city != null && !city.isBlank()) {
                        return Optional.of(city.trim());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding airport city for code='{}'", code, e);
        }
        return Optional.empty();
    }
}
