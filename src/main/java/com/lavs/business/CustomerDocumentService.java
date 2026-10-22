package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.CustomerDocument;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Coordinates metadata-only document operations for a customer. */
public final class CustomerDocumentService {
    private final MainframeGateway gateway;

    public CustomerDocumentService(MainframeGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    // Load every document attached to the selected customer.
    public List<CustomerDocument> getDocuments(String customerId) {
        requireText(customerId, "customerId");
        return List.copyOf(gateway.getDocuments(customerId));
    }

    // Create document metadata; the mainframe assigns its ID.
    public CustomerDocument addDocument(String customerId, String fileName,
            String documentType, LocalDate uploadDate) {
        return gateway.addDocument(new CustomerDocument(null, customerId, fileName,
                documentType, uploadDate));
    }

    // Replace the stored metadata for an existing document.
    public CustomerDocument updateDocument(CustomerDocument document) {
        Objects.requireNonNull(document, "document");
        requireText(document.getDocumentId(), "documentId");
        return gateway.updateDocument(document);
    }

    // Remove one document after checking both identifiers.
    public void deleteDocument(String customerId, String documentId) {
        requireText(customerId, "customerId");
        requireText(documentId, "documentId");
        gateway.deleteDocument(customerId, documentId);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
