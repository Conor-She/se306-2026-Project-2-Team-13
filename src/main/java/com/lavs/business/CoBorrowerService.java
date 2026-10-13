package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.CoBorrower;
import com.lavs.model.Loan;
import java.util.List;

// Business logic for adding and removing a loan's co-borrowers
// A loan has one primary borrower plus up to MAX_CO_BORROWERS
// co-borrowers, and the same customer can't be on a loan twice. Depends only
// on the MainframeGateway interface.
public class CoBorrowerService {

    // The agreed limit (still to be confirmed with the product owner): the
    // primary borrower plus up to three co-borrowers, four people in total
    public static final int MAX_CO_BORROWERS = 3;

    static final String LIMIT_MESSAGE = "A loan can have at most " + MAX_CO_BORROWERS
            + " co-borrowers in addition to the primary borrower";
    static final String PRIMARY_BORROWER_MESSAGE =
            "The primary borrower cannot also be added as a co-borrower";
    static final String DUPLICATE_MESSAGE =
            "This customer is already a co-borrower on this loan";

    private final MainframeGateway gateway;

    public CoBorrowerService(MainframeGateway gateway) {
        this.gateway = gateway;
    }

    // Adds a customer as a co-borrower on a loan, unless they are already on
    // it or the loan already has the maximum number
    public CoBorrower addCoBorrower(String loanId, String customerId) {
        requireNotBlank(loanId, "loanId");
        requireNotBlank(customerId, "customerId");

        // Fetching the loan also confirms it exists and tells us who the
        // primary borrower is
        Loan loan = gateway.getLoan(loanId);
        if (customerId.equals(loan.getCustomerId())) {
            throw new IllegalArgumentException(PRIMARY_BORROWER_MESSAGE);
        }

        // Who is already on the loan, looked at before adding
        List<CoBorrower> existing = gateway.getCoBorrowers(loanId);

        // Checked before the limit, so someone already on a full loan is
        // told they are a duplicate, which is more useful than "loan full"
        boolean alreadyOnLoan = existing.stream()
                .anyMatch(coBorrower -> customerId.equals(coBorrower.getCustomerId()));
        if (alreadyOnLoan) {
            throw new IllegalArgumentException(DUPLICATE_MESSAGE);
        }

        // The new co-borrower is allowed only while the number already on
        // the loan is below the maximum (<3, not <= 3)
        if (existing.size() >= MAX_CO_BORROWERS) {
            throw new CoBorrowerLimitException(LIMIT_MESSAGE);
        }

        return gateway.addCoBorrower(new CoBorrower(null, loanId, customerId));
    }

    // Removes a co-borrower. There is deliberately no limit check here:
    // removal is always allowed, whatever the current count.
    public void removeCoBorrower(String loanId, String coBorrowerId) {
        requireNotBlank(loanId, "loanId");
        requireNotBlank(coBorrowerId, "coBorrowerId");
        gateway.deleteCoBorrower(loanId, coBorrowerId);
    }

    private void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }
}