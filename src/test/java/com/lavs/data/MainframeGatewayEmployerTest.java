package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lavs.model.Employer;
import java.util.HashMap;
import java.util.List;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayEmployerTest {

    // The mainframe's employer type is Primary or Secondary, not an employment
    // type, so a new employer is added as Primary and LAVS's employment type is
    // carried over in the reply, not stored
    @Test
    void addsAnEmployerAsThePrimaryOne() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "id", "9", "customer", "C001", "name", "Acme Ltd", "position", "Engineer",
                "salary", "85000.0", "type", "Primary"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Employer saved = gateway.addEmployer(
                new Employer(1L, "C001", "Acme Ltd", "Engineer", 85000, "Full-time"));

        assertEquals(Long.valueOf(9), saved.getEmployerId());
        assertEquals("C001", saved.getCustomerId());
        assertEquals("Acme Ltd", saved.getCompanyName());
        assertEquals("Engineer", saved.getJobTitle());
        assertEquals(85000.0, saved.getIncome());
        assertEquals("Full-time", saved.getEmploymentType());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.addEmployer", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("Acme Ltd", request.getValue().getValue("name"));
        assertEquals("Engineer", request.getValue().getValue("position"));
        assertEquals("85000.0", request.getValue().getValue("salary"));
        assertEquals("Primary", request.getValue().getValue("type"));
    }

    // The mainframe's list only has each employer's name and type, so after
    // listing, each employer is fetched in full. The employment type isn't
    // stored by the mainframe, so it comes back null.
    @Test
    void listsAnEmployerInFull() {
        Connection connection = mock(Connection.class);
        MessageResponse list = response("page", "1", "total", "1", "items", "1",
                "1:id", "9", "1:name", "Acme Ltd", "1:type", "Primary");
        MessageResponse retrieve = response("id", "9", "customerNumber", "C001",
                "type", "Primary", "name", "Acme Ltd", "position", "Engineer",
                "salary", "85000.0");
        when(connection.send(any(MessageRequest.class))).thenReturn(list, retrieve);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        List<Employer> employers = gateway.getEmployers("C001");

        assertEquals(1, employers.size());
        Employer employer = employers.get(0);
        assertEquals(Long.valueOf(9), employer.getEmployerId());
        assertEquals("C001", employer.getCustomerId());
        assertEquals("Acme Ltd", employer.getCompanyName());
        assertEquals("Engineer", employer.getJobTitle());
        assertEquals(85000.0, employer.getIncome());
        assertNull(employer.getEmploymentType());

        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        MessageRequest listRequest = requests.getAllValues().get(0);
        assertEquals("customerEmployer.list", listRequest.getRequestType());
        assertEquals("C001", listRequest.getValue("number"));
        MessageRequest retrieveRequest = requests.getAllValues().get(1);
        assertEquals("customerEmployer.retrieve", retrieveRequest.getRequestType());
        assertEquals("9", retrieveRequest.getValue("id"));
        assertEquals("C001", retrieveRequest.getValue("number"));
    }

    @Test
    void listsNothingForACustomerWithNoEmployers() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("page", "1", "total", "0", "items", "0"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        assertTrue(gateway.getEmployers("C001").isEmpty());
        verify(connection).send(any(MessageRequest.class));
    }

    // The mainframe's update replaces every field, so the Primary or Secondary
    // type that LAVS doesn't model must be read first and sent back unchanged,
    // or an edit would turn a Secondary employer into a Primary one
    @Test
    void updateKeepsTheEmployerType() {
        Connection connection = mock(Connection.class);
        MessageResponse current = response("id", "9", "customerNumber", "C001",
                "type", "Secondary", "name", "Old Ltd", "position", "Clerk",
                "salary", "50000.0");
        MessageResponse updated = response("id", "9", "customer", "C001", "type", "Secondary",
                "name", "New Ltd", "position", "Manager", "salary", "90000.0");
        when(connection.send(any(MessageRequest.class))).thenReturn(current, updated);
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Employer result = gateway.updateEmployer(
                new Employer(9L, "C001", "New Ltd", "Manager", 90000, "Part-time"));

        assertEquals("New Ltd", result.getCompanyName());
        assertEquals("Manager", result.getJobTitle());
        assertEquals(90000.0, result.getIncome());
        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        assertEquals("customerEmployer.retrieve",
                requests.getAllValues().get(0).getRequestType());
        MessageRequest update = requests.getAllValues().get(1);
        assertEquals(MessageRequest.UPDATE, update.getMessageType());
        assertEquals("customer.updateEmployer", update.getRequestType());
        assertEquals("9", update.getValue("id"));
        assertEquals("C001", update.getValue("customer"));
        assertEquals("New Ltd", update.getValue("name"));
        assertEquals("Manager", update.getValue("position"));
        assertEquals("90000.0", update.getValue("salary"));
        assertEquals("Secondary", update.getValue("type"));
    }

    @Test
    void deletesAnEmployerUsingCustomerAndEmployerIds() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(response("customer", "C001", "id", "9"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        gateway.deleteEmployer("C001", 9L);

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.deleteEmployer", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("9", request.getValue().getValue("id"));
    }

    private static MessageResponse response(String... entries) {
        HashMap<String, String> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return new MessageResponse(new Status(1), values);
    }
}