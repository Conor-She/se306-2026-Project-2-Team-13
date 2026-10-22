package com.lavs.business;

import com.lavs.model.LoanDraft;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The rules a loan draft must meet before it can move on in the loan wizard. The screen only
 * shows the message this returns; it does not decide what is valid.
 */
public final class LoanDetailsValidator {
    static final String BORROWER_MESSAGE = "Select a primary borrower from the customer search results.";
    static final String LOAN_TYPE_MESSAGE = "Select a loan type.";
    static final String PRINCIPAL_MESSAGE = "Enter a principal greater than zero.";
    static final String INTEREST_RATE_MESSAGE = "Enter an interest rate of zero or greater.";
    static final String PAYMENT_FREQUENCY_MESSAGE = "Select a payment frequency.";
    static final String RATE_FREQUENCY_MESSAGE = "Select a rate frequency.";
    static final String TERM_POSITIVE_MESSAGE = "Enter a term greater than zero in months.";
    static final String TERM_WHOLE_MESSAGE = "Enter the term as a whole number of months.";
    static final String DATES_UNREADABLE_MESSAGE = "Enter valid start and renewal dates.";
    static final String DATES_MISSING_MESSAGE = "Choose start and renewal dates.";
    static final String RENEWAL_AFTER_START_MESSAGE = "Renewal date must be after the start date.";
    static final String STATUS_MESSAGE = "Select a status.";

    /** The problem with the borrower, or null when one has been chosen. */
    public String borrowerError(LoanDraft draft) {
        return draft.primaryBorrower == null ? BORROWER_MESSAGE : null;
    }

    /**
     * The first problem with the loan details, or null when they are all acceptable.
     * The checks run in the order they appear on the form. {@code datesReadable} says whether
     * the screen could read the dates typed into the date fields at all.
     */
    public String detailsError(LoanDraft draft, boolean datesReadable) {
        Map<String, String> details = draft.details;
        if (isBlank(details, "loanType")) return LOAN_TYPE_MESSAGE;
        if (!isNumber(details.getOrDefault("principal", ""), false)) return PRINCIPAL_MESSAGE;
        if (!isNumber(details.getOrDefault("interestRate", ""), true)) return INTEREST_RATE_MESSAGE;
        if (isBlank(details, "paymentFrequency")) return PAYMENT_FREQUENCY_MESSAGE;
        if (isBlank(details, "rateFrequency")) return RATE_FREQUENCY_MESSAGE;
        try {
            if (Integer.parseInt(details.getOrDefault("term", "").strip()) <= 0) return TERM_POSITIVE_MESSAGE;
        } catch (NumberFormatException exception) {
            return TERM_WHOLE_MESSAGE;
        }
        if (!datesReadable) return DATES_UNREADABLE_MESSAGE;
        if (draft.startDate == null || draft.renewDate == null) return DATES_MISSING_MESSAGE;
        if (!draft.renewDate.isAfter(draft.startDate)) return RENEWAL_AFTER_START_MESSAGE;
        if (isBlank(details, "status")) return STATUS_MESSAGE;
        return null;
    }

    private static boolean isBlank(Map<String, String> details, String key) {
        return details.getOrDefault(key, "").isBlank();
    }

    private static boolean isNumber(String text, boolean allowZero) {
        try {
            int sign = new BigDecimal(text.strip()).signum();
            return allowZero ? sign >= 0 : sign > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
