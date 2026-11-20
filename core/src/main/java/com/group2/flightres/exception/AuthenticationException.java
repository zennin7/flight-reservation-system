package com.group2.flightres.exception;

public class AuthenticationException extends RuntimeException {

    public enum AuthFailureReason {
        UNKNOWN_USER,
        BAD_PASSWORD,
        INACTIVE
    }

    private final AuthFailureReason reason;

    public AuthenticationException(String message) {
        this(message, null);
    }

    public AuthenticationException(String message, AuthFailureReason reason) {
        super(message);
        this.reason = reason;
    }

    public AuthFailureReason getReason() {
        return reason;
    }
}

