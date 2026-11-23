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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPortFallbackTest {

    private WebApplication webApp;
    private ServerSocket dummyListener;

    @AfterEach
    void tearDown() throws Exception {
        if (webApp != null) {
            webApp.stop();
        }
        if (dummyListener != null && !dummyListener.isClosed()) {
            dummyListener.close();
        }
    }

    @Test
    @DisplayName("startWithFallback moves to next free port when preferred port is occupied and updates QR boundBaseUrl")
    void fallsBackToNextFreePortWhenPreferredBusy(@TempDir Path tempDir) throws Exception {
        dummyListener = new ServerSocket();
        dummyListener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        int busyPort = dummyListener.getLocalPort();

        assertFalse(WebApplication.isPortAvailable(busyPort), "Dummy listener port must be detected as busy");

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

        int actualPort = webApp.startWithFallback(busyPort, 30);
        assertNotEquals(busyPort, actualPort);
        assertTrue(actualPort > busyPort, "Expected fallback port after " + busyPort + ", got " + actualPort);
        assertEquals("http://localhost:" + actualPort, webApp.getBoundBaseUrl());
        assertEquals(webApp.getBoundBaseUrl(), webApp.getController().resolveBaseUrl(null));

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> homeResp = client.send(
                HttpRequest.newBuilder(URI.create(webApp.getBoundBaseUrl() + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, homeResp.statusCode());
        assertTrue(homeResp.body().contains("Sky Royal"));
    }
}
