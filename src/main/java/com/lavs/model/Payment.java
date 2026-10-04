package com.lavs.model;

import java.time.LocalDate;

public class Payment {

    private final String paymentId;
    private final String loanId;
    private final String repaymentId;
    private final LocalDate paymentDate;
    private final double amountPaid;

    public Payment(String paymentId, String loanId, String repaymentId, LocalDate paymentDate,
            double amountPaid) {
        // Basic structural checks
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId must not be null or blank");
        }
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalArgumentException("loanId must not be null or blank");
        }
        if (amountPaid <= 0) {
            throw new IllegalArgumentException("amountPaid must be positive");
        }

        // Assign all fields once the payment is confirmed valid
        this.paymentId = paymentId;
        this.loanId = loanId;
        this.repaymentId = repaymentId;
        this.paymentDate = paymentDate;
        this.amountPaid = amountPaid;
    }

    // Getters for all fields
    public String getPaymentId() {
        return paymentId;
    }

    public String getLoanId() {
        return loanId;
    }

    public String getRepaymentId() {
        return repaymentId;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public double getAmountPaid() {
        return amountPaid;
    }
}