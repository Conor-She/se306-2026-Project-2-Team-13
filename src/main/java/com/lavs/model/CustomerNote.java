package com.lavs.model;

import java.time.LocalDate;

// Stores a dated note attached to a customer.
public class CustomerNote {
    private final String noteId;
    private final String customerId;
    private final String noteText;
    private final LocalDate dateAdded;
    private final String type;
    private final String author;

    public CustomerNote(String noteId, String customerId, String noteText,
            LocalDate dateAdded) {
        this(noteId, customerId, noteText, dateAdded, "General", null);
    }

    /** A null ID represents a new note awaiting its mainframe-assigned ID. */
    public CustomerNote(String noteId, String customerId, String noteText,
            LocalDate dateAdded, String type, String author) {
        // Basic structural checks only
        if (noteId != null && noteId.isBlank()) {
            throw new IllegalArgumentException("noteId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or blank");
        }
        if (noteText == null || noteText.isBlank()) {
            throw new IllegalArgumentException("noteText must not be null or blank");
        }
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Note title is required");

        // Assign all fields once the note is confirmed valid
        this.noteId = noteId;
        this.customerId = customerId;
        this.noteText = noteText;
        this.dateAdded = dateAdded;
        this.type = type;
        this.author = author;
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

    public String getType() { return type; }
    public String getAuthor() { return author; }
}
