package com.group2.flightres.service;

import com.group2.flightres.config.EmailConfig;
import com.group2.flightres.model.User;
import com.group2.flightres.service.WelcomeEmailRenderer.RenderedWelcomeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes welcome emails (HTML and plain text) to a git-ignored local outbox directory
 * when SMTP credentials are not configured.
 */
public class FileOutboxEmailService implements EmailService, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FileOutboxEmailService.class);
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final AtomicLong FILE_SEQ = new AtomicLong(0);

    private final EmailConfig config;
    private final WelcomeEmailRenderer renderer;
    private final WelcomeEmailRateLimiter rateLimiter;
    private final ExecutorService executor;

    public FileOutboxEmailService(EmailConfig config) {
        this(config, new WelcomeEmailRenderer(), new WelcomeEmailRateLimiter());
    }

    public FileOutboxEmailService(EmailConfig config,
                                  WelcomeEmailRenderer renderer,
                                  WelcomeEmailRateLimiter rateLimiter) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.executor = createBoundedExecutor("skyroyal-outbox");
    }

    @Override
    public void sendWelcome(User user, String baseUrl) {
        dispatchWelcome(user, baseUrl);
    }

    public CompletableFuture<Boolean> dispatchWelcome(User user, String baseUrl) {
        RenderedWelcomeEmail rendered = renderer.renderWelcome(user, baseUrl, config);
        if (!rateLimiter.tryAcquire(rendered.toAddress())) {
            logger.info("Welcome email skipped due to 1h rate limit for {}",
                    RegistrationService.maskEmail(rendered.toAddress()));
            return CompletableFuture.completedFuture(false);
        }

        try {
            return CompletableFuture.supplyAsync(() -> writeOutboxFiles(user, rendered), executor);
        } catch (RejectedExecutionException e) {
            logger.warn("Outbox email queue full; skipping welcome email for {}: {}",
                    RegistrationService.maskEmail(rendered.toAddress()), e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    private boolean writeOutboxFiles(User user, RenderedWelcomeEmail rendered) {
        try {
            Path dir = config.getOutboxDir();
            Files.createDirectories(dir);
            String ts = TS_FMT.format(java.time.Instant.now());
            long seq = FILE_SEQ.incrementAndGet();
            String prefix = "welcome-" + ts + "-u" + user.getUserId() + "-" + seq;

            Path htmlPath = dir.resolve(prefix + ".html");
            Path txtPath = dir.resolve(prefix + ".txt");

            Files.writeString(htmlPath, rendered.htmlBody(), StandardCharsets.UTF_8);
            String txtContent = "From: " + rendered.fromName() + " <" + rendered.fromAddress() + ">\n"
                    + "To: " + rendered.toAddress() + "\n"
                    + "Subject: " + rendered.subject() + "\n\n"
                    + rendered.textBody();
            Files.writeString(txtPath, txtContent, StandardCharsets.UTF_8);

            logger.info("Wrote welcome email to outbox for userId={} email={} files={}.html/.txt",
                    user.getUserId(), RegistrationService.maskEmail(rendered.toAddress()), prefix);
            return true;
        } catch (IOException | RuntimeException e) {
            logger.warn("Failed to write outbox welcome email for {}: {}",
                    RegistrationService.maskEmail(rendered.toAddress()), e.getMessage());
            return false;
        }
    }

    static ExecutorService createBoundedExecutor(String threadPrefix) {
        AtomicInteger threadNum = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                1,
                2,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                r -> {
                    Thread t = new Thread(r, threadPrefix + "-" + threadNum.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
