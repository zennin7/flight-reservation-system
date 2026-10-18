package com.group2.flightres.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain model aggregating overall platform and booking statistics for the Admin Dashboard.
 */
public class DashboardStats {

    private long totalBookings;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private long totalFlights;
    private double seatsSoldPercentage;

    private List<DailyBookingStat> bookingsPerDay = new ArrayList<>();
    private Map<SeatClass, Long> bookingsByClass = new HashMap<>();
    private List<DestinationStat> topDestinations = new ArrayList<>();

    public DashboardStats() {}

    public long getTotalBookings() { return totalBookings; }
    public void setTotalBookings(long totalBookings) { this.totalBookings = totalBookings; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public long getTotalFlights() { return totalFlights; }
    public void setTotalFlights(long totalFlights) { this.totalFlights = totalFlights; }

    public double getSeatsSoldPercentage() { return seatsSoldPercentage; }
    public void setSeatsSoldPercentage(double seatsSoldPercentage) { this.seatsSoldPercentage = seatsSoldPercentage; }

    public String getRevenueFormatted() {
        if (totalRevenue == null) return "R 0";
        return String.format(java.util.Locale.US, "R %,d", totalRevenue.longValue());
    }

    public String getSeatsSoldPercentageFormatted() {
        return Math.round(seatsSoldPercentage) + "%";
    }

    public List<DailyBookingStat> getBookingsPerDay() { return bookingsPerDay; }
    public void setBookingsPerDay(List<DailyBookingStat> bookingsPerDay) { this.bookingsPerDay = bookingsPerDay; }

    public Map<SeatClass, Long> getBookingsByClass() { return bookingsByClass; }
    public void setBookingsByClass(Map<SeatClass, Long> bookingsByClass) { this.bookingsByClass = bookingsByClass; }

    public List<DestinationStat> getTopDestinations() { return topDestinations; }
    public void setTopDestinations(List<DestinationStat> topDestinations) { this.topDestinations = topDestinations; }

    public static class DailyBookingStat {
        private final LocalDate date;
        private final String dayLabel;
        private final long count;

        public DailyBookingStat(LocalDate date, String dayLabel, long count) {
            this.date = date;
            this.dayLabel = dayLabel;
            this.count = count;
        }

        public LocalDate getDate() { return date; }
        public String getDayLabel() { return dayLabel; }
        public long getCount() { return count; }
        public long getBookingCount() { return count; }
    }

    public static class DestinationStat {
        private final String city;
        private final String iataCode;
        private final long bookingCount;

        public DestinationStat(String city, String iataCode, long bookingCount) {
            this.city = city;
            this.iataCode = iataCode;
            this.bookingCount = bookingCount;
        }

        public String getCity() { return city; }
        public String getIataCode() { return iataCode; }
        public long getBookingCount() { return bookingCount; }
    }
}
