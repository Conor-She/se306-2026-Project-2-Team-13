package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CoBorrowerTest {

    // Confirms a fully valid CoBorrower builds and its fields are retrievable
    @Test
    void constructsWithValidFields() {
        CoBorrower coBorrower = new CoBorrower("CB001", "L001", "C002");

        assertEquals("CB001", coBorrower.getCoBorrowerId());
        assertEquals("C002", coBorrower.getCustomerId());
    }

    // A co-borrower link with no customer attached isn't meaningful, so an
    // empty customerId must be rejected
    @Test
    void rejectsBlankCustomerId() {
        assertThrows(IllegalArgumentException.class, () ->
                new CoBorrower("CB001", "L001", ""));
    }
}