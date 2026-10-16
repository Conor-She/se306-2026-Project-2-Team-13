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

    public List<CustomerNote> getNotes(String customerId) {
        requireText(customerId, "Customer ID");
        return List.copyOf(gateway.getNotes(customerId));
    }

    public CustomerNote addNote(String customerId, String text) { return addNote(customerId, text, "General"); }

    public CustomerNote addNote(String customerId, String text, String type) {
        return gateway.addNote(new CustomerNote(null, customerId, text, LocalDate.now(), type, null));
    }

    public CustomerNote updateNote(CustomerNote note) {
        Objects.requireNonNull(note, "note");
        requireText(note.getNoteId(), "Note ID");
        return gateway.updateNote(note);
    }

    public void deleteNote(String customerId, String noteId) {
        requireText(customerId, "Customer ID");
        requireText(noteId, "Note ID");
        gateway.deleteNote(customerId, noteId);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
