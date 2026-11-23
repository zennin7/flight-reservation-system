package com.group2.flightres.util;

import com.group2.flightres.config.AppInfo;
import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.FlightSalesPolicy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-scope helper finding the earliest open Sky Royal (SR) flight with all seats AVAILABLE
 * in the next 14 days (SA time Africa/Johannesburg).
 */
public final class TestFlightFinder {

    public record OpenAltFlight(
            long flightId,
            String flightNumber,
            String originAirportName,
            String originCode,
            String originCity,
            String destAirportName,
            String destCode,
            String destCity,
            Instant departureInstant,
            Instant arrivalInstant,
            String departureDate,
            String departureTime,
            String arrivalTime,
            String duration,
            BigDecimal basePrice,
            String aircraftModel,
            List<FlightSeat> availableSeats
    ) {}

    private TestFlightFinder() {}

    public static OpenAltFlight findEarliestOpenAltFlight() {
        return findEarliestOpenAltFlight(Clock.systemUTC());
    }

    public static OpenAltFlight findEarliestOpenAltFlight(Clock clock) {
        Clock refClock = clock != null ? clock : Clock.systemUTC();
        Duration minMargin = FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE.plusMinutes(15);
        Instant minDepartureTime = refClock.instant().plus(minMargin);

        String query = """
            SELECT f.flight_id, f.flight_number, f.departure_time, f.arrival_time, f.base_price,
                   orig.name AS origin_name, orig.iata_code AS origin_code, orig.city AS origin_city, orig.timezone AS origin_tz,
                   dest.name AS dest_name, dest.iata_code AS dest_code, dest.city AS dest_city, dest.timezone AS dest_tz
            FROM flights f
            JOIN airports orig ON orig.airport_id = f.origin_airport_id
            JOIN airports dest ON dest.airport_id = f.dest_airport_id
            JOIN flight_seats fs ON fs.flight_id = f.flight_id
            WHERE f.flight_number LIKE ?
              AND f.status = 'SCHEDULED'
              AND f.departure_time >= ?
              AND f.departure_time <= NOW() + INTERVAL '14 days'
            GROUP BY f.flight_id, f.flight_number, f.departure_time, f.arrival_time, f.base_price,
                     orig.name, orig.iata_code, orig.city, orig.timezone,
                     dest.name, dest.iata_code, dest.city, dest.timezone
            HAVING count(fs.flight_seat_id) FILTER (WHERE fs.status != 'AVAILABLE') = 0
            ORDER BY f.departure_time ASC
            LIMIT 1;
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, AppInfo.FLIGHT_PREFIX + " %");
            stmt.setTimestamp(2, Timestamp.from(minDepartureTime));
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No open " + AppInfo.FLIGHT_PREFIX + " flight found with 100% available seats in next 14 days.");
                }

            long flightId = rs.getLong("flight_id");
            String flightNumber = rs.getString("flight_number");
            Timestamp depTs = rs.getTimestamp("departure_time");
            Timestamp arrTs = rs.getTimestamp("arrival_time");
            Instant depInstant = depTs != null ? depTs.toInstant() : Instant.now();
            Instant arrInstant = arrTs != null ? arrTs.toInstant() : depInstant.plusSeconds(7200);

            BigDecimal basePrice = rs.getBigDecimal("base_price");
            String originName = rs.getString("origin_name");
            String originCode = rs.getString("origin_code");
            String originCity = rs.getString("origin_city");
            String destName = rs.getString("dest_name");
            String destCode = rs.getString("dest_code");
            String destCity = rs.getString("dest_city");

            ZoneId saZone = ZoneId.of("Africa/Johannesburg");
            String depDate = FlightDateFormatter.format(LocalDate.ofInstant(depInstant, saZone));
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(saZone);
            String depTime = timeFmt.format(depInstant);
            String arrTime = timeFmt.format(arrInstant);
            String duration = FlightTimes.formatDuration(depInstant, arrInstant);

            List<FlightSeat> seats = loadSeatsForFlight(conn, flightId);

                return new OpenAltFlight(
                        flightId, flightNumber, originName, originCode, originCity,
                        destName, destCode, destCity, depInstant, arrInstant,
                        depDate, depTime, arrTime, duration, basePrice, "Airbus A320", seats
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error looking up open " + AppInfo.FLIGHT_PREFIX + " flight", e);
        }
    }

    private static List<FlightSeat> loadSeatsForFlight(Connection conn, long flightId) throws SQLException {
        String sql = """
            SELECT fs.flight_seat_id, fs.flight_id, fs.status, fs.held_until,
                   st.seat_row, st.seat_col, st.seat_class
            FROM flight_seats fs
            JOIN seat_templates st ON fs.seat_template_id = st.seat_template_id
            WHERE fs.flight_id = ?
            ORDER BY st.seat_row, st.seat_col
            """;
        List<FlightSeat> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, flightId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FlightSeat seat = new FlightSeat();
                    seat.setFlightSeatId(rs.getLong("flight_seat_id"));
                    seat.setFlightId(rs.getLong("flight_id"));
                    seat.setSeatRow(rs.getInt("seat_row"));
                    seat.setSeatCol(rs.getString("seat_col").charAt(0));
                    seat.setSeatClass(SeatClass.valueOf(rs.getString("seat_class")));
                    seat.setStatus(SeatStatus.valueOf(rs.getString("status")));
                    Timestamp hu = rs.getTimestamp("held_until");
                    if (hu != null) seat.setHeldUntil(hu.toInstant());
                    list.add(seat);
                }
            }
        }
        return list;
    }
}
