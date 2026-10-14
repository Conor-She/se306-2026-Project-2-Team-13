package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lavs.model.CustomerDocument;
import java.time.LocalDate;
import java.util.HashMap;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayDocumentTest {
    private static final LocalDate UPLOAD_DATE = LocalDate.of(2026, 10, 14);

    @Test
    void addsOnlyDocumentMetadata() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response("id", "41"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, "Test Organisation");

        CustomerDocument saved = gateway.addDocument(new CustomerDocument(null, "C001",
                "payslip.pdf", "Income", UPLOAD_DATE));

        assertEquals("41", saved.getDocumentId());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.addDocument", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("payslip.pdf", request.getValue().getValue("fileName"));
        assertEquals("Income", request.getValue().getValue("type"));
        assertEquals(UPLOAD_DATE.toString(), request.getValue().getValue("uploadDate"));
        assertNull(request.getValue().getValue("fileType"));
        assertNull(request.getValue().getValue("size"));
    }

    @Test
    void updatesMetadataForAnExistingDocument() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response());
        MainframeGateway gateway = new MainframeGatewayImpl(connection, "Test Organisation");
        CustomerDocument document = new CustomerDocument("41", "C001", "passport.pdf",
                "Identity", UPLOAD_DATE);

        assertEquals(document, gateway.updateDocument(document));
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals(MessageRequest.UPDATE, request.getValue().getMessageType());
        assertEquals("customer.updateDocument", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("41", request.getValue().getValue("id"));
        assertEquals("passport.pdf", request.getValue().getValue("fileName"));
        assertEquals("Identity", request.getValue().getValue("type"));
        assertEquals(UPLOAD_DATE.toString(), request.getValue().getValue("uploadDate"));
        assertNull(request.getValue().getValue("fileType"));
        assertNull(request.getValue().getValue("size"));
    }

    @Test
    void deletesDocumentUsingCustomerAndDocumentIds() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response());
        MainframeGateway gateway = new MainframeGatewayImpl(connection, "Test Organisation");

        gateway.deleteDocument("C001", "41");

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals("customer.deleteDocument", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("customer"));
        assertEquals("41", request.getValue().getValue("id"));
    }

    @Test
    void listsDocumentMetadataIncludingUploadDate() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "page", "1", "total", "1", "items", "1", "1:id", "41",
                "1:fileName", "payslip.pdf", "1:type", "Income",
                "1:uploadDate", UPLOAD_DATE.toString()));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, "Test Organisation");

        var documents = gateway.getDocuments("C001");

        assertEquals(1, documents.size());
        assertEquals("41", documents.get(0).getDocumentId());
        assertEquals("C001", documents.get(0).getCustomerId());
        assertEquals("payslip.pdf", documents.get(0).getFileName());
        assertEquals("Income", documents.get(0).getDocumentType());
        assertEquals(UPLOAD_DATE, documents.get(0).getUploadDate());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection).send(request.capture());
        assertEquals("customerDocument.list", request.getValue().getRequestType());
        assertEquals("C001", request.getValue().getValue("number"));
    }

    private static MessageResponse response(String... entries) {
        HashMap<String, String> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return new MessageResponse(new Status(1), values);
    }
}
