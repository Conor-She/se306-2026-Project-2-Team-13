package com.lavs.business;

import com.lavs.model.CustomerDraft;
import java.util.List;

/** UI boundary; replace the stub without changing the wizard. Field paths identify list indexes. */
public interface CustomerRegistrationService {
    record ValidationError(int step, String field, String message) {}
    List<ValidationError> validate(CustomerDraft draft);
    String save(CustomerDraft draft) throws RegistrationException;

    class RegistrationException extends Exception {
        private final List<ValidationError> errors;
        public RegistrationException(List<ValidationError> errors) {
            super("Customer could not be saved");
            this.errors = List.copyOf(errors);
        }
        public List<ValidationError> errors() { return errors; }
    }
}
