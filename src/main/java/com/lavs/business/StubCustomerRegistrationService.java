package com.lavs.business;

import com.lavs.model.CustomerDraft;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Personal-details validation until the real registration service is available. */
public class StubCustomerRegistrationService implements CustomerRegistrationService {
    private static final DateTimeFormatter DATE_OF_BIRTH_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
    @Override public List<ValidationError> validate(CustomerDraft draft) {
        List<ValidationError> errors = new ArrayList<>();
        for (String key : List.of("firstName", "lastName", "dateOfBirth")) {
            if (draft.personal.getOrDefault(key, "").isBlank())
                errors.add(new ValidationError(0, key, "This field is required."));
        }
        String date = draft.personal.getOrDefault("dateOfBirth", "");
        if (!date.isBlank()) {
            try {
                if (!LocalDate.parse(date, DATE_OF_BIRTH_FORMAT).isBefore(LocalDate.now()))
                    errors.add(new ValidationError(0, "dateOfBirth", "Enter a date before today."));
            } catch (DateTimeParseException e) {
                errors.add(new ValidationError(0, "dateOfBirth", "Enter a valid date in dd-mm-yyyy format."));
            }
        }
        return List.copyOf(errors);
    }
}
