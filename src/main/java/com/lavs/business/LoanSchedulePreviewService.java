package com.lavs.business;

import com.lavs.model.Loan;
import com.lavs.model.LoanDraft;
import com.lavs.model.Repayment;
import java.util.List;
import java.util.Locale;

/** Calculates an unsaved draft's schedule without creating a loan or calling a gateway. */
public class LoanSchedulePreviewService {
    public List<Repayment> generate(LoanDraft draft) {
        if (draft == null || draft.primaryBorrower == null) {
            throw new IllegalArgumentException("Select a primary borrower before generating a schedule.");
        }
        double principal = Double.parseDouble(draft.details.getOrDefault("principal", ""));
        if (!Double.isFinite(principal)) throw new IllegalArgumentException("Enter a finite principal amount.");
        Loan.Type type = Loan.Type.valueOf(option(draft, "loanType"));
        Loan loan = new Loan("PREVIEW", draft.primaryBorrower.customerId(),
                Loan.Status.valueOf(option(draft, "status")), principal,
                Double.parseDouble(draft.details.getOrDefault("interestRate", "")), type,
                false, draft.startDate, draft.renewDate, 0,
                Loan.PaymentFrequency.valueOf(option(draft, "paymentFrequency")),
                Loan.RateFrequency.valueOf(option(draft, "rateFrequency")),
                Integer.parseInt(draft.details.getOrDefault("term", "").strip()));
        RepaymentCalculator calculator = switch (type) {
            case TABLE_LOAN -> new TableLoanCalculator();
            case REDUCING_BALANCE -> new ReducingBalanceCalculator();
            case INTEREST_ONLY -> new InterestOnlyCalculator();
        };
        return calculator.calculateSchedule(loan);
    }

    private static String option(LoanDraft draft, String key) {
        return draft.details.getOrDefault(key, "").strip().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
