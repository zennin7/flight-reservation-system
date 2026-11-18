package com.group2.flightres.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedPaymentGatewayTest {

    private SimulatedPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SimulatedPaymentGateway();
    }

    @Test
    @DisplayName("Luhn checksum validation for valid and invalid card numbers")
    void testLuhnChecksum() {
        // Valid cards
        assertTrue(SimulatedPaymentGateway.isValidLuhn("4242424242424242"));
        assertTrue(SimulatedPaymentGateway.isValidLuhn("4000000000000002"));
        assertTrue(SimulatedPaymentGateway.isValidLuhn("378282246310005")); // AMEX 15-digit

        // Invalid cards
        assertFalse(SimulatedPaymentGateway.isValidLuhn("4242424242424243"));
        assertFalse(SimulatedPaymentGateway.isValidLuhn("1234567812345671"));
        assertFalse(SimulatedPaymentGateway.isValidLuhn(""));
        assertFalse(SimulatedPaymentGateway.isValidLuhn(null));
        assertFalse(SimulatedPaymentGateway.isValidLuhn("abcd"));
    }

    @Test
    @DisplayName("Card expiry validation: past dates rejected, future dates accepted")
    void testExpiryValidation() {
        // Future dates
        assertTrue(SimulatedPaymentGateway.isValidExpiry("12/30"));
        assertTrue(SimulatedPaymentGateway.isValidExpiry("08 / 28"));
        assertTrue(SimulatedPaymentGateway.isValidExpiry("01/2035"));

        // Past dates
        assertFalse(SimulatedPaymentGateway.isValidExpiry("01/20"));
        assertFalse(SimulatedPaymentGateway.isValidExpiry("12/23"));
        assertFalse(SimulatedPaymentGateway.isValidExpiry("05/1998"));

        // Malformed inputs
        assertFalse(SimulatedPaymentGateway.isValidExpiry(""));
        assertFalse(SimulatedPaymentGateway.isValidExpiry(null));
        assertFalse(SimulatedPaymentGateway.isValidExpiry("13/30")); // Invalid month
        assertFalse(SimulatedPaymentGateway.isValidExpiry("00/30"));
        assertFalse(SimulatedPaymentGateway.isValidExpiry("invalid"));
    }

    @Test
    @DisplayName("Specific test card 4000 0000 0000 0002 is always declined")
    void testDeclineCard() {
        PaymentResult result = gateway.processPayment(
                "4000 0000 0000 0002",
                "08/28",
                "123",
                new BigDecimal("1522.00")
        );

        assertFalse(result.isApproved(), "Card 4000 0000 0000 0002 must always be declined");
        assertEquals("VISA", result.getCardBrand());
        assertEquals("0002", result.getCardLast4());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("declined"));
    }

    @Test
    @DisplayName("Valid test card 4242 4242 4242 4242 is approved")
    void testApproveCard() {
        PaymentResult result = gateway.processPayment(
                "4242 4242 4242 4242",
                "08/28",
                "123",
                new BigDecimal("1522.00")
        );

        assertTrue(result.isApproved(), "Card 4242 4242 4242 4242 must be approved");
        assertEquals("VISA", result.getCardBrand());
        assertEquals("4242", result.getCardLast4());
        assertNotNull(result.getTransactionReference());
        assertTrue(result.getTransactionReference().startsWith("TXN-"));
    }

    @Test
    @DisplayName("Invalid CVV format is rejected")
    void testInvalidCvv() {
        PaymentResult result = gateway.processPayment(
                "4242 4242 4242 4242",
                "08/28",
                "12", // too short
                new BigDecimal("1522.00")
        );

        assertFalse(result.isApproved());
        assertTrue(result.getErrorMessage().contains("CVV"));
    }

    @Test
    @DisplayName("Refund records transaction reference as refunded")
    void testRefund() {
        PaymentResult result = gateway.processPayment(
                "4242 4242 4242 4242",
                "08/28",
                "123",
                new BigDecimal("1522.00")
        );
        assertTrue(result.isApproved());
        String ref = result.getTransactionReference();
        assertNotNull(ref);
        assertFalse(gateway.isRefunded(ref));

        gateway.refund(ref);
        assertTrue(gateway.isRefunded(ref));
    }
}
