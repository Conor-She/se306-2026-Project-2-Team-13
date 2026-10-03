package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RepaymentTest {

    // Confirms a fully valid Repayment builds and its fields are retrievable
    @Test
    void constructsWithValidFields() {
        Repayment repayment = new Repayment("R001", "L001", LocalDate.of(2026, 2, 1), 2500, 1200,
                1300, 497500);

        assertEquals("R001", repayment.getRepaymentId());
        assertEquals(2500, repayment.getAmountDue());
    }

    // A negative amount due would mean the bank owes the customer, which
    // isn't a valid repayment row, so it must be rejected
    @Test
    void rejectsNegativeAmountDue() {
        assertThrows(IllegalArgumentException.class, () ->
                new Repayment("R001", "L001", LocalDate.now(), -1, 0, 0, 0));
    }

    // A negative remaining balance would mean the loan is overpaid beyond
    // zero, which this entity shouldn't represent, so it must be rejected
    @Test
    void rejectsNegativeRemainingBalance() {
        assertThrows(IllegalArgumentException.class, () ->
                new Repayment("R001", "L001", LocalDate.now(), 2500, 1200, 1300, -1));
    }
}