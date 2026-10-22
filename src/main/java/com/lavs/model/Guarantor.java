package com.lavs.model;

// Links a customer to the separate customer who guarantees their loan.
public class Guarantor {

    private final String guarantorId;
    private final String customerId;
    private final String guarantorCustomerId;
    private final String relationshipType;

    public Guarantor(String guarantorId, String customerId, String guarantorCustomerId,
            String relationshipType) {
        if (guarantorId != null && guarantorId.isBlank()) {
            throw new IllegalArgumentException("guarantorId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }
        if (guarantorCustomerId == null || guarantorCustomerId.isBlank()) {
            throw new IllegalArgumentException("guarantorCustomerId must not be null or blank");
        }
        // A customer cannot be their own guarantor
        if (customerId.equals(guarantorCustomerId)) {
            throw new IllegalArgumentException("customerId and guarantorCustomerId must differ");
        }
        if (relationshipType == null || relationshipType.isBlank()) {
            throw new IllegalArgumentException("relationshipType must not be null or blank");
        }

        // Assign all fields once the guarantor link is confirmed valid
        this.guarantorId = guarantorId;
        this.customerId = customerId;
        this.guarantorCustomerId = guarantorCustomerId;
        this.relationshipType = relationshipType;
    }

    // Getters only
    public String getGuarantorId() {
        return guarantorId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getGuarantorCustomerId() {
        return guarantorCustomerId;
    }

    public String getRelationshipType() {
        return relationshipType;
    }
}
