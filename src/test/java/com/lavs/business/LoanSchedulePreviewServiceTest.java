package com.lavs.business;

import com.lavs.model.LoanDraft;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class LoanSchedulePreviewServiceTest {
    private LoanDraft draft(String type) {
        LoanDraft draft = new LoanDraft();
        draft.primaryBorrower = new LoanDraft.Borrower("C001", "Amelia Adams");
        draft.startDate = LocalDate.of(2026, 10, 14);
        draft.renewDate = LocalDate.of(2027, 10, 14);
        draft.details.putAll(Map.of("loanType", type, "principal", "12000", "interestRate", "6.25",
                "term", "12", "paymentFrequency", "Monthly", "rateFrequency", "Monthly", "status", "New"));
        return draft;
    }

    @ParameterizedTest @ValueSource(strings = {"Table Loan", "Reducing Balance", "Interest Only"})
    void previewsAllLoanTypesWithoutChangingTheDraft(String type) {
        LoanDraft draft = draft(type);
        var before = draft.copy();
        var schedule = new LoanSchedulePreviewService().generate(draft);
        assertEquals(12, schedule.size());
        assertEquals(draft.startDate.plusMonths(1), schedule.get(0).getDueDate());
        assertEquals(0, schedule.get(11).getRemainingBalance());
        assertEquals(12000, schedule.stream().mapToDouble(row -> row.getPrincipalComponent()).sum(), 0.01);
        assertEquals(before.details, draft.details);
        assertEquals(before.primaryBorrower, draft.primaryBorrower);
    }

    @Test void rejectsUnsupportedPaymentFrequency() {
        LoanDraft draft = draft("Table Loan");
        draft.details.put("paymentFrequency", "On Renewal");
        assertThrows(IllegalArgumentException.class, () -> new LoanSchedulePreviewService().generate(draft));
    }
}
