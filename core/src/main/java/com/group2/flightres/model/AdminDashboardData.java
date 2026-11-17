// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.model;

import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.util.AirportNames;
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

    public enum Range {
        TODAY("Today", 1),
        SEVEN_DAYS("7 days", 7),
        THIRTY_DAYS("30 days", 30);

        private final String label;
        private final int days;

        Range(String label, int days) {
            this.label = label;
            this.days = days;
        }

        public String getLabel() { return label; }
        public int getDays() { return days; }
    }

    public record DailySeriesPoint(
            LocalDate date,
            String dayLabel,
            long bookingsCount,
            BigDecimal revenue,
            long seatsSold,
            long cancellationsCount
    ) {}

    public record CabinLoadBreakdown(
            long economyBooked,
            long firstBooked,
            long openSeats,
            long totalSeats,
            long seatsHeldRightNow,
            long flightsBelowLowFreePct
    ) {
        public double soldPercent() {
            if (totalSeats <= 0) return 0.0;
            return ((economyBooked + firstBooked) * 100.0) / totalSeats;
        }

        public double economyPercent() {
            if (totalSeats <= 0) return 0.0;
            return (economyBooked * 100.0) / totalSeats;
        }

        public double firstPercent() {
            if (totalSeats <= 0) return 0.0;
            return (firstBooked * 100.0) / totalSeats;
        }

        public double openPercent() {
            if (totalSeats <= 0) return 0.0;
            return Math.max(0.0, 100.0 - economyPercent() - firstPercent());
        }
    }

    public record FlightLoadFactor(
            long flightId,
            String flightNumber,
            String origin,
            String destination,
            Instant departureInstant,
            ZoneId originZone,
            int bookedSeats,
            int totalSeats,
            int availableSeats,
            double loadFactorPct,
            String status
    ) {
        public String getRoute() {
            return origin + " -> " + destination;
        }

        public String getFormattedDepartureTime() {
            return FlightTimes.formatLocalTime(departureInstant, originZone);
        }

        public String getFormattedDepartureDate() {
            return FlightTimes.formatLocalDate(departureInstant, originZone);
        }

        public static String deriveStatus(Instant departureInstant, Instant now) {
            if (departureInstant == null || now == null) return "Scheduled";
            if (!now.isBefore(departureInstant)) {
                return "Departed";
            }
            if (!now.isBefore(departureInstant.minusSeconds(BoardingPolicy.BOARDING_MINUTES_BEFORE * 60L))) {
                return "Boarding";
            }
            return "Scheduled";
        }
    }

    public record TopDestinationItem(
            String airportCode,
            String cityName,
            long bookingsCount
    ) {
        public static String resolveCityName(String code) {
            if (code == null) return "";
            return switch (code.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "CPT" -> "Cape Town";
                case "JNB" -> "Johannesburg";
                case "DUR" -> "Durban";
                case "PLZ" -> "Gqeberha";
                case "GRJ" -> "George";
                case "BFN" -> "Bloemfontein";
                case "ELS" -> "East London";
                default -> AirportNames.shortName(code);
            };
        }
    }

    public record RecentBookingItem(
            String bookingReference,
            String passengerName,
            String route,
            String seatLabels,
            BigDecimal totalAmount
    ) {}

    private final Range range;
    private final long totalBookings;
    private final Double bookingsDeltaPct;
    private final BigDecimal totalRevenue;
    private final Double revenueDeltaPct;
    private final long seatsSold;
    private final Double seatsSoldDeltaPct;
    private final long cancellationsCount;
    private final Double cancellationsDeltaPct;
    private final List<DailySeriesPoint> dailySeries;
    private final CabinLoadBreakdown cabinLoad;
    private final List<FlightLoadFactor> todayFlights;
    private final List<FlightLoadFactor> upcomingFlights;
    private final List<TopDestinationItem> topDestinations;
    private final List<RecentBookingItem> recentBookings;

    public AdminDashboardData(
            Range range,
            long totalBookings,
            Double bookingsDeltaPct,
            BigDecimal totalRevenue,
            Double revenueDeltaPct,
            long seatsSold,
            Double seatsSoldDeltaPct,
            long cancellationsCount,
            Double cancellationsDeltaPct,
            List<DailySeriesPoint> dailySeries,
            CabinLoadBreakdown cabinLoad,
            List<FlightLoadFactor> todayFlights,
            List<FlightLoadFactor> upcomingFlights,
            List<TopDestinationItem> topDestinations,
            List<RecentBookingItem> recentBookings
    ) {
        this.range = range != null ? range : Range.SEVEN_DAYS;
        this.totalBookings = totalBookings;
        this.bookingsDeltaPct = bookingsDeltaPct;
        this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        this.revenueDeltaPct = revenueDeltaPct;
        this.seatsSold = seatsSold;
        this.seatsSoldDeltaPct = seatsSoldDeltaPct;
        this.cancellationsCount = cancellationsCount;
        this.cancellationsDeltaPct = cancellationsDeltaPct;
        this.dailySeries = dailySeries != null ? Collections.unmodifiableList(dailySeries) : List.of();
        this.cabinLoad = cabinLoad != null ? cabinLoad : new CabinLoadBreakdown(0, 0, 0, 0, 0, 0);
        this.todayFlights = todayFlights != null ? Collections.unmodifiableList(todayFlights) : List.of();
        this.upcomingFlights = upcomingFlights != null ? Collections.unmodifiableList(upcomingFlights) : List.of();
        this.topDestinations = topDestinations != null ? Collections.unmodifiableList(topDestinations) : List.of();
        this.recentBookings = recentBookings != null ? Collections.unmodifiableList(recentBookings) : List.of();
    }

    public Range getRange() { return range; }
    public long getTotalBookings() { return totalBookings; }
    public Double getBookingsDeltaPct() { return bookingsDeltaPct; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public Double getRevenueDeltaPct() { return revenueDeltaPct; }
    public long getSeatsSold() { return seatsSold; }
    public Double getSeatsSoldDeltaPct() { return seatsSoldDeltaPct; }
    public long getCancellationsCount() { return cancellationsCount; }
    public Double getCancellationsDeltaPct() { return cancellationsDeltaPct; }
    public List<DailySeriesPoint> getDailySeries() { return dailySeries; }
    public CabinLoadBreakdown getCabinLoad() { return cabinLoad; }
    public List<FlightLoadFactor> getTodayFlights() { return todayFlights; }
    public List<FlightLoadFactor> getUpcomingFlights() { return upcomingFlights; }
    public List<TopDestinationItem> getTopDestinations() { return topDestinations; }
    public List<RecentBookingItem> getRecentBookings() { return recentBookings; }

    public boolean isEmpty() {
        return totalBookings == 0
                && cancellationsCount == 0
                && seatsSold == 0
                && Objects.equals(BigDecimal.ZERO.compareTo(totalRevenue), 0)
                && todayFlights.isEmpty()
                && upcomingFlights.isEmpty();
    }
}
