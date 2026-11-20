package com.group2.flightres.service;

import com.group2.flightres.dao.UserDao;
import com.group2.flightres.dao.impl.UserDaoImpl;
import com.group2.flightres.exception.AuthenticationException;
import com.group2.flightres.exception.AuthenticationException.AuthFailureReason;
import com.group2.flightres.model.User;
import com.group2.flightres.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserDao userDao;

    public AuthService() {
        this(new UserDaoImpl());
    }

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    public User authenticate(String username, String password) {
        Optional<User> userOpt = userDao.findByUsernameOrEmail(username);

        if (userOpt.isEmpty()) {
            logger.info("Login failed: reason={} identifier={}", AuthFailureReason.UNKNOWN_USER, maskIdentifier(username));
            throw new AuthenticationException("Incorrect username or password.", AuthFailureReason.UNKNOWN_USER);
        }

        User user = userOpt.get();

        if (!user.isActive()) {
            logger.info("Login failed: reason={} identifier={}", AuthFailureReason.INACTIVE, maskIdentifier(username));
            throw new AuthenticationException("This account has been deactivated.", AuthFailureReason.INACTIVE);
        }

        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            logger.info("Login failed: reason={} identifier={}", AuthFailureReason.BAD_PASSWORD, maskIdentifier(username));
            throw new AuthenticationException("Incorrect username or password.", AuthFailureReason.BAD_PASSWORD);
        }

        return user;
    }

    public boolean userExists(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userDao.findByUsername(username.trim()).isPresent();
    }

    public static String maskIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "***";
        }
        String trimmed = identifier.trim();
        if (trimmed.length() <= 2) {
            return trimmed + "***";
        }
        return trimmed.substring(0, 2) + "***";
    }
}

