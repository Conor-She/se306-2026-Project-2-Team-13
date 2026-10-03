package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LoanTest {

    private Loan buildValidLoan() {
        return new Loan("L001", "C001", Loan.Status.NEW, 500000, 5.5, Loan.Type.TABLE_LOAN,
                false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), 2500,
                Loan.Frequency.MONTHLY, Loan.Frequency.MONTHLY, 360);
    }

    @Test
    void constructsWithValidFields() {
        Loan loan = buildValidLoan();

        assertEquals("L001", loan.getLoanId());
        assertEquals(360, loan.getTermMonths());
    }

    @Test
    void rejectsBlankLoanId() {
        assertThrows(IllegalArgumentException.class, () ->
                new Loan("", "C001", Loan.Status.NEW, 500000, 5.5, Loan.Type.TABLE_LOAN, false,
                        LocalDate.now(), LocalDate.now(), 2500, Loan.Frequency.MONTHLY,
                        Loan.Frequency.MONTHLY, 360));
    }

    @Test
    void rejectsNonPositivePrincipal() {
        assertThrows(IllegalArgumentException.class, () ->
                new Loan("L001", "C001", Loan.Status.NEW, 0, 5.5, Loan.Type.TABLE_LOAN, false,
                        LocalDate.now(), LocalDate.now(), 2500, Loan.Frequency.MONTHLY,
                        Loan.Frequency.MONTHLY, 360));
    }

    @Test
    void rejectsNonPositiveTermMonths() {
        assertThrows(IllegalArgumentException.class, () ->
                new Loan("L001", "C001", Loan.Status.NEW, 500000, 5.5, Loan.Type.TABLE_LOAN,
                        false, LocalDate.now(), LocalDate.now(), 2500, Loan.Frequency.MONTHLY,
                        Loan.Frequency.MONTHLY, 0));
    }
}