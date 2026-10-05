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
        // Basic structural checks only
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be null or blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be null or blank");
        }

        // Assign all fields once the document record is confirmed valid
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

    public LocalDate getUploadDate() {
        return uploadDate;
    }
}