package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.Payment;
import com.lavs.model.Repayment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Business logic for recording, editing and deleting payments against a loan,
// and for working out whether each scheduled repayment has been met
// Depends only on the MainframeGateway interface.
public class PaymentService {

    static final String DATE_REQUIRED_MESSAGE = "A payment must have a payment date";
    static final String REPAYMENT_NOT_ON_LOAN_MESSAGE =
            "The repayment a payment is linked to must be one of this loan's repayments";
    static final String EXISTING_PAYMENT_REQUIRED_MESSAGE =
            "An existing payment with a paymentId is required";
    static final String PAYMENT_NOT_ON_LOAN_MESSAGE = "That payment was not found on this loan";
    static final String STATUS_INPUTS_MESSAGE =
            "A repayment, its payments and the date to judge them on are all required";
    static final String AS_OF_REQUIRED_MESSAGE =
            "The date to judge the repayments on is required";

    private final MainframeGateway gateway;

    public PaymentService(MainframeGateway gateway) {
        this.gateway = gateway;
    }

    // Records a payment against a loan. It can be linked to one of the loan's
    // scheduled repayments, or left unlinked (e.g. an extra lump sum).
    public Payment addPayment(Payment payment) {
        requireValid(payment);
        return gateway.addPayment(payment);
    }

    // Edits a payment that is already on the loan. Checking it is on that loan
    // also stops an edit moving a payment to a different loan.
    public Payment updatePayment(Payment payment) {
        if (payment != null && payment.getPaymentId() == null) {
            throw new IllegalArgumentException(EXISTING_PAYMENT_REQUIRED_MESSAGE);
        }
        requireValid(payment);

        boolean isOnThatLoan = gateway.getPayments(payment.getLoanId()).stream()
                .anyMatch(existing -> payment.getPaymentId().equals(existing.getPaymentId()));
        if (!isOnThatLoan) {
            throw new IllegalArgumentException(PAYMENT_NOT_ON_LOAN_MESSAGE);
        }

        return gateway.updatePayment(payment);
    }

    public void deletePayment(String loanId, String paymentId) {
        requireNotBlank(loanId, "loanId");
        requireNotBlank(paymentId, "paymentId");
        gateway.deletePayment(loanId, paymentId);
    }

    // All the payments made against a loan, linked to a repayment or not
    public List<Payment> getPayments(String loanId) {
        requireNotBlank(loanId, "loanId");
        gateway.getLoan(loanId);
        return List.copyOf(gateway.getPayments(loanId));
    }

    // Compares what has been paid towards one repayment with what was due.
    // Only payments linked to this repayment count, and they are added up
    public RepaymentStatus statusOf(
            Repayment repayment, List<Payment> payments, LocalDate asOf) {
        if (repayment == null || payments == null || asOf == null) {
            throw new IllegalArgumentException(STATUS_INPUTS_MESSAGE);
        }

        BigDecimal due = toMoney(repayment.getAmountDue());
        BigDecimal paid = payments.stream()
                .filter(payment -> repayment.getRepaymentId().equals(payment.getRepaymentId()))
                .map(payment -> toMoney(payment.getAmountPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (paid.signum() == 0) {
            if (due.signum() == 0) {
                // Nothing was owed, so nothing can be missed or coming up
                return RepaymentStatus.ON_TIME;
            }
            return asOf.isAfter(repayment.getDueDate())
                    ? RepaymentStatus.MISSED
                    : RepaymentStatus.UPCOMING;
        }
        return paid.compareTo(due) >= 0 ? RepaymentStatus.ON_TIME : RepaymentStatus.PARTIAL;
    }

    // The status of every repayment in a loan's schedule as of a date, keyed by
    // repayment ID and in due-date order, for the Payments screen
    public Map<String, RepaymentStatus> statusesFor(String loanId, LocalDate asOf) {
        requireNotBlank(loanId, "loanId");
        if (asOf == null) {
            throw new IllegalArgumentException(AS_OF_REQUIRED_MESSAGE);
        }

        // Fetching the loan first reports an unknown loan as an error instead
        // of as an empty schedule
        gateway.getLoan(loanId);
        List<Payment> payments = gateway.getPayments(loanId);

        Map<String, RepaymentStatus> statuses = new LinkedHashMap<>();
        gateway.getRepaymentSchedule(loanId).stream()
                .sorted(Comparator.comparing(Repayment::getDueDate))
                .forEach(repayment -> statuses.put(
                        repayment.getRepaymentId(), statusOf(repayment, payments, asOf)));

        return Collections.unmodifiableMap(statuses);
    }

    // The checks that apply to adding and to editing a payment
    private void requireValid(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("payment must not be null");
        }
        if (payment.getPaymentDate() == null) {
            throw new IllegalArgumentException(DATE_REQUIRED_MESSAGE);
        }

        // The mainframe reports an unknown loan as an error
        gateway.getLoan(payment.getLoanId());

        // A link to a repayment must point at one of this same loan's repayments
        if (payment.getRepaymentId() != null) {
            boolean isOnThisLoan = gateway.getRepaymentSchedule(payment.getLoanId()).stream()
                    .anyMatch(repayment ->
                            payment.getRepaymentId().equals(repayment.getRepaymentId()));
            if (!isOnThisLoan) {
                throw new IllegalArgumentException(REPAYMENT_NOT_ON_LOAN_MESSAGE);
            }
        }
    }

    private void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    // Money is compared to the cent, so split payments add up exactly
    private static BigDecimal toMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
}