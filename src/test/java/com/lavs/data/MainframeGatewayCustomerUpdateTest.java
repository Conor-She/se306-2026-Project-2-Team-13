package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lavs.model.Customer;
import java.time.LocalDate;
import java.util.HashMap;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayCustomerUpdateTest {
    @Test void createReturnsSubmittedDateWhenReplyOmitsOptionalDateOfBirth() {
        Connection connection = mock(Connection.class);
        HashMap<String, String> values = new HashMap<>();
        values.put("number", "C2");
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(new MessageResponse(new Status(1), values));
        LocalDate dateOfBirth = LocalDate.of(2001, 2, 3);

        Customer created = new MainframeGatewayImpl(connection, 1L).createCustomer(
                new Customer(null, "Mr", "John", "Smith", dateOfBirth, "NZ"));

        assertEquals(dateOfBirth, created.getDateOfBirth());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals("2001-02-03", request.getValue().getValue("dateOfBirth"));
    }

    @Test void updateReturnsSubmittedDateWhenReplyOmitsOptionalDateOfBirth() {
        Connection connection = mock(Connection.class);
        HashMap<String, String> values = new HashMap<>();
        values.put("number", "C1");
        values.put("title", "Ms");
        values.put("firstName", "Jane");
        values.put("familyName", "Jones");
        values.put("nationality", "NZ");
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(new MessageResponse(new Status(1), values));
        LocalDate dateOfBirth = LocalDate.of(1990, 5, 1);

        Customer updated = new MainframeGatewayImpl(connection, "Test").updateCustomer(
                new Customer("C1", "Ms", "Jane", "Jones", dateOfBirth, "NZ"));

        assertEquals(dateOfBirth, updated.getDateOfBirth());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals("1990-05-01", request.getValue().getValue("dateOfBirth"));
    }

    @Test void updateAllowsMissingDateOfBirth() {
        Connection connection = mock(Connection.class);
        HashMap<String, String> values = new HashMap<>();
        values.put("number", "C1");
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(new MessageResponse(new Status(1), values));

        Customer updated = new MainframeGatewayImpl(connection, "Test").updateCustomer(
                new Customer("C1", "Ms", "Jane", "Jones", null, "NZ"));

        assertNull(updated.getDateOfBirth());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertNull(request.getValue().getValue("dateOfBirth"));
    }
}
