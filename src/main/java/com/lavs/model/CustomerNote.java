package com.lavs.model;

import java.time.LocalDate;

public class CustomerNote {
    private final String noteId;
    private final String customerId;
    private final String noteText;
    private final LocalDate dateAdded;

    public CustomerNote(String noteId, String customerId, String noteText,
            LocalDate dateAdded) {
        // Basic structural checks only
        if (noteId == null || noteId.isBlank()) {
            throw new IllegalArgumentException("noteId must not be null or blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }
        if (noteText == null || noteText.isBlank()) {
            throw new IllegalArgumentException("noteText must not be null or blank");
        }

        // Assign all fields once the note is confirmed valid
        this.noteId = noteId;
        this.customerId = customerId;
        this.noteText = noteText;
        this.dateAdded = dateAdded;
    }

    // Getters
    public String getNoteId() {
        return noteId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getNoteText() {
        return noteText;
    }

    public LocalDate getDateAdded() {
        return dateAdded;
    }
}