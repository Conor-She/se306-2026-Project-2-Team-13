package com.lavs.business;

import com.lavs.model.Loan;
import com.lavs.model.LoanDraft;
import com.lavs.model.Repayment;
import java.util.List;
import java.util.Locale;

/** Calculates an unsaved draft's schedule without creating a loan or calling a gateway. */
public class LoanSchedulePreviewService {
    // Select the calculator that matches the draft's loan type.
    public List<Repayment> generate(LoanDraft draft) {
        Loan loan = toLoan(draft, "PREVIEW", 0);
        RepaymentCalculator calculator = switch (loan.getType()) {
            case TABLE_LOAN -> new TableLoanCalculator();
            case REDUCING_BALANCE -> new ReducingBalanceCalculator();
            case INTEREST_ONLY -> new InterestOnlyCalculator();
        };
        return calculator.calculateSchedule(loan);
    }

    /** The Loan a draft describes. The loan ID is null for a loan that isn't created yet. */
    public Loan toLoan(LoanDraft draft, String loanId, double paymentAmount) {
        if (draft == null || draft.primaryBorrower == null) {
            throw new IllegalArgumentException("Select a primary borrower before generating a schedule.");
        }
        double principal = Double.parseDouble(draft.details.getOrDefault("principal", ""));
        if (!Double.isFinite(principal)) throw new IllegalArgumentException("Enter a finite principal amount.");
        Loan.Type type = Loan.Type.valueOf(option(draft, "loanType"));
        return new Loan(loanId, draft.primaryBorrower.customerId(),
                Loan.Status.valueOf(option(draft, "status")), principal,
                Double.parseDouble(draft.details.getOrDefault("interestRate", "")), type,
                false, draft.startDate, draft.renewDate, paymentAmount,
                Loan.PaymentFrequency.valueOf(option(draft, "paymentFrequency")),
                Loan.RateFrequency.valueOf(option(draft, "rateFrequency")),
                Integer.parseInt(draft.details.getOrDefault("term", "").strip()));
    }

    private static String option(LoanDraft draft, String key) {
        // Convert user-facing option text into the matching enum name.
        return draft.details.getOrDefault(key, "").strip().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
