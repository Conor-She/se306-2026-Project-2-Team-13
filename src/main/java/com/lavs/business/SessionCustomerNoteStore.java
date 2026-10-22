package com.lavs.business;

import com.lavs.model.CustomerNote;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Customer notes retained for the current application session. */
public final class SessionCustomerNoteStore {
    private final Map<String, CustomerNote> notes = new LinkedHashMap<>();

    // Return only notes belonging to the requested customer.
    public List<CustomerNote> getNotes(String customerId) {
        requireCustomer(customerId);
        return notes.values().stream().filter(n -> customerId.equals(n.getCustomerId())).toList();
    }

    // Add an in-memory note with a temporary unique ID.
    public CustomerNote addNote(String customerId, String text) {
        CustomerNote note = new CustomerNote(UUID.randomUUID().toString(), customerId, text, LocalDate.now());
        notes.put(note.getNoteId(), note);
        return note;
    }

    // Replace an existing note while retaining its original date.
    public CustomerNote updateNote(String customerId, String noteId, String text) {
        CustomerNote previous = find(customerId, noteId);
        CustomerNote updated = new CustomerNote(noteId, customerId, text, previous.getDateAdded());
        notes.put(noteId, updated);
        return updated;
    }

    // Confirm ownership before removing the note.
    public void deleteNote(String customerId, String noteId) {
        find(customerId, noteId);
        notes.remove(noteId);
    }

    private CustomerNote find(String customerId, String noteId) {
        requireCustomer(customerId);
        CustomerNote note = notes.get(noteId);
        if (note == null || !customerId.equals(note.getCustomerId()))
            throw new IllegalArgumentException("Customer note not found.");
        return note;
    }

    private static void requireCustomer(String customerId) {
        if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("Select a customer.");
    }
}
