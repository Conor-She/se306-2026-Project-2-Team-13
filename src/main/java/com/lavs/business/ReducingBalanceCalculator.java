package com.lavs.business;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;

// AI-assisted: Helped implement the reducing-balance repayment schedule.
/**
 * Generates a reducing-balance schedule with a fixed principal component and
 * decreasing interest. The floating flag remains independent of the loan type
 * and does not change the initial schedule calculation.
 */
public class ReducingBalanceCalculator extends BaseRepaymentCalculator {

    @Override
    public List<Repayment> calculateSchedule(Loan loan) {
        validate(loan, Loan.Type.REDUCING_BALANCE);

        // Prepare the payment count and interest rate for the schedule.
        int paymentsPerYear = periodsPerYear(loan.getPaymentFrequency());
        int ratePeriodsPerYear = periodsPerYear(loan.getRateFrequency());
        int paymentCount = paymentCount(loan.getTermMonths(), paymentsPerYear);
        double paymentPeriodRate = paymentPeriodRate(
                loan.getInterestRate(), ratePeriodsPerYear, paymentsPerYear);

        BigDecimal balance = money(BigDecimal.valueOf(loan.getPrincipal()));
        BigDecimal regularPrincipal = money(balance.divide(
                BigDecimal.valueOf(paymentCount),
                MONEY_SCALE + 8,
                MONEY_ROUNDING));
        List<Repayment> schedule = new ArrayList<>(paymentCount);

        // Pay equal principal amounts while interest falls with the balance.
        for (int paymentNumber = 1; paymentNumber <= paymentCount; paymentNumber++) {
            BigDecimal interest = money(
                    balance.multiply(BigDecimal.valueOf(paymentPeriodRate)));
            // The final payment clears any balance left by cent rounding.
            BigDecimal principalComponent = paymentNumber == paymentCount
                    ? balance
                    : regularPrincipal;
            BigDecimal amountDue = principalComponent.add(interest);
            balance = paymentNumber == paymentCount
                    ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                    : money(balance.subtract(principalComponent));

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
