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

import com.lavs.model.Phone;
import java.util.HashMap;
import java.util.List;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayPhoneTest {

    @Test
    void addsAPhoneAsAMobile() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "id", "5", "customer", "C001", "number", "0211234567", "isPrimary", "y",
                "canTxt", "y", "type", "Mobile"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Phone saved = gateway.addPhone(new Phone(1L, "C001", "0211234567", true, true));

        assertEquals(Long.valueOf(5), saved.getPhoneId());
        assertEquals("0211234567", saved.getNumber());
        assertTrue(saved.isPrimary());
        assertTrue(saved.isSendTXT());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.addPhoneNumber", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("0211234567", request.getValue().getValue("number"));
        assertEquals("y", request.getValue().getValue("canTxt"));
        assertEquals("y", request.getValue().getValue("isPrimary"));
        assertEquals("Mobile", request.getValue().getValue("type"));
    }

    // The mainframe's list has no reliable phone number, so after listing, each
    // phone is fetched in full and the number comes from there
    @Test
    void listsAPhoneInFull() {
        Connection connection = mock(Connection.class);
        MessageResponse list = response("page", "1", "total", "1", "items", "1", "1:id", "5",
                "1:canTxt", "y", "1:countryCode", "64", "1:isPrimary", "y", "1:type", "Mobile");
        MessageResponse retrieve = response("id", "5", "customerNumber", "C001",
                "type", "Mobile", "countryCode", "64", "number", "0211234567",
                "isPrimary", "y", "canTxt", "y");
        when(connection.send(any(MessageRequest.class))).thenReturn(list, retrieve);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        List<Phone> phones = gateway.getPhones("C001");

        assertEquals(1, phones.size());
        Phone phone = phones.get(0);
        assertEquals(Long.valueOf(5), phone.getPhoneId());
        assertEquals("C001", phone.getCustomerId());
        assertEquals("0211234567", phone.getNumber());
        assertTrue(phone.isPrimary());
        assertTrue(phone.isSendTXT());

        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        MessageRequest listRequest = requests.getAllValues().get(0);
        assertEquals("customerPhoneNumber.list", listRequest.getRequestType());
        assertEquals("C001", listRequest.getValue("number"));
        MessageRequest retrieveRequest = requests.getAllValues().get(1);
        assertEquals("customerPhoneNumber.retrieve", retrieveRequest.getRequestType());
        assertEquals("5", retrieveRequest.getValue("id"));
        assertEquals("C001", retrieveRequest.getValue("number"));
    }

    @Test
    void listsNothingForACustomerWithNoPhones() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("page", "1", "total", "0", "items", "0"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        assertTrue(gateway.getPhones("C001").isEmpty());
        verify(connection).send(any(MessageRequest.class));
    }

    // The mainframe's update replaces every field, so the country code, type
    // and other type that LAVS doesn't model must be read first and sent back
    // unchanged, or they would be wiped
    @Test
    void updateKeepsTheFieldsLavsDoesNotModel() {
        Connection connection = mock(Connection.class);
        MessageResponse current = response("id", "5", "customerNumber", "C001",
                "type", "Home", "otherType", "Cabin", "countryCode", "64",
                "number", "0211234567", "isPrimary", "n", "canTxt", "n");
        MessageResponse updated = response("id", "5", "customer", "C001", "type", "Home",
                "otherType", "Cabin", "countryCode", "64", "number", "0219999999",
                "isPrimary", "y", "canTxt", "y");
        when(connection.send(any(MessageRequest.class))).thenReturn(current, updated);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Phone result = gateway.updatePhone(new Phone(5L, "C001", "0219999999", true, true));

        assertEquals("0219999999", result.getNumber());
        assertTrue(result.isPrimary());
        assertTrue(result.isSendTXT());
        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        assertEquals("customerPhoneNumber.retrieve",
                requests.getAllValues().get(0).getRequestType());
        MessageRequest update = requests.getAllValues().get(1);
        assertEquals(MessageRequest.UPDATE, update.getMessageType());
        assertEquals("customer.updatePhoneNumber", update.getRequestType());
        assertEquals("5", update.getValue("id"));
        assertEquals("C001", update.getValue("customer"));
        assertEquals("0219999999", update.getValue("number"));
        assertEquals("y", update.getValue("canTxt"));
        assertEquals("y", update.getValue("isPrimary"));
        assertEquals("64", update.getValue("countryCode"));
        assertEquals("Home", update.getValue("type"));
        assertEquals("Cabin", update.getValue("otherType"));
    }

    @Test
    void deletesAPhoneUsingCustomerAndPhoneIds() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("customer", "C001", "id", "5"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        gateway.deletePhone("C001", "5");

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.deletePhoneNumber", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("5", request.getValue().getValue("id"));
    }

    @Test
    void refusesANonNumericPhoneIdWithoutCallingTheMainframe() {
        Connection connection = mock(Connection.class);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        assertThrows(IllegalArgumentException.class,
                () -> gateway.deletePhone("C001", "not-a-number"));

        verifyNoInteractions(connection);
    }

    private static MessageResponse response(String... entries) {
        HashMap<String, String> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return new MessageResponse(new Status(1), values);
    }
}