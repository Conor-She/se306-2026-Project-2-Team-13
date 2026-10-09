package com.lavs.business;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;

/**
 * Generates a table-loan schedule using a fixed payment calculated from the
 * principal, annual percentage rate and term. Monetary values are rounded to
 * cents for every repayment, with the final payment adjusted to clear the
 * remaining balance exactly.
 */
public class TableLoanCalculator implements RepaymentCalculator {
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final String ON_RENEWAL_UNSUPPORTED =
        "ON_RENEWAL payments cannot be scheduled by TableLoanCalculator";

    @Override
    public List<Repayment> calculateSchedule(Loan loan) {
        validate(loan);

        int paymentsPerYear = periodsPerYear(loan.getPaymentFrequency());
        int ratePeriodsPerYear = periodsPerYear(loan.getRateFrequency());
        int paymentCount = paymentCount(loan.getTermMonths(), paymentsPerYear);
        double paymentPeriodRate = paymentPeriodRate(
                loan.getInterestRate(), ratePeriodsPerYear, paymentsPerYear);

        BigDecimal balance = money(BigDecimal.valueOf(loan.getPrincipal()));
        BigDecimal regularPayment = regularPayment(balance, paymentPeriodRate, paymentCount);
        List<Repayment> schedule = new ArrayList<>(paymentCount);

        for (int paymentNumber = 1; paymentNumber <= paymentCount; paymentNumber++) {
            BigDecimal interest = money(
                    balance.multiply(BigDecimal.valueOf(paymentPeriodRate)));
            BigDecimal principalComponent;
            BigDecimal amountDue;

            if (paymentNumber == paymentCount) {
                principalComponent = balance;
                amountDue = principalComponent.add(interest);
                balance = BigDecimal.ZERO.setScale(MONEY_SCALE);
            } else {
                amountDue = regularPayment;
                principalComponent = amountDue.subtract(interest);

                if (principalComponent.signum() <= 0) {
                    throw new IllegalArgumentException(
                            "The calculated payment must be greater than the interest due");
                }

                balance = money(balance.subtract(principalComponent));
            }

            LocalDate dueDate = dueDate(
                    loan.getStartDate(), loan.getPaymentFrequency(), paymentNumber);
            schedule.add(new Repayment(
                    loan.getLoanId() + "-repayment-" + paymentNumber,
                    loan.getLoanId(),
                    dueDate,
                    amountDue.doubleValue(),
                    principalComponent.doubleValue(),
                    interest.doubleValue(),
                    balance.doubleValue()));
        }

        return List.copyOf(schedule);
    }

    private void validate(Loan loan) {
        if (loan == null) {
            throw new IllegalArgumentException("loan must not be null");
        }
        if (loan.getType() != Loan.Type.TABLE_LOAN) {
            throw new IllegalArgumentException("TableLoanCalculator requires a table loan");
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
        if (loan.getLoanId() == null) {
            throw new IllegalArgumentException(
                    "loanId must be assigned before a schedule can be calculated");
        }
        if (loan.getTermMonths() == null) {
            throw new IllegalArgumentException("termMonths must not be null");
        }
    }

    private int periodsPerYear(Loan.PaymentFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> 52;
            case FORTNIGHTLY -> 26;
            case MONTHLY -> 12;
            case YEARLY -> 1;
            case ON_RENEWAL -> throw new IllegalArgumentException(ON_RENEWAL_UNSUPPORTED);
        };
    }

    private int periodsPerYear(Loan.RateFrequency frequency) {
        return switch (frequency) {
            case DAILY -> 365;
            case WEEKLY -> 52;
            case FORTNIGHTLY -> 26;
            case MONTHLY -> 12;
            case YEARLY -> 1;
        };
    }

    private int paymentCount(int termMonths, int paymentsPerYear) {
        int periodNumerator = Math.multiplyExact(termMonths, paymentsPerYear);
        if (periodNumerator % 12 != 0) {
            throw new IllegalArgumentException(
                    "termMonths must produce a whole number of repayments");
        }
        return periodNumerator / 12;
    }

    private double paymentPeriodRate(
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
        return Math.pow(
                1.0 + ratePerCompoundingPeriod,
                compoundingPeriodsPerPayment) - 1.0;
    }

    private BigDecimal regularPayment(
            BigDecimal principal,
            double paymentPeriodRate,
            int paymentCount) {
        if (paymentPeriodRate == 0) {
            return money(principal.divide(
                    BigDecimal.valueOf(paymentCount),
                    MONEY_SCALE + 8,
                    MONEY_ROUNDING));
        }

        double compoundFactor = Math.pow(1.0 + paymentPeriodRate, paymentCount);
        double payment = principal.doubleValue()
                * paymentPeriodRate
                * compoundFactor
                / (compoundFactor - 1.0);

        if (!Double.isFinite(payment) || payment <= 0) {
            throw new IllegalArgumentException("Unable to calculate a valid payment amount");
        }

        return money(BigDecimal.valueOf(payment));
    }

    private LocalDate dueDate(
            LocalDate startDate,
            Loan.PaymentFrequency paymentFrequency,
            int paymentNumber) {
        return switch (paymentFrequency) {
            case WEEKLY -> startDate.plusWeeks(paymentNumber);
            case FORTNIGHTLY -> startDate.plusWeeks(2L * paymentNumber);
            case MONTHLY -> startDate.plusMonths(paymentNumber);
            case YEARLY -> startDate.plusYears(paymentNumber);
            case ON_RENEWAL -> throw new IllegalArgumentException(ON_RENEWAL_UNSUPPORTED);
        };
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
