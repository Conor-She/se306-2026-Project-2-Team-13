package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.CustomerNote;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** DFD 5.0: customer-scoped note operations through the mainframe gateway. */
public final class CustomerNoteService {
    private final MainframeGateway gateway;

    public CustomerNoteService(MainframeGateway gateway) { this.gateway = Objects.requireNonNull(gateway); }

    // Load the notes belonging to one customer.
    public List<CustomerNote> getNotes(String customerId) {
        requireText(customerId, "Customer ID");
        return List.copyOf(gateway.getNotes(customerId));
    }

    public CustomerNote addNote(String customerId, String text) { return addNote(customerId, text, "General"); }

    // Create a note with today's date and the selected type.
    public CustomerNote addNote(String customerId, String text, String type) {
        return gateway.addNote(new CustomerNote(null, customerId, text, LocalDate.now(), type, null));
    }

    // Save changed text or type for an existing note.
    public CustomerNote updateNote(CustomerNote note) {
        Objects.requireNonNull(note, "note");
        requireText(note.getNoteId(), "Note ID");
        return gateway.updateNote(note);
    }

    // Delete the note identified within this customer.
    public void deleteNote(String customerId, String noteId) {
        requireText(customerId, "Customer ID");
        requireText(noteId, "Note ID");
        gateway.deleteNote(customerId, noteId);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
