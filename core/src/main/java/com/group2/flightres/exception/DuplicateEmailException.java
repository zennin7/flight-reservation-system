package com.group2.flightres.exception;

/**
 * Thrown when attempting to register with an email address already in use
 * (case-insensitive comparison).
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }

    public DuplicateEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
