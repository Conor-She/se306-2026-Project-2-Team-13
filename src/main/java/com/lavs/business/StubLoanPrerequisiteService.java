package com.lavs.business;

import com.lavs.model.Address;
import com.lavs.model.Email;
import com.lavs.model.Phone;
import java.util.List;

/** Temporary customer records for all Add Loan prerequisite checks. */
public class StubLoanPrerequisiteService {
    private final LoanPrerequisiteChecker checker = new LoanPrerequisiteChecker();
    private final StubLoanGuarantorService guarantorService = new StubLoanGuarantorService();

    /** Replace fixture records with backend lookups when connected. */
    public void validate(String customerId) {
        if (customerId == null || customerId.isBlank())
            throw new IllegalArgumentException("customerId must not be null or blank");
        // Liam has a residential address, but none marked as mailing.
        List<Address> addresses = List.of(new Address(null, customerId, "10 Sample Street",
                "Central", "Auckland", "1010", "New Zealand", !"C006".equals(customerId)));
        // Oliver has neither contact option; Sophie has email only; William has phone only.
        List<Phone> phones = List.of("C007", "C008").contains(customerId) ? List.of()
                : List.of(new Phone(null, customerId, "021 555 0100", true, false));
        List<Email> emails = List.of("C007", "C009").contains(customerId) ? List.of()
                : List.of(new Email(null, customerId, "sample@example.com", true));
        checker.checkAll(addresses, phones, emails);
        guarantorService.validate(customerId);
    }
}
