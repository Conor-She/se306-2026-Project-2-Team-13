package com.lavs.business;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeException;
import com.lavs.model.CustomerNote;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerNoteServiceTest {
    @Test void addsAndReadsCustomerNotesWithServerAssignedIdentity() {
        var gateway = new FakeMainframeGateway();
        var service = new CustomerNoteService(gateway);
        var note = service.addNote("C001", "Called to confirm address", "Contact");
        assertNotNull(note.getNoteId());
        assertNotNull(note.getDateAdded());
        assertEquals("Contact", note.getType());
        assertEquals("Called to confirm address", new CustomerNoteService(gateway).getNotes("C001").get(0).getNoteText());
        assertTrue(service.getNotes("C002").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> service.getNotes("C001").clear());
    }

    @Test void updatesOnlyTheSelectedCustomerNote() {
        var gateway = new FakeMainframeGateway();
        var service = new CustomerNoteService(gateway);
        var original = service.addNote("C001", "Original");
        var updated = service.updateNote(new CustomerNote(original.getNoteId(), "C001", "Updated",
                original.getDateAdded(), "Follow up", original.getAuthor()));
        assertEquals(original.getNoteId(), updated.getNoteId());
        assertEquals(original.getDateAdded(), updated.getDateAdded());
        assertEquals("Updated", service.getNotes("C001").get(0).getNoteText());
        assertEquals("Follow up", service.getNotes("C001").get(0).getType());
        assertThrows(MainframeException.class, () -> service.updateNote(new CustomerNote(original.getNoteId(), "C002", "Wrong customer", original.getDateAdded())));
        assertEquals("Updated", service.getNotes("C001").get(0).getNoteText());
    }

    @Test void deletionKeepsOtherNotesAndChecksTheCustomer() {
        var service = new CustomerNoteService(new FakeMainframeGateway());
        var remove = service.addNote("C001", "Remove");
        var keep = service.addNote("C001", "Keep");
        assertThrows(MainframeException.class, () -> service.deleteNote("C002", remove.getNoteId()));
        service.deleteNote("C001", remove.getNoteId());
        assertEquals(java.util.List.of(keep), service.getNotes("C001"));
        assertThrows(MainframeException.class, () -> service.deleteNote("C001", remove.getNoteId()));
    }

    @Test void invalidInputAndNetworkFailuresDoNotChangeNotes() {
        var gateway = new FakeMainframeGateway();
        var service = new CustomerNoteService(gateway);
        var original = service.addNote("C001", "Original");
        assertThrows(IllegalArgumentException.class, () -> service.addNote("C001", " "));
        assertThrows(IllegalArgumentException.class, () -> service.getNotes(" "));
        assertThrows(IllegalArgumentException.class, () -> service.deleteNote("C001", null));
        assertThrows(IllegalArgumentException.class, () -> service.updateNote(new CustomerNote(null, "C001", "Text", null)));
        gateway.simulateUnavailable(true);
        assertThrows(MainframeException.class, () -> service.deleteNote("C001", original.getNoteId()));
        gateway.simulateUnavailable(false);
        assertEquals(1, service.getNotes("C001").size());
    }
}
