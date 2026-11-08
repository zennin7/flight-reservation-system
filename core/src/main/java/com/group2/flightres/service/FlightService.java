package com.group2.flightres.service;

import com.group2.flightres.dao.AirportDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.AirportDaoImpl;
import com.group2.flightres.dao.impl.FlightDaoImpl;
import com.group2.flightres.model.Airport;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.Route;
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
    private final AirportDao airportDao;
    private final Clock clock;

    public FlightService() {
        this(new FlightDaoImpl(), new AirportDaoImpl(), Clock.systemUTC());
    }

    public FlightService(FlightDao flightDao) {
        this(flightDao, new AirportDaoImpl(), Clock.systemUTC());
    }

    public FlightService(Clock clock) {
        this(new FlightDaoImpl(), new AirportDaoImpl(), clock);
    }

    public FlightService(FlightDao flightDao, Clock clock) {
        this(flightDao, new AirportDaoImpl(), clock);
    }

    public FlightService(FlightDao flightDao, AirportDao airportDao) {
        this(flightDao, airportDao, Clock.systemUTC());
    }

    public FlightService(FlightDao flightDao, AirportDao airportDao, Clock clock) {
        this.flightDao = flightDao;
        this.airportDao = airportDao != null ? airportDao : new AirportDaoImpl();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Lists all operational airports ordered by city ascending.
     */
    public List<Airport> listAirports() {
        return airportDao.findAll();
    }

    /**
     * Resolves the default route:
     * 1) Route of the earliest open scheduled flight (enforcing sales cutoff)
     * 2) If none open, the first two airports by city
     * 3) If fewer than two airports exist, empty
     */
    public Optional<Route> defaultRoute() {
        Instant minDeparture = clock.instant().plus(FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE);
        Optional<Route> openRoute = flightDao.findEarliestOpenFlightRoute(minDeparture);
        if (openRoute.isPresent()) {
            return openRoute;
        }
        List<Airport> airports = airportDao.findAll();
        if (airports.size() >= 2) {
            return Optional.of(new Route(airports.get(0), airports.get(1)));
        }
        return Optional.empty();
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

    /**
     * Queries the next available date with open scheduled flights on the given route.
     * Uses current date in the origin airport's timezone as the search start date.
     */
    public Optional<LocalDate> nextAvailableDate(Route route) {
        if (route == null || route.origin() == null || route.destination() == null) {
            return Optional.empty();
        }
        LocalDate fromDate = LocalDate.now(route.origin().zoneId());
        return nextAvailableDate(route.origin().code(), route.destination().code(), fromDate);
    }
}
