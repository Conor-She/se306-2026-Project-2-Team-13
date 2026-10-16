package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CustomerNoteTest {

    // Confirms a fully valid CustomerNote builds and its fields are retrievable
    @Test
    void constructsWithValidFields() {
        CustomerNote note = new CustomerNote("N001", "C001", "Called to confirm address",
                LocalDate.of(2026, 3, 1));

        assertEquals("N001", note.getNoteId());
        assertEquals("Called to confirm address", note.getNoteText());
    }

    // An empty note has nothing to record, so it must be rejected
    @Test
    void rejectsBlankNoteText() {
        assertThrows(IllegalArgumentException.class, () ->
                new CustomerNote("N001", "C001", "", LocalDate.now()));
    }

    @Test
    void allowsMissingIdBeforeANoteIsSaved() {
        CustomerNote note = new CustomerNote(null, "C001", "New customer note",
                LocalDate.of(2026, 10, 16));

        assertNull(note.getNoteId());
    }

    @Test
    void rejectsBlankNoteId() {
        assertThrows(IllegalArgumentException.class, () ->
                new CustomerNote(" ", "C001", "New customer note", LocalDate.now()));
    }
}
