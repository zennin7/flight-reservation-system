// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.dao;

import com.group2.flightres.model.AdminDashboardData;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;

public interface AdminDashboardDao {

    AdminDashboardData loadDashboardData(Clock clock, int lowSeatThreshold);

    default AdminDashboardData loadDashboardData(Connection conn, Clock clock, int lowSeatThreshold) throws SQLException {
        return loadDashboardData(clock, lowSeatThreshold);
    }
}
