// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.dao;

import com.group2.flightres.model.AdminDashboardData;
import com.group2.flightres.model.AdminDashboardData.Range;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;

public interface AdminDashboardDao {

    AdminDashboardData loadDashboardData(Clock clock, Range range, int lowFreePercent);

    default AdminDashboardData loadDashboardData(Connection conn, Clock clock, Range range, int lowFreePercent) throws SQLException {
        return loadDashboardData(clock, range, lowFreePercent);
    }
}
