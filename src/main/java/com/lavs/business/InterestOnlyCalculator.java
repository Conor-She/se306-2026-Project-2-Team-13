package com.lavs.business;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;

/**
 * Generates an interest-only schedule. Each regular payment contains interest
 * only, and the final payment also clears the principal. The floating flag
 * remains independent of the loan type and does not change the initial schedule.
 */
public class InterestOnlyCalculator extends BaseRepaymentCalculator {

    @Override
    public List<Repayment> calculateSchedule(Loan loan) {
        validate(loan, Loan.Type.INTEREST_ONLY);

        int paymentsPerYear = periodsPerYear(loan.getPaymentFrequency());
        int ratePeriodsPerYear = periodsPerYear(loan.getRateFrequency());
        int paymentCount = paymentCount(loan.getTermMonths(), paymentsPerYear);
        double paymentPeriodRate = paymentPeriodRate(
                loan.getInterestRate(), ratePeriodsPerYear, paymentsPerYear);

        BigDecimal balance = money(BigDecimal.valueOf(loan.getPrincipal()));
        BigDecimal zero = BigDecimal.ZERO.setScale(MONEY_SCALE);
        List<Repayment> schedule = new ArrayList<>(paymentCount);

        for (int paymentNumber = 1; paymentNumber <= paymentCount; paymentNumber++) {
            BigDecimal interest = money(
                    balance.multiply(BigDecimal.valueOf(paymentPeriodRate)));
            BigDecimal principalComponent = paymentNumber == paymentCount ? balance : zero;
            BigDecimal amountDue = principalComponent.add(interest);

            if (paymentNumber == paymentCount) {
                balance = zero;
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
}
