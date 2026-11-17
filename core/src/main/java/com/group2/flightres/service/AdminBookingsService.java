// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.service;

import com.group2.flightres.dao.AdminBookingsDao;
import com.group2.flightres.dao.impl.AdminBookingsDaoImpl;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Service managing Admin Bookings search, status filtering, and 25-row pagination with RBAC enforcement.
 */
public class AdminBookingsService {

    private static final Logger logger = LoggerFactory.getLogger(AdminBookingsService.class);

    public static final int PAGE_SIZE = 25;

    private final AdminBookingsDao dao;

    public AdminBookingsService() {
        this(new AdminBookingsDaoImpl());
    }

    public AdminBookingsService(AdminBookingsDao dao) {
        this.dao = dao != null ? dao : new AdminBookingsDaoImpl();
    }

    public AdminBookingsPage searchBookings(User currentUser, String searchTerm, String statusFilter, int page) {
        requireAdmin(currentUser);
        return dao.searchBookings(searchTerm, statusFilter, page, PAGE_SIZE);
    }

    public AdminBookingsPage searchBookings(User currentUser, String searchTerm, String statusFilter, int page, Connection conn)
            throws SQLException {
        requireAdmin(currentUser);
        return dao.searchBookings(conn, searchTerm, statusFilter, page, PAGE_SIZE);
    }

    private void requireAdmin(User currentUser) {
        if (currentUser == null || currentUser.getRole() != UserRole.ADMIN) {
            String actor = currentUser != null ? currentUser.getEmail() + " (" + currentUser.getRole() + ")" : "anonymous";
            logger.warn("RBAC violation: {} attempted to access AdminBookingsService", actor);
            throw new SecurityException("Access denied: ADMIN role required to view Admin Bookings.");
        }
    }
}
