package com.group2.flightres.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseConfig {

    private static HikariDataSource dataSource;

    private DatabaseConfig() {}

    public static synchronized HikariDataSource getDataSource() {
        if (dataSource == null) {
            AppConfig appConfig = AppConfig.getInstance();

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s",
                    appConfig.getDbHost(), appConfig.getDbPort(), appConfig.getDbName()));
            config.setUsername(appConfig.getDbUsername());
            config.setPassword(appConfig.getDbPassword());
            config.setDriverClassName("org.postgresql.Driver");
            config.setMaximumPoolSize(appConfig.getPoolSize());
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
