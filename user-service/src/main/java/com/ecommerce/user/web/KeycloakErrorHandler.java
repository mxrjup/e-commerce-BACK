package com.ecommerce.user.web;

import com.ecommerce.common.api.ErrorResponse;
import com.ecommerce.user.keycloak.KeycloakUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * A failing Keycloak is answered with 502 rather than the 500 of the shared
 * handler: the back-office can tell the user to retry, not report a bug.
 * Ordered first so the shared catch-all never sees this exception.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KeycloakErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakErrorHandler.class);

    @ExceptionHandler(KeycloakUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakUnavailable(KeycloakUnavailableException ex) {
        LOGGER.error("Keycloak call failed", ex);
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message("The identity provider is unavailable, please retry later")
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
