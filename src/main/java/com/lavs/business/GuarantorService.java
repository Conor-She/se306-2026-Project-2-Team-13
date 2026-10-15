package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.Customer;
import com.lavs.model.Guarantor;

/** Business logic for creating, editing and deleting customer guarantors. */
public class GuarantorService {
    private final MainframeGateway gateway;

    public GuarantorService(MainframeGateway gateway) {
        if (gateway == null) {
            throw new IllegalArgumentException("gateway must not be null");
        }
        this.gateway = gateway;
    }

    public Guarantor addGuarantor(
            String customerId,
            String guarantorCustomerId,
            String relationshipType) {
        validateRelationship(customerId, guarantorCustomerId, relationshipType);
        return gateway.addGuarantor(
                new Guarantor(null, customerId, guarantorCustomerId, relationshipType));
    }

    public Guarantor updateGuarantor(
            String guarantorId,
            String customerId,
            String guarantorCustomerId,
            String relationshipType) {
        requireNotBlank(guarantorId, "guarantorId");
        validateRelationship(customerId, guarantorCustomerId, relationshipType);
        return gateway.updateGuarantor(
                new Guarantor(
                        guarantorId,
                        customerId,
                        guarantorCustomerId,
                        relationshipType));
    }

    public void deleteGuarantor(String customerId, String guarantorId) {
        requireNotBlank(customerId, "customerId");
        requireNotBlank(guarantorId, "guarantorId");
        gateway.deleteGuarantor(customerId, guarantorId);
    }

    private void validateRelationship(
            String customerId,
            String guarantorCustomerId,
            String relationshipType) {
        requireNotBlank(customerId, "customerId");
        requireNotBlank(guarantorCustomerId, "guarantorCustomerId");
        requireNotBlank(relationshipType, "relationshipType");

        if (customerId.equals(guarantorCustomerId)) {
            throw new IllegalArgumentException(
                    "customerId and guarantorCustomerId must differ");
        }

        requireExistingCustomer(customerId);
        requireExistingCustomer(guarantorCustomerId);
    }

    private void requireExistingCustomer(String customerId) {
        Customer customer = gateway.getCustomer(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Customer '" + customerId + "' does not exist");
        }
    }

    private void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }
}
