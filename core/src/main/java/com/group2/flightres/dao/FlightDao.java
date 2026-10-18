package com.group2.flightres.dao;

import com.group2.flightres.model.FlightSearchResult;

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
}
