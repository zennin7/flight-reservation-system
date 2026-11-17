// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.model;

import com.group2.flightres.util.FlightTimes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated analytics snapshot for the Admin Dashboard, backed strictly by SQL queries.
 */
public class AdminDashboardData {

    public record DailyBookingsPoint(LocalDate date, String dayLabel, long bookingsCount) {}

    public record RouteRevenuePoint(String route, BigDecimal revenue, long bookingsCount) {}

    public record FlightLoadFactor(
            long flightId,
            String flightNumber,
            String route,
            Instant departureInstant,
            ZoneId originZone,
            int bookedSeats,
            int totalSeats,
            int availableSeats,
            double loadFactorPct
    ) {
        public String getFormattedDepartureTime() {
            return FlightTimes.formatLocalTime(departureInstant, originZone);
        }

        public String getFormattedDepartureDate() {
            return FlightTimes.formatLocalDate(departureInstant, originZone);
        }
    }

    private final BigDecimal totalRevenue;
    private final long bookingsCount;
    private final long ticketsSold;
    private final List<DailyBookingsPoint> bookingsPerDay;
    private final List<RouteRevenuePoint> revenuePerRoute;
    private final List<FlightLoadFactor> upcomingFlights;
    private final List<FlightLoadFactor> lowSeatAlerts;

    public AdminDashboardData(
            BigDecimal totalRevenue,
            long bookingsCount,
            long ticketsSold,
            List<DailyBookingsPoint> bookingsPerDay,
            List<RouteRevenuePoint> revenuePerRoute,
            List<FlightLoadFactor> upcomingFlights,
            List<FlightLoadFactor> lowSeatAlerts
    ) {
        this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        this.bookingsCount = bookingsCount;
        this.ticketsSold = ticketsSold;
        this.bookingsPerDay = bookingsPerDay != null ? Collections.unmodifiableList(bookingsPerDay) : List.of();
        this.revenuePerRoute = revenuePerRoute != null ? Collections.unmodifiableList(revenuePerRoute) : List.of();
        this.upcomingFlights = upcomingFlights != null ? Collections.unmodifiableList(upcomingFlights) : List.of();
        this.lowSeatAlerts = lowSeatAlerts != null ? Collections.unmodifiableList(lowSeatAlerts) : List.of();
    }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public long getBookingsCount() { return bookingsCount; }
    public long getTicketsSold() { return ticketsSold; }
    public List<DailyBookingsPoint> getBookingsPerDay() { return bookingsPerDay; }
    public List<RouteRevenuePoint> getRevenuePerRoute() { return revenuePerRoute; }
    public List<FlightLoadFactor> getUpcomingFlights() { return upcomingFlights; }
    public List<FlightLoadFactor> getLowSeatAlerts() { return lowSeatAlerts; }

    public boolean isEmpty() {
        return bookingsCount == 0
                && ticketsSold == 0
                && Objects.equals(BigDecimal.ZERO.compareTo(totalRevenue), 0)
                && upcomingFlights.isEmpty();
    }
}
