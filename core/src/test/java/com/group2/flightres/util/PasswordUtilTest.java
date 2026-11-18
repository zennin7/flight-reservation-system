package com.group2.flightres.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    @Test
    @DisplayName("Hash password produces non-null bcrypt hash and verifies correctly")
    void testHashAndCheckPassword() {
        String raw = "AdminPass2026";
        String hashed = PasswordUtil.hash(raw);

        assertNotNull(hashed);
        assertTrue(PasswordUtil.verify(raw, hashed));
        assertFalse(PasswordUtil.verify("WrongPass", hashed));
    }
}
