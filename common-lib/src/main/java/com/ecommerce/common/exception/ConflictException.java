package com.ecommerce.common.exception;

/**
 * Thrown when a request is valid but clashes with the current state of the
 * data: a name already taken, a resource still in use. Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
