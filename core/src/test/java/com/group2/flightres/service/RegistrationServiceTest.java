package com.group2.flightres.service;

import com.group2.flightres.dao.UserDao;
import com.group2.flightres.exception.DuplicateEmailException;
import com.group2.flightres.exception.DuplicateUsernameException;
import com.group2.flightres.exception.ValidationException;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationServiceTest {

    private static final class InMemoryUserDao implements UserDao {
        private final Map<String, User> byUsername = new HashMap<>();
        private final Map<String, User> byEmailLower = new HashMap<>();
        private final AtomicInteger seq = new AtomicInteger(1);
        private boolean failFirstUsernameOnce = false;

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(byUsername.get(username));
        }

        @Override
        public Optional<User> findByUsernameOrEmail(String identifier) {
            if (identifier == null) return Optional.empty();
            String t = identifier.trim().toLowerCase(Locale.ROOT);
            if (byEmailLower.containsKey(t)) return Optional.of(byEmailLower.get(t));
            return byUsername.values().stream()
                    .filter(u -> u.getUsername().equalsIgnoreCase(t))
                    .findFirst();
        }

        @Override
        public boolean existsByUsernameIgnoreCase(String username) {
            return byUsername.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(username));
        }

        @Override
        public boolean existsByEmailIgnoreCase(String email) {
            return email != null && byEmailLower.containsKey(email.trim().toLowerCase(Locale.ROOT));
        }

        @Override
        public User createCustomer(User user) {
            String lowerEmail = user.getEmail().toLowerCase(Locale.ROOT);
            if (byEmailLower.containsKey(lowerEmail)) {
                throw new DuplicateEmailException("An account with this email address already exists.");
            }
            if (failFirstUsernameOnce) {
                failFirstUsernameOnce = false;
                throw new DuplicateUsernameException("Simulated concurrent collision", null);
            }
            if (existsByUsernameIgnoreCase(user.getUsername())) {
                throw new DuplicateUsernameException("Username collision", null);
            }
            User saved = new User();
            saved.setUserId(seq.getAndIncrement());
            saved.setUsername(user.getUsername());
            saved.setPasswordHash(user.getPasswordHash());
            saved.setRole(UserRole.CUSTOMER);
            saved.setFullName(user.getFullName());
            saved.setEmail(user.getEmail());
            saved.setActive(true);
            byUsername.put(saved.getUsername(), saved);
            byEmailLower.put(lowerEmail, saved);
            return saved;
        }
    }

    @Test
    @DisplayName("Registers a customer with BCrypt-12 hash, CUSTOMER role, generated username, and welcome trigger")
    void registersCustomerAndSendsWelcome() {
        InMemoryUserDao dao = new InMemoryUserDao();
        List<String> welcomed = new ArrayList<>();
        EmailService emailService = (user, baseUrl) -> welcomed.add(user.getUsername() + "@" + baseUrl);
        RegistrationService service = new RegistrationService(dao, emailService);

        User saved = service.register("  Zoë   Müller ", "zoe@example.com", "StrongPass12", "http://localhost:8080");

        assertEquals(1L, saved.getUserId());
        assertEquals("Zoë Müller", saved.getFullName());
        assertEquals("zoe@example.com", saved.getEmail());
        assertEquals("zoe.muller", saved.getUsername());
        assertEquals(UserRole.CUSTOMER, saved.getRole());
        assertTrue(saved.isActive());
        assertTrue(saved.getPasswordHash().startsWith("$2a$12$"));
        assertTrue(PasswordUtil.verify("StrongPass12", saved.getPasswordHash()));
        assertEquals(List.of("zoe.muller@http://localhost:8080"), welcomed);
    }

    @Test
    @DisplayName("Deduplicates colliding generated usernames with numeric suffix <= 30 chars")
    void deduplicatesUsernamesAndTruncatesLongNames() {
        InMemoryUserDao dao = new InMemoryUserDao();
        RegistrationService service = new RegistrationService(dao);

        String longName = "Bartholomew Maximillian Featherstonehaugh";
        User u1 = service.register(longName, "one@example.com", "StrongPass12");
        User u2 = service.register(longName, "two@example.com", "StrongPass12");
        dao.failFirstUsernameOnce = true;
        User u3 = service.register(longName, "three@example.com", "StrongPass12");

        assertTrue(u1.getUsername().length() <= 30);
        assertTrue(u2.getUsername().length() <= 30);
        assertTrue(u3.getUsername().length() <= 30);
        assertTrue(u1.getUsername().matches("^[a-z0-9.]+$"));
        assertTrue(u2.getUsername().matches("^[a-z0-9.]+2$"));
        assertTrue(u3.getUsername().matches("^[a-z0-9.]+4$"));
        assertNotEquals(u1.getUsername(), u2.getUsername());
        assertNotEquals(u2.getUsername(), u3.getUsername());
    }

    @Test
    @DisplayName("Rejects duplicate email case-insensitively")
    void rejectsDuplicateEmailIgnoreCase() {
        InMemoryUserDao dao = new InMemoryUserDao();
        RegistrationService service = new RegistrationService(dao);

        service.register("Thabo Mbeki", "Thabo@Example.com", "StrongPass12");
        assertThrows(DuplicateEmailException.class,
                () -> service.register("Thabo Two", "thabo@example.com", "StrongPass12"));
    }

    @Test
    @DisplayName("Validates full name (first+last), email format, and password policy (10+ chars, upper, lower, digit)")
    void validatesInputs() {
        InMemoryUserDao dao = new InMemoryUserDao();
        RegistrationService service = new RegistrationService(dao);

        assertEquals("fullName",
                assertThrows(ValidationException.class, () -> service.register(" ", "a@example.com", "StrongPass12")).getField());
        assertEquals("fullName",
                assertThrows(ValidationException.class, () -> service.register("Cher", "a@example.com", "StrongPass12")).getField());
        assertEquals("fullName",
                assertThrows(ValidationException.class, () -> service.register("<script>alert(1)</script>", "a@example.com", "StrongPass12")).getField());
        assertEquals("email",
                assertThrows(ValidationException.class, () -> service.register("Thabo Mbeki", "not-an-email", "StrongPass12")).getField());
        assertEquals("password",
                assertThrows(ValidationException.class, () -> service.register("Thabo Mbeki", "a@example.com", "Short1A")).getField());
        assertEquals("password",
                assertThrows(ValidationException.class, () -> service.register("Thabo Mbeki", "a@example.com", "alllowercase1")).getField());
        assertEquals("password",
                assertThrows(ValidationException.class, () -> service.register("Thabo Mbeki", "a@example.com", "ALLUPPERCASE1")).getField());
        assertEquals("password",
                assertThrows(ValidationException.class, () -> service.register("Thabo Mbeki", "a@example.com", "NoDigitsHere")).getField());
    }

    @Test
    @DisplayName("Registration still succeeds even if welcome EmailService throws synchronously")
    void survivesEmailServiceException() {
        InMemoryUserDao dao = new InMemoryUserDao();
        EmailService failing = (user, baseUrl) -> {
            throw new IllegalStateException("SMTP boom");
        };
        RegistrationService service = new RegistrationService(dao, failing);

        User saved = service.register("Naledi Sithole", "naledi@example.com", "StrongPass12");
        assertEquals("naledi.sithole", saved.getUsername());
    }
}
