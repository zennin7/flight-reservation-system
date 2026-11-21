package com.group2.flightres.exception;

/**
 * Thrown when an external or simulated payment gateway fails to process a refund during cancellation.
 */
public class RefundFailedException extends RuntimeException {

    public RefundFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
