package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lavs.model.Email;
import java.util.HashMap;
import java.util.List;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayEmailTest {

    @Test
    void addsAnEmailAsAPersonalOne() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "id", "3", "customer", "C001", "address", "jane@example.com",
                "isPrimary", "y", "type", "Personal"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Email saved = gateway.addEmail(new Email(1L, "C001", "jane@example.com", true));

        assertEquals(Long.valueOf(3), saved.getEmailId());
        assertEquals("C001", saved.getCustomerId());
        assertEquals("jane@example.com", saved.getEmailAddress());
        assertTrue(saved.isPrimary());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.addEmail", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("jane@example.com", request.getValue().getValue("address"));
        assertEquals("y", request.getValue().getValue("isPrimary"));
        assertEquals("Personal", request.getValue().getValue("type"));
    }

    // The mainframe's email list has everything LAVS models, so one request is
    // enough, however many emails there are
    @Test
    void listsEmailsWithASingleRequest() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "page", "1", "total", "2", "items", "2",
                "1:id", "3", "1:address", "jane@example.com", "1:isPrimary", "y",
                "1:type", "Personal",
                "2:id", "4", "2:address", "jane@work.example", "2:isPrimary", "n",
                "2:type", "Work"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        List<Email> emails = gateway.getEmails("C001");

        assertEquals(2, emails.size());
        assertEquals(Long.valueOf(3), emails.get(0).getEmailId());
        assertEquals("C001", emails.get(0).getCustomerId());
        assertEquals("jane@example.com", emails.get(0).getEmailAddress());
        assertTrue(emails.get(0).isPrimary());
        assertEquals(Long.valueOf(4), emails.get(1).getEmailId());
        assertEquals("jane@work.example", emails.get(1).getEmailAddress());
        assertFalse(emails.get(1).isPrimary());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals("customerEmail.list", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("number"));
    }

    @Test
    void listsNothingForACustomerWithNoEmails() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("page", "1", "total", "0", "items", "0"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        assertTrue(gateway.getEmails("C001").isEmpty());
        verify(connection).send(any(MessageRequest.class));
    }

    // The mainframe's update replaces every field, so the type and other type
    // that LAVS doesn't model must be read first and sent back unchanged, or
    // they would be wiped
    @Test
    void updateKeepsTheFieldsLavsDoesNotModel() {
        Connection connection = mock(Connection.class);
        MessageResponse current = response("id", "3", "customerNumber", "C001",
                "type", "Other", "otherType", "Side business", "address", "old@example.com",
                "isPrimary", "n");
        MessageResponse updated = response("id", "3", "customer", "C001", "type", "Other",
                "otherType", "Side business", "address", "new@example.com", "isPrimary", "y");
        when(connection.send(any(MessageRequest.class))).thenReturn(current, updated);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Email result = gateway.updateEmail(new Email(3L, "C001", "new@example.com", true));

        assertEquals("new@example.com", result.getEmailAddress());
        assertTrue(result.isPrimary());
        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        assertEquals("customerEmail.retrieve", requests.getAllValues().get(0).getRequestType());
        MessageRequest update = requests.getAllValues().get(1);
        assertEquals(MessageRequest.UPDATE, update.getMessageType());
        assertEquals("customer.updateEmail", update.getRequestType());
        assertEquals("3", update.getValue("id"));
        assertEquals("C001", update.getValue("customer"));
        assertEquals("new@example.com", update.getValue("address"));
        assertEquals("y", update.getValue("isPrimary"));
        assertEquals("Other", update.getValue("type"));
        assertEquals("Side business", update.getValue("otherType"));
    }

    @Test
    void deletesAnEmailUsingCustomerAndEmailIds() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("customer", "C001", "id", "3"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        gateway.deleteEmail("C001", 3L);

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.deleteEmail", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("3", request.getValue().getValue("id"));
    }

    private static MessageResponse response(String... entries) {
        HashMap<String, String> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return new MessageResponse(new Status(1), values);
    }
}