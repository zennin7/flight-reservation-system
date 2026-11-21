package com.group2.flightres.config;

import java.nio.file.Path;
import java.util.Map;

/**
 * Resolved email transport configuration sourced from environment variables and app.json.
 * The SMTP password is read strictly from SKY_ROYAL_SMTP_PASSWORD and never persisted or logged.
 */
public class EmailConfig {

    public static final String DEFAULT_SMTP_HOST = "smtp.gmail.com";
    public static final int DEFAULT_SMTP_PORT = 587;

    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUser;
    private final String smtpPassword;
    private final String fromAddress;
    private final String fromName;
    private final String publicBaseUrl;
    private final Path outboxDir;

    public EmailConfig(String smtpHost, int smtpPort, String smtpUser, String smtpPassword,
                       String fromAddress, String fromName, String publicBaseUrl, Path outboxDir) {
        this.smtpHost = firstNonBlank(smtpHost, DEFAULT_SMTP_HOST);
        this.smtpPort = smtpPort > 0 ? smtpPort : DEFAULT_SMTP_PORT;
        this.smtpUser = trimToNull(smtpUser);
        this.smtpPassword = (smtpPassword != null && !smtpPassword.isBlank()) ? smtpPassword : null;
        String fallbackFrom = (this.smtpUser != null && this.smtpUser.contains("@"))
                ? this.smtpUser
                : BrandConfig.SUPPORT_EMAIL;
        this.fromAddress = firstNonBlank(fromAddress, fallbackFrom);
        this.fromName = firstNonBlank(fromName, AppInfo.NAME);
        this.publicBaseUrl = trimToEmpty(publicBaseUrl);
        this.outboxDir = outboxDir != null ? outboxDir : Path.of("outbox");
    }

    public static EmailConfig fromEnvironmentAndAppConfig() {
        AppConfig appConfig = AppConfig.getInstance();
        return fromSources(System.getenv(), appConfig, Path.of("outbox"));
    }

    public static EmailConfig fromSources(Map<String, String> env, AppConfig appConfig, Path outboxDir) {
        String host = firstNonBlank(envGet(env, "SKY_ROYAL_SMTP_HOST"),
                appConfig != null ? appConfig.getSmtpHost() : null,
                DEFAULT_SMTP_HOST);
        int port = parsePort(envGet(env, "SKY_ROYAL_SMTP_PORT"),
                appConfig != null ? appConfig.getSmtpPort() : 0,
                DEFAULT_SMTP_PORT);
        String user = trimToNull(envGet(env, "SKY_ROYAL_SMTP_USER"));
        String password = envGet(env, "SKY_ROYAL_SMTP_PASSWORD");
        String cfgFrom = appConfig != null ? appConfig.getSmtpFromAddress() : null;
        String fallbackFrom = (user != null && user.contains("@")) ? user : BrandConfig.SUPPORT_EMAIL;
        String fromAddr = firstNonBlank(envGet(env, "SKY_ROYAL_SMTP_FROM"), cfgFrom, fallbackFrom);
        String fromName = firstNonBlank(envGet(env, "SKY_ROYAL_SMTP_FROM_NAME"),
                appConfig != null ? appConfig.getSmtpFromName() : null,
                AppInfo.NAME);
        String baseUrl = firstNonBlank(envGet(env, "SKY_ROYAL_PUBLIC_BASE_URL"),
                appConfig != null ? appConfig.getPublicBaseUrl() : null,
                "");
        return new EmailConfig(host, port, user, password, fromAddr, fromName, baseUrl, outboxDir);
    }

    public boolean isSmtpConfigured() {
        return smtpUser != null && !smtpUser.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank();
    }

    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getSmtpUser() { return smtpUser; }
    public String getSmtpPassword() { return smtpPassword; }
    public String getFromAddress() { return fromAddress; }
    public String getFromName() { return fromName; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public Path getOutboxDir() { return outboxDir; }

    @Override
    public String toString() {
        return "EmailConfig{smtpHost='" + smtpHost + '\''
                + ", smtpPort=" + smtpPort
                + ", smtpConfigured=" + isSmtpConfigured()
                + ", fromAddress='" + fromAddress + '\''
                + ", fromName='" + fromName + '\''
                + ", publicBaseUrl='" + publicBaseUrl + '\''
                + ", outboxDir=" + outboxDir
                + '}';
    }

    private static String envGet(Map<String, String> env, String key) {
        return env == null ? null : env.get(key);
    }

    private static int parsePort(String envPort, int cfgPort, int fallback) {
        if (envPort != null && !envPort.isBlank()) {
            try {
                int parsed = Integer.parseInt(envPort.trim());
                if (parsed > 0 && parsed <= 65535) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return (cfgPort > 0 && cfgPort <= 65535) ? cfgPort : fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
