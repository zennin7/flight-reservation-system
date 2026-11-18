package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserDaoImpl implements UserDao {

    private static final String FIND_BY_USERNAME_QUERY = """
        SELECT user_id, username, password_hash, role, full_name, email, is_active
        FROM users
        WHERE username = ?
        """;

    @Override
    public Optional<User> findByUsername(String username) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_USERNAME_QUERY)) {

            stmt.setString(1, username);

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
            throw new RuntimeException("Failed to look up user " + username, e);
        }
    }
}

