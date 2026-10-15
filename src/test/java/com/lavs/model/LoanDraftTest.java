package com.lavs.model;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoanDraftTest {
    @Test void defaultStatusAloneIsNotAnEnteredLoan() {
        LoanDraft draft = new LoanDraft();
        assertFalse(draft.hasData());
        draft.details.put("status", "New");
        draft.details.put("principal", "  ");
        assertFalse(draft.hasData());
        draft.details.put("principal", "unfinished amount");
        assertTrue(draft.hasData());
    }

    @Test void copyPreservesValuesWithoutSharingMutableCollections() {
        LoanDraft draft = new LoanDraft();
        draft.primaryBorrower = new LoanDraft.Borrower("C001", "Amelia Adams");
        draft.coborrowers.add(new LoanDraft.Borrower("C002", "Benjamin Baker"));
        draft.details.put("principal", "500000");
        draft.details.put("term", "360");
        draft.startDate = LocalDate.of(2026, 10, 14);
        draft.renewDate = LocalDate.of(2031, 10, 14);
        LoanDraft copy = draft.copy();
        assertEquals(draft.primaryBorrower, copy.primaryBorrower);
        assertEquals(draft.coborrowers, copy.coborrowers);
        assertEquals(draft.details, copy.details);
        assertEquals(draft.startDate, copy.startDate);
        assertEquals(draft.renewDate, copy.renewDate);
        copy.details.put("principal", "600000");
        copy.coborrowers.clear();
        copy.primaryBorrower = null;
        assertEquals("500000", draft.details.get("principal"));
        assertEquals(1, draft.coborrowers.size());
        assertNotNull(draft.primaryBorrower);
    }

    @Test void recognizesBorrowersAndDatesAsEnteredData() {
        LoanDraft draft = new LoanDraft();
        draft.primaryBorrower = new LoanDraft.Borrower("C001", "Amelia Adams");
        assertTrue(draft.hasData());
        draft.primaryBorrower = null;
        draft.startDate = LocalDate.of(2026, 10, 14);
        assertTrue(draft.hasData());
        draft.startDate = null;
        draft.coborrowers.add(new LoanDraft.Borrower("C002", "Benjamin Baker"));
        assertTrue(draft.hasData());
    }
}
