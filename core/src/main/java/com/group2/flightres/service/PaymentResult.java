package com.group2.flightres.service;

public class PaymentResult {
    private final boolean approved;
    private final String transactionReference;
    private final String cardBrand;
    private final String cardLast4;
    private final String errorMessage;

    public PaymentResult(boolean approved, String transactionReference, String cardBrand, String cardLast4, String errorMessage) {
        this.approved = approved;
        this.transactionReference = transactionReference;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.errorMessage = errorMessage;
    }

    public static PaymentResult approved(String transactionReference, String cardBrand, String cardLast4) {
        return new PaymentResult(true, transactionReference, cardBrand, cardLast4, null);
    }

    public static PaymentResult declined(String errorMessage, String cardBrand, String cardLast4) {
        return new PaymentResult(false, null, cardBrand, cardLast4, errorMessage);
    }

    public boolean isApproved() { return approved; }
    public String getTransactionReference() { return transactionReference; }
    public String getCardBrand() { return cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public String getErrorMessage() { return errorMessage; }
}
