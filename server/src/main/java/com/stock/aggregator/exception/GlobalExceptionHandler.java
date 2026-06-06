package com.stock.aggregator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

/**
 * WHAT IS THIS CLASS?
 * -------------------
 * This is a GLOBAL EXCEPTION HANDLER. It catches exceptions thrown ANYWHERE
 * in our controllers and converts them into proper JSON error responses.
 *
 * Without this class, Spring would return ugly default error pages.
 * With this class, we return clean JSON like:
 * {
 *   "timestamp": "2024-01-15T09:15:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Unsupported timeframe '12m'"
 * }
 *
 * @ControllerAdvice = tells Spring "this class handles exceptions for ALL controllers"
 * @ExceptionHandler = marks a method to handle a SPECIFIC type of exception
 */
@ControllerAdvice
public class GlobalExceptionHandler {


    public static class ErrorResponse {
        private Instant timestamp;
        private int status;
        private String error;
        private String message;

        public ErrorResponse(Instant timestamp, int status, String error, String message) {
            this.timestamp = timestamp;
            this.status = status;
            this.error = error;
            this.message = message;
        }

        // Getters (needed for Jackson to convert this object to JSON)
        public Instant getTimestamp() { return timestamp; }
        public int getStatus() { return status; }
        public String getError() { return error; }
        public String getMessage() { return message; }
    }

    /**
     * Handles InvalidRequestException -> returns 400 Bad Request
     * Example: user sends timeframe "12m" which is not supported
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),          // 400
                HttpStatus.BAD_REQUEST.getReasonPhrase(), // "Bad Request"
                ex.getMessage()                           // our custom error message
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ResourceNotFoundException -> returns 404 Not Found
     * Example: no data found for stock symbol "INFY"
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),          // 404
                HttpStatus.NOT_FOUND.getReasonPhrase(), // "Not Found"
                ex.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles validation errors (e.g., from @Valid annotations) -> returns 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Collect all field error messages into one string
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles type mismatch (e.g., user sends text where a number is expected) -> returns 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid value for parameter: " + ex.getName()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Catches ALL OTHER unexpected exceptions -> returns 500 Internal Server Error
     * This is a safety net so the user never sees a raw stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),          // 500
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), // "Internal Server Error"
                ex.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
