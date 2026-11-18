package com.group2.flightres.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

public class AppConfig {

    private static AppConfig instance;

    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
    private int poolSize;

    private AppConfig() {}

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AppConfig load() {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = AppConfig.class.getResourceAsStream("/com/group2/flightres/app.json");
        if (is == null) {
            is = AppConfig.class.getResourceAsStream("/com/group2/flightres/app.json.example");
        }
        if (is == null) {
            throw new IllegalStateException("app.json not found on classpath");
        }
        try (InputStream stream = is) {
            return mapper.readValue(stream, AppConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load app.json", e);
        }
    }

    public String getDbHost() { return dbHost; }
    public void setDbHost(String dbHost) { this.dbHost = dbHost; }

    public int getDbPort() { return dbPort; }
    public void setDbPort(int dbPort) { this.dbPort = dbPort; }

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getDbUsername() { return dbUsername; }
    public void setDbUsername(String dbUsername) { this.dbUsername = dbUsername; }

    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }

    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
}
