package com.lavs.business;

import com.lavs.model.CustomerDraft;
import java.util.List;

/** Validation boundary for customer personal details. */
public interface CustomerRegistrationService {
    record ValidationError(int step, String field, String message) {}
    List<ValidationError> validate(CustomerDraft draft);
}
