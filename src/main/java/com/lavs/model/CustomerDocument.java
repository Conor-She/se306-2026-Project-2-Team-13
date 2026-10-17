package com.lavs.model;

import java.time.LocalDate;

public class CustomerDocument {

    private final String documentId;
    private final String customerId;
    private final String fileName;
    private final String documentType;
    private final LocalDate uploadDate;

    public CustomerDocument(String documentId, String customerId, String fileName,
            String documentType, LocalDate uploadDate) {
        // The mainframe assigns the ID when a new document is created.
        if (documentId != null && documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be null or blank");
        }
        if (documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException("documentType must not be null or blank");
        }
        // Existing mainframe records may not include an upload date.
        if (uploadDate == null && documentId == null) {
            throw new IllegalArgumentException("uploadDate must not be null");
        }

        this.documentId = documentId;
        this.customerId = customerId;
        this.fileName = fileName;
        this.documentType = documentType;
        this.uploadDate = uploadDate;
    }

    // Getters
    public String getDocumentId() {
        return documentId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDocumentType() {
        return documentType;
    }

    /** Returns null when a persisted backend record has no upload date. */
    public LocalDate getUploadDate() {
        return uploadDate;
    }
}
