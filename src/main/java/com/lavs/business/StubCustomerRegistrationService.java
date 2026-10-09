package com.lavs.business;

import com.lavs.model.CustomerDraft;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        validateEntries("addresses", draft.addresses, errors);
        validateEntries("phones", draft.phones, errors);
        validateEntries("emails", draft.emails, errors);
        for (String kind : List.of("addresses", "phones", "emails")) {
            var entries = kind.equals("addresses") ? draft.addresses : kind.equals("phones") ? draft.phones : draft.emails;
            String flag = kind.equals("addresses") ? "mailing" : "primary";
            if (entries.stream().filter(entry -> "true".equals(entry.get(flag))).count() > 1)
                errors.add(new ValidationError(1, "", "Select only one " + flag + " " + kind + "."));
        }
        return List.copyOf(errors);
    }
    private void validateEntries(String kind, List<Map<String, String>> entries, List<ValidationError> errors) {
        for (int i = 0; i < entries.size(); i++) {
            for (ValidationError error : validateEntry(kind, entries.get(i)))
                errors.add(new ValidationError(error.step(), kind + "." + i + "." + error.field(), error.message()));
        }
    }

    private List<ValidationError> validateEntry(String kind, Map<String, String> entry) {
        List<ValidationError> errors = new ArrayList<>();
        int step = 1;
        List<String> required = switch (kind) {
            case "addresses" -> List.of("street", "city");
            case "phones" -> List.of("number");
            case "emails" -> List.of("email");
            default -> throw new IllegalArgumentException("Unknown entry kind: " + kind);
        };
        for (String key : required)
            if (entry.getOrDefault(key, "").isBlank()) errors.add(new ValidationError(step, key, "This field is required."));
        String email = entry.getOrDefault("email", "");
        if (!email.isBlank() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            errors.add(new ValidationError(step, "email", "Enter a valid email address."));
        String number = entry.getOrDefault("number", "");
        if (!number.isBlank() && !number.matches("\\+?[0-9 ()-]{7,20}"))
            errors.add(new ValidationError(step, "number", "Enter a valid phone number."));
        return List.copyOf(errors);
    }

}
