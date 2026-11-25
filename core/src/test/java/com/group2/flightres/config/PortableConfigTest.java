package com.group2.flightres.config;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.service.BoardingPassVerificationService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.EmailService;
import com.group2.flightres.service.EmailServiceFactory;
import com.group2.flightres.service.FileOutboxEmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableConfigTest {

    @Test
    @DisplayName("AppConfig.resolve falls back to standard PostgreSQL defaults when file config and env are empty")
    void defaultsMatchStandardInstall() {
        AppConfig resolved = AppConfig.resolve(null, Map.of());

        assertEquals("localhost", resolved.getDbHost());
        assertEquals(5432, resolved.getDbPort());
        assertEquals("flight_reservation", resolved.getDbName());
        assertEquals("postgres", resolved.getDbUsername());
        assertEquals("", resolved.getDbPassword());
        assertEquals(10, resolved.getPoolSize());
    }

    @Test
    @DisplayName("SKY_ROYAL_DB_* overrides LAUNCH_DB_* and file config while LAUNCH_DB_* remains supported")
    void envPrecedenceSupportsNewAndLegacyVarsAndMasksPassword() {
        AppConfig fileCfg = new AppConfig();
        fileCfg.setDbHost("db.local");
        fileCfg.setDbPort(5433);
        fileCfg.setDbName("file_db");
        fileCfg.setDbUsername("file_user");
        fileCfg.setDbPassword("file-secret-value");
        fileCfg.setPoolSize(12);

        AppConfig legacyOnly = AppConfig.resolve(fileCfg, Map.of(
                "LAUNCH_DB_HOST", "127.0.0.1",
                "LAUNCH_DB_PORT", "5434",
                "LAUNCH_DB_NAME", "legacy_db",
                "LAUNCH_DB_USER", "legacy_user",
                "LAUNCH_DB_PASSWORD", "legacy-secret-pw"
        ));
        assertEquals("127.0.0.1", legacyOnly.getDbHost());
        assertEquals(5434, legacyOnly.getDbPort());
        assertEquals("legacy_db", legacyOnly.getDbName());
        assertEquals("legacy_user", legacyOnly.getDbUsername());
        assertEquals("legacy-secret-pw", legacyOnly.getDbPassword());

        AppConfig skyRoyalWins = AppConfig.resolve(fileCfg, Map.of(
                "LAUNCH_DB_HOST", "127.0.0.1",
                "LAUNCH_DB_PORT", "5434",
                "LAUNCH_DB_NAME", "legacy_db",
                "LAUNCH_DB_USER", "legacy_user",
                "LAUNCH_DB_PASSWORD", "legacy-secret-pw",
                "SKY_ROYAL_DB_HOST", "pg.host",
                "SKY_ROYAL_DB_PORT", "5435",
                "SKY_ROYAL_DB_NAME", "sr_run",
                "SKY_ROYAL_DB_USER", "lecturer",
                "SKY_ROYAL_DB_PASSWORD", "ultra-secret-token"
        ));
        assertEquals("pg.host", skyRoyalWins.getDbHost());
        assertEquals(5435, skyRoyalWins.getDbPort());
        assertEquals("sr_run", skyRoyalWins.getDbName());
        assertEquals("lecturer", skyRoyalWins.getDbUsername());
        assertEquals("ultra-secret-token", skyRoyalWins.getDbPassword());

        String text = skyRoyalWins.toString();
        assertFalse(text.contains("ultra-secret-token"), "toString() must mask database password");
        assertTrue(text.contains("dbPassword='***'"));
    }

    @Test
    @DisplayName("Invalid DB port strings in env fall back cleanly instead of throwing")
    void invalidEnvPortFallsBackCleanly() {
        AppConfig fileCfg = new AppConfig();
        fileCfg.setDbPort(5433);
        AppConfig resolved = AppConfig.resolve(fileCfg, Map.of(
                "SKY_ROYAL_DB_PORT", "not-a-number",
                "LAUNCH_DB_PORT", "99999"
        ));
        assertEquals(5433, resolved.getDbPort());
    }

    @Test
    @DisplayName("AppConfig.resolveWithPortProbe probes configured port, 5432, and 5433 unless env port is explicit")
    void portProbeMatchesWebResolutionOrder() {
        AppConfig fileCfg = new AppConfig();
        fileCfg.setDbHost("localhost");
        fileCfg.setDbPort(5432);
        fileCfg.setDbName("sr_p8");

        java.util.List<Integer> probed = new java.util.ArrayList<>();
        AppConfig probedTo5433 = AppConfig.resolveWithPortProbe(fileCfg, Map.of(), (host, port) -> {
            probed.add(port);
            return port == 5433;
        });
        assertEquals(5433, probedTo5433.getDbPort());
        assertEquals(java.util.List.of(5432, 5433), probed);

        java.util.List<Integer> probedWithExplicit = new java.util.ArrayList<>();
        AppConfig explicitEnv = AppConfig.resolveWithPortProbe(fileCfg, Map.of("SKY_ROYAL_DB_PORT", "5439"), (host, port) -> {
            probedWithExplicit.add(port);
            return true;
        });
        assertEquals(5439, explicitEnv.getDbPort());
        assertTrue(probedWithExplicit.isEmpty(), "Explicit SKY_ROYAL_DB_PORT must bypass port probing");
    }

    @Test
    @DisplayName("Email defaults to FileOutboxEmailService when SMTP env credentials are absent")
    void emailDefaultsToOutboxWithoutSmtp(@TempDir Path tempDir) {
        EmailConfig cfg = EmailConfig.fromSources(Map.of(), new AppConfig(), tempDir);
        assertFalse(cfg.isSmtpConfigured());
        EmailService service = EmailServiceFactory.create(cfg);
        assertInstanceOf(FileOutboxEmailService.class, service);
        if (service instanceof FileOutboxEmailService outbox) {
            outbox.close();
        }
    }

    @Test
    @DisplayName("QR verification auto-generates and persists secret file when missing")
    void qrSecretAutoGeneratesInConfiguredDir(@TempDir Path tempDir) throws Exception {
        Path secretFile = tempDir.resolve(".sky-royal").resolve("qr-secret");
        assertFalse(Files.exists(secretFile));

        BookingService dummyBookings = new BookingService((BookingDao) null, (FlightDao) null);
        BoardingPassVerificationService svc1 = new BoardingPassVerificationService(dummyBookings, secretFile);
        assertTrue(Files.exists(secretFile));
        String stored = Files.readString(secretFile).trim();
        assertFalse(stored.isEmpty());

        BoardingPassVerificationService svc2 = new BoardingPassVerificationService(dummyBookings, secretFile);
        BookingRecord rec = new BookingRecord();
        rec.setBookingId(42L);
        rec.setBookingReference("ABC123");
        rec.setFlightId(7L);

        String url1 = svc1.buildVerificationUrl("http://localhost:8099", rec);
        String url2 = svc2.buildVerificationUrl("http://localhost:8099", rec);
        assertEquals(url1, url2, "Reloading persisted secret must produce identical signatures");
        assertTrue(url1.startsWith("http://localhost:8099/verify/ABC123?sig="));
    }
}
