package com.group2.flightres.service;

import com.group2.flightres.dao.DashboardStatsDao;
import com.group2.flightres.dao.impl.DashboardStatsDaoImpl;
import com.group2.flightres.model.DashboardStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service providing platform analytics and KPI aggregates for administrators.
 * Shared between JavaFX desktop and web modules.
 */
public class DashboardStatsService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardStatsService.class);

    private final DashboardStatsDao statsDao;

    public DashboardStatsService() {
        this(new DashboardStatsDaoImpl());
    }

    public DashboardStatsService(DashboardStatsDao statsDao) {
        this.statsDao = statsDao;
    }

    /**
     * Retrieves aggregated platform statistics from the database.
     */
    public DashboardStats getStats() {
        logger.debug("Fetching platform dashboard statistics");
        DashboardStats stats = statsDao.getDashboardStats();
        logger.info("Retrieved stats: {} total bookings, R {} revenue",
                stats.getTotalBookings(), stats.getTotalRevenue());
        return stats;
    }
}
