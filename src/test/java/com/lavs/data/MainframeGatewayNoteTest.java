package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.lavs.model.CustomerNote;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayNoteTest {

    @Test
    void addsACustomerNote() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "customer", "C001", "id", "9", "lines", "Prefers email contact",
                "author", "test", "type", "Customer note"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        CustomerNote saved = gateway.addNote(new CustomerNote(null, "C001",
                "Prefers email contact", LocalDate.of(2026, 10, 16)));

        assertEquals("9", saved.getNoteId());
        assertEquals("C001", saved.getCustomerId());
        assertEquals("Prefers email contact", saved.getNoteText());
        assertNotNull(saved.getDateAdded());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.addNote", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("Prefers email contact", request.getValue().getValue("lines"));
        assertEquals("Customer note", request.getValue().getValue("type"));
    }

    @Test
    void listsCustomerNotesWithTheirFullText() {
        Connection connection = mock(Connection.class);
        MessageResponse list = response("page", "1", "total", "1", "items", "1",
                "1:id", "9", "1:type", "Customer note", "1:dateAdded", "2026-10-16",
                "1:numberOfLines", "1", "1:author", "test");
        MessageResponse retrieve = response("customerNumber", "C001", "id", "9",
                "lines", "Prefers email contact", "dateAdded", "2026-10-16",
                "author", "test", "type", "Customer note");
        when(connection.send(any(MessageRequest.class))).thenReturn(list, retrieve);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        List<CustomerNote> notes = gateway.getNotes("C001");

        assertEquals(1, notes.size());
        assertEquals("9", notes.get(0).getNoteId());
        assertEquals("Prefers email contact", notes.get(0).getNoteText());
        assertEquals(LocalDate.of(2026, 10, 16), notes.get(0).getDateAdded());
        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        assertEquals("customerNote.list", requests.getAllValues().get(0).getRequestType());
        assertEquals("C001", requests.getAllValues().get(0).getValue("number"));
        assertEquals("customerNote.retrieve", requests.getAllValues().get(1).getRequestType());
        assertEquals("9", requests.getAllValues().get(1).getValue("id"));
        assertEquals("C001", requests.getAllValues().get(1).getValue("number"));
    }

    private static MessageResponse response(String... entries) {
        HashMap<String, String> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return new MessageResponse(new Status(1), values);
    }
}
