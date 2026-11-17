// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.service;

import com.group2.flightres.dao.AdminDashboardDao;
import com.group2.flightres.dao.impl.AdminDashboardDaoImpl;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
import com.group2.flightres.model.AdminDashboardData.Range;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

/**
 * Service providing real-data Admin Dashboard analytics with RBAC enforcement.
 */
public class AdminDashboardService {

    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardService.class);

    public static final int LOW_FREE_PERCENT = 20;

    private final AdminDashboardDao dao;
    private final Clock clock;

    public AdminDashboardService() {
        this(new AdminDashboardDaoImpl(), Clock.systemUTC());
    }

    public AdminDashboardService(AdminDashboardDao dao, Clock clock) {
        this.dao = dao != null ? dao : new AdminDashboardDaoImpl();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public AdminDashboardData getDashboardData(User currentUser) {
        return getDashboardData(currentUser, Range.SEVEN_DAYS);
    }

    public AdminDashboardData getDashboardData(User currentUser, Range range) {
        requireAdmin(currentUser);
        return dao.loadDashboardData(clock, range != null ? range : Range.SEVEN_DAYS, LOW_FREE_PERCENT);
    }

    public AdminDashboardData getDashboardData(User currentUser, Range range, Connection conn) throws SQLException {
        requireAdmin(currentUser);
        return dao.loadDashboardData(conn, clock, range != null ? range : Range.SEVEN_DAYS, LOW_FREE_PERCENT);
    }

    public List<FlightLoadFactor> filterLowFreeFlights(List<FlightLoadFactor> flights) {
        if (flights == null) {
            return List.of();
        }
        return flights.stream()
                .filter(f -> f.totalSeats() > 0 && (f.availableSeats() * 100.0 / f.totalSeats()) < LOW_FREE_PERCENT)
                .toList();
    }

    public String exportDashboardCsv(User currentUser, AdminDashboardData data) {
        requireAdmin(currentUser);
        StringBuilder sb = new StringBuilder();
        sb.append("Metric,Value,DeltaPercent\n");
        sb.append("Range,").append(csvCell(data.getRange().getLabel())).append(",\n");
        sb.append("Total bookings,").append(data.getTotalBookings()).append(",").append(formatDelta(data.getBookingsDeltaPct())).append("\n");
        sb.append("Revenue,").append(data.getTotalRevenue()).append(",").append(formatDelta(data.getRevenueDeltaPct())).append("\n");
        sb.append("Seats sold,").append(data.getSeatsSold()).append(",").append(formatDelta(data.getSeatsSoldDeltaPct())).append("\n");
        sb.append("Cancellations,").append(data.getCancellationsCount()).append(",").append(formatDelta(data.getCancellationsDeltaPct())).append("\n\n");
        sb.append("Date,Bookings,Revenue,SeatsSold,Cancellations\n");
        for (AdminDashboardData.DailySeriesPoint p : data.getDailySeries()) {
            sb.append(p.date()).append(",")
                    .append(p.bookingsCount()).append(",")
                    .append(p.revenue()).append(",")
                    .append(p.seatsSold()).append(",")
                    .append(p.cancellationsCount()).append("\n");
        }
        return sb.toString();
    }

    private String formatDelta(Double delta) {
        return delta == null ? "-" : String.format(java.util.Locale.US, "%.1f%%", delta);
    }

    private String csvCell(String raw) {
        if (raw == null) return "";
        String s = raw;
        if (!s.isEmpty() && (s.charAt(0) == '=' || s.charAt(0) == '+' || s.charAt(0) == '-' || s.charAt(0) == '@')) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private void requireAdmin(User currentUser) {
        if (currentUser == null || currentUser.getRole() != UserRole.ADMIN) {
            String actor = currentUser != null ? currentUser.getEmail() + " (" + currentUser.getRole() + ")" : "anonymous";
            logger.warn("RBAC violation: {} attempted to access AdminDashboardService", actor);
            throw new SecurityException("Access denied: ADMIN role required to view Admin Dashboard.");
        }
    }
}
