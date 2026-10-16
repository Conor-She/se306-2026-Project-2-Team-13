package com.lavs.data;

import com.lavs.model.CustomerNote;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import mainframe.client.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class MainframeGatewayCustomerNoteTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "2026-10-16",
            "2026-10-16T17:35:46.4670000+13:00",
            "2026-10-16T23:35:46.467Z",
            "2026-10-16T17:35:46"
    })
    void readsDateOnlyAndTimestampDatesWithoutLosingTheRecordedDay(String dateAdded) {
        var connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                response("page", "1", "items", "1", "total", "1", "1:id", "42"),
                response("id", "42", "lines", "Customer note", "type", "General", "dateAdded", dateAdded));
        var note = new MainframeGatewayImpl(connection, 13).getNotes("C001").get(0);
        assertEquals(LocalDate.of(2026, 10, 16), note.getDateAdded());
        assertEquals("Customer note", note.getNoteText());
    }

    private static MessageResponse response(String... values) {
        var data = new HashMap<String, String>();
        for (int index = 0; index < values.length; index += 2) data.put(values[index], values[index + 1]);
        return new MessageResponse(new Status(1), data);
    }

    @Test void sendsAddUpdateAndDeleteWithCustomerAndNoteIds() {
        var connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response("id", "42", "author", "alyza"), response("author", "alyza"), response());
        var gateway = new MainframeGatewayImpl(connection, 13);
        var added = gateway.addNote(new CustomerNote(null, "C001", "Line one\nLine two", LocalDate.of(2026, 10, 16), "General", null));
        assertEquals("42", added.getNoteId());
        assertEquals("alyza", added.getAuthor());
        var updated = gateway.updateNote(new CustomerNote("42", "C001", "Updated", added.getDateAdded(), "Follow up", added.getAuthor()));
        assertEquals("Updated", updated.getNoteText());
        gateway.deleteNote("C001", "42");
        var requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(3)).send(requests.capture());
        var sent = requests.getAllValues();
        assertEquals("customer.addNote", sent.get(0).getRequestType());
        assertEquals("Line one\nLine two", sent.get(0).getValue("lines"));
        assertEquals("General", sent.get(0).getValue("type"));
        assertEquals("customer.updateNote", sent.get(1).getRequestType());
        assertEquals("42", sent.get(1).getValue("id"));
        assertEquals("Follow up", sent.get(1).getValue("type"));
        assertEquals("customer.deleteNote", sent.get(2).getRequestType());
        assertEquals("42", sent.get(2).getValue("id"));
        sent.forEach(request -> assertEquals("C001", request.getValue("customer")));
    }

    @Test void retrievesAllPagesAndFullNoteText() {
        var connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                response("page", "1", "items", "1", "total", "2", "1:id", "42"),
                response("id", "42", "lines", "First\nSecond", "type", "General", "author", "alyza", "dateAdded", "2026-10-16"),
                response("page", "2", "items", "1", "total", "2", "1:id", "43"),
                response("id", "43", "lines", "Other", "type", "Contact", "author", "admin", "dateAdded", "2026-10-17"));
        var notes = new MainframeGatewayImpl(connection, 13).getNotes("C001");
        assertEquals(2, notes.size());
        assertEquals("First\nSecond", notes.get(0).getNoteText());
        assertEquals("alyza", notes.get(0).getAuthor());
        assertEquals(LocalDate.of(2026, 10, 16), notes.get(0).getDateAdded());
        assertEquals("43", notes.get(1).getNoteId());
        var requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(4)).send(requests.capture());
        assertEquals("customerNote.list", requests.getAllValues().get(0).getRequestType());
        assertEquals("2", requests.getAllValues().get(2).getValue("page"));
        requests.getAllValues().forEach(request -> assertEquals("C001", request.getValue("number")));
    }

    @Test void handlesEmptyListsMissingIdsAndServerFailures() {
        var connection = mock(Connection.class);
        var gateway = new MainframeGatewayImpl(connection, 13);
        when(connection.send(any(MessageRequest.class))).thenReturn(response("page", "1", "items", "0", "total", "0"));
        assertTrue(gateway.getNotes("C001").isEmpty());
        when(connection.send(any(MessageRequest.class))).thenReturn(response());
        var note = new CustomerNote(null, "C001", "Text", LocalDate.now());
        assertThrows(MainframeException.class, () -> gateway.addNote(note));
        when(connection.send(any(MessageRequest.class))).thenReturn(new MessageResponse(new Status(3, "Rejected", 1), new HashMap<>()));
        assertEquals(MainframeException.Type.BUSINESS_ERROR, assertThrows(MainframeException.class,
                () -> gateway.deleteNote("C001", "42")).getType());
        when(connection.send(any(MessageRequest.class))).thenAnswer(request -> { throw new IOException("Offline"); });
        assertEquals(MainframeException.Type.UNAVAILABLE, assertThrows(MainframeException.class,
                () -> gateway.getNotes("C001")).getType());
    }
}
