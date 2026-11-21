package com.group2.flightres.dao;

import com.group2.flightres.model.User;

import java.util.Optional;

public interface UserDao {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameOrEmail(String identifier);

    default boolean existsByUsernameIgnoreCase(String username) {
        throw new UnsupportedOperationException("existsByUsernameIgnoreCase is not implemented");
    }

    default boolean existsByEmailIgnoreCase(String email) {
        throw new UnsupportedOperationException("existsByEmailIgnoreCase is not implemented");
    }

    default User createCustomer(User user) {
        throw new UnsupportedOperationException("createCustomer is not implemented");
    }
}

