package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lavs.model.LoanDraft;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanDetailsValidatorTest {
    private final LoanDetailsValidator validator = new LoanDetailsValidator();
    private LoanDraft draft;

    @BeforeEach void validDraft() {
        draft = new LoanDraft();
        draft.primaryBorrower = new LoanDraft.Borrower("C1", "Sarah Johnson");
        draft.details.put("loanType", "Table Loan");
        draft.details.put("principal", "12000");
        draft.details.put("interestRate", "5");
        draft.details.put("paymentFrequency", "Monthly");
        draft.details.put("rateFrequency", "Monthly");
        draft.details.put("term", "12");
        draft.details.put("status", "New");
        draft.startDate = LocalDate.of(2026, 1, 1);
        draft.renewDate = LocalDate.of(2027, 1, 1);
    }

    private String error() { return validator.detailsError(draft, true); }

    @Test void aCompleteDraftHasNoError() {
        assertNull(validator.borrowerError(draft));
        assertNull(error());
    }

    @Test void aBorrowerMustBeChosen() {
        draft.primaryBorrower = null;
        assertEquals(LoanDetailsValidator.BORROWER_MESSAGE, validator.borrowerError(draft));
    }

    @Test void theLoanTypeMustBeChosen() {
        draft.details.put("loanType", " ");
        assertEquals(LoanDetailsValidator.LOAN_TYPE_MESSAGE, error());
    }

    @Test void thePrincipalMustBeAPositiveNumber() {
        for (String bad : new String[] {"", "abc", "0", "-5"}) {
            draft.details.put("principal", bad);
            assertEquals(LoanDetailsValidator.PRINCIPAL_MESSAGE, error(), bad);
        }
        draft.details.put("principal", " 0.01 ");
        assertNull(error());
    }

    @Test void theInterestRateMayBeZeroButNotNegativeOrText() {
        draft.details.put("interestRate", "0");
        assertNull(error());
        for (String bad : new String[] {"", "abc", "-0.1"}) {
            draft.details.put("interestRate", bad);
            assertEquals(LoanDetailsValidator.INTEREST_RATE_MESSAGE, error(), bad);
        }
    }

    @Test void bothFrequenciesMustBeChosen() {
        draft.details.put("paymentFrequency", "");
        assertEquals(LoanDetailsValidator.PAYMENT_FREQUENCY_MESSAGE, error());
        draft.details.put("paymentFrequency", "Monthly");
        draft.details.put("rateFrequency", "");
        assertEquals(LoanDetailsValidator.RATE_FREQUENCY_MESSAGE, error());
    }

    @Test void theTermMustBeAWholeNumberOfMonthsAboveZero() {
        for (String bad : new String[] {"0", "-3"}) {
            draft.details.put("term", bad);
            assertEquals(LoanDetailsValidator.TERM_POSITIVE_MESSAGE, error(), bad);
        }
        for (String bad : new String[] {"", "1.5", "twelve"}) {
            draft.details.put("term", bad);
            assertEquals(LoanDetailsValidator.TERM_WHOLE_MESSAGE, error(), bad);
        }
    }

    @Test void datesMustBeReadableChosenAndInOrder() {
        assertEquals(LoanDetailsValidator.DATES_UNREADABLE_MESSAGE, validator.detailsError(draft, false));
        draft.startDate = null;
        assertEquals(LoanDetailsValidator.DATES_MISSING_MESSAGE, error());
        draft.startDate = LocalDate.of(2027, 1, 1);
        assertEquals(LoanDetailsValidator.RENEWAL_AFTER_START_MESSAGE, error());
        draft.renewDate = LocalDate.of(2026, 6, 1);
        assertEquals(LoanDetailsValidator.RENEWAL_AFTER_START_MESSAGE, error());
    }

    @Test void theStatusMustBeChosen() {
        draft.details.put("status", "");
        assertEquals(LoanDetailsValidator.STATUS_MESSAGE, error());
    }

    @Test void problemsAreReportedInTheOrderTheyAppearOnTheForm() {
        draft.details.put("loanType", "");
        draft.details.put("principal", "0");
        draft.details.put("status", "");
        assertEquals(LoanDetailsValidator.LOAN_TYPE_MESSAGE, error());
        draft.details.put("loanType", "Table Loan");
        assertEquals(LoanDetailsValidator.PRINCIPAL_MESSAGE, error());
        draft.details.put("principal", "100");
        assertEquals(LoanDetailsValidator.STATUS_MESSAGE, error());
    }
}
