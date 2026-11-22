package com.group2.flightres.service;

import java.math.BigDecimal;

public interface PaymentService {
    PaymentResult processPayment(String cardNumber, String expiry, String cvv, BigDecimal amount);
    void refund(String reference);

    default String refundWithReference(String reference) {
        refund(reference);
        return "REF-" + reference;
    }
}
