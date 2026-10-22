package com.lavs.model;

import java.time.LocalDate;

public class Loan {

    public enum Status {
        NEW,
        PENDING,
        ACTIVE,
        DEFAULTED,
        CANCELLED
    }

    public enum Type {
        TABLE_LOAN("Table Loan"),
        REDUCING_BALANCE("Reducing Balance"),
        INTEREST_ONLY("Interest Only");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        // AI-assisted: Claude added readable loan type labels (displayName and fromDisplayName).
        /** The name shown to users, for example "Reducing Balance". */
        public String displayName() {
            return displayName;
        }

        /** The type with this display name, or an IllegalArgumentException if there is none. */
        public static Type fromDisplayName(String displayName) {
            for (Type type : values()) {
                if (type.displayName.equals(displayName)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown loan type: " + displayName);
        }
    }

    // How often interest is calculated (matches the mainframe's options)
    public enum RateFrequency {
        DAILY,
        WEEKLY,
        FORTNIGHTLY,
        MONTHLY,
        YEARLY
    }

    // How often the customer pays. Separate from RateFrequency because
    // ON_RENEWAL only makes sense for payments
    public enum PaymentFrequency {
        WEEKLY,
        FORTNIGHTLY,
        MONTHLY,
        YEARLY,
        ON_RENEWAL
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
    private final PaymentFrequency paymentFrequency;
    private final RateFrequency rateFrequency;
    private final Integer termMonths;

    public Loan(String loanId, String customerId, Status status, double principal,
            double interestRate, Type type, boolean isFloating, LocalDate startDate,
            LocalDate renewDate, double paymentAmount, PaymentFrequency paymentFrequency,
            RateFrequency rateFrequency, Integer termMonths) {
        // Basic structural checks only 
        if (loanId != null && loanId.isBlank()) {
            throw new IllegalArgumentException("loanId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }
        if (principal <= 0) {
            throw new IllegalArgumentException("principal must be positive");
        }
        if (termMonths != null && termMonths <= 0) {
            throw new IllegalArgumentException("termMonths must be positive when known");
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

    // Getters
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

    public PaymentFrequency getPaymentFrequency() {
        return paymentFrequency;
    }

    public RateFrequency getRateFrequency() {
        return rateFrequency;
    }

    public Integer getTermMonths() {
        return termMonths;
    }
}
