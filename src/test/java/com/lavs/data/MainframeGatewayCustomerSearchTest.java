package com.lavs.data;

import java.util.HashMap;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MainframeGatewayCustomerSearchTest {
    @Test void customerNumberUsesRetrievalInsteadOfNameSearch() {
        Connection connection = mock(Connection.class);
        var values = new HashMap<String, String>();
        values.put("number", "01-0001-0027680");
        values.put("firstName", "Sunghoon");
        values.put("familyName", "Park");
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(new MessageResponse(new Status(1), values));

        var results = new MainframeGatewayImpl(connection, "Test")
                .searchCustomers(" 01-0001-0027680 ");

        assertEquals(1, results.size());
        assertEquals("Sunghoon", results.get(0).getFirstName());
        assertEquals("01-0001-0027680", results.get(0).getCustomerId());
        var request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals("customer.retrieve", request.getValue().getRequestType());
        assertEquals("01-0001-0027680", request.getValue().getValue("number"));
    }

    @Test void unknownCustomerNumberReturnsNoMatches() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                new MessageResponse(new Status(3, "Customer not found", 1), new HashMap<>()));
        assertTrue(new MainframeGatewayImpl(connection, "Test")
                .searchCustomers("01-0001-9999999").isEmpty());
    }

    @Test void unavailableCustomerLookupIsNotReportedAsNoMatches() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                mainframe.client.StatusMessage.NETWORK_FAILURE_UNAVAILABLE.generateMessageResponse(1));
        var exception = assertThrows(MainframeException.class, () ->
                new MainframeGatewayImpl(connection, "Test").searchCustomers("01-0001-0027680"));
        assertEquals(MainframeException.Type.UNAVAILABLE, exception.getType());
    }

    @Test void blankQueryDoesNotContactMainframe() {
        Connection connection = mock(Connection.class);
        assertTrue(new MainframeGatewayImpl(connection, "Test").searchCustomers("   ").isEmpty());
        verifyNoInteractions(connection);
    }

    @Test void readsEverySearchPageWithoutRetrievingCustomerDetails() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                page("101", "1", "C1"), page("101", "1", "C101"));

        var results = new MainframeGatewayImpl(connection, "Test").searchCustomers("Smith");

        assertEquals(2, results.size());
        assertEquals(java.time.LocalDate.of(1990, 5, 1), results.get(0).getDateOfBirth());
        assertEquals("C1", results.get(0).getCustomerId());
        assertEquals("C101", results.get(1).getCustomerId());
        var requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        assertEquals("1", requests.getAllValues().get(0).getValue("page"));
        assertEquals("2", requests.getAllValues().get(1).getValue("page"));
        requests.getAllValues().forEach(request -> {
            assertEquals("customer.search", request.getRequestType());
            assertEquals("Smith", request.getValue("search"));
        });
    }

    @Test void emptySearchPageStopsPagination() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(page("1000", "0", null));

        assertTrue(new MainframeGatewayImpl(connection, "Test").searchCustomers("Nobody").isEmpty());
        verify(connection).send(any(MessageRequest.class));
    }

    @Test void missingSearchDateOfBirthIsNull() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(page("1", "1", "C1", null));

        var result = new MainframeGatewayImpl(connection, "Test").searchCustomers("Jane");

        assertEquals(1, result.size());
        assertNull(result.get(0).getDateOfBirth());
    }

    private static MessageResponse page(String total, String items, String id) {
        return page(total, items, id, "1990-05-01");
    }

    private static MessageResponse page(String total, String items, String id, String dateOfBirth) {
        HashMap<String, String> values = new HashMap<>();
        values.put("total", total);
        values.put("items", items);
        values.put("1:number", id);
        values.put("1:firstName", "Jane");
        values.put("1:familyName", "Smith");
        values.put("1:title", "Ms");
        if (dateOfBirth != null) values.put("1:dateOfBirth", dateOfBirth);
        return new MessageResponse(new Status(1), values);
    }
}
