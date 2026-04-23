package com.deliveryroute.exception;

/**
 * Exception thrown when no route can be found between two cities
 */
public class RouteNotFoundException extends Exception {
    public RouteNotFoundException(String message) {
        super(message);
    }

    public RouteNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
