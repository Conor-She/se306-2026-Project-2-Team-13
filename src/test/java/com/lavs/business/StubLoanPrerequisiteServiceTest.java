package com.lavs.business;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class StubLoanPrerequisiteServiceTest {
    private final StubLoanPrerequisiteService service = new StubLoanPrerequisiteService();

    @Test void residentialAddressDoesNotSatisfyMailingRequirement() {
        var error = assertThrows(LoanPrerequisiteException.class, () -> service.validate("C006"));
        assertEquals(LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE, error.getMessage());
    }

    @Test void missingBothContactOptionsIsBlocked() {
        var error = assertThrows(LoanPrerequisiteException.class, () -> service.validate("C007"));
        assertEquals(LoanPrerequisiteChecker.CONTACT_MESSAGE, error.getMessage());
    }

    @ParameterizedTest @ValueSource(strings = {"C001", "C004", "C008", "C009"})
    void validCustomersIncludingSingleContactOptionsPass(String customerId) {
        assertDoesNotThrow(() -> service.validate(customerId));
    }

    @Test void stillChecksGuarantorAfterAddressAndContact() {
        assertThrows(LoanPrerequisiteException.class, () -> service.validate("C003"));
    }
}
