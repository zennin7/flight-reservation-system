package com.group2.flightres.util;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PasswordUtil {

    private static final Logger logger = LoggerFactory.getLogger(PasswordUtil.class);

    private PasswordUtil() {}

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    public static boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        String normalized = hashedPassword;
        if (normalized.startsWith("$2b$") || normalized.startsWith("$2y$")) {
            normalized = "$2a$" + normalized.substring(4);
        }
        try {
            return BCrypt.checkpw(plainPassword, normalized);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid password hash format encountered during verification: {}", e.getMessage());
            return false;
        }
    }
}

