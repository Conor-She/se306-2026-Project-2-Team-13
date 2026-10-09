package com.lavs.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Unsaved customer registration details. */
public class CustomerDraft {
    /** Options such as phone type or primary status alone are not contact details. */
    public static boolean hasEntryDetails(String kind, Map<String, String> entry) {
        List<String> keys = switch (kind) {
            case "addresses" -> List.of("street", "suburb", "city", "postcode", "country");
            case "phones" -> List.of("number");
            case "emails" -> List.of("email");
            case "employers" -> List.of("name", "role", "address", "phone", "employmentType", "income");
            default -> throw new IllegalArgumentException("Unknown entry kind: " + kind);
        };
        return keys.stream().anyMatch(key -> !entry.getOrDefault(key, "").isBlank());
    }
    public final Map<String, String> personal = new LinkedHashMap<>();

    public final List<Map<String, String>> addresses = new ArrayList<>();
    public final List<Map<String, String>> phones = new ArrayList<>();
    public final List<Map<String, String>> emails = new ArrayList<>();

    public final List<Map<String, String>> employers = new ArrayList<>();

    public boolean hasData() {
        return personal.values().stream().anyMatch(value -> !value.isBlank())
                || !addresses.isEmpty() || !phones.isEmpty() || !emails.isEmpty() || !employers.isEmpty();
    }
    public CustomerDraft copy() {
        CustomerDraft copy = new CustomerDraft();
        copy.personal.putAll(personal);
        copyEntries(addresses, copy.addresses);
        copyEntries(phones, copy.phones);
        copyEntries(emails, copy.emails);
        copyEntries(employers, copy.employers);
        return copy;
    }

    private static void copyEntries(List<Map<String, String>> from, List<Map<String, String>> to) {
        from.forEach(entry -> to.add(new LinkedHashMap<>(entry)));
    }
}
