package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lavs.model.Guarantor;
import java.util.HashMap;
import java.util.List;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayGuarantorTest {

    // A guarantor is stored as the mainframe's relationship from the customer to
    // the guarantor
    @Test
    void addsAGuarantorAsARelationship() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "id", "11", "customer", "C001", "secondaryCustomerNumber", "C002",
                "type", "Parent"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Guarantor saved = gateway.addGuarantor(new Guarantor(null, "C001", "C002", "Parent"));

        assertEquals("11", saved.getGuarantorId());
        assertEquals("C001", saved.getCustomerId());
        assertEquals("C002", saved.getGuarantorCustomerId());
        assertEquals("Parent", saved.getRelationshipType());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.addRelationship", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("C002", request.getValue().getValue("secondaryCustomerNumber"));
        assertEquals("Parent", request.getValue().getValue("type"));
    }

    // The mainframe's relationship types are a fixed set, so free text is sent
    // as Other with the text alongside, and comes back as that text
    @Test
    void sendsFreeTextAsOtherAndReadsItBack() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "id", "12", "customer", "C001", "secondaryCustomerNumber", "C002",
                "type", "Other", "otherType", "Family friend"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Guarantor saved =
                gateway.addGuarantor(new Guarantor(null, "C001", "C002", "Family friend"));

        assertEquals("Family friend", saved.getRelationshipType());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals("Other", request.getValue().getValue("type"));
        assertEquals("Family friend", request.getValue().getValue("otherType"));
    }

    // Text that matches a fixed type, ignoring case and spaces, uses that type
    @Test
    void matchesKnownTypesIgnoringCaseAndSpaces() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "id", "13", "customer", "C001", "secondaryCustomerNumber", "C002",
                "type", "LegalAuthority"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        gateway.addGuarantor(new Guarantor(null, "C001", "C002", "legal authority"));

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals("LegalAuthority", request.getValue().getValue("type"));
    }

    // The list has each relationship's type but not its other-type text, so only
    // the relationship of type Other is fetched in full
    @Test
    void listsGuarantorsAndFetchesTheTextOnlyForOther() {
        Connection connection = mock(Connection.class);
        MessageResponse list = response("page", "1", "total", "2", "items", "2",
                "1:id", "11", "1:secondaryCustomerNumber", "C002", "1:type", "Parent",
                "2:id", "12", "2:secondaryCustomerNumber", "C003", "2:type", "Other");
        MessageResponse retrieve = response("id", "12", "customerNumber", "C001",
                "type", "Other", "otherType", "Family friend",
                "secondaryCustomerNumber", "C003");
        when(connection.send(any(MessageRequest.class))).thenReturn(list, retrieve);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        List<Guarantor> guarantors = gateway.getGuarantors("C001");

        assertEquals(2, guarantors.size());
        assertEquals("11", guarantors.get(0).getGuarantorId());
        assertEquals("C002", guarantors.get(0).getGuarantorCustomerId());
        assertEquals("Parent", guarantors.get(0).getRelationshipType());
        assertEquals("12", guarantors.get(1).getGuarantorId());
        assertEquals("C003", guarantors.get(1).getGuarantorCustomerId());
        assertEquals("Family friend", guarantors.get(1).getRelationshipType());

        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        assertEquals("customerRelationship.list", requests.getAllValues().get(0).getRequestType());
        assertEquals("C001", requests.getAllValues().get(0).getValue("number"));
        MessageRequest retrieveRequest = requests.getAllValues().get(1);
        assertEquals("customerRelationship.retrieve", retrieveRequest.getRequestType());
        assertEquals("12", retrieveRequest.getValue("id"));
        assertEquals("C001", retrieveRequest.getValue("number"));
    }

    @Test
    void listsNothingForACustomerWithNoRelationships() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("page", "1", "total", "0", "items", "0"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        assertTrue(gateway.getGuarantors("C001").isEmpty());
        verify(connection).send(any(MessageRequest.class));
    }

    @Test
    void updatesAGuarantor() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "id", "11", "customer", "C001", "secondaryCustomerNumber", "C003",
                "type", "Spouse"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Guarantor result =
                gateway.updateGuarantor(new Guarantor("11", "C001", "C003", "Spouse"));

        assertEquals("11", result.getGuarantorId());
        assertEquals("C003", result.getGuarantorCustomerId());
        assertEquals("Spouse", result.getRelationshipType());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.updateRelationship", request.getValue().getRequestType());
        assertEquals("11", request.getValue().getValue("id"));
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("C003", request.getValue().getValue("secondaryCustomerNumber"));
        assertEquals("Spouse", request.getValue().getValue("type"));
    }

    @Test
    void deletesAGuarantorUsingCustomerAndGuarantorIds() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("customer", "C001", "id", "11"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        gateway.deleteGuarantor("C001", "11");

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.deleteRelationship", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("11", request.getValue().getValue("id"));
    }

    @Test
    void refusesANonNumericGuarantorIdWithoutCallingTheMainframe() {
        Connection connection = mock(Connection.class);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        assertThrows(IllegalArgumentException.class,
                () -> gateway.deleteGuarantor("C001", "not-a-number"));
        assertThrows(IllegalArgumentException.class,
                () -> gateway.updateGuarantor(new Guarantor(null, "C001", "C002", "Parent")));

        verifyNoInteractions(connection);
    }

    @Test
    void addToleratesAReplyThatOnlyHasTheId() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response("id", "11"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Guarantor saved = gateway.addGuarantor(new Guarantor(null, "C001", "C002", "Parent"));

        assertEquals("11", saved.getGuarantorId());
        assertEquals("C001", saved.getCustomerId());
        assertEquals("C002", saved.getGuarantorCustomerId());
        assertEquals("Parent", saved.getRelationshipType());
    }

    private static MessageResponse response(String... entries) {
        HashMap<String, String> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return new MessageResponse(new Status(1), values);
    }
}