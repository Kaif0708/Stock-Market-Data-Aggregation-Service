package com.stock.aggregator.exception;

/**
 * WHAT IS THIS?
 * -------------
 * A custom exception for when NO DATA is found for a query.
 * For example: user asks for stock "XYZ" but there's no data for "XYZ" in the database.
 *
 * When this is thrown, GlobalExceptionHandler catches it and returns 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);  // passes the error message to the parent Exception class
    }
}
