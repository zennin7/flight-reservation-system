package com.group2.flightres.exception;

/**
 * Exception thrown when input data fails domain validation.
 */
public class ValidationException extends RuntimeException {

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
