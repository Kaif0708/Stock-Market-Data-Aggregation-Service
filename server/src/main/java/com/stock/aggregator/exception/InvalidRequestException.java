package com.stock.aggregator.exception;

/**
 * WHAT IS THIS?
 * -------------
 * A custom exception class for INVALID USER INPUT.
 * For example: user sends an unsupported timeframe like "12m", or missing parameters.
 *
 * WHY CUSTOM EXCEPTIONS?
 * ----------------------
 * Instead of returning error messages manually, we THROW this exception.
 * Then our GlobalExceptionHandler catches it and returns a proper 400 Bad Request response.
 *
 * RuntimeException = an unchecked exception (we don't need to add "throws" everywhere)
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);  // passes the error message to the parent Exception class
    }
}
