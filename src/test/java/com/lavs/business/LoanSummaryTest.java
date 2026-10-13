package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lavs.model.Repayment;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanSummaryTest {

    private Repayment repayment(String id, double amountDue, double principal, double interest,
            double remaining) {
        return new Repayment(id, "L001", LocalDate.of(2026, 2, 1), amountDue, principal, interest,
                remaining);
    }

    // The totals are the sums of the interest column and the amount-due column
    @Test
    void addsUpInterestAndTheTotalRepayable() {
        List<Repayment> schedule = List.of(
                repayment("R1", 600.00, 500.00, 100.00, 500.00),
                repayment("R2", 550.00, 500.00, 50.00, 0.00));

        LoanSummary summary = LoanSummary.fromSchedule(schedule);

        assertEquals(150.00, summary.getTotalInterest());
        assertEquals(1_150.00, summary.getTotalRepayable());
    }

    // Plain double addition gives 0.30000000000000004 for 0.10 + 0.20 and
    // 2.3000000000000003 for 1.10 + 1.20. These are compared exactly (no
    // tolerance), so they fail if the sums drift.
    @Test
    void doesNotDriftFromAddingDecimalAmounts() {
        List<Repayment> schedule = List.of(
                repayment("R1", 1.10, 1.00, 0.10, 1.00),
                repayment("R2", 1.20, 1.00, 0.20, 0.00));

        LoanSummary summary = LoanSummary.fromSchedule(schedule);

        assertEquals(0.30, summary.getTotalInterest());
        assertEquals(2.30, summary.getTotalRepayable());
    }

    @Test
    void anEmptyScheduleHasZeroTotals() {
        LoanSummary summary = LoanSummary.fromSchedule(Collections.emptyList());

        assertEquals(0.0, summary.getTotalInterest());
        assertEquals(0.0, summary.getTotalRepayable());
    }
}