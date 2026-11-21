package com.group2.flightres.service;

import com.group2.flightres.config.AppInfo;
import com.group2.flightres.config.EmailConfig;
import com.group2.flightres.model.User;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Renders HTML and plain-text welcome emails from Thymeleaf templates in core,
 * rejecting CR/LF header injection in any header value.
 */
public class WelcomeEmailRenderer {

    public record RenderedWelcomeEmail(
            String toAddress,
            String fromAddress,
            String fromName,
            String subject,
            String htmlBody,
            String textBody
    ) {}

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;

    public WelcomeEmailRenderer() {
        this.htmlEngine = buildEngine(TemplateMode.HTML, ".html");
        this.textEngine = buildEngine(TemplateMode.TEXT, ".txt");
    }

    private static TemplateEngine buildEngine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/email/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    public RenderedWelcomeEmail renderWelcome(User user, String requestBaseUrl, EmailConfig config) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        String toAddress = requireNoCrLf("recipientEmail", user.getEmail());
        String fullName = requireNoCrLf("fullName", user.getFullName());
        String username = requireNoCrLf("username",
                (user.getUsername() == null || user.getUsername().isBlank()) ? "traveller" : user.getUsername());
        String fromAddress = requireNoCrLf("fromAddress", config.getFromAddress());
        String fromName = requireNoCrLf("fromName", config.getFromName());

        String firstName = extractFirstName(fullName);
        String subject = requireNoCrLf("subject", "Welcome to " + AppInfo.NAME + ", " + firstName);
        String effectiveBaseUrl = normalizeBaseUrl(config.getPublicBaseUrl(), requestBaseUrl);
        String flightsUrl = effectiveBaseUrl + "/flights";

        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("appName", AppInfo.NAME);
        ctx.setVariable("firstName", firstName);
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("username", username);
        ctx.setVariable("recipientEmail", toAddress);
        ctx.setVariable("baseUrl", effectiveBaseUrl);
        ctx.setVariable("flightsUrl", flightsUrl);
        ctx.setVariable("heroCid", "skyroyal-hero");

        String htmlBody = htmlEngine.process("welcome", ctx);
        String textBody = textEngine.process("welcome", ctx);
        return new RenderedWelcomeEmail(toAddress, fromAddress, fromName, subject, htmlBody, textBody);
    }

    public static String requireNoCrLf(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0') {
                throw new IllegalArgumentException(
                        "Header field '" + fieldName + "' contains illegal CR/LF or NUL character");
            }
        }
        return value.trim();
    }

    public static String normalizeBaseUrl(String configuredBaseUrl, String requestBaseUrl) {
        String candidate = (configuredBaseUrl != null && !configuredBaseUrl.isBlank())
                ? configuredBaseUrl
                : requestBaseUrl;
        if (candidate == null || candidate.isBlank()) {
            return "http://localhost:8080";
        }
        String clean = requireNoCrLf("baseUrl", candidate).replaceAll("/+$", "");
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return "http://localhost:8080";
        }
        if (clean.chars().anyMatch(Character::isWhitespace)) {
            return "http://localhost:8080";
        }
        return clean;
    }

    static String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Traveller";
        }
        String trimmed = fullName.trim();
        int idx = trimmed.indexOf(' ');
        return idx > 0 ? trimmed.substring(0, idx) : trimmed;
    }
}
