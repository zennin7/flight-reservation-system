// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.dao;

import com.group2.flightres.model.AdminBookingsPage;

import java.sql.Connection;
import java.sql.SQLException;

public interface AdminBookingsDao {

    AdminBookingsPage searchBookings(String searchTerm, String statusFilter, int page, int pageSize);

    default AdminBookingsPage searchBookings(Connection conn, String searchTerm, String statusFilter, int page, int pageSize)
            throws SQLException {
        return searchBookings(searchTerm, statusFilter, page, pageSize);
    }
}
