package com.group2.flightres.service;

import com.group2.flightres.dao.UserDao;
import com.group2.flightres.dao.impl.UserDaoImpl;
import com.group2.flightres.exception.AuthenticationException;
import com.group2.flightres.model.User;
import com.group2.flightres.util.PasswordUtil;

import java.util.Optional;

public class AuthService {

    private final UserDao userDao = new UserDaoImpl();

    public User authenticate(String username, String password) {
        Optional<User> userOpt = userDao.findByUsername(username);

        if (userOpt.isEmpty()) {
            throw new AuthenticationException("Incorrect username or password.");
        }

        User user = userOpt.get();

        if (!user.isActive()) {
            throw new AuthenticationException("This account has been deactivated.");
        }

        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            throw new AuthenticationException("Incorrect username or password.");
        }

        return user;
    }
}
