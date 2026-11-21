package com.group2.flightres.service;

import com.group2.flightres.dao.UserDao;
import com.group2.flightres.dao.impl.UserDaoImpl;
import com.group2.flightres.exception.DuplicateEmailException;
import com.group2.flightres.exception.DuplicateUsernameException;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.PasswordUtil;
import com.group2.flightres.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Registers new CUSTOMER accounts with validated inputs, BCrypt (cost 12) password hashing,
 * unique generated usernames, case-insensitive email uniqueness, and async welcome emails.
 */
public class RegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationService.class);
    private static final int MAX_USERNAME_LEN = 30;
    private static final int MAX_USERNAME_PROBES = 500;
    private static final int MAX_INSERT_RETRIES = 8;

    private final UserDao userDao;
    private final EmailService emailService;

    public RegistrationService() {
        this(new UserDaoImpl(), (user, baseUrl) -> {});
    }

    public RegistrationService(UserDao userDao) {
        this(userDao, (user, baseUrl) -> {});
    }

    public RegistrationService(UserDao userDao, EmailService emailService) {
        this.userDao = Objects.requireNonNull(userDao, "userDao must not be null");
        this.emailService = Objects.requireNonNull(emailService, "emailService must not be null");
    }

    public User register(String fullName, String email, String password) {
        return register(fullName, email, password, "");
    }

    /**
     * Validates input, checks duplicate email, generates a unique username, persists a CUSTOMER
     * account, and triggers an asynchronous welcome email without blocking registration.
     */
    public User register(String fullName, String email, String password, String baseUrl) {
        String validFullName = ValidationUtil.validateFullName(fullName);
        String validEmail = ValidationUtil.validateEmail(email);
        String validPassword = ValidationUtil.validatePassword(password);

        if (userDao.existsByEmailIgnoreCase(validEmail)) {
            logger.info("Registration rejected: duplicate email ({})", maskEmail(validEmail));
            throw new DuplicateEmailException("An account with this email address already exists.");
        }

        String passwordHash = PasswordUtil.hash(validPassword);
        String baseUsername = deriveUsernameBase(validFullName);
        int suffixNumber = nextAvailableSuffix(baseUsername, 1);

        User saved = null;
        for (int attempt = 0; attempt < MAX_INSERT_RETRIES; attempt++) {
            String candidateUsername = (attempt < MAX_INSERT_RETRIES - 1)
                    ? applyUsernameSuffix(baseUsername, suffixNumber)
                    : applyRandomHexSuffix(baseUsername);

            User candidate = new User();
            candidate.setUsername(candidateUsername);
            candidate.setPasswordHash(passwordHash);
            candidate.setRole(UserRole.CUSTOMER);
            candidate.setFullName(validFullName);
            candidate.setEmail(validEmail);
            candidate.setActive(true);

            try {
                saved = userDao.createCustomer(candidate);
                break;
            } catch (DuplicateUsernameException race) {
                suffixNumber = nextAvailableSuffix(baseUsername, suffixNumber + 1);
            }
        }

        if (saved == null) {
            throw new IllegalStateException("Could not allocate a unique username after retries.");
        }

        logger.info("Customer registered: userId={} email={}",
                saved.getUserId(), maskEmail(saved.getEmail()));

        try {
            emailService.sendWelcome(saved, baseUrl);
        } catch (Exception e) {
            logger.warn("Welcome email trigger failed for userId={}: {}",
                    saved.getUserId(), e.getMessage());
        }

        return saved;
    }

    private int nextAvailableSuffix(String baseUsername, int startSuffix) {
        for (int s = Math.max(1, startSuffix); s < startSuffix + MAX_USERNAME_PROBES; s++) {
            String candidate = applyUsernameSuffix(baseUsername, s);
            if (!userDao.existsByUsernameIgnoreCase(candidate)) {
                return s;
            }
        }
        return startSuffix + MAX_USERNAME_PROBES;
    }

    static String deriveUsernameBase(String fullName) {
        String decomposed = Normalizer.normalize(fullName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String cleaned = decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "traveller";
        }
        if (cleaned.length() > MAX_USERNAME_LEN) {
            cleaned = cleaned.substring(0, MAX_USERNAME_LEN).replaceAll("\\.+$", "");
            if (cleaned.isEmpty()) {
                cleaned = "traveller";
            }
        }
        return cleaned;
    }

    static String applyUsernameSuffix(String base, int suffixNumber) {
        if (suffixNumber <= 1) {
            return base;
        }
        String suffix = Integer.toString(suffixNumber);
        int maxBaseLen = Math.max(1, MAX_USERNAME_LEN - suffix.length());
        String head = base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base;
        head = head.replaceAll("\\.+$", "");
        if (head.isEmpty()) {
            head = "u";
        }
        return head + suffix;
    }

    private static String applyRandomHexSuffix(String base) {
        String hex = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String sep = ".";
        int maxBaseLen = Math.max(1, MAX_USERNAME_LEN - sep.length() - hex.length());
        String head = base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base;
        head = head.replaceAll("\\.+$", "");
        if (head.isEmpty()) {
            head = "u";
        }
        return head + sep + hex;
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String trimmed = email.trim();
        int atIdx = trimmed.indexOf('@');
        if (atIdx <= 0 || atIdx == trimmed.length() - 1) {
            return AuthService.maskIdentifier(trimmed);
        }
        String local = trimmed.substring(0, atIdx);
        String domain = trimmed.substring(atIdx + 1);
        String maskedLocal = local.length() <= 2 ? local + "***" : local.substring(0, 2) + "***";
        return maskedLocal + "@" + domain;
    }
}
