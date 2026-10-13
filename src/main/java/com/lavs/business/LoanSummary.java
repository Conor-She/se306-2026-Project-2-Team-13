package com.lavs.business;

import com.lavs.model.Repayment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Totals worked out from a loan's repayment schedule, to show the MMM and the
// customer. Nothing here is stored: it is always computed from the schedule.
public class LoanSummary {

    private final double totalInterest;
    private final double totalRepayable;

    private LoanSummary(double totalInterest, double totalRepayable) {
        this.totalInterest = totalInterest;
        this.totalRepayable = totalRepayable;
    }

    // Adds up every row of the schedule. The sums go through BigDecimal because
    // adding plain doubles drifts and these are money amounts shown to a customer.
    public static LoanSummary fromSchedule(List<Repayment> schedule) {
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal repayable = BigDecimal.ZERO;

        for (Repayment repayment : schedule) {
            interest = interest.add(BigDecimal.valueOf(repayment.getInterestComponent()));
            repayable = repayable.add(BigDecimal.valueOf(repayment.getAmountDue()));
        }

        return new LoanSummary(toMoney(interest), toMoney(repayable));
    }

    // The interest paid over the life of the loan
    public double getTotalInterest() {
        return totalInterest;
    }

    // Everything the customer pays back: the amount borrowed plus the interest
    public double getTotalRepayable() {
        return totalRepayable;
    }

    private static double toMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}