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

/**
 * In-memory registration service. Its validation rules are the ones the real
 * {@link MainframeCustomerRegistrationService} uses; its in-memory saving is only used by tests.
 */
public class StubCustomerRegistrationService implements CustomerRegistrationService {
    private static final DateTimeFormatter DATE_OF_BIRTH_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final AtomicInteger sequence = new AtomicInteger(11);
    private final Map<String, CustomerDraft> saved = new LinkedHashMap<>();

    // Validate the personal details and every related record in the draft.
    @Override public List<ValidationError> validate(CustomerDraft draft) {
        List<ValidationError> errors = new ArrayList<>();
        for (String key : List.of("title", "firstName", "lastName", "dateOfBirth", "nationality")) {
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
        validateEntries("notes", draft.notes, errors);
        validateEntries("documents", draft.documents, errors);
        for (String kind : List.of("addresses", "phones", "emails")) {
            var entries = kind.equals("addresses") ? draft.addresses : kind.equals("phones") ? draft.phones : draft.emails;
            String flag = kind.equals("addresses") ? "mailing" : "primary";
            if (entries.stream().filter(entry -> "true".equals(entry.get(flag))).count() > 1)
                errors.add(new ValidationError(1, "", "Select only one " + flag + " " + kind + "."));
        }
        return List.copyOf(errors);
    }

    // Add each entry's errors with its list position included in the field path.
    private void validateEntries(String kind, List<Map<String, String>> entries, List<ValidationError> errors) {
        for (int i = 0; i < entries.size(); i++) {
            for (ValidationError error : validateEntry(kind, entries.get(i)))
                errors.add(new ValidationError(error.step(), kind + "." + i + "." + error.field(), error.message()));
        }
    }

    // Apply the rules that belong to one address, contact or supporting record.
    private List<ValidationError> validateEntry(String kind, Map<String, String> entry) {
        List<ValidationError> errors = new ArrayList<>();
        int step = kind.equals("documents") ? 4 : kind.equals("notes") ? 3 : kind.equals("employers") ? 2 : 1;
        List<String> required = switch (kind) {
            case "addresses" -> List.of("street", "suburb", "city", "postcode", "country");
            case "phones" -> List.of("number");
            case "emails" -> List.of("email");
            case "employers" -> List.of("name", "role", "employmentType", "income", "phone");
            case "notes" -> List.of("text");
            case "documents" -> List.of("fileName", "documentType", "uploadDate");
            default -> throw new IllegalArgumentException("Unknown entry kind: " + kind);
        };
        for (String key : required)
            if (entry.getOrDefault(key, "").isBlank()) errors.add(new ValidationError(step, key, "This field is required."));
        String email = entry.getOrDefault("email", "");
        if (!email.isBlank() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            errors.add(new ValidationError(step, "email", "Enter a valid email address."));
        String income = entry.getOrDefault("income", "");
        String postcode = kind.equals("addresses") ? entry.getOrDefault("postcode", "") : "";
        if (!postcode.isBlank() && !postcode.matches("[0-9]+"))
            errors.add(new ValidationError(step, "postcode", "Enter a postcode using digits only."));
        if (kind.equals("employers") && !income.isBlank() && !income.matches("[0-9]+(\\.[0-9]{1,2})?"))
            errors.add(new ValidationError(step, "income", "Enter a non-negative annual income with up to two decimal places."));
        if (kind.equals("documents") && !entry.getOrDefault("uploadDate", "").isBlank()) {
            try {
                java.time.LocalDate.parse(entry.get("uploadDate"));
            } catch (java.time.DateTimeException invalid) {
                errors.add(new ValidationError(step, "uploadDate", "Use a valid date in yyyy-MM-dd format."));
            }
        }
        String number = kind.equals("phones") ? entry.getOrDefault("number", "") : "";
        String employerPhone = kind.equals("employers") ? entry.getOrDefault("phone", "") : "";
        if (!employerPhone.isBlank() && !employerPhone.matches("\\+?[0-9 ()-]{7,20}"))
            errors.add(new ValidationError(step, "phone", "Enter a valid phone number."));
        if (!number.isBlank() && !number.matches("\\+?[0-9 ()-]{7,20}"))
            errors.add(new ValidationError(step, "number", "Enter a valid phone number."));
        return List.copyOf(errors);
    }

    // Store a copy only after every validation rule passes.
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
