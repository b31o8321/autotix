package dev.autotix.interfaces;

import dev.autotix.domain.AutotixException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler mapping domain exceptions to HTTP responses.
 */
@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(AutotixException.NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AutotixException.NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AutotixException.ValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(AutotixException.ValidationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AutotixException.ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(AutotixException.ConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AutotixException.AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AutotixException.AuthException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AutotixException.IntegrationException.class)
    public ResponseEntity<Map<String, String>> handleIntegration(AutotixException.IntegrationException e) {
        log.warn("Integration error on platform={}: {}", e.platform(), e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(AutotixException.class)
    public ResponseEntity<Map<String, String>> handleAutotix(AutotixException e) {
        log.error("Unhandled AutotixException: {}", e.getMessage(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, String>> handleUnsupported(UnsupportedOperationException e) {
        return error(HttpStatus.NOT_IMPLEMENTED, e.getMessage() != null ? e.getMessage() : "Not implemented");
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
