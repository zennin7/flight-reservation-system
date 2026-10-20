package com.group2.flightres.util;

import com.group2.flightres.config.AppConfig;
import com.group2.flightres.config.DatabaseConfig;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;

/**
 * Reusable JUnit 5 Assumption helper that skips database-dependent tests
 * when PostgreSQL is not reachable or app.json configuration is missing.
 */
public final class DatabaseAssumption {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseAssumption.class);
    private static Boolean available = null;

    private DatabaseAssumption() {}

    public static synchronized boolean isDatabaseAvailable() {
        if (available != null) {
            return available;
        }

        try {
            AppConfig config = AppConfig.getInstance();
            if (config == null || config.getDbHost() == null || config.getDbPort() <= 0) {
                logger.info("app.json is missing or incomplete; skipping database-dependent tests.");
                available = false;
                return false;
            }

            // Fast socket connectivity probe (500ms timeout)
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(config.getDbHost(), config.getDbPort()), 500);
            } catch (Exception e) {
                logger.info("PostgreSQL port {}:{} is not reachable ({}); skipping database-dependent tests.",
                        config.getDbHost(), config.getDbPort(), e.getMessage());
                available = false;
                return false;
            }

            // Probe actual JDBC connection
            try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
                available = (conn != null && !conn.isClosed());
                return available;
            } catch (Exception e) {
                logger.info("Failed to establish PostgreSQL connection ({}); skipping database-dependent tests.", e.getMessage());
                available = false;
                return false;
            }
        } catch (Throwable t) {
            logger.info("Database configuration check failed ({}); skipping database-dependent tests.", t.getMessage());
            available = false;
            return false;
        }
    }

    /**
     * Skips the currently executing test if PostgreSQL or app.json is unavailable.
     */
    public static void assumeDatabaseAvailable() {
        Assumptions.assumeTrue(isDatabaseAvailable(),
                "PostgreSQL database is not reachable or app.json is missing. Skipping database-dependent test.");
    }
}
