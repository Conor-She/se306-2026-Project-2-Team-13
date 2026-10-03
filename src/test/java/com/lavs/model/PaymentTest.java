package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentTest {

    // Confirms a fully valid Payment (linked to a repayment) builds correctly
    @Test
    void constructsWithValidFields() {
        Payment payment = new Payment("P001", "L001", "R001", LocalDate.of(2026, 2, 1), 2500);

        assertEquals("P001", payment.getPaymentId());
        assertEquals("R001", payment.getRepaymentId());
    }

    // a payment not tied to a specific scheduled repayment must still construct cleanly
    @Test
    void compilesAndConstructsWithNullRepaymentId() {
        Payment payment = assertDoesNotThrow(() ->
                new Payment("P002", "L001", null, LocalDate.of(2026, 2, 1), 500));

        assertNull(payment.getRepaymentId());
    }

    // A payment of zero (or less) must be rejected
    @Test
    void rejectsNonPositiveAmountPaid() {
        assertThrows(IllegalArgumentException.class, () ->
                new Payment("P001", "L001", "R001", LocalDate.now(), 0));
    }
}