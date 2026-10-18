package com.group2.flightres.dao;

import com.group2.flightres.model.DashboardStats;

public interface DashboardStatsDao {

    /**
     * Computes real-time platform statistics using SQL aggregates.
     * Includes total bookings, revenue, flights, seats sold %, bookings per day (last 7 days),
     * bookings by seat class, and top destinations.
     */
    DashboardStats getDashboardStats();
}
