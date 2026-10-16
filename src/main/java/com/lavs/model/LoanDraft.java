package com.lavs.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Unsaved loan details. Text values may be incomplete or invalid while editing. */
public class LoanDraft {
    /** Customer identity kept without depending on a UI controller. */
    public record Borrower(String customerId, String name) {}

    public Borrower primaryBorrower;
    public final Map<String, String> details = new LinkedHashMap<>();
    public LocalDate startDate;
    public LocalDate renewDate;
    public final List<Borrower> coborrowers = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();

    public boolean hasData() {
        return primaryBorrower != null || !notes.isEmpty() || !coborrowers.isEmpty() || startDate != null || renewDate != null
                || details.entrySet().stream().anyMatch(entry -> !entry.getValue().isBlank()
                        && !(entry.getKey().equals("status") && entry.getValue().equals("New")));
    }

    public LoanDraft copy() {
        LoanDraft copy = new LoanDraft();
        copy.primaryBorrower = primaryBorrower;
        copy.details.putAll(details);
        copy.startDate = startDate;
        copy.renewDate = renewDate;
        // Borrower records and LocalDate values are immutable.
        copy.coborrowers.addAll(coborrowers);
        copy.notes.addAll(notes);
        return copy;
    }
}
