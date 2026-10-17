package com.lavs.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A customer and their persisted contact, employment, loan and supporting records. */
public record CustomerDetails(Customer customer, List<Address> addresses, List<Phone> phones,
        List<Email> emails, List<Employer> employers, List<Loan> loans,
        List<CustomerDocument> documents, List<Guarantor> guarantors,
        Map<String, String> guarantorNames) {
    public CustomerDetails {
        Objects.requireNonNull(customer, "customer must not be null");
        addresses = List.copyOf(addresses);
        phones = List.copyOf(phones);
        emails = List.copyOf(emails);
        employers = List.copyOf(employers);
        loans = List.copyOf(loans);
        documents = List.copyOf(documents);
        guarantors = List.copyOf(guarantors);
        guarantorNames = Map.copyOf(guarantorNames);
    }

    public CustomerDetails(Customer customer, List<Address> addresses, List<Phone> phones,
            List<Email> emails, List<Employer> employers, List<Loan> loans,
            List<CustomerDocument> documents, List<Guarantor> guarantors) {
        this(customer, addresses, phones, emails, employers, loans, documents, guarantors, Map.of());
    }

    public CustomerDetails(Customer customer, List<Address> addresses, List<Phone> phones,
            List<Email> emails, List<Employer> employers, List<Loan> loans) {
        this(customer, addresses, phones, emails, employers, loans, List.of(), List.of());
    }

    /** Details without any loans. */
    public CustomerDetails(Customer customer, List<Address> addresses, List<Phone> phones,
            List<Email> emails, List<Employer> employers) {
        this(customer, addresses, phones, emails, employers, List.of());
    }
}
