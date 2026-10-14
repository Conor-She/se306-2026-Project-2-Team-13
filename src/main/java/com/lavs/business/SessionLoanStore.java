package com.lavs.business;

import com.lavs.model.LoanDraft;
import com.lavs.model.Repayment;
import java.util.ArrayList;
import java.util.List;

/** Loans created in this app session only; nothing is written to disk or the mainframe. */
public class SessionLoanStore {
    public record SavedLoan(String loanId, LoanDraft draft, List<Repayment> schedule) {
        public SavedLoan {
            draft = draft.copy();
            schedule = List.copyOf(schedule);
        }
        @Override public LoanDraft draft() { return draft.copy(); }
    }

    private final List<SavedLoan> loans = new ArrayList<>();

    public SavedLoan save(LoanDraft draft) {
        LoanDraft snapshot = draft.copy();
        new StubLoanPrerequisiteService().validate(snapshot.primaryBorrower.customerId());
        if (snapshot.coborrowers.size() > 3) throw new IllegalArgumentException("A loan can have at most three co-borrowers.");
        if (!"New".equals(snapshot.details.get("status")))
            throw new IllegalArgumentException("A new loan must start with status New.");
        var preview = new LoanSchedulePreviewService().generate(snapshot);
        String id = String.format("SESSION-%04d", loans.size() + 1);
        List<Repayment> schedule = new ArrayList<>();
        for (int index = 0; index < preview.size(); index++) {
            Repayment row = preview.get(index);
            schedule.add(new Repayment(id + "-repayment-" + (index + 1), id, row.getDueDate(), row.getAmountDue(),
                    row.getPrincipalComponent(), row.getInterestComponent(), row.getRemainingBalance()));
        }
        SavedLoan saved = new SavedLoan(id, snapshot, schedule);
        loans.add(saved);
        return saved;
    }

    public List<SavedLoan> getLoans() { return List.copyOf(loans); }
}
