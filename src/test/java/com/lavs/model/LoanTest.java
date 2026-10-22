package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LoanTest {

    // Shared helper so each test only has to override the one field it's
    // actually testing, rather than repeating all 13 constructor arguments
    private Loan buildValidLoan() {
        return new Loan("L001", "C001", Loan.Status.NEW, 500000, 5.5, Loan.Type.TABLE_LOAN,
                false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), 2500,
                Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 360);
    }

    // Confirms a fully valid Loan builds and its fields are retrievable
    @Test
    void constructsWithValidFields() {
        Loan loan = buildValidLoan();

        assertEquals("L001", loan.getLoanId());
        assertEquals(Integer.valueOf(360), loan.getTermMonths());
    }

    // A loan that hasn't been created on the mainframe yet has no loanId,
    // and one read back from it has no type or term, so all three must be
    // allowed to be null
    @Test
    void acceptsUnassignedIdAndUnknownTypeAndTerm() {
        Loan loan = assertDoesNotThrow(() ->
                new Loan(null, "C001", Loan.Status.NEW, 500000, 5.5, null, false,
                        LocalDate.now(), LocalDate.now(), 2500, Loan.PaymentFrequency.MONTHLY,
                        Loan.RateFrequency.MONTHLY, null));

        assertNull(loan.getLoanId());
        assertNull(loan.getType());
        assertNull(loan.getTermMonths());
    }

    // A blank (as opposed to missing) loanId is a mistake, so it must be
    // rejected
    @Test
    void rejectsBlankLoanId() {
        assertThrows(IllegalArgumentException.class, () ->
                new Loan("", "C001", Loan.Status.NEW, 500000, 5.5, Loan.Type.TABLE_LOAN, false,
                        LocalDate.now(), LocalDate.now(), 2500, Loan.PaymentFrequency.MONTHLY,
                        Loan.RateFrequency.MONTHLY, 360));
    }

    // A loan with no principal doesn't make sense, so zero must be rejected
    @Test
    void rejectsNonPositivePrincipal() {
        assertThrows(IllegalArgumentException.class, () ->
                new Loan("L001", "C001", Loan.Status.NEW, 0, 5.5, Loan.Type.TABLE_LOAN, false,
                        LocalDate.now(), LocalDate.now(), 2500, Loan.PaymentFrequency.MONTHLY,
                        Loan.RateFrequency.MONTHLY, 360));
    }

    // A known term of zero would mean the loan has no length, so it must
    // be rejected (unknown/null is fine, zero is not)
    @Test
    void rejectsNonPositiveTermMonths() {
        assertThrows(IllegalArgumentException.class, () ->
                new Loan("L001", "C001", Loan.Status.NEW, 500000, 5.5, Loan.Type.TABLE_LOAN,
                        false, LocalDate.now(), LocalDate.now(), 2500,
                        Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 0));
    }

    @Test
    void loanTypesHaveReadableNamesThatMapBackToTheType() {
        assertEquals("Table Loan", Loan.Type.TABLE_LOAN.displayName());
        assertEquals("Reducing Balance", Loan.Type.REDUCING_BALANCE.displayName());
        assertEquals("Interest Only", Loan.Type.INTEREST_ONLY.displayName());
        for (Loan.Type type : Loan.Type.values()) {
            assertEquals(type, Loan.Type.fromDisplayName(type.displayName()));
        }
        assertThrows(IllegalArgumentException.class, () -> Loan.Type.fromDisplayName("REDUCING_BALANCE"));
        assertThrows(IllegalArgumentException.class, () -> Loan.Type.fromDisplayName(null));
    }
}