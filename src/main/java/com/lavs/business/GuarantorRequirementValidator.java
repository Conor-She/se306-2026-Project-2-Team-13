package com.lavs.business;

import java.util.Collection;

import com.lavs.model.Employer;
import com.lavs.model.Guarantor;

public class GuarantorRequirementValidator {
    static final String GUARANTOR_REQUIRED_MESSAGE =
            "A customer with no employer must have at least one guarantor before a loan can be created.";

    public void validate(
            String customerId,
            Collection<Employer> employers,
            Collection<Guarantor> guarantors) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }

        // checks if the customer has an employer, if so, no guarantor is required
        boolean hasEmployer = employers != null && employers.stream()
                .anyMatch(employer -> customerId.equals(employer.getCustomerId()));

        if (hasEmployer) {
            return;
        }

        boolean hasGuarantor = guarantors != null && guarantors.stream()
                .anyMatch(guarantor -> customerId.equals(guarantor.getCustomerId()));

        // if no throw an exception
        if (!hasGuarantor) {
            throw new LoanPrerequisiteException(GUARANTOR_REQUIRED_MESSAGE);
        }
    }
}
