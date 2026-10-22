package com.lavs.business;

// Reports that the loan exists but a later part of the submission failed.
/** A loan was created on the mainframe but could not be completed (e.g. a co-borrower failed). */
public class LoanSubmissionException extends RuntimeException {

    public LoanSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
