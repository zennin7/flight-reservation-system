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
        assertTrue(hashed.startsWith("$2a$"));
        assertTrue(PasswordUtil.verify(raw, hashed));
        assertFalse(PasswordUtil.verify("WrongPass", hashed));
    }

    @Test
    @DisplayName("Verify normalizes $2b$ and $2y$ bcrypt hashes")
    void testVerify2bAnd2yHashes() {
        String hash2b = "$2b$12$Mxh/5oczFTWNSNOP.yx2JutGuTVdCxyCtDRg00QqZrkCTWl7xai42";
        String hash2y = "$2y$12$Mxh/5oczFTWNSNOP.yx2JutGuTVdCxyCtDRg00QqZrkCTWl7xai42";

        assertTrue(PasswordUtil.verify("Passw0rd!", hash2b));
        assertTrue(PasswordUtil.verify("Passw0rd!", hash2y));
        assertFalse(PasswordUtil.verify("WrongPass", hash2b));
    }

    @Test
    @DisplayName("Verify returns false without throwing on invalid or blank hashes")
    void testVerifyInvalidHashDoesNotThrow() {
        assertFalse(PasswordUtil.verify("Passw0rd!", "not-a-bcrypt-hash"));
        assertFalse(PasswordUtil.verify("Passw0rd!", "$2x$12$invalid"));
        assertFalse(PasswordUtil.verify("Passw0rd!", ""));
        assertFalse(PasswordUtil.verify("Passw0rd!", null));
        assertFalse(PasswordUtil.verify(null, "$2a$12$PbLU1N1783HPCUauaurDb.dhCCkT2YhRtGe5iFruRMvWXCUX16GxG"));
    }

    @Test
    @DisplayName("Both seeded auth hashes verify against Passw0rd!")
    void testSeededHashesVerify() {
        assertTrue(PasswordUtil.verify("Passw0rd!", "$2a$12$PbLU1N1783HPCUauaurDb.dhCCkT2YhRtGe5iFruRMvWXCUX16GxG"));
        assertTrue(PasswordUtil.verify("Passw0rd!", "$2a$12$wlq6HDixf4yVSCGKvxkjP.a9g1hpAErPgaUXUIIuovvqbFIV7mm8a"));
    }
}
