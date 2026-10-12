package com.lavs.business;

// Thrown when adding a co-borrower would put a loan over the limit of 3 co-borrowers
public class CoBorrowerLimitException extends RuntimeException {

    public CoBorrowerLimitException(String message) {
        super(message);
    }
}