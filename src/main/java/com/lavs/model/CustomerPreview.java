package com.lavs.model;

/** Search result containing only identifying information, without customer details. */
public record CustomerPreview(CustomerId customerId, String title, String firstName,
        String lastName) {
}
