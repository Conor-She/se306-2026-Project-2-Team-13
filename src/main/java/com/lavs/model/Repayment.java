package com.lavs.model;

import java.time.LocalDate;

public class Repayment {

    private final String repaymentId;
    private final String loanId;
    private final LocalDate dueDate;
    private final double amountDue;
    private final double principalComponent;
    private final double interestComponent;
    private final double remainingBalance;

    public Repayment(String repaymentId, String loanId, LocalDate dueDate, double amountDue,
            double principalComponent, double interestComponent, double remainingBalance) {
        if (repaymentId == null || repaymentId.isBlank()) {
            throw new IllegalArgumentException("repaymentId must not be null or blank");
        }
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalArgumentException("loanId must not be null or blank");
        }
        if (amountDue < 0) {
            throw new IllegalArgumentException("amountDue must not be negative");
        }
        if (remainingBalance < 0) {
            throw new IllegalArgumentException("remainingBalance must not be negative");
        }

        this.repaymentId = repaymentId;
        this.loanId = loanId;
        this.dueDate = dueDate;
        this.amountDue = amountDue;
        this.principalComponent = principalComponent;
        this.interestComponent = interestComponent;
        this.remainingBalance = remainingBalance;
    }

    public String getRepaymentId() {
        return repaymentId;
    }

    public String getLoanId() {
        return loanId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public double getAmountDue() {
        return amountDue;
    }

    public double getPrincipalComponent() {
        return principalComponent;
    }

    public double getInterestComponent() {
        return interestComponent;
    }

    public double getRemainingBalance() {
        return remainingBalance;
    }
}