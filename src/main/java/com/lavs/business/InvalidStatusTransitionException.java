package com.lavs.business;

// Thrown when a loan's status is changed in a way the business rules don't
// allow (only New to Pending is allowed)
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}