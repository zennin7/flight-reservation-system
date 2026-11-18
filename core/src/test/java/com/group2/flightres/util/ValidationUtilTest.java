package com.group2.flightres.util;

import com.group2.flightres.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilTest {

    @Test
    @DisplayName("Valid passenger name passes and normalizes")
    void testValidPassengerName() {
        assertEquals("Jane Doe", ValidationUtil.validatePassengerName("  Jane Doe "));
        assertEquals("Jean-Luc Picard", ValidationUtil.validatePassengerName("Jean-Luc Picard"));
    }

    @Test
    @DisplayName("Invalid or empty passenger name throws ValidationException")
    void testInvalidPassengerName() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassengerName(""));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassengerName(null));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassengerName("SingleWord"));
    }

    @Test
    @DisplayName("Valid email formats pass validation")
    void testValidEmail() {
        assertEquals("jane.doe@example.com", ValidationUtil.validateEmail(" jane.doe@example.com "));
        assertEquals("admin@skyroyal.co.za", ValidationUtil.validateEmail("admin@skyroyal.co.za"));
    }

    @Test
    @DisplayName("Invalid email formats throw ValidationException")
    void testInvalidEmail() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail(""));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail("plainaddress"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail("missing@domain"));
    }

    @Test
    @DisplayName("Valid passport numbers are capitalized and verified")
    void testValidPassport() {
        assertEquals("PA123456", ValidationUtil.validatePassportNumber("pa123456"));
        assertEquals("B987654321", ValidationUtil.validatePassportNumber("B987654321"));
    }

    @Test
    @DisplayName("Invalid passport numbers throw ValidationException")
    void testInvalidPassport() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassportNumber(""));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassportNumber("123")); // too short
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassportNumber("P@$$PORT")); // invalid characters
    }

    @Test
    @DisplayName("Valid flight search criteria pass validation")
    void testValidFlightSearch() {
        assertDoesNotThrow(() -> ValidationUtil.validateFlightSearch(
                "Johannesburg (JNB)", "Cape Town (CPT)", LocalDate.of(2026, 11, 2)
        ));
    }

    @Test
    @DisplayName("Same origin and destination throws ValidationException")
    void testSameRouteThrows() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateFlightSearch(
                "Johannesburg (JNB)", "Johannesburg (JNB)", LocalDate.of(2026, 11, 2)
        ));
    }
}
