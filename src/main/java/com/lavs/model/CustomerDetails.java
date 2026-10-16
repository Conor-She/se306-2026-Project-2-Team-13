package com.lavs.model;

import java.util.List;
import java.util.Objects;

/** A customer, all of their contact and employment records, and their loans. */
public record CustomerDetails(Customer customer, List<Address> addresses, List<Phone> phones,
        List<Email> emails, List<Employer> employers, List<Loan> loans) {
    public CustomerDetails {
        Objects.requireNonNull(customer, "customer must not be null");
        addresses = List.copyOf(addresses);
        phones = List.copyOf(phones);
        emails = List.copyOf(emails);
        employers = List.copyOf(employers);
        loans = List.copyOf(loans);
    }

    /** Details without any loans. */
    public CustomerDetails(Customer customer, List<Address> addresses, List<Phone> phones,
            List<Email> emails, List<Employer> employers) {
        this(customer, addresses, phones, emails, employers, List.of());
    }
}