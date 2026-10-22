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
import java.util.HashMap;
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
            "A schedule, its payments and the date to judge them on are all required";
    static final String AS_OF_REQUIRED_MESSAGE =
            "The date to judge the repayments on is required";

    // AI-assisted: Claude suggested this shared payment amount rule.
    // The one place that decides whether an amount can be paid. The Add Payment dialog asks
    // this too, so the screen doesn't carry its own copy of the rule.
    public static boolean isValidAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

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
    public Map<String, RepaymentStatus> statusesOf(
            List<Repayment> schedule, List<Payment> payments, LocalDate asOf) {
        if (schedule == null || payments == null || asOf == null) {
            throw new IllegalArgumentException(STATUS_INPUTS_MESSAGE);
        }

        // What has been paid towards each repayment directly, and the pool of
        // payments that aren't linked to one
        Map<String, BigDecimal> linkedTotals = new HashMap<>();
        BigDecimal pool = BigDecimal.ZERO;
        for (Payment payment : payments) {
            BigDecimal amount = toMoney(payment.getAmountPaid());
            if (payment.getRepaymentId() == null) {
                pool = pool.add(amount);
            } else {
                linkedTotals.merge(payment.getRepaymentId(), amount, BigDecimal::add);
            }
        }

        Map<String, RepaymentStatus> statuses = new LinkedHashMap<>();
        List<Repayment> inDueOrder = schedule.stream()
                .sorted(Comparator.comparing(Repayment::getDueDate))
                .toList();
        for (Repayment repayment : inDueOrder) {
            BigDecimal due = toMoney(repayment.getAmountDue());
            BigDecimal covered =
                    linkedTotals.getOrDefault(repayment.getRepaymentId(), BigDecimal.ZERO);

            // The pool fills whatever this repayment is still short of
            BigDecimal shortfall = due.subtract(covered).max(BigDecimal.ZERO);
            BigDecimal fromPool = shortfall.min(pool);
            covered = covered.add(fromPool);
            pool = pool.subtract(fromPool);

            statuses.put(repayment.getRepaymentId(), statusFor(repayment, due, covered, asOf));
        }

        return Collections.unmodifiableMap(statuses);
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
        return statusesOf(
                gateway.getRepaymentSchedule(loanId), gateway.getPayments(loanId), asOf);
    }

    private RepaymentStatus statusFor(
            Repayment repayment, BigDecimal due, BigDecimal covered, LocalDate asOf) {
        if (covered.signum() == 0) {
            if (due.signum() == 0) {
                // Nothing was owed, so nothing can be missed or coming up
                return RepaymentStatus.ON_TIME;
            }
            return asOf.isAfter(repayment.getDueDate())
                    ? RepaymentStatus.MISSED
                    : RepaymentStatus.UPCOMING;
        }
        return covered.compareTo(due) >= 0 ? RepaymentStatus.ON_TIME : RepaymentStatus.PARTIAL;
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