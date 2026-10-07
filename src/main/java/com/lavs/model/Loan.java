package com.lavs.model;

import java.time.LocalDate;

public class Loan {

    // Status tracks the loan's progress through the workflow
    public enum Status {
        NEW,
        PENDING
    }

    // Type defines the method of interest calculation and repayment
    public enum Type {
        TABLE_LOAN,
        REDUCING_BALANCE,
        INTEREST_ONLY
    }

    // Frequency defines how often payments are made and how often interest is calculated
    public enum Frequency {
        WEEKLY,
        FORTNIGHTLY,
        MONTHLY
    }

    private final String loanId;
    private final String customerId;
    private final Status status;
    private final double principal;
    private final double interestRate;
    private final Type type;
    private final boolean isFloating;
    private final LocalDate startDate;
    private final LocalDate renewDate;
    private final double paymentAmount;
    private final Frequency paymentFrequency;
    private final Frequency rateFrequency;
    private final int termMonths;

    public Loan(String loanId, String customerId, Status status, double principal,
            double interestRate, Type type, boolean isFloating, LocalDate startDate,
            LocalDate renewDate, double paymentAmount, Frequency paymentFrequency,
            Frequency rateFrequency, int termMonths) {
        // Basic structural checks
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalArgumentException("loanId must not be null or blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }
        if (principal <= 0) {
            throw new IllegalArgumentException("principal must be positive");
        }
        if (termMonths <= 0) {
            throw new IllegalArgumentException("termMonths must be positive");
        }

        // Assign all fields once the loan is confirmed valid
        this.loanId = loanId;
        this.customerId = customerId;
        this.status = status;
        this.principal = principal;
        this.interestRate = interestRate;
        this.type = type;
        this.isFloating = isFloating;
        this.startDate = startDate;
        this.renewDate = renewDate;
        this.paymentAmount = paymentAmount;
        this.paymentFrequency = paymentFrequency;
        this.rateFrequency = rateFrequency;
        this.termMonths = termMonths;
    }

    // Getters for all fields
    public String getLoanId() {
        return loanId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Status getStatus() {
        return status;
    }

    public double getPrincipal() {
        return principal;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public Type getType() {
        return type;
    }

    public boolean isFloating() {
        return isFloating;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getRenewDate() {
        return renewDate;
    }

    public double getPaymentAmount() {
        return paymentAmount;
    }

    public Frequency getPaymentFrequency() {
        return paymentFrequency;
    }

    public Frequency getRateFrequency() {
        return rateFrequency;
    }

    public int getTermMonths() {
        return termMonths;
    }
}
