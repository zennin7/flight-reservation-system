PreparedStatement stmt = conn.prepareStatement(sql);

stmt.setString(1, "SA123");
 

package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.model.Flight;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FlightDaoImpl implements FlightDao {

    private static final String SEARCH_QUERY = """
        SELECT
            f.flight_id,
            f.flight_number,
            f.departure_time,
            f.arrival_time,
            f.base_price

        FROM flights f

        JOIN airports o
            ON f.origin_airport_id = o.airport_id

        JOIN airports d
            ON f.dest_airport_id = d.airport_id

        WHERE o.iata_code = ?
          AND d.iata_code = ?
          AND f.departure_time >= ?
          AND f.departure_time < ?

        ORDER BY f.departure_time ASC
        """;

    @Override
    public List<Flight> searchFlights(
            String originIata,
            String destinationIata,
            LocalDate departureDate) {

        List<Flight> flights = new ArrayList<>();

        LocalDate nextDay = departureDate.plusDays(1);

        try (
            Connection conn = DatabaseConfig
                    .getDataSource()
                    .getConnection();

            PreparedStatement stmt =
                    conn.prepareStatement(SEARCH_QUERY)
        ) {

            // Set the SQL parameters

            stmt.setString(1, originIata);
            stmt.setString(2, destinationIata);

            stmt.setTimestamp(
                    3,
                    Timestamp.valueOf(
                            departureDate.atStartOfDay()
                    )
            );

            stmt.setTimestamp(
                    4,
                    Timestamp.valueOf(
                            nextDay.atStartOfDay()
                    )
            );

            // Execute the query

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    Flight flight = new Flight();

                    flight.setFlightId(
                            rs.getLong("flight_id")
                    );

                    flight.setFlightNumber(
                            rs.getString("flight_number")
                    );

                    flight.setDepartureTime(
                            rs.getTimestamp("departure_time")
                                    .toLocalDateTime()
                    );

                    flight.setArrivalTime(
                            rs.getTimestamp("arrival_time")
                                    .toLocalDateTime()
                    );

                    flight.setBasePrice(
                            rs.getBigDecimal("base_price")
                    );

                    flights.add(flight);
                }
            }

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to search for flights",
                    e
            );
        }

        return flights;
    }
}
List<Flight> flights = new ArrayList<>();


Connection conn = DatabaseConfig
        .getDataSource()
        .getConnection();

stmt.setString(1, originIata);
stmt.setString(2, destinationIata);

originIata = "JHB";
destinationIata = "CPT";

stmt.setTimestamp(3, Timestamp.valueOf(
        departureDate.atStartOfDay()
));

stmt.setTimestamp(4, Timestamp.valueOf(
        nextDay.atStartOfDay()
));

ResultSet rs = stmt.executeQuery();

while (rs.next()) {

    Flight flight = new Flight();

    flight.setFlightNumber(
    rs.getString("flight_number")
);
flights.add(flight);