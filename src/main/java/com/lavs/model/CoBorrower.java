package com.lavs.model;

// Links an existing customer to a loan as a co-borrower.
public class CoBorrower {

    private final String coBorrowerId;
    private final String loanId;
    private final String customerId;

    public CoBorrower(String coBorrowerId, String loanId, String customerId) {
      // Basic structural checks only (3-co-borrower limit enforced in Business Logic Layer (LoanService))
        if (coBorrowerId != null && coBorrowerId.isBlank()) {
            throw new IllegalArgumentException("coBorrowerId must not be blank");
        }
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalArgumentException("loanId must not be null or blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }

        // Assign all fields once the co-borrower link is confirmed valid
        this.coBorrowerId = coBorrowerId;
        this.loanId = loanId;
        this.customerId = customerId;
    }
    
    // Getters for all fields
    public String getCoBorrowerId() {
        return coBorrowerId;
    }

    public String getLoanId() {
        return loanId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
