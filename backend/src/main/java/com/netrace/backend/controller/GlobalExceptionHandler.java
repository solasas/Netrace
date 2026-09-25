package com.netrace.backend.controller;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.dto.ErrorResponse;
import com.netrace.backend.service.InvalidUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central place mapping every exception that can escape a controller to a
 * consistent {timestamp, status, error, message} body. Messages sent to the
 * client are ones this class or our own exceptions authored deliberately;
 * for anything unanticipated, the real exception is logged server-side and
 * only a generic message is returned, so internal details never leak.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request");
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Malformed request body");
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_URL", e.getMessage());
    }

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisFailure(AnalysisException e) {
        HttpStatus status = switch (e.reason()) {
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case DNS_FAILURE, CONNECTION_FAILURE, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case BLOCKED_TARGET -> HttpStatus.BAD_REQUEST;
        };
        // The cause (with its raw, possibly OS-specific message) is logged
        // here for operators; only our own crafted e.getMessage() ever
        // reaches the client.
        log.warn("Analysis failed with reason {}: {}", e.reason(), e.getMessage(), e);
        return errorResponse(status, e.reason().name(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError(Exception e) {
        log.error("Unexpected error while handling a request", e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "ANALYSIS_FAILED",
                "An unexpected error occurred while analyzing the URL");
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), error, message));
    }
}
