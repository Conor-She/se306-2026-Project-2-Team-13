package com.lavs.model;

/** Identifies an existing customer. */
public record CustomerId(String value) {
    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }
    }
}
