package com.lavs.business;

import java.math.BigDecimal;
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
public class TableLoanCalculator extends BaseRepaymentCalculator {
    @Override
    public List<Repayment> calculateSchedule(Loan loan) {
        validate(loan, Loan.Type.TABLE_LOAN);

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

            schedule.add(repayment(
                    loan,
                    paymentNumber,
                    amountDue,
                    principalComponent,
                    interest,
                    balance));
        }

        return List.copyOf(schedule);
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

    private int periodsPerYear(Enum<?> frequency) {
        return switch (frequency.name().toUpperCase()) {
            case "ANNUAL", "ANNUALLY", "YEARLY" -> 1;
            case "SEMI_ANNUAL", "SEMI_ANNUALLY", "HALF_YEARLY" -> 2;
            case "QUARTERLY" -> 4;
            case "MONTHLY" -> 12;
            case "FORTNIGHTLY" -> 26;
            case "WEEKLY" -> 52;
            case "DAILY" -> 365;
            default -> throw new IllegalArgumentException(
                    "Unsupported rate frequency: " + frequency);
        };
    }

}
