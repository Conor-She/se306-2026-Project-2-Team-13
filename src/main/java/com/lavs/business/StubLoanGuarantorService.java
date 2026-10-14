package com.lavs.business;

import com.lavs.model.Employer;
import com.lavs.model.Guarantor;
import java.util.List;

/** Temporary employment/guarantor fixtures for the Add Loan wizard. */
public class StubLoanGuarantorService {
    private final GuarantorRequirementValidator validator = new GuarantorRequirementValidator();

    /** Replace fixture lookups with the customer's backend records when connected. */
    public void validate(String customerId) {
        List<Employer> employers = switch (customerId) {
            // Charlotte has neither an employer nor a guarantor; Ethan has a guarantor.
            case "C003", "C004" -> List.of();
            default -> List.of(new Employer(null, customerId, "Sample employer", "Employee", 60000, "Full-time"));
        };
        List<Guarantor> guarantors = "C004".equals(customerId)
                ? List.of(new Guarantor("G004", "C004", "C002", "Family")) : List.of();
        validator.validate(customerId, employers, guarantors);
    }
}
