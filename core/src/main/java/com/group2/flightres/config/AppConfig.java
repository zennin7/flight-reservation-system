package com.group2.flightres.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {

    public static final String DEFAULT_DB_HOST = "localhost";
    public static final int DEFAULT_DB_PORT = 5432;
    public static final String DEFAULT_DB_NAME = "flight_reservation";
    public static final String DEFAULT_DB_USERNAME = "postgres";
    public static final int DEFAULT_POOL_SIZE = 10;

    private static AppConfig instance;

    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
    private int poolSize;
    private String smtpHost;
    private int smtpPort;
    private String smtpFromAddress;
    private String smtpFromName;
    private String publicBaseUrl;

    public AppConfig() {}

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    @FunctionalInterface
    public interface PortProbe {
        boolean isOpen(String host, int port);
    }

    public static final int ALT_DB_PORT = 5433;
    private static final int PORT_PROBE_TIMEOUT_MS = 500;

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
            AppConfig fileConfig = mapper.readValue(stream, AppConfig.class);
            return resolveWithPortProbe(fileConfig, System.getenv(), AppConfig::isTcpPortOpen);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load app.json", e);
        }
    }

    /**
     * Resolves configuration using the exact shared precedence for web and desktop:
     * SKY_ROYAL_DB_* env > LAUNCH_DB_* env > app.json > probe of [configured port, 5432, 5433].
     * When an explicit valid port is provided via env, that port is honored without probing.
     */
    public static AppConfig resolveWithPortProbe(AppConfig fileConfig, Map<String, String> env, PortProbe probe) {
        AppConfig resolved = resolve(fileConfig, env);
        boolean explicitEnvPort = parseValidPort(envGet(env, "SKY_ROYAL_DB_PORT")) != null
                || parseValidPort(envGet(env, "LAUNCH_DB_PORT")) != null;
        if (!explicitEnvPort && probe != null) {
            Set<Integer> candidates = new LinkedHashSet<>();
            if (resolved.getDbPort() > 0 && resolved.getDbPort() <= 65535) {
                candidates.add(resolved.getDbPort());
            }
            candidates.add(DEFAULT_DB_PORT);
            candidates.add(ALT_DB_PORT);
            for (int candidate : candidates) {
                if (probe.isOpen(resolved.getDbHost(), candidate)) {
                    resolved.setDbPort(candidate);
                    break;
                }
            }
        }
        return resolved;
    }

    public static boolean isTcpPortOpen(String host, int port) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PORT_PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Resolves portable configuration using precedence:
     * SKY_ROYAL_DB_* env vars > LAUNCH_DB_* env vars > app.json / app.json.example > standard defaults.
     */
    public static AppConfig resolve(AppConfig fileConfig, Map<String, String> env) {
        AppConfig resolved = new AppConfig();
        String cfgHost = fileConfig != null ? fileConfig.getDbHost() : null;
        int cfgPort = fileConfig != null ? fileConfig.getDbPort() : 0;
        String cfgName = fileConfig != null ? fileConfig.getDbName() : null;
        String cfgUser = fileConfig != null ? fileConfig.getDbUsername() : null;
        String cfgPass = fileConfig != null ? fileConfig.getDbPassword() : null;
        int cfgPool = fileConfig != null ? fileConfig.getPoolSize() : 0;

        resolved.setDbHost(firstNonBlank(
                envGet(env, "SKY_ROYAL_DB_HOST"),
                envGet(env, "LAUNCH_DB_HOST"),
                cfgHost,
                DEFAULT_DB_HOST));
        resolved.setDbPort(resolvePort(
                envGet(env, "SKY_ROYAL_DB_PORT"),
                envGet(env, "LAUNCH_DB_PORT"),
                cfgPort,
                DEFAULT_DB_PORT));
        resolved.setDbName(firstNonBlank(
                envGet(env, "SKY_ROYAL_DB_NAME"),
                envGet(env, "LAUNCH_DB_NAME"),
                cfgName,
                DEFAULT_DB_NAME));
        resolved.setDbUsername(firstNonBlank(
                envGet(env, "SKY_ROYAL_DB_USER"),
                envGet(env, "LAUNCH_DB_USER"),
                cfgUser,
                DEFAULT_DB_USERNAME));
        resolved.setDbPassword(firstNonEmpty(
                envGet(env, "SKY_ROYAL_DB_PASSWORD"),
                envGet(env, "LAUNCH_DB_PASSWORD"),
                cfgPass,
                ""));
        resolved.setPoolSize(cfgPool > 0 ? cfgPool : DEFAULT_POOL_SIZE);

        if (fileConfig != null) {
            resolved.setSmtpHost(fileConfig.getSmtpHost());
            resolved.setSmtpPort(fileConfig.getSmtpPort());
            resolved.setSmtpFromAddress(fileConfig.getSmtpFromAddress());
            resolved.setSmtpFromName(fileConfig.getSmtpFromName());
            resolved.setPublicBaseUrl(fileConfig.getPublicBaseUrl());
        }
        return resolved;
    }

    private static String envGet(Map<String, String> env, String key) {
        return env == null ? null : env.get(key);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static int resolvePort(String primaryEnv, String legacyEnv, int cfgPort, int fallback) {
        Integer parsed = parseValidPort(primaryEnv);
        if (parsed != null) return parsed;
        parsed = parseValidPort(legacyEnv);
        if (parsed != null) return parsed;
        if (cfgPort > 0 && cfgPort <= 65535) return cfgPort;
        return fallback;
    }

    private static Integer parseValidPort(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int port = Integer.parseInt(raw.trim());
            if (port > 0 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
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

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort; }

    public String getSmtpFromAddress() { return smtpFromAddress; }
    public void setSmtpFromAddress(String smtpFromAddress) { this.smtpFromAddress = smtpFromAddress; }

    public String getSmtpFromName() { return smtpFromName; }
    public void setSmtpFromName(String smtpFromName) { this.smtpFromName = smtpFromName; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    @Override
    public String toString() {
        String maskedPwd = (dbPassword != null && !dbPassword.isEmpty()) ? "***" : "<unset>";
        return "AppConfig{"
                + "dbHost='" + dbHost + '\''
                + ", dbPort=" + dbPort
                + ", dbName='" + dbName + '\''
                + ", dbUsername='" + dbUsername + '\''
                + ", dbPassword='" + maskedPwd + '\''
                + ", poolSize=" + poolSize
                + ", smtpHost='" + smtpHost + '\''
                + ", smtpPort=" + smtpPort
                + ", publicBaseUrl='" + publicBaseUrl + '\''
                + '}';
    }
}
