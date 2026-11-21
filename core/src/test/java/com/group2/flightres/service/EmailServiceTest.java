package com.group2.flightres.service;

import com.group2.flightres.config.EmailConfig;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailServiceTest {

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        void advance(Duration d) {
            now.updateAndGet(cur -> cur.plus(d));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private static User buildUser(long id, String username, String fullName, String email) {
        User u = new User();
        u.setUserId(id);
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setRole(UserRole.CUSTOMER);
        u.setActive(true);
        return u;
    }

    private static EmailConfig greenMailConfig(GreenMail gm, Path outboxDir) {
        return new EmailConfig(
                "127.0.0.1",
                gm.getSmtp().getPort(),
                "smtp-user@example.com",
                "dummy-test-credential",
                "noreply@skyroyal.com",
                "Sky Royal",
                "https://skyroyal.example",
                outboxDir
        );
    }

    private static void collectBodies(Part part, StringBuilder textOut, StringBuilder htmlOut) throws Exception {
        Object content = part.getContent();
        if (part.isMimeType("text/plain") && content instanceof String s) {
            textOut.append(s);
        } else if (part.isMimeType("text/html") && content instanceof String s) {
            htmlOut.append(s);
        } else if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                collectBodies(bp, textOut, htmlOut);
            }
        }
    }

    @Test
    @DisplayName("Delivers multipart/alternative welcome email via GreenMail for normal and accented names")
    void deliversMultipartEmailWithUtf8AndHtmlEscaping(@TempDir Path tmp) throws Exception {
        GreenMail gm = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        gm.setUser("smtp-user@example.com", "dummy-test-credential");
        gm.start();
        try (SmtpEmailService service = new SmtpEmailService(
                greenMailConfig(gm, tmp),
                new WelcomeEmailRenderer(),
                new WelcomeEmailRateLimiter(),
                List.of(10L, 20L, 30L),
                false)) {

            User accented = buildUser(101L, "zoe.muller", "Zoë Müller", "zoe@example.com");
            assertTrue(service.dispatchWelcome(accented, "").get(10, TimeUnit.SECONDS));

            User scriptUser = buildUser(102L, "script.user", "<script>alert(1)</script> Victim", "victim@example.com");
            assertTrue(service.dispatchWelcome(scriptUser, "").get(10, TimeUnit.SECONDS));

            assertTrue(gm.waitForIncomingEmail(5000, 2));
            MimeMessage[] received = gm.getReceivedMessages();
            assertEquals(2, received.length);

            MimeMessage m1 = received[0];
            assertEquals("zoe@example.com", m1.getAllRecipients()[0].toString());
            assertTrue(m1.getSubject().contains("Zoë"));
            assertTrue(m1.getContentType().toLowerCase().contains("multipart/alternative"));

            StringBuilder txt1 = new StringBuilder();
            StringBuilder html1 = new StringBuilder();
            collectBodies(m1, txt1, html1);
            assertTrue(txt1.toString().contains("Zoë Müller"));
            assertTrue(txt1.toString().contains("zoe.muller"));
            assertTrue(txt1.toString().contains("https://skyroyal.example/flights"));
            assertTrue(html1.toString().contains("Zoë Müller"));
            assertTrue(html1.toString().contains("https://skyroyal.example/flights"));

            MimeMessage m2 = received[1];
            StringBuilder txt2 = new StringBuilder();
            StringBuilder html2 = new StringBuilder();
            collectBodies(m2, txt2, html2);
            assertFalse(html2.toString().contains("<script>"));
            assertTrue(html2.toString().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        } finally {
            gm.stop();
        }
    }

    @Test
    @DisplayName("Rejects CR/LF injection in recipient email and display name without sending email")
    void rejectsCrLfHeaderInjection(@TempDir Path tmp) throws Exception {
        GreenMail gm = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        gm.setUser("smtp-user@example.com", "dummy-test-credential");
        gm.start();
        try (SmtpEmailService service = new SmtpEmailService(
                greenMailConfig(gm, tmp),
                new WelcomeEmailRenderer(),
                new WelcomeEmailRateLimiter(),
                List.of(10L, 20L, 30L),
                false)) {

            User badEmail = buildUser(1L, "evil", "Alice Smith", "victim@example.com\r\nBcc: attacker@evil.com");
            assertThrows(IllegalArgumentException.class, () -> service.sendWelcome(badEmail, "http://localhost:8080"));

            User badName = buildUser(2L, "evil2", "Alice\nBcc: attacker@evil.com", "good@example.com");
            assertThrows(IllegalArgumentException.class, () -> service.sendWelcome(badName, "http://localhost:8080"));

            assertEquals(0, gm.getReceivedMessages().length);
        } finally {
            gm.stop();
        }
    }

    @Test
    @DisplayName("Enforces at most one welcome email per address per hour")
    void enforcesHourlyRateLimitPerAddress(@TempDir Path tmp) throws Exception {
        GreenMail gm = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        gm.setUser("smtp-user@example.com", "dummy-test-credential");
        gm.start();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-21T10:00:00Z"));
        try (SmtpEmailService service = new SmtpEmailService(
                greenMailConfig(gm, tmp),
                new WelcomeEmailRenderer(),
                new WelcomeEmailRateLimiter(clock),
                List.of(10L, 20L, 30L),
                false)) {

            User u1 = buildUser(10L, "sipho.nkosi", "Sipho Nkosi", "Sipho@Example.com");
            assertTrue(service.dispatchWelcome(u1, "").get(10, TimeUnit.SECONDS));
            assertFalse(service.dispatchWelcome(buildUser(10L, "sipho.nkosi", "Sipho Nkosi", "sipho@example.com"), "")
                    .get(10, TimeUnit.SECONDS));

            clock.advance(Duration.ofMinutes(61));
            assertTrue(service.dispatchWelcome(u1, "").get(10, TimeUnit.SECONDS));
            assertEquals(2, gm.getReceivedMessages().length);
        } finally {
            gm.stop();
        }
    }

    @Test
    @DisplayName("Retries transient SMTP failures up to 3 times with backoff and never fails registration when down")
    void retriesThreeTimesAndRegistrationSurvivesDownServer(@TempDir Path tmp) throws Exception {
        AtomicInteger connections = new AtomicInteger(0);
        try (ServerSocket failingServer = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))) {
            int port = failingServer.getLocalPort();
            Thread acceptor = new Thread(() -> {
                while (!failingServer.isClosed()) {
                    try (Socket s = failingServer.accept();
                         OutputStream os = s.getOutputStream()) {
                        connections.incrementAndGet();
                        os.write("421 4.3.2 Temporary SMTP failure\r\n".getBytes(StandardCharsets.US_ASCII));
                        os.flush();
                    } catch (Exception ignored) {
                        break;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            EmailConfig badCfg = new EmailConfig("127.0.0.1", port, "u@example.com", "pass",
                    "noreply@skyroyal.com", "Sky Royal", "http://localhost:8080", tmp);
            try (SmtpEmailService service = new SmtpEmailService(
                    badCfg,
                    new WelcomeEmailRenderer(),
                    new WelcomeEmailRateLimiter(),
                    List.of(10L, 15L, 20L),
                    false)) {

                User u = buildUser(44L, "thabo.m", "Thabo Mbeki", "thabo@example.com");
                boolean delivered = service.dispatchWelcome(u, "http://localhost:8080").get(10, TimeUnit.SECONDS);
                assertFalse(delivered);
                assertEquals(4, connections.get(), "Expected 1 initial attempt + 3 retries = 4 total attempts");
            }
        }

        // Also verify registration succeeds synchronously when SMTP port refuses connections immediately
        int closedPort;
        try (ServerSocket s = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))) {
            closedPort = s.getLocalPort();
        }
        EmailConfig downCfg = new EmailConfig("127.0.0.1", closedPort, "u@example.com", "pass",
                "noreply@skyroyal.com", "Sky Royal", "http://localhost:8080", tmp);
        try (SmtpEmailService downService = new SmtpEmailService(
                downCfg, new WelcomeEmailRenderer(), new WelcomeEmailRateLimiter(), List.of(5L, 5L, 5L), false)) {

            List<User> stored = new ArrayList<>();
            UserDao fakeDao = new UserDao() {
                @Override public Optional<User> findByUsername(String username) { return Optional.empty(); }
                @Override public Optional<User> findByUsernameOrEmail(String id) { return Optional.empty(); }
                @Override public boolean existsByUsernameIgnoreCase(String u) { return false; }
                @Override public boolean existsByEmailIgnoreCase(String e) { return false; }
                @Override public User createCustomer(User u) {
                    u.setUserId(99L);
                    stored.add(u);
                    return u;
                }
            };
            RegistrationService reg = new RegistrationService(fakeDao, downService);
            User created = reg.register("Amina Patel", "amina@example.com", "StrongPass12", "http://localhost:8080");
            assertEquals("amina.patel", created.getUsername());
            assertEquals(1, stored.size());
        }
    }

    @Test
    @DisplayName("Falls back to FileOutboxEmailService writing HTML and TXT when SMTP env vars missing, and hides password")
    void outboxFallbackAndSecretSafety(@TempDir Path outboxDir) throws Exception {
        EmailConfig outboxCfg = EmailConfig.fromSources(Map.of(), null, outboxDir);
        assertFalse(outboxCfg.isSmtpConfigured());
        EmailService chosen = EmailServiceFactory.create(outboxCfg);
        assertTrue(chosen instanceof FileOutboxEmailService);

        try (FileOutboxEmailService outbox = (FileOutboxEmailService) chosen) {
            User u = buildUser(77L, "lerato.mokoena", "Lerato Mokoena", "lerato@example.com");
            assertTrue(outbox.dispatchWelcome(u, "https://app.skyroyal.test").get(10, TimeUnit.SECONDS));

            List<Path> files;
            try (var stream = Files.list(outboxDir)) {
                files = stream.toList();
            }
            assertEquals(2, files.size());
            Path htmlFile = files.stream().filter(p -> p.toString().endsWith(".html")).findFirst().orElseThrow();
            Path txtFile = files.stream().filter(p -> p.toString().endsWith(".txt")).findFirst().orElseThrow();
            assertTrue(Files.readString(htmlFile).contains("Welcome aboard, Lerato."));
            assertTrue(Files.readString(txtFile).contains("https://app.skyroyal.test/flights"));
        }

        EmailConfig smtpCfg = EmailConfig.fromSources(
                Map.of("SKY_ROYAL_SMTP_USER", "bot@example.com", "SKY_ROYAL_SMTP_PASSWORD", "SUPER_SECRET_PWD"),
                null,
                outboxDir);
        assertTrue(smtpCfg.isSmtpConfigured());
        assertFalse(smtpCfg.toString().contains("SUPER_SECRET_PWD"));
    }
}
