package com.group2.flightres.exception;

/**
 * Thrown when a generated username collides on database insert so the
 * registration service can retry with the next numeric suffix.
 */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String message, Throwable cause) {
        super(message, cause);
    }
}
