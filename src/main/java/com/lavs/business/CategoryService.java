package com.lavs.business;

import com.lavs.data.MainframeGateway;
import java.util.List;

/** Retrieves the current values for a mainframe-managed category. */
public class CategoryService {
    private final MainframeGateway gateway;

    public CategoryService(MainframeGateway gateway) {
        if (gateway == null) {
            throw new IllegalArgumentException("gateway must not be null");
        }
        this.gateway = gateway;
    }

    public List<String> get(String categoryType) {
        if (categoryType == null || categoryType.isBlank()) {
            throw new IllegalArgumentException("categoryType must not be null or blank");
        }
        return List.copyOf(gateway.getCategoryValues(categoryType.strip()));
    }
}
