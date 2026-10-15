package com.lavs.model;

import java.time.LocalDate;

/** Search result containing only identifying information, without customer details. */
public record CustomerPreview(CustomerId customerId, String title, String firstName,
        String lastName, LocalDate dateOfBirth) {
}
