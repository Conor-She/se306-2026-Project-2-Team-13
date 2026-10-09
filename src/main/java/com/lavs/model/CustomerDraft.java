package com.lavs.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Unsaved personal details. */
public class CustomerDraft {
    public final Map<String, String> personal = new LinkedHashMap<>();

    public boolean hasData() {
        return personal.values().stream().anyMatch(value -> !value.isBlank());
    }
}
