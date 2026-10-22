package com.lavs.business;

// Reports which customer record is missing before a loan can be created.
public class LoanPrerequisiteException extends RuntimeException {
    public LoanPrerequisiteException(String message) {
        super(message);
    }
}
