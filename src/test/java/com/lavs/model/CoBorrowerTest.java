package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CoBorrowerTest {

    @Test
    void constructsWithValidFields() {
        CoBorrower coBorrower = new CoBorrower("CB001", "L001", "C002");

        assertEquals("CB001", coBorrower.getCoBorrowerId());
        assertEquals("C002", coBorrower.getCustomerId());
    }

    @Test
    void rejectsBlankCustomerId() {
        assertThrows(IllegalArgumentException.class, () ->
                new CoBorrower("CB001", "L001", ""));
    }
}