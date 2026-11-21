package com.group2.flightres.service;

import com.group2.flightres.config.EmailConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects SMTP delivery when both SKY_ROYAL_SMTP_USER and SKY_ROYAL_SMTP_PASSWORD
 * are set, and otherwise falls back to writing rendered messages into the outbox folder.
 */
public final class EmailServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceFactory.class);

    private EmailServiceFactory() {}

    public static EmailService createDefault() {
        return create(EmailConfig.fromEnvironmentAndAppConfig());
    }

    public static EmailService create(EmailConfig config) {
        if (config.isSmtpConfigured()) {
            logger.info("Email transport configured: SMTP ({}:{})", config.getSmtpHost(), config.getSmtpPort());
            return new SmtpEmailService(config);
        }
        logger.info("Email transport configured: local outbox ({})", config.getOutboxDir());
        return new FileOutboxEmailService(config);
    }
}
