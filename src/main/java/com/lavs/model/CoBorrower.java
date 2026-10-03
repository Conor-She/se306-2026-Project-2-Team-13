package com.lavs.model;

public class CoBorrower {

    private final String coBorrowerId;
    private final String loanId;
    private final String customerId;

    public CoBorrower(String coBorrowerId, String loanId, String customerId) {
        if (coBorrowerId == null || coBorrowerId.isBlank()) {
            throw new IllegalArgumentException("coBorrowerId must not be null or blank");
        }
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalArgumentException("loanId must not be null or blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }

        this.coBorrowerId = coBorrowerId;
        this.loanId = loanId;
        this.customerId = customerId;
    }

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