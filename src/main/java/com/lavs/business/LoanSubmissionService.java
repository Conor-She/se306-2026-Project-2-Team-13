package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.Loan;
import com.lavs.model.LoanDraft;
import com.lavs.model.LoanDraft.Borrower;
import com.lavs.model.Repayment;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saves a finished loan wizard through the mainframe: the loan is created with the real
 * prerequisite checks, then its co-borrowers are added. Nothing is kept in memory.
 */
public class LoanSubmissionService {

    /** A loan created on the mainframe, with the schedule that was shown when it was created. */
    public record SavedLoan(String loanId, LoanDraft draft, List<Repayment> schedule) {
        public SavedLoan {
            draft = draft.copy();
            schedule = List.copyOf(schedule);
        }

        @Override
        public LoanDraft draft() {
            return draft.copy();
        }
    }

    static final String TOO_MANY_COBORROWERS_MESSAGE =
            "A loan can have at most three co-borrowers.";
    static final String NEW_LOAN_MESSAGE = "A new loan must start with status New.";
    static final String RENEWAL_DATE_MESSAGE = "Enter a renewal date before saving the loan.";
    static final String PRIMARY_AS_COBORROWER_MESSAGE =
            "The primary borrower cannot also be a co-borrower.";
    static final String DUPLICATE_COBORROWER_MESSAGE =
            "A customer can only be a co-borrower once.";

    private final MainframeGateway gateway;
    private final LoanService loanService;
    private final CoBorrowerService coBorrowerService;
    private final LoanSchedulePreviewService previewService;

    public LoanSubmissionService(MainframeGateway gateway) {
        this(gateway, new LoanService(gateway), new CoBorrowerService(gateway),
                new LoanSchedulePreviewService());
    }

    LoanSubmissionService(MainframeGateway gateway, LoanService loanService,
            CoBorrowerService coBorrowerService, LoanSchedulePreviewService previewService) {
        this.gateway = gateway;
        this.loanService = loanService;
        this.coBorrowerService = coBorrowerService;
        this.previewService = previewService;
    }

    public SavedLoan submit(LoanDraft draft) {
        LoanDraft snapshot = draft.copy();
        if (snapshot.coborrowers.size() > CoBorrowerService.MAX_CO_BORROWERS) {
            throw new IllegalArgumentException(TOO_MANY_COBORROWERS_MESSAGE);
        }
        if (!"New".equals(snapshot.details.get("status"))) {
            throw new IllegalArgumentException(NEW_LOAN_MESSAGE);
        }

        // Works out the schedule from what was entered. This also rejects unusable numbers
        // and options exactly as the wizard's preview does.
        List<Repayment> preview = previewService.generate(snapshot);
        if (snapshot.renewDate == null) {
            throw new IllegalArgumentException(RENEWAL_DATE_MESSAGE);
        }
        checkCoBorrowers(snapshot);

        // The wizard has no payment amount, and the mainframe builds its schedule from one, so it
        // gets the first instalment of the schedule calculated here
        Loan loan = previewService.toLoan(snapshot, null, preview.get(0).getAmountDue());
        LoanWithSchedule created = loanService.create(loan);
        String loanId = created.getLoan().getLoanId();

        try {
            for (Borrower coBorrower : snapshot.coborrowers) {
                coBorrowerService.addCoBorrower(loanId, coBorrower.customerId());
            }
        } catch (RuntimeException failure) {
            throw cancelAfterFailure(loanId, failure);
        }

        return new SavedLoan(loanId, snapshot, created.getSchedule());
    }

    // Everything about the co-borrowers that can be checked before the loan exists
    private void checkCoBorrowers(LoanDraft draft) {
        String primaryId = draft.primaryBorrower.customerId();
        Set<String> seen = new HashSet<>();
        for (Borrower coBorrower : draft.coborrowers) {
            if (coBorrower.customerId().equals(primaryId)) {
                throw new IllegalArgumentException(PRIMARY_AS_COBORROWER_MESSAGE);
            }
            if (!seen.add(coBorrower.customerId())) {
                throw new IllegalArgumentException(DUPLICATE_COBORROWER_MESSAGE);
            }
        }
    }

    // A co-borrower couldn't be added after the loan was created. The mainframe can't delete a
    // loan, so it is cancelled instead of being left half finished.
    private LoanSubmissionException cancelAfterFailure(String loanId, RuntimeException failure) {
        try {
            gateway.updateLoanStatus(loanId, Loan.Status.CANCELLED);
            return new LoanSubmissionException("The loan could not be completed and has been "
                    + "cancelled (loan " + loanId + "): " + failure.getMessage(), failure);
        } catch (RuntimeException cancelFailure) {
            return new LoanSubmissionException("A co-borrower could not be added, and the loan "
                    + "could not be cancelled. Please check loan " + loanId
                    + " on the mainframe: " + failure.getMessage(), failure);
        }
    }
}