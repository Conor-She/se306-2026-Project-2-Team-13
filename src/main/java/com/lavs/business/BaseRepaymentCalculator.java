package com.lavs.business;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;

// AI-assisted: Helped design shared validation, frequency conversion and financial calculations.
abstract class BaseRepaymentCalculator implements RepaymentCalculator {
    protected static final int MONEY_SCALE = 2;
    protected static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    // Check that the loan contains everything needed to build a schedule.
    protected final void validate(Loan loan, Loan.Type expectedType) {
        if (loan == null) {
            throw new IllegalArgumentException("loan must not be null");
        }
        if (loan.getType() != expectedType) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName() + " requires a " + expectedType + " loan");
        }
        if (!Double.isFinite(loan.getInterestRate()) || loan.getInterestRate() < 0) {
            throw new IllegalArgumentException("interestRate must be finite and non-negative");
        }
        if (loan.getStartDate() == null) {
            throw new IllegalArgumentException("startDate must not be null");
        }
        if (loan.getPaymentFrequency() == null) {
            throw new IllegalArgumentException("paymentFrequency must not be null");
        }
        if (loan.getRateFrequency() == null) {
            throw new IllegalArgumentException("rateFrequency must not be null");
        }
                if (loan.getRateFrequency() == null) {
            throw new IllegalArgumentException("rateFrequency must not be null");
        }
        if (loan.getLoanId() == null) {
            throw new IllegalArgumentException(
                    "loanId must be assigned before a schedule can be calculated");
        }
        if (loan.getTermMonths() == null) {
            throw new IllegalArgumentException("termMonths must not be null");
        }
    }

    // Convert the selected payment frequency into payments per year.
    protected final int periodsPerYear(Loan.PaymentFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> 52;
            case FORTNIGHTLY -> 26;
            case MONTHLY -> 12;
            case YEARLY -> 1;
            case ON_RENEWAL -> throw new IllegalArgumentException(
                    "ON_RENEWAL has no fixed number of payments per year");
        };
    }

    // Convert the selected interest frequency into rate periods per year.
    protected final int periodsPerYear(Loan.RateFrequency frequency) {
        return switch (frequency) {
            case DAILY -> 365;
            case WEEKLY -> 52;
            case FORTNIGHTLY -> 26;
            case MONTHLY -> 12;
            case YEARLY -> 1;
        };
    }

    // Work out how many payments fit into the loan term.
    protected final int paymentCount(int termMonths, int paymentsPerYear) {
        int periodNumerator = Math.multiplyExact(termMonths, paymentsPerYear);
        if (periodNumerator % 12 != 0) {
            throw new IllegalArgumentException(
                    "termMonths must produce a whole number of repayments");
        }
        return periodNumerator / 12;
    }

    // Convert the annual interest rate into the rate charged for one payment.
    protected final double paymentPeriodRate(
            double annualPercentageRate,
            int ratePeriodsPerYear,
            int paymentsPerYear) {
        if (annualPercentageRate == 0) {
            return 0;
        }

        double ratePerCompoundingPeriod =
                annualPercentageRate / 100.0 / ratePeriodsPerYear;
        double compoundingPeriodsPerPayment =
                (double) ratePeriodsPerYear / paymentsPerYear;
        double rate = Math.pow(
                1.0 + ratePerCompoundingPeriod,
                compoundingPeriodsPerPayment) - 1.0;

        if (!Double.isFinite(rate)) {
            throw new IllegalArgumentException("Unable to calculate a valid payment-period rate");
        }
        return rate;
    }

    // Build one repayment row with its calculated due date.
    protected final Repayment repayment(
            Loan loan,
            int paymentNumber,
            BigDecimal amountDue,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            BigDecimal remainingBalance) {
        return new Repayment(
                loan.getLoanId() + "-repayment-" + paymentNumber,
                loan.getLoanId(),
                dueDate(loan.getStartDate(), loan.getPaymentFrequency(), paymentNumber),
                amountDue.doubleValue(),
                principalComponent.doubleValue(),
                interestComponent.doubleValue(),
                remainingBalance.doubleValue());
    }

    // Keep all money values rounded to cents.
    protected final BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    // Move the start date forward by the correct payment interval.
    private LocalDate dueDate(
            LocalDate startDate,
            Loan.PaymentFrequency paymentFrequency,
            int paymentNumber) {
        return switch (paymentFrequency) {
            case WEEKLY -> startDate.plusWeeks(paymentNumber);
            case FORTNIGHTLY -> startDate.plusWeeks(2L * paymentNumber);
            case MONTHLY -> startDate.plusMonths(paymentNumber);
            case YEARLY -> startDate.plusYears(paymentNumber);
            case ON_RENEWAL -> throw new IllegalArgumentException(
                    "ON_RENEWAL requires a renewal date");
        };
    }
}
