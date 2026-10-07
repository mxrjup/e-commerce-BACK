package com.ecommerce.common.web;

import com.ecommerce.common.api.ErrorResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Translates exceptions into the shared {@link ErrorResponse} payload for every
 * service of the platform.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} keeps the status codes the
 * framework already decided: an unknown URL stays a 404, a wrong HTTP method a
 * 405, an unreadable body a 400. Only genuinely unexpected exceptions become a
 * 500, and their message is logged rather than sent to the client, so internal
 * details (SQL, table names, stack messages) never leave the service.
 *
 * <p>A service is free to declare its own {@code GlobalExceptionHandler} bean:
 * the auto-configuration then backs off. A service adding Spring Security must
 * also map {@code AccessDeniedException} explicitly, otherwise it would be
 * caught here as an unexpected error instead of a 403.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred";
    private static final String VALIDATION_ERROR_MESSAGE = "Validation failed for input data";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse body = errorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        LOGGER.error("Unexpected error while handling request", ex);
        ErrorResponse body = errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                 HttpHeaders headers,
                                                                 HttpStatusCode status,
                                                                 WebRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .toList();
        ErrorResponse body = errorResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_MESSAGE, details);
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
    }

    /**
     * Renders every other exception handled by the framework with our payload
     * instead of the default {@link ProblemDetail}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        String message = body instanceof ProblemDetail problemDetail && problemDetail.getDetail() != null
                ? problemDetail.getDetail()
                : reasonPhrase(statusCode);
        ErrorResponse payload = errorResponse(statusCode, message, null);
        return super.handleExceptionInternal(ex, payload, headers, statusCode, request);
    }

    private static ErrorResponse errorResponse(HttpStatusCode statusCode, String message, List<String> details) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(statusCode.value())
                .error(reasonPhrase(statusCode))
                .message(message != null ? message : reasonPhrase(statusCode))
                .details(details)
                .build();
    }

    private static String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.getReasonPhrase() : String.valueOf(statusCode.value());
    }
}
