package com.ecommerce.mcp.auth;

import com.ecommerce.common.api.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/** A missing scope is a 403, not the 500 the shared catch-all would make of it. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpAccessDeniedHandler {

    @ExceptionHandler(McpAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handle(McpAccessDeniedException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getMessage())
                .build());
    }
}
