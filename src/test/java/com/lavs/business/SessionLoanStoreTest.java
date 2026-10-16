package com.lavs.business;

import com.lavs.model.LoanDraft;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionLoanStoreTest {
    private LoanDraft draft() {
        LoanDraft draft = new LoanDraft();
        draft.primaryBorrower = new LoanDraft.Borrower("C001", "Amelia Adams");
        draft.startDate = LocalDate.of(2026, 10, 14);
        draft.renewDate = draft.startDate.plusYears(1);
        draft.details.putAll(Map.of("loanType", "Table Loan", "principal", "12000", "interestRate", "6.25",
                "term", "12", "paymentFrequency", "Monthly", "rateFrequency", "Monthly", "status", "New"));
        return draft;
    }

    @Test void savesIndependentDraftAndScheduleOnlyInThisStore() {
        var store = new SessionLoanStore();
        var draft = draft();
        var saved = store.save(draft);
        draft.details.put("principal", "1");
        saved.draft().details.clear();
        assertEquals("12000", saved.draft().details.get("principal"));
        assertEquals(12, saved.schedule().size());
        assertEquals(saved.loanId(), saved.schedule().get(0).getLoanId());
        assertEquals(0, saved.schedule().get(11).getRemainingBalance());
        assertEquals(1, store.getLoans().size());
        assertNotEquals(saved.loanId(), store.save(draft()).loanId());
        assertTrue(new SessionLoanStore().getLoans().isEmpty());
    }

    @Test void failuresDoNotSaveAnything() {
        var store = new SessionLoanStore();
        var draft = draft();
        draft.primaryBorrower = new LoanDraft.Borrower("C006", "Liam Patel");
        assertThrows(LoanPrerequisiteException.class, () -> store.save(draft));
        draft.primaryBorrower = new LoanDraft.Borrower("C001", "Amelia Adams");
        draft.details.put("status", "Active");
        assertThrows(IllegalArgumentException.class, () -> store.save(draft));
        assertTrue(store.getLoans().isEmpty());
    }
}
