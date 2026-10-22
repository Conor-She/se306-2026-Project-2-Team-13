package com.lavs.business;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules for the details entered when adding or editing a record on a customer's page
 * (personal details, contacts, employment, loans, documents and so on). The screen says which
 * fields its form has, and shows the messages this returns beside the matching fields.
 */
public final class CustomerRecordValidator {
    /** One field on a form: its key and, for a drop-down, the values it allows. */
    public record FieldSpec(String key, List<String> options) {}

    static final String REQUIRED_MESSAGE = "This field is required.";
    static final String EMAIL_MESSAGE = "Enter a valid email address.";
    static final String PHONE_MESSAGE = "Enter a valid phone number.";
    static final String RATE_MESSAGE = "Enter a rate from 0 to 100.";
    static final String AMOUNT_MESSAGE = "Enter a non-negative amount with up to two decimal places.";
    static final String NUMBER_MESSAGE = "Enter a valid number.";
    static final String BIRTH_DATE_PAST_MESSAGE = "Enter a date before today.";
    static final String BIRTH_DATE_FORMAT_MESSAGE = "Use a valid date in dd-MM-yyyy format.";
    static final String UPLOAD_DATE_MESSAGE = "Choose a valid upload date.";
    static final String PICK_FROM_LIST_MESSAGE = "Select a value from the list.";

    private static final String EMAIL_PATTERN = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+";
    private static final String PHONE_PATTERN = "\\+?[0-9 ()-]{7,20}";
    private static final List<String> AMOUNT_FIELDS = List.of("income", "amount", "rate", "paymentAmount");
    private static final DateTimeFormatter BIRTH_DATE =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Returns an error message for each field that is not acceptable, keyed by field. Empty means valid. */
    public Map<String, String> validate(List<FieldSpec> fields, Map<String, String> values) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldSpec field : fields) {
            String message = fieldError(field, values.getOrDefault(field.key(), ""));
            if (message != null) errors.put(field.key(), message);
        }
        if (fields.stream().anyMatch(field -> field.key().equals("contactType"))) {
            checkContact(values, errors);
        }
        return errors;
    }

    private static String fieldError(FieldSpec field, String value) {
        String key = field.key();
        if (value.isBlank()) return REQUIRED_MESSAGE;
        if (key.equals("email") && !value.matches(EMAIL_PATTERN)) return EMAIL_MESSAGE;
        if (key.equals("phone") && !value.matches(PHONE_PATTERN)) return PHONE_MESSAGE;
        if (AMOUNT_FIELDS.contains(key)) return amountError(key, value);
        if (key.equals("birthDate")) return birthDateError(value);
        if (key.equals("date")) return uploadDateError(value);
        if (!isSpecial(key) && !field.options().isEmpty() && !field.options().contains(value)) {
            return PICK_FROM_LIST_MESSAGE;
        }
        return null;
    }

    // Fields with their own rule above are not also checked against a list
    private static boolean isSpecial(String key) {
        return key.equals("email") || key.equals("phone") || AMOUNT_FIELDS.contains(key)
                || key.equals("birthDate") || key.equals("date");
    }

    private static String amountError(String key, String value) {
        try {
            BigDecimal number = new BigDecimal(value);
            boolean badShape = !value.matches("[0-9]+(\\.[0-9]{1,2})?") || number.signum() < 0
                    || (key.equals("rate") && number.compareTo(new BigDecimal("100")) > 0);
            if (badShape) return key.equals("rate") ? RATE_MESSAGE : AMOUNT_MESSAGE;
            return null;
        } catch (NumberFormatException invalid) {
            return NUMBER_MESSAGE;
        }
    }

    private static String birthDateError(String value) {
        try {
            return LocalDate.parse(value, BIRTH_DATE).isBefore(LocalDate.now()) ? null : BIRTH_DATE_PAST_MESSAGE;
        } catch (java.time.DateTimeException invalid) {
            return BIRTH_DATE_FORMAT_MESSAGE;
        }
    }

    private static String uploadDateError(String value) {
        try {
            LocalDate.parse(value);
            return null;
        } catch (java.time.DateTimeException invalid) {
            return UPLOAD_DATE_MESSAGE;
        }
    }

    // A contact record is an email, a phone number or an address, and what "details" must hold
    // depends on which
    private static void checkContact(Map<String, String> values, Map<String, String> errors) {
        String value = values.getOrDefault("value", "");
        String type = values.getOrDefault("contactType", "");
        if (!value.isBlank() && type.equals("Email") && !value.matches(EMAIL_PATTERN)) {
            errors.put("value", EMAIL_MESSAGE);
        }
        if (!value.isBlank() && type.equals("Phone") && !value.matches(PHONE_PATTERN)) {
            errors.put("value", PHONE_MESSAGE);
        }
        if (type.equals("Address")) {
            errors.remove("value");
            for (String key : List.of("street", "city", "country")) {
                if (values.getOrDefault(key, "").isBlank()) errors.put(key, REQUIRED_MESSAGE);
            }
        }
    }
}
