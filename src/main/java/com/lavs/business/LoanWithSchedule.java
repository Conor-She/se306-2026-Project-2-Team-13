package com.lavs.business;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;
import java.util.List;

// A loan together with its repayment schedule. LoanService returns this so
// the UI always has both to show the MMM, and can't show a loan without its
// schedule.
public class LoanWithSchedule {

    private final Loan loan;
    private final List<Repayment> schedule;

    public LoanWithSchedule(Loan loan, List<Repayment> schedule) {
        this.loan = loan;
        this.schedule = List.copyOf(schedule);
    }

    public Loan getLoan() {
        return loan;
    }

    public List<Repayment> getSchedule() {
        return schedule;
    }
}