package com.group2.flightres.dao;

import com.group2.flightres.model.Airport;

import java.util.List;

/**
 * Data Access Object for airport queries.
 */
public interface AirportDao {

    /**
     * Retrieves all operational airports ordered by city name ascending.
     *
     * @return list of airports
     */
    List<Airport> findAll();
}
