package com.group2.flightres.web;

import com.group2.flightres.config.EmailConfig;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.impl.UserDaoImpl;
import com.group2.flightres.service.BoardingPassVerificationService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FileOutboxEmailService;
import com.group2.flightres.service.LoginThrottle;
import com.group2.flightres.service.PaymentThrottle;
import com.group2.flightres.service.RegistrationService;
import com.group2.flightres.service.RegistrationThrottle;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.service.VerifyThrottle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPortConfigTest {

    private WebApplication webApp;

    @AfterEach
    void tearDown() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    @Test
    @DisplayName("resolvePreferredPort honors SKY_ROYAL_WEB_PORT over PORT and defaults to 8080")
    void resolvePreferredPortPrecedence() {
        assertEquals(8080, WebApplication.resolvePreferredPort(Map.of()));
        assertEquals(8085, WebApplication.resolvePreferredPort(Map.of("PORT", "8085")));
        assertEquals(9091, WebApplication.resolvePreferredPort(Map.of(
                "PORT", "8085",
                "SKY_ROYAL_WEB_PORT", "9091"
        )));
        assertEquals(8085, WebApplication.resolvePreferredPort(Map.of(
                "PORT", "8085",
                "SKY_ROYAL_WEB_PORT", "invalid"
        )));
    }

    @Test
    @DisplayName("WebApplication tracks actual bound port and propagates boundBaseUrl to WebController")
    void boundBaseUrlTracksActualEphemeralPort(@TempDir Path tempDir) {
        BookingService bookingService = new BookingService((BookingDao) null, (FlightDao) null);
        BoardingPassVerificationService verifySvc = new BoardingPassVerificationService(
                bookingService, tempDir.resolve(".sky-royal").resolve("qr-secret"));
        EmailConfig outboxCfg = EmailConfig.fromSources(Map.of(), null, tempDir.resolve("outbox"));
        RegistrationService regSvc = new RegistrationService(new UserDaoImpl(), new FileOutboxEmailService(outboxCfg));

        webApp = new WebApplication(
                null, null, bookingService, null,
                new LoginThrottle(), regSvc, new RegistrationThrottle(),
                new SimulatedPaymentGateway(), new PaymentThrottle(),
                verifySvc, new VerifyThrottle(), null
        );
        webApp.start(0);

        int actualPort = webApp.getActualPort();
        assertTrue(actualPort > 0, "Expected ephemeral bound port > 0");
        assertEquals("http://localhost:" + actualPort, webApp.getBoundBaseUrl());
        assertEquals("http://localhost:" + actualPort, webApp.getController().resolveBaseUrl(null));
    }
}
