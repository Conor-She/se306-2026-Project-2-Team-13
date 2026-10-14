package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayCategoryTest {

    @Test
    void retrievesValuesForTheMatchingCategory() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                categoryResponse(), itemResponse());
        MainframeGateway gateway = new MainframeGatewayImpl(connection, "Test Organisation");

        List<String> values = gateway.getCategoryValues("EmploymentType");

        assertEquals(List.of("Full-time", "Part-time"), values);

        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(requests.capture());
        MessageRequest categoryRequest = requests.getAllValues().get(0);
        MessageRequest itemRequest = requests.getAllValues().get(1);
        assertEquals(MessageRequest.QUERY, categoryRequest.getMessageType());
        assertEquals("constantCategory.list", categoryRequest.getRequestType());
        assertEquals("1", categoryRequest.getValue("page"));
        assertEquals("100", categoryRequest.getValue("size"));
        assertEquals(MessageRequest.QUERY, itemRequest.getMessageType());
        assertEquals("constantItem.list", itemRequest.getRequestType());
        assertEquals("41", itemRequest.getValue("category"));
        assertEquals("1", itemRequest.getValue("page"));
        assertEquals("100", itemRequest.getValue("size"));
    }

    @Test
    void returnsEmptyListWhenCategoryDoesNotExist() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(categoryResponse());
        MainframeGateway gateway = new MainframeGatewayImpl(connection, "Test Organisation");

        assertTrue(gateway.getCategoryValues("Missing").isEmpty());
        verify(connection, times(1)).send(any(MessageRequest.class));
    }

    private MessageResponse categoryResponse() {
        HashMap<String, String> values = new HashMap<>();
        values.put("page", "1");
        values.put("total", "2");
        values.put("items", "2");
        values.put("1:id", "41");
        values.put("1:name", "EmploymentType");
        values.put("2:id", "42");
        values.put("2:name", "LoanType");
        return new MessageResponse(new Status(1), values);
    }

    private MessageResponse itemResponse() {
        HashMap<String, String> values = new HashMap<>();
        values.put("categoryId", "41");
        values.put("categoryName", "EmploymentType");
        values.put("page", "1");
        values.put("total", "2");
        values.put("items", "2");
        values.put("1:name", "Part-time");
        values.put("1:order", "2");
        values.put("2:name", "Full-time");
        values.put("2:order", "1");
        return new MessageResponse(new Status(2), values);
    }
}
