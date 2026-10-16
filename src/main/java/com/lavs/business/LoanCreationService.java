package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.Loan;
import com.lavs.model.LoanDraft;
import com.lavs.model.Repayment;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Saves a loan application and its co-borrowers through the backend services. */
public class LoanCreationService {
    private final MainframeGateway gateway;
    private final LoanService loanService;
    private final CoBorrowerService coBorrowerService;
    private final LoanSchedulePreviewService previewService;

    public LoanCreationService(MainframeGateway gateway) {
        this(gateway, new LoanService(gateway), new CoBorrowerService(gateway),
                new LoanSchedulePreviewService());
    }

    LoanCreationService(MainframeGateway gateway, LoanService loanService,
            CoBorrowerService coBorrowerService, LoanSchedulePreviewService previewService) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.loanService = Objects.requireNonNull(loanService, "loanService must not be null");
        this.coBorrowerService = Objects.requireNonNull(coBorrowerService, "coBorrowerService must not be null");
        this.previewService = Objects.requireNonNull(previewService, "previewService must not be null");
    }

    /** Uses the same live backend checks as loan creation for the wizard's borrower notice. */
    public void validatePrerequisites(String customerId) {
        loanService.validatePrerequisites(customerId);
    }

    /** Creates the loan, persists its schedule, then attaches each selected co-borrower. */
    public LoanWithSchedule create(LoanDraft draft) {
        if (draft == null || draft.primaryBorrower == null) {
            throw new IllegalArgumentException("Select a primary borrower before saving the loan.");
        }
        if (!"New".equalsIgnoreCase(text(draft.details.get("status")))) {
            throw new IllegalArgumentException(LoanService.NEW_LOAN_MESSAGE);
        }
        validateCoBorrowers(draft);

        // The Loan model requires a payment amount when creating. Use the first
        // amount from the same calculator preview shown in the wizard.
        List<Repayment> preview = previewService.generate(draft);
        if (preview.isEmpty()) {
            throw new IllegalArgumentException("The repayment schedule is empty.");
        }
        Loan loan = toLoan(draft, preview.get(0).getAmountDue());
        LoanWithSchedule created = loanService.create(loan);
        for (LoanDraft.Borrower borrower : draft.coborrowers) {
            coBorrowerService.addCoBorrower(created.getLoan().getLoanId(), borrower.customerId());
        }
        return created;
    }

    private void validateCoBorrowers(LoanDraft draft) {
        if (draft.coborrowers.size() > CoBorrowerService.MAX_CO_BORROWERS) {
            throw new IllegalArgumentException(CoBorrowerService.LIMIT_MESSAGE);
        }
        Set<String> seen = new HashSet<>();
        seen.add(draft.primaryBorrower.customerId());
        for (LoanDraft.Borrower borrower : draft.coborrowers) {
            if (borrower == null || borrower.customerId() == null || borrower.customerId().isBlank()) {
                throw new IllegalArgumentException("Each co-borrower must have a customer ID.");
            }
            if (!seen.add(borrower.customerId())) {
                if (draft.primaryBorrower.customerId().equals(borrower.customerId())) {
                    throw new IllegalArgumentException(CoBorrowerService.PRIMARY_BORROWER_MESSAGE);
                }
                throw new IllegalArgumentException(CoBorrowerService.DUPLICATE_MESSAGE);
            }
            // Check all selected customers before creating the loan, avoiding
            // a partial backend loan when a co-borrower no longer exists.
            if (gateway.getCustomer(borrower.customerId()) == null) {
                throw new IllegalArgumentException("Co-borrower customer "
                        + borrower.customerId() + " could not be found.");
            }
        }
    }

    private static Loan toLoan(LoanDraft draft, double paymentAmount) {
        double principal = parseNumber(draft, "principal");
        double interestRate = parseNumber(draft, "interestRate");
        int termMonths;
        try {
            termMonths = Integer.parseInt(text(draft.details.get("term")));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter the term as a whole number of months.", exception);
        }

        return new Loan(null, draft.primaryBorrower.customerId(), Loan.Status.NEW,
                principal, interestRate, enumValue(Loan.Type.class, draft, "loanType"), false,
                draft.startDate, draft.renewDate, paymentAmount,
                enumValue(Loan.PaymentFrequency.class, draft, "paymentFrequency"),
                enumValue(Loan.RateFrequency.class, draft, "rateFrequency"), termMonths);
    }

    private static double parseNumber(LoanDraft draft, String key) {
        try {
            return Double.parseDouble(text(draft.details.get(key)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid " + key + ".", exception);
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, LoanDraft draft, String key) {
        String value = text(draft.details.get(key)).toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Select a valid " + key + ".", exception);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
