package com.group2.flightres.service;

import com.group2.flightres.dao.UserDao;
import com.group2.flightres.exception.AuthenticationException;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    private User activeCustomer;
    private User inactiveUser;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        activeCustomer = new User();
        activeCustomer.setUserId(2L);
        activeCustomer.setUsername("customer");
        activeCustomer.setEmail("customer@example.com");
        activeCustomer.setFullName("Demo Customer");
        activeCustomer.setRole(UserRole.CUSTOMER);
        activeCustomer.setActive(true);
        activeCustomer.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        inactiveUser = new User();
        inactiveUser.setUserId(3L);
        inactiveUser.setUsername("disabled");
        inactiveUser.setEmail("disabled@example.com");
        inactiveUser.setFullName("Disabled User");
        inactiveUser.setRole(UserRole.CUSTOMER);
        inactiveUser.setActive(false);
        inactiveUser.setPasswordHash(PasswordUtil.hash("Passw0rd!"));

        List<User> users = List.of(activeCustomer, inactiveUser);
        UserDao stubDao = new UserDao() {
            @Override
            public Optional<User> findByUsername(String username) {
                if (username == null) return Optional.empty();
                return users.stream().filter(u -> u.getUsername().equals(username)).findFirst();
            }

            @Override
            public Optional<User> findByUsernameOrEmail(String identifier) {
                if (identifier == null || identifier.isBlank()) return Optional.empty();
                String trimmed = identifier.trim();
                return users.stream()
                        .filter(u -> u.getUsername().equalsIgnoreCase(trimmed)
                                || (u.getEmail() != null && u.getEmail().equalsIgnoreCase(trimmed)))
                        .findFirst();
            }
        };
        authService = new AuthService(stubDao);
    }

    @Test
    @DisplayName("Username login succeeds")
    void testUsernameLogin() {
        User loggedIn = authService.authenticate("customer", "Passw0rd!");
        assertEquals("customer", loggedIn.getUsername());
    }

    @Test
    @DisplayName("Mixed-case email login with surrounding whitespace succeeds")
    void testMixedCaseEmailLogin() {
        User loggedIn = authService.authenticate("  Customer@Example.COM  ", "Passw0rd!");
        assertEquals("customer", loggedIn.getUsername());
    }

    @Test
    @DisplayName("Wrong password throws same generic AuthenticationException")
    void testWrongPassword() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authService.authenticate("customer", "WrongPass!"));
        assertEquals("Incorrect username or password.", ex.getMessage());
    }

    @Test
    @DisplayName("Unknown user throws same generic AuthenticationException")
    void testUnknownUser() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authService.authenticate("nobody@example.com", "Passw0rd!"));
        assertEquals("Incorrect username or password.", ex.getMessage());
    }

    @Test
    @DisplayName("Inactive user throws deactivated message")
    void testInactiveUser() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authService.authenticate("disabled", "Passw0rd!"));
        assertEquals("This account has been deactivated.", ex.getMessage());
    }
}
