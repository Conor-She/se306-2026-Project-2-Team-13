package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lavs.model.CustomerDocument;
import com.lavs.model.CustomerDraft;
import com.lavs.business.MainframeCustomerRegistrationService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// AI-assisted: Helped write the document metadata gateway tests.
class MainframeGatewayDocumentTest {
    private static final LocalDate UPLOAD_DATE = LocalDate.of(2026, 10, 14);

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = " ")
    void loadsDocumentsWhenBackendOmitsUploadDate(String uploadDate) {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response(
                "page", "1", "total", "1", "items", "1", "1:id", "41",
                "1:fileName", "licence", "1:type", "Proof of Identity", "1:uploadDate", uploadDate));
        var gateway = new MainframeGatewayImpl(connection, 1L);

        var documents = gateway.getDocuments("C001");

        assertEquals(1, documents.size());
        assertEquals("licence", documents.get(0).getFileName());
        assertNull(documents.get(0).getUploadDate());
    }

    @Test
    void wizardSavesDocumentWithRequiredBackendFieldsAndReloadsTimestamp() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenAnswer(call -> {
            MessageRequest request = call.getArgument(0);
            return switch (request.getRequestType()) {
                case "customer.create" -> response("number", "C001");
                case "customer.update" -> {
                    assertEquals("C001", request.getValue("number"));
                    assertEquals("1990-12-10", request.getValue("dateOfBirth"));
                    yield response("number", "C001", "title", "Ms", "firstName", "Ada",
                            "familyName", "Lovelace", "nationality", "New Zealand");
                }
                case "customer.addDocument" -> {
                    if (request.getValue("fileType") == null || request.getValue("size") == null) {
                        yield new MessageResponse(new Status(400, "Missing fileType or size", 1),
                                new HashMap<>());
                    }
                    assertEquals("C001", request.getValue("customer"));
                    yield response("id", "41");
                }
                case "customerDocument.list" -> response("page", "1", "total", "1", "items", "1",
                        "1:id", "41", "1:fileName", "Driver's License", "1:type", "Proof of Identity",
                        "1:uploadDate", "2026-10-14T12:34:56");
                default -> throw new AssertionError("Unexpected request: " + request.getRequestType());
            };
        });
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 1L);
        var draft = new CustomerDraft();
        draft.personal.putAll(Map.of("title", "Ms", "firstName", "Ada", "lastName", "Lovelace",
                "dateOfBirth", "10-12-1990", "nationality", "New Zealand"));
        draft.documents.add(Map.of("fileName", "Driver's License", "documentType", "Proof of Identity",
                "uploadDate", UPLOAD_DATE.toString()));

        String customerId = new MainframeCustomerRegistrationService(gateway).save(draft);

        var documents = gateway.getDocuments(customerId);
        assertEquals(1, documents.size());
        assertEquals("Driver's License", documents.get(0).getFileName());
        assertEquals(UPLOAD_DATE, documents.get(0).getUploadDate());
    }

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
        assertEquals("application/octet-stream", request.getValue().getValue("fileType"));
        assertEquals("0", request.getValue().getValue("size"));
    }

    @Test
    void updatesDocumentByAddingReplacementThenDeletingOriginal() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(response("id", "42"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, "Test Organisation");
        CustomerDocument document = new CustomerDocument("41", "C001", "passport.pdf",
                "Identity", UPLOAD_DATE);

        CustomerDocument replacement = gateway.updateDocument(document);
        assertEquals("42", replacement.getDocumentId());
        assertEquals("passport.pdf", replacement.getFileName());
        assertEquals("Identity", replacement.getDocumentType());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(request.capture());
        MessageRequest add = request.getAllValues().get(0);
        assertEquals("customer.addDocument", add.getRequestType());
        assertEquals("C001", add.getValue("customer"));
        assertEquals("passport.pdf", add.getValue("fileName"));
        assertEquals("Identity", add.getValue("type"));
        MessageRequest delete = request.getAllValues().get(1);
        assertEquals("customer.deleteDocument", delete.getRequestType());
        assertEquals("C001", delete.getValue("customer"));
        assertEquals("41", delete.getValue("id"));
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
