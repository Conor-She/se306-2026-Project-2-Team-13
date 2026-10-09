package com.lavs.business;

import com.lavs.model.CustomerDraft;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Registration validation until the real registration service is available. */
public class StubCustomerRegistrationService implements CustomerRegistrationService {
    private static final DateTimeFormatter DATE_OF_BIRTH_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final AtomicInteger sequence = new AtomicInteger(11);
    private final Map<String, CustomerDraft> saved = new LinkedHashMap<>();

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
        validateEntries("employers", draft.employers, errors);
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
        int step = kind.equals("employers") ? 2 : 1;
        List<String> required = switch (kind) {
            case "addresses" -> List.of("street", "city");
            case "phones" -> List.of("number");
            case "emails" -> List.of("email");
            case "employers" -> List.of("name");
            default -> throw new IllegalArgumentException("Unknown entry kind: " + kind);
        };
        for (String key : required)
            if (entry.getOrDefault(key, "").isBlank()) errors.add(new ValidationError(step, key, "This field is required."));
        String email = entry.getOrDefault("email", "");
        if (!email.isBlank() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            errors.add(new ValidationError(step, "email", "Enter a valid email address."));
        String income = entry.getOrDefault("income", "");
        if (kind.equals("employers") && !income.isBlank() && !income.matches("[0-9]+(\\.[0-9]{1,2})?"))
            errors.add(new ValidationError(step, "income", "Enter a non-negative annual income with up to two decimal places."));
        String number = entry.getOrDefault(kind.equals("employers") ? "phone" : "number", "");
        if (!number.isBlank() && !number.matches("\\+?[0-9 ()-]{7,20}"))
            errors.add(new ValidationError(step, kind.equals("employers") ? "phone" : "number", "Enter a valid phone number."));
        return List.copyOf(errors);
    }

    @Override public synchronized String save(CustomerDraft draft) throws RegistrationException {
        List<ValidationError> errors = validate(draft);
        if (!errors.isEmpty()) throw new RegistrationException(errors);
        String id = "C%03d".formatted(sequence.getAndIncrement());
        saved.put(id, draft.copy());
        return id;
    }

    public synchronized Map<String, CustomerDraft> savedCustomers() {
        Map<String, CustomerDraft> copy = new LinkedHashMap<>();
        saved.forEach((id, draft) -> copy.put(id, draft.copy()));
        return copy;
    }
}
