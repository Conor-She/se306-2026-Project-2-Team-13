package com.lavs.business;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionCustomerNoteStoreTest {
    @Test void addsAndReadsNotesForOnlyTheirCustomer() {
        var store = new SessionCustomerNoteStore();
        var note = store.addNote("C001", "Discuss repayment options");
        assertEquals("Discuss repayment options", store.getNotes("C001").get(0).getNoteText());
        assertNotNull(note.getDateAdded());
        assertTrue(store.getNotes("C002").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> store.getNotes("C001").clear());
        assertThrows(IllegalArgumentException.class, () -> store.addNote("C001", " "));
    }

    @Test void updatesTextAndPreservesIdentityAndDate() {
        var store = new SessionCustomerNoteStore();
        var note = store.addNote("C001", "Original");
        var updated = store.updateNote("C001", note.getNoteId(), "Updated");
        assertEquals(note.getNoteId(), updated.getNoteId());
        assertEquals(note.getDateAdded(), updated.getDateAdded());
        assertEquals("Updated", store.getNotes("C001").get(0).getNoteText());
        assertThrows(IllegalArgumentException.class, () -> store.updateNote("C002", note.getNoteId(), "Wrong customer"));
        assertThrows(IllegalArgumentException.class, () -> store.updateNote("C001", note.getNoteId(), ""));
        assertEquals("Updated", store.getNotes("C001").get(0).getNoteText());
    }

    @Test void deletesOnlyTheSelectedCustomerNote() {
        var store = new SessionCustomerNoteStore();
        var note = store.addNote("C001", "Remove me");
        var keep = store.addNote("C001", "Keep me");
        assertThrows(IllegalArgumentException.class, () -> store.deleteNote("C002", note.getNoteId()));
        store.deleteNote("C001", note.getNoteId());
        assertEquals(java.util.List.of(keep), store.getNotes("C001"));
        assertThrows(IllegalArgumentException.class, () -> store.deleteNote("C001", note.getNoteId()));
    }
}
