package com.group2.flightres.service;

import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service providing flight search and lookup capabilities.
 * Shared by desktop and web applications.
 */
public class FlightService {

    private static final Logger logger = LoggerFactory.getLogger(FlightService.class);

    private final FlightDao flightDao;

    public FlightService() {
        this(new FlightDaoImpl());
    }

    public FlightService(FlightDao flightDao) {
        this.flightDao = flightDao;
    }

    /**
     * Searches for flights matching route and departure date.
     * Validates input parameters and logs results with SLF4J.
     *
     * @param origin departure airport or city
     * @param destination arrival airport or city
     * @param departureDate departure date
     * @return list of matching flights, or empty list if no results
     */
    public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
        ValidationUtil.validateFlightSearch(origin, destination, departureDate);
        logger.info("Searching flights for route {} -> {} on date {}", origin, destination, departureDate);

        List<FlightSearchResult> results = flightDao.searchFlights(origin, destination, departureDate);
        if (results.isEmpty()) {
            logger.info("No flights found for {} -> {} on {}", origin, destination, departureDate);
        } else {
            logger.info("Retrieved {} available flights", results.size());
        }
        return results;
    }

    /**
     * Finds a single flight by its ID.
     */
    public Optional<FlightSearchResult> getFlightById(long flightId) {
        return flightDao.findById(flightId);
    }
}
