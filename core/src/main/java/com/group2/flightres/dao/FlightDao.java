package com.group2.flightres.dao;

import com.group2.flightres.model.FlightSearchResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FlightDao {

    /**
     * Searches for scheduled flights by origin, destination, and departure date.
     * Origin and destination can be IATA codes (e.g. "JNB") or city names (e.g. "Johannesburg").
     *
     * @param origin origin airport IATA code or city name
     * @param destination destination airport IATA code or city name
     * @param departureDate flight departure date
     * @return list of matching flights, or empty list if none found
     */
    List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate);

    /**
     * Looks up a flight by ID.
     */
    Optional<FlightSearchResult> findById(long flightId);

    /**
     * Finds the earliest date on or after fromDate (and after minDepartureInstant)
     * with scheduled flights for the given route.
     *
     * @param origin origin airport IATA code or city name
     * @param destination destination airport IATA code or city name
     * @param fromDate starting search date
     * @param minDepartureInstant earliest allowable departure instant (e.g. sales cutoff)
     * @return optional next available departure date in the origin airport's timezone
     */
    default Optional<LocalDate> findNextAvailableDate(String origin, String destination, LocalDate fromDate, Instant minDepartureInstant) {
        return Optional.empty();
    }

    /**
     * Resolves the official airport name by IATA code or city name.
     */
    default Optional<String> findAirportName(String code, String city) {
        return Optional.empty();
    }

    /**
     * Resolves the airport city name by IATA code.
     */
    default Optional<String> findAirportCity(String code) {
        return Optional.empty();
    }
}
