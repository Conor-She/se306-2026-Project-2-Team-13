package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lavs.model.Employer;
import com.lavs.model.Guarantor;

class GuarantorRequirementValidatorTest {
    private static final String CUSTOMER_ID = "C001";

    private final GuarantorRequirementValidator validator = new GuarantorRequirementValidator();

    @Test
    void rejectsUnemployedCustomerWithoutGuarantor() {
        LoanPrerequisiteException exception = assertThrows(
                LoanPrerequisiteException.class,
                () -> validator.validate(CUSTOMER_ID, List.of(), List.of()));

        assertEquals(GuarantorRequirementValidator.GUARANTOR_REQUIRED_MESSAGE, exception.getMessage());
    }

    @Test
    void acceptsUnemployedCustomerWithGuarantor() {
        Guarantor guarantor = new Guarantor("G001", CUSTOMER_ID, "C002", "Parent");

        assertDoesNotThrow(() -> validator.validate(
                CUSTOMER_ID,
                List.of(),
                List.of(guarantor)));
    }

    @Test
    void acceptsEmployedCustomerWithoutGuarantor() {
        Employer employer = new Employer(
                1L,
                CUSTOMER_ID,
                "Example Limited",
                "Engineer",
                85000.00,
                "Full-time");

        assertDoesNotThrow(() -> validator.validate(
                CUSTOMER_ID,
                List.of(employer),
                List.of()));
    }

    @Test
    void ignoresEmployersAndGuarantorsBelongingToOtherCustomers() {
        Employer otherEmployer = new Employer(
                1L,
                "C999",
                "Example Limited",
                "Engineer",
                85000.00,
                "Full-time");
        Guarantor otherGuarantor = new Guarantor("G001", "C999", "C002", "Parent");

        assertThrows(
                LoanPrerequisiteException.class,
                () -> validator.validate(
                        CUSTOMER_ID,
                        List.of(otherEmployer),
                        List.of(otherGuarantor)));
    }
}
