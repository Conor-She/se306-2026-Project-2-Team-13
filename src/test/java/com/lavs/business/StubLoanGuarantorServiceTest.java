package com.lavs.business;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StubLoanGuarantorServiceTest {
    private final StubLoanGuarantorService service = new StubLoanGuarantorService();

    @Test void unemployedCustomerWithoutGuarantorIsBlocked() {
        assertThrows(LoanPrerequisiteException.class, () -> service.validate("C003"));
    }

    @Test void unemployedCustomerWithGuarantorCanProceed() {
        assertDoesNotThrow(() -> service.validate("C004"));
    }

    @Test void employedCustomerDoesNotNeedGuarantor() {
        assertDoesNotThrow(() -> service.validate("C001"));
    }
}
