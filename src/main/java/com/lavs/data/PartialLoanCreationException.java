package com.lavs.data;

/** A loan received an ID before a later creation step failed. */
public class PartialLoanCreationException extends RuntimeException {
    private final String loanId;
    private final boolean cancelled;

    public PartialLoanCreationException(
            String loanId, boolean cancelled, Throwable cause, Throwable cancellationFailure) {
        super(message(loanId, cancelled), cause);
        this.loanId = loanId;
        this.cancelled = cancelled;
        if (cancellationFailure != null) {
            addSuppressed(cancellationFailure);
        }
    }

    public String getLoanId() {
        return loanId;
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    private static String message(String loanId, boolean cancelled) {
        if (cancelled) {
            return "Loan " + loanId + " was created but could not be completed, so it has "
                    + "been cancelled. You can safely try again.";
        }
        return "Loan " + loanId + " was created but could not be completed or cancelled. "
                + "Check this loan on the mainframe before retrying.";
    }
}
