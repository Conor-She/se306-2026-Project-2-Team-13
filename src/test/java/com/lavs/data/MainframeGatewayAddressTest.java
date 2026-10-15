package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lavs.model.Address;
import java.util.HashMap;
import java.util.List;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayAddressTest {

    // The mainframe's reply to adding an address: it assigns the ID
    @Test
    void addsAnAddress() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "id", "7", "customer", "C001", "line1", "5 Queen St", "suburb", "CBD",
                "city", "Auckland", "postCode", "1010", "country", "NZ", "isMailing", "y"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Address saved = gateway.addAddress(
                new Address(1L, "C001", "5 Queen St", "CBD", "Auckland", "1010", "NZ", true));

        assertEquals(Long.valueOf(7), saved.getAddressId());
        assertEquals("5 Queen St", saved.getStreet());
        assertTrue(saved.isMailing());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.addAddress", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("5 Queen St", request.getValue().getValue("line1"));
        assertEquals("CBD", request.getValue().getValue("suburb"));
        assertEquals("Auckland", request.getValue().getValue("city"));
        assertEquals("1010", request.getValue().getValue("postCode"));
        assertEquals("NZ", request.getValue().getValue("country"));
        assertEquals("y", request.getValue().getValue("isMailing"));
        assertEquals("n", request.getValue().getValue("isPrimary"));
        assertEquals("Residential", request.getValue().getValue("type"));
    }

    // The mainframe's list only has city, country, flags and type, so the
    // adapter asks for the list and then fetches each address in full
    @Test
    void listsAnAddressInFull() {
        Connection connection = mock(Connection.class);
        MessageResponse list = response("page", "1", "total", "1", "items", "1", "1:id", "7",
                "1:city", "Auckland", "1:country", "NZ", "1:isMailing", "y",
                "1:isPrimary", "n", "1:type", "Residential");
        MessageResponse retrieve = response("id", "7", "customerNumber", "C001",
                "type", "Residential", "isPrimary", "n", "isMailing", "y",
                "line1", "5 Queen St", "suburb", "CBD", "city", "Auckland",
                "country", "NZ", "postCode", "1010");
        when(connection.send(any(MessageRequest.class))).thenReturn(list, retrieve);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        List<Address> addresses = gateway.getAddresses("C001");

        assertEquals(1, addresses.size());
        Address address = addresses.get(0);
        assertEquals(Long.valueOf(7), address.getAddressId());
        assertEquals("C001", address.getCustomerId());
        assertEquals("5 Queen St", address.getStreet());
        assertEquals("CBD", address.getSuburb());
        assertEquals("Auckland", address.getCity());
        assertEquals("1010", address.getPostCode());
        assertEquals("NZ", address.getCountry());
        assertTrue(address.isMailing());

        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        MessageRequest listRequest = requests.getAllValues().get(0);
        assertEquals("customerAddress.list", listRequest.getRequestType());
        assertEquals("C001", listRequest.getValue("number"));
        MessageRequest retrieveRequest = requests.getAllValues().get(1);
        assertEquals("customerAddress.retrieve", retrieveRequest.getRequestType());
        assertEquals("7", retrieveRequest.getValue("id"));
        assertEquals("C001", retrieveRequest.getValue("number"));
    }

    // A customer with no addresses costs a single request and gives an empty list
    @Test
    void listsNothingForACustomerWithNoAddresses() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("page", "1", "total", "0", "items", "0"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        assertTrue(gateway.getAddresses("C001").isEmpty());
        verify(connection).send(any(MessageRequest.class));
    }

    // The mainframe's update replaces every field, so the second address line,
    // type and primary flag that LAVS doesn't model must be read first and sent
    // back unchanged, or they would be wiped
    @Test
    void updateKeepsTheFieldsLavsDoesNotModel() {
        Connection connection = mock(Connection.class);
        MessageResponse current = response("id", "7", "customerNumber", "C001",
                "type", "Postal", "isPrimary", "y", "isMailing", "y",
                "line1", "5 Queen St", "line2", "Level 2", "suburb", "CBD",
                "city", "Auckland", "country", "NZ", "postCode", "1010");
        MessageResponse updated = response("id", "7", "customer", "C001", "type", "Postal",
                "isPrimary", "y", "isMailing", "n", "line1", "9 King St", "line2", "Level 2",
                "suburb", "CBD", "city", "Auckland", "country", "NZ", "postCode", "1010");
        when(connection.send(any(MessageRequest.class))).thenReturn(current, updated);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Address result = gateway.updateAddress(
                new Address(7L, "C001", "9 King St", "CBD", "Auckland", "1010", "NZ", false));

        assertEquals("9 King St", result.getStreet());
        assertFalse(result.isMailing());
        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        assertEquals("customerAddress.retrieve", requests.getAllValues().get(0).getRequestType());
        MessageRequest update = requests.getAllValues().get(1);
        assertEquals(MessageRequest.UPDATE, update.getMessageType());
        assertEquals("customer.updateAddress", update.getRequestType());
        assertEquals("7", update.getValue("id"));
        assertEquals("C001", update.getValue("customer"));
        assertEquals("9 King St", update.getValue("line1"));
        assertEquals("n", update.getValue("isMailing"));
        assertEquals("Level 2", update.getValue("line2"));
        assertEquals("Postal", update.getValue("type"));
        assertEquals("y", update.getValue("isPrimary"));
    }

    // The mainframe's delete needs the customer as well as the address
    @Test
    void deletesAnAddressUsingCustomerAndAddressIds() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("customer", "C001", "id", "7"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        gateway.deleteAddress("C001", "7");

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.deleteAddress", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("7", request.getValue().getValue("id"));
    }

    // An ID that isn't a number can't be a mainframe ID, so nothing is sent
    @Test
    void refusesANonNumericAddressIdWithoutCallingTheMainframe() {
        Connection connection = mock(Connection.class);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        assertThrows(IllegalArgumentException.class,
                () -> gateway.deleteAddress("C001", "not-a-number"));

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