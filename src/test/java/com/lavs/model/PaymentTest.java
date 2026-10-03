package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void constructsWithValidFields() {
        Payment payment = new Payment("P001", "L001", "R001", LocalDate.of(2026, 2, 1), 2500);

        assertEquals("P001", payment.getPaymentId());
        assertEquals("R001", payment.getRepaymentId());
    }

    @Test
    void compilesAndConstructsWithNullRepaymentId() {
        // this is the key acceptance criterion for this ticket -
        // a payment not tied to a specific scheduled repayment
        Payment payment = assertDoesNotThrow(() ->
                new Payment("P002", "L001", null, LocalDate.of(2026, 2, 1), 500));

        assertNull(payment.getRepaymentId());
    }

    @Test
    void rejectsNonPositiveAmountPaid() {
        assertThrows(IllegalArgumentException.class, () ->
                new Payment("P001", "L001", "R001", LocalDate.now(), 0));
    }
}