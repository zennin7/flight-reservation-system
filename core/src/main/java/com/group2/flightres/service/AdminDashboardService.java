// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.service;

import com.group2.flightres.dao.AdminDashboardDao;
import com.group2.flightres.dao.impl.AdminDashboardDaoImpl;
import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.FlightLoadFactor;
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

    public static final int LOW_SEAT_THRESHOLD = 20;

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
        requireAdmin(currentUser);
        return dao.loadDashboardData(clock, LOW_SEAT_THRESHOLD);
    }

    public AdminDashboardData getDashboardData(User currentUser, Connection conn) throws SQLException {
        requireAdmin(currentUser);
        return dao.loadDashboardData(conn, clock, LOW_SEAT_THRESHOLD);
    }

    public List<FlightLoadFactor> filterLowSeatAlerts(List<FlightLoadFactor> flights) {
        if (flights == null) {
            return List.of();
        }
        return flights.stream()
                .filter(f -> f.availableSeats() < LOW_SEAT_THRESHOLD)
                .toList();
    }

    private void requireAdmin(User currentUser) {
        if (currentUser == null || currentUser.getRole() != UserRole.ADMIN) {
            String actor = currentUser != null ? currentUser.getEmail() + " (" + currentUser.getRole() + ")" : "anonymous";
            logger.warn("RBAC violation: {} attempted to access AdminDashboardService", actor);
            throw new SecurityException("Access denied: ADMIN role required to view Admin Dashboard.");
        }
    }
}
