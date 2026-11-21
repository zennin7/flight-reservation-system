package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.exception.DuplicateEmailException;
import com.group2.flightres.exception.DuplicateUsernameException;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

public class UserDaoImpl implements UserDao {

    private static final String FIND_BY_USERNAME_QUERY = """
        SELECT user_id, username, password_hash, role, full_name, email, is_active
        FROM users
        WHERE username = ?
        """;

    private static final String FIND_BY_USERNAME_OR_EMAIL_QUERY = """
        SELECT user_id, username, password_hash, role, full_name, email, is_active
        FROM users
        WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)
        """;

    private static final String EXISTS_BY_USERNAME_QUERY = """
        SELECT 1
        FROM users
        WHERE LOWER(username) = LOWER(?)
        LIMIT 1
        """;

    private static final String EXISTS_BY_EMAIL_QUERY = """
        SELECT 1
        FROM users
        WHERE LOWER(email) = LOWER(?)
        LIMIT 1
        """;

    private static final String INSERT_CUSTOMER_SQL = """
        INSERT INTO users (username, password_hash, role, full_name, email, is_active)
        VALUES (?, ?, 'CUSTOMER'::user_role, ?, ?, TRUE)
        RETURNING user_id
        """;

    @Override
    public Optional<User> findByUsername(String username) {
        return findOne(FIND_BY_USERNAME_QUERY, username, null);
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        return findOne(FIND_BY_USERNAME_OR_EMAIL_QUERY, trimmed, trimmed);
    }

    @Override
    public boolean existsByUsernameIgnoreCase(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return existsByLowerParam(EXISTS_BY_USERNAME_QUERY, username.trim());
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return existsByLowerParam(EXISTS_BY_EMAIL_QUERY, email.trim());
    }

    @Override
    public User createCustomer(User user) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_CUSTOMER_SQL)) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, user.getPasswordHash());
                stmt.setString(3, user.getFullName());
                stmt.setString(4, user.getEmail());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("INSERT into users did not return generated user_id");
                    }
                    long userId = rs.getLong("user_id");
                    conn.commit();

                    User saved = new User();
                    saved.setUserId(userId);
                    saved.setUsername(user.getUsername());
                    saved.setPasswordHash(user.getPasswordHash());
                    saved.setRole(UserRole.CUSTOMER);
                    saved.setFullName(user.getFullName());
                    saved.setEmail(user.getEmail());
                    saved.setActive(true);
                    return saved;
                }
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                if ("23505".equals(e.getSQLState())) {
                    String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
                    if (msg.contains("email")) {
                        throw new DuplicateEmailException("An account with this email address already exists.", e);
                    }
                    throw new DuplicateUsernameException("Generated username already exists.", e);
                }
                throw new RuntimeException("Failed to create customer account", e);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to obtain database connection for user registration", e);
        }
    }

    private boolean existsByLowerParam(String sql, String value) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check user existence", e);
        }
    }

    private Optional<User> findOne(String sql, String firstParam, String secondParam) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, firstParam);
            if (secondParam != null) {
                stmt.setString(2, secondParam);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getLong("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setRole(UserRole.valueOf(rs.getString("role")));
                    user.setFullName(rs.getString("full_name"));
                    user.setEmail(rs.getString("email"));
                    user.setActive(rs.getBoolean("is_active"));
                    return Optional.of(user);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to look up user " + firstParam, e);
        }
    }
}

