package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CustomerDocumentTest {

    // Confirms a fully valid CustomerDocument builds and its fields are retrievable
    @Test
    void constructsWithValidFields() {
        CustomerDocument document = new CustomerDocument("D001", "C001", "payslip.pdf",
                "Payslip", LocalDate.of(2026, 3, 1));

        assertEquals("D001", document.getDocumentId());
        assertEquals("Payslip", document.getDocumentType());
    }

    // A document with no file name must be rejected
    @Test
    void rejectsBlankFileName() {
        assertThrows(IllegalArgumentException.class, () ->
                new CustomerDocument("D001", "C001", "", "Payslip", LocalDate.now()));
    }
}