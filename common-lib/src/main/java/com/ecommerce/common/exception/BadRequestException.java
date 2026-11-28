package com.ecommerce.common.exception;

/**
 * Thrown when a request passes bean validation but still cannot be processed
 * as sent, and the client has to change it. Mapped to HTTP 400, with the
 * message in the response.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
