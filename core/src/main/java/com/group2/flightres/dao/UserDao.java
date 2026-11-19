package com.group2.flightres.dao;

import com.group2.flightres.model.User;

import java.util.Optional;

public interface UserDao {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameOrEmail(String identifier);
}

