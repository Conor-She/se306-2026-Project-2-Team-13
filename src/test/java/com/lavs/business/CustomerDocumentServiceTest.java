package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lavs.data.MainframeGateway;
import com.lavs.model.CustomerDocument;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// AI-assisted: Helped write the customer document service tests.
class CustomerDocumentServiceTest {
    private static final LocalDate UPLOAD_DATE = LocalDate.of(2026, 10, 14);

    @Test
    void listsDocumentsForTheRequestedCustomer() {
        MainframeGateway gateway = mock(MainframeGateway.class);
        CustomerDocument document = document("D001", "C001", "payslip.pdf", "Income");
        when(gateway.getDocuments("C001")).thenReturn(List.of(document));

        List<CustomerDocument> result = new CustomerDocumentService(gateway).getDocuments("C001");

        assertEquals(List.of(document), result);
        verify(gateway).getDocuments("C001");
    }

    @Test
    void addsMetadataWithoutAFilePayload() {
        MainframeGateway gateway = mock(MainframeGateway.class);
        CustomerDocument created = document("D001", "C001", "payslip.pdf", "Income");
        when(gateway.addDocument(any(CustomerDocument.class))).thenReturn(created);

        CustomerDocument result = new CustomerDocumentService(gateway).addDocument(
                "C001", "payslip.pdf", "Income", UPLOAD_DATE);

        assertSame(created, result);
        ArgumentCaptor<CustomerDocument> request = ArgumentCaptor.forClass(CustomerDocument.class);
        verify(gateway).addDocument(request.capture());
        assertNull(request.getValue().getDocumentId());
        assertEquals("C001", request.getValue().getCustomerId());
        assertEquals("payslip.pdf", request.getValue().getFileName());
        assertEquals("Income", request.getValue().getDocumentType());
        assertEquals(UPLOAD_DATE, request.getValue().getUploadDate());
    }

    @Test
    void updatesDocumentMetadata() {
        MainframeGateway gateway = mock(MainframeGateway.class);
        CustomerDocument updated = document("D001", "C001", "updated.pdf", "Identity");
        when(gateway.updateDocument(updated)).thenReturn(updated);

        CustomerDocument result = new CustomerDocumentService(gateway).updateDocument(updated);

        assertSame(updated, result);
        verify(gateway).updateDocument(updated);
    }

    @Test
    void deletesDocumentForItsCustomer() {
        MainframeGateway gateway = mock(MainframeGateway.class);

        new CustomerDocumentService(gateway).deleteDocument("C001", "D001");

        verify(gateway).deleteDocument("C001", "D001");
    }

    private static CustomerDocument document(String id, String customerId, String fileName,
            String type) {
        return new CustomerDocument(id, customerId, fileName, type, UPLOAD_DATE);
    }
}
