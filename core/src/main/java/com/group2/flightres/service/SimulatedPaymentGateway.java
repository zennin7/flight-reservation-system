package com.group2.flightres.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SimulatedPaymentGateway implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedPaymentGateway.class);
    public static final String DECLINED_TEST_CARD = "4000000000000002";

    private final Set<String> refundedReferences = ConcurrentHashMap.newKeySet();

    @Override
    public PaymentResult processPayment(String cardNumber, String expiry, String cvv, BigDecimal amount) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return PaymentResult.declined("Card number is required", "UNKNOWN", "");
        }

        String sanitizedCard = cardNumber.replaceAll("\\s+", "");
        if (!sanitizedCard.matches("\\d{13,19}")) {
            return PaymentResult.declined("Card number must be 13 to 19 digits", "UNKNOWN", extractLast4(sanitizedCard));
        }

        String brand = detectBrand(sanitizedCard);
        String last4 = extractLast4(sanitizedCard);

        if (!isValidLuhn(sanitizedCard)) {
            return PaymentResult.declined("Card number failed Luhn checksum validation", brand, last4);
        }

        if (expiry == null || expiry.isBlank()) {
            return PaymentResult.declined("Expiry date is required", brand, last4);
        }

        if (!isValidExpiry(expiry)) {
            return PaymentResult.declined("Card has expired or invalid format (MM/YY)", brand, last4);
        }

        if (cvv == null || !cvv.trim().matches("\\d{3,4}")) {
            return PaymentResult.declined("CVV must be 3 or 4 digits", brand, last4);
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentResult.declined("Payment amount must be greater than zero", brand, last4);
        }

        // Test card specifically designated to decline: 4000 0000 0000 0002
        if (DECLINED_TEST_CARD.equals(sanitizedCard)) {
            logger.info("Simulated payment DECLINED for test card ending in {}", last4);
            return PaymentResult.declined("Card was declined by issuing bank (simulated test decline)", brand, last4);
        }

        String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        logger.info("Simulated payment APPROVED: brand={}, last4={}, ref={}", brand, last4, txnRef);
        return PaymentResult.approved(txnRef, brand, last4);
    }

    @Override
    public void refund(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Transaction reference is required for refund");
        }
        refundedReferences.add(reference);
        logger.info("Simulated refund processed for reference: {}", reference);
    }

    public boolean isRefunded(String reference) {
        return reference != null && refundedReferences.contains(reference);
    }

    public static boolean isValidLuhn(String number) {
        if (number == null || number.length() < 13 || number.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            char c = number.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            int n = c - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static boolean isValidExpiry(String expiryStr) {
        if (expiryStr == null) return false;
        String clean = expiryStr.trim().replace(" ", "");
        try {
            int slashIndex = clean.indexOf('/');
            if (slashIndex == -1) return false;
            String monthPart = clean.substring(0, slashIndex);
            String yearPart = clean.substring(slashIndex + 1);

            int month = Integer.parseInt(monthPart);
            if (month < 1 || month > 12) return false;

            int year = Integer.parseInt(yearPart);
            if (yearPart.length() == 2) {
                year += 2000;
            } else if (yearPart.length() != 4) {
                return false;
            }

            YearMonth cardYm = YearMonth.of(year, month);
            YearMonth nowYm = YearMonth.now();
            return !cardYm.isBefore(nowYm);
        } catch (Exception e) {
            return false;
        }
    }

    public static String detectBrand(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) return "CARD";
        if (cardNumber.startsWith("4")) return "VISA";
        if (cardNumber.matches("^(5[1-5]|2[2-7]).*")) return "MASTERCARD";
        if (cardNumber.startsWith("34") || cardNumber.startsWith("37")) return "AMEX";
        return "CARD";
    }

    private static String extractLast4(String cardNumber) {
        if (cardNumber == null) return "";
        return cardNumber.length() >= 4 ? cardNumber.substring(cardNumber.length() - 4) : cardNumber;
    }
}
