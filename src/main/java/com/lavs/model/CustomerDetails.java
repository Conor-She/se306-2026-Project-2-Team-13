package com.lavs.model;

import java.util.List;
import java.util.Objects;

/** A customer and all of their contact and employment records. */
public record CustomerDetails(Customer customer, List<Address> addresses, List<Phone> phones,
        List<Email> emails, List<Employer> employers, List<CustomerNote> notes) {
    public CustomerDetails(Customer customer, List<Address> addresses, List<Phone> phones,
            List<Email> emails, List<Employer> employers) {
        this(customer, addresses, phones, emails, employers, List.of());
    }

    public CustomerDetails {
        Objects.requireNonNull(customer, "customer must not be null");
        addresses = List.copyOf(addresses);
        phones = List.copyOf(phones);
        emails = List.copyOf(emails);
        employers = List.copyOf(employers);
        notes = List.copyOf(notes);
    }
}
