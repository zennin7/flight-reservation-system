// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.dao;

import com.group2.flightres.model.AdminBookingRow;
import com.group2.flightres.model.AdminBookingsPage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface AdminBookingsDao {

    record AdminBookingsFilter(
            String searchTerm,
            String statusFilter,
            String dateFilter,
            LocalDate customFrom,
            LocalDate customTo,
            String cabinFilter,
            Long flightId
    ) {
        public static AdminBookingsFilter defaults() {
            return new AdminBookingsFilter("", "ALL", "ALL", null, null, "ALL", null);
        }
    }

    record FlightFilterOption(long flightId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    AdminBookingsPage searchBookings(AdminBookingsFilter filter, int page, int pageSize);

    AdminBookingsPage searchBookings(Connection conn, AdminBookingsFilter filter, int page, int pageSize) throws SQLException;

    List<AdminBookingRow> findAllMatching(AdminBookingsFilter filter);

    List<AdminBookingRow> findAllMatching(Connection conn, AdminBookingsFilter filter) throws SQLException;

    List<FlightFilterOption> listFlightOptions();

    default AdminBookingsPage searchBookings(String searchTerm, String statusFilter, int page, int pageSize) {
        return searchBookings(new AdminBookingsFilter(searchTerm, statusFilter, "ALL", null, null, "ALL", null), page, pageSize);
    }

    default AdminBookingsPage searchBookings(Connection conn, String searchTerm, String statusFilter, int page, int pageSize) throws SQLException {
        return searchBookings(conn, new AdminBookingsFilter(searchTerm, statusFilter, "ALL", null, null, "ALL", null), page, pageSize);
    }
}
