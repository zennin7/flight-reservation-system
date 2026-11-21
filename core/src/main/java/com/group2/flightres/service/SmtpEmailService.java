package com.group2.flightres.service;

import com.group2.flightres.config.EmailConfig;
import com.group2.flightres.model.User;
import com.group2.flightres.service.WelcomeEmailRenderer.RenderedWelcomeEmail;
import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Asynchronous STARTTLS SMTP welcome email sender backed by Jakarta Mail + Angus Mail.
 * Never blocks or fails registration; retries transient failures up to 3 times with backoff.
 */
public class SmtpEmailService implements EmailService, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailService.class);
    private static final String HERO_RESOURCE = "/email/skyroyal-hero.jpg";
    private static final List<Long> DEFAULT_RETRY_DELAYS_MS = List.of(1000L, 3000L, 7000L);

    private final EmailConfig config;
    private final WelcomeEmailRenderer renderer;
    private final WelcomeEmailRateLimiter rateLimiter;
    private final ExecutorService executor;
    private final List<Long> retryDelaysMs;
    private final boolean requireStartTls;

    public SmtpEmailService(EmailConfig config) {
        this(config, new WelcomeEmailRenderer(), new WelcomeEmailRateLimiter(),
                DEFAULT_RETRY_DELAYS_MS, !isLoopbackHost(config.getSmtpHost()));
    }

    public SmtpEmailService(EmailConfig config,
                            WelcomeEmailRenderer renderer,
                            WelcomeEmailRateLimiter rateLimiter,
                            List<Long> retryDelaysMs,
                            boolean requireStartTls) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.retryDelaysMs = retryDelaysMs != null ? List.copyOf(retryDelaysMs) : DEFAULT_RETRY_DELAYS_MS;
        this.requireStartTls = requireStartTls;
        this.executor = FileOutboxEmailService.createBoundedExecutor("skyroyal-smtp");
    }

    @Override
    public void sendWelcome(User user, String baseUrl) {
        dispatchWelcome(user, baseUrl);
    }

    public CompletableFuture<Boolean> dispatchWelcome(User user, String baseUrl) {
        RenderedWelcomeEmail rendered = renderer.renderWelcome(user, baseUrl, config);
        if (!config.isSmtpConfigured()) {
            logger.warn("SMTP credentials missing; skipping welcome email for {}",
                    RegistrationService.maskEmail(rendered.toAddress()));
            return CompletableFuture.completedFuture(false);
        }
        if (!rateLimiter.tryAcquire(rendered.toAddress())) {
            logger.info("Welcome email skipped due to 1h rate limit for {}",
                    RegistrationService.maskEmail(rendered.toAddress()));
            return CompletableFuture.completedFuture(false);
        }

        try {
            return CompletableFuture.supplyAsync(() -> sendWithRetries(user, rendered), executor);
        } catch (RejectedExecutionException e) {
            logger.warn("SMTP email queue full; skipping welcome email for {}: {}",
                    RegistrationService.maskEmail(rendered.toAddress()), e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    private boolean sendWithRetries(User user, RenderedWelcomeEmail rendered) {
        int maxAttempts = 1 + retryDelaysMs.size();
        String masked = RegistrationService.maskEmail(rendered.toAddress());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                MimeMessage message = buildMimeMessage(rendered);
                Transport.send(message);
                logger.info("Welcome email sent via SMTP for userId={} email={}", user.getUserId(), masked);
                return true;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    long delayMs = retryDelaysMs.get(attempt - 1);
                    logger.warn("Welcome email attempt {}/{} failed for {} ({}: {}): retrying in {} ms",
                            attempt, maxAttempts, masked, e.getClass().getSimpleName(), e.getMessage(), delayMs);
                    if (delayMs > 0) {
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            logger.warn("Welcome email retry interrupted for {}", masked);
                            return false;
                        }
                    }
                } else {
                    logger.warn("Welcome email delivery gave up after {} attempts for {} ({}): {}",
                            maxAttempts, masked, e.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
        return false;
    }

    MimeMessage buildMimeMessage(RenderedWelcomeEmail rendered)
            throws MessagingException, UnsupportedEncodingException {
        Session session = createSession();
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(rendered.fromAddress(), rendered.fromName(), StandardCharsets.UTF_8.name()));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(rendered.toAddress()));
        message.setSubject(rendered.subject(), StandardCharsets.UTF_8.name());

        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(rendered.textBody(), StandardCharsets.UTF_8.name());
        alternative.addBodyPart(textPart);

        byte[] heroBytes = loadHeroImageBytes();
        if (heroBytes != null && heroBytes.length > 0) {
            MimeMultipart related = new MimeMultipart("related");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(rendered.htmlBody(), "text/html; charset=UTF-8");
            related.addBodyPart(htmlPart);

            MimeBodyPart imagePart = new MimeBodyPart();
            imagePart.setDataHandler(new DataHandler(new ByteArrayDataSource(heroBytes, "image/jpeg")));
            imagePart.setHeader("Content-ID", "<skyroyal-hero>");
            imagePart.setDisposition(MimeBodyPart.INLINE);
            imagePart.setFileName("skyroyal-hero.jpg");
            related.addBodyPart(imagePart);

            MimeBodyPart relatedWrapper = new MimeBodyPart();
            relatedWrapper.setContent(related);
            alternative.addBodyPart(relatedWrapper);
        } else {
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(rendered.htmlBody(), "text/html; charset=UTF-8");
            alternative.addBodyPart(htmlPart);
        }

        message.setContent(alternative);
        return message;
    }

    private Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", Boolean.toString(requireStartTls));
        props.put("mail.smtp.host", config.getSmtpHost());
        props.put("mail.smtp.port", Integer.toString(config.getSmtpPort()));
        props.put("mail.smtp.localhost", "skyroyal.local");
        props.put("mail.host", "skyroyal.local");
        props.put("mail.from", config.getFromAddress());
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.getSmtpUser(), config.getSmtpPassword());
            }
        });
    }

    private static byte[] loadHeroImageBytes() {
        try (InputStream is = SmtpEmailService.class.getResourceAsStream(HERO_RESOURCE)) {
            if (is == null) {
                return null;
            }
            return is.readAllBytes();
        } catch (IOException e) {
            logger.warn("Could not read embedded hero image {}: {}", HERO_RESOURCE, e.getMessage());
            return null;
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) return false;
        String lower = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(lower) || "127.0.0.1".equals(lower) || "::1".equals(lower);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
