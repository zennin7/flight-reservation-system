package com.group2.flightres.service;

import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;

    public FlightService() {
        this(new FlightDaoImpl(), Clock.systemUTC());
    }

    public FlightService(FlightDao flightDao) {
        this(flightDao, Clock.systemUTC());
    }

    public FlightService(Clock clock) {
        this(new FlightDaoImpl(), clock);
    }

    public FlightService(FlightDao flightDao, Clock clock) {
        this.flightDao = flightDao;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Searches for flights matching route and departure date.
     * Enforces FlightSalesPolicy: closed or departed flights (<= 60 min before departure) are excluded.
     *
     * @param origin departure airport or city
     * @param destination arrival airport or city
     * @param departureDate departure date
     * @return list of matching open flights, or empty list if no results
     */
    public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
        ValidationUtil.validateFlightSearch(origin, destination, departureDate);
        logger.info("Searching flights for route {} -> {} on date {}", origin, destination, departureDate);

        List<FlightSearchResult> results = flightDao.searchFlights(origin, destination, departureDate);
        List<FlightSearchResult> openFlights = results.stream()
                .filter(f -> FlightSalesPolicy.isOpen(f.getDeparture(), clock))
                .toList();

        if (openFlights.isEmpty()) {
            logger.info("No open flights found for {} -> {} on {}", origin, destination, departureDate);
        } else {
            logger.info("Retrieved {} available open flights", openFlights.size());
        }
        return openFlights;
    }

    /**
     * Finds a single flight by its ID.
     */
    public Optional<FlightSearchResult> getFlightById(long flightId) {
        return flightDao.findById(flightId);
    }

    /**
     * Queries the next available date with open scheduled flights on the given route.
     *
     * @param origin departure airport or city
     * @param destination arrival airport or city
     * @param fromDate search start date
     * @return optional date of the next open scheduled flight
     */
    public Optional<LocalDate> nextAvailableDate(String origin, String destination, LocalDate fromDate) {
        Instant minDeparture = clock.instant().plus(FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE);
        return flightDao.findNextAvailableDate(origin, destination, fromDate, minDeparture);
    }
}
