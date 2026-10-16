package com.lavs.business;

/** A loan was created on the mainframe but could not be completed (e.g. a co-borrower failed). */
public class LoanSubmissionException extends RuntimeException {

    public LoanSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}