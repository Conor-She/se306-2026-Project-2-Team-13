package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.*;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MainframeCustomerRegistrationServiceTest {
    private CustomerDraft draft() {
        var draft = new CustomerDraft();
        draft.personal.putAll(Map.of("title", "Ms", "firstName", "Ada", "lastName", "Lovelace",
                "dateOfBirth", "10-12-1990", "nationality", "New Zealand"));
        return draft;
    }

    private MainframeGateway gateway() {
        var gateway = mock(MainframeGateway.class);
        when(gateway.createCustomer(any())).thenReturn(new Customer("C013", "Ms", "Ada", "Lovelace",
                LocalDate.of(1990, 12, 10), "New Zealand"));
        return gateway;
    }

    @Test void mapsPersonalDetailsAndUsesServerIdForRelatedRecords() throws Exception {
        var gateway = gateway();
        var draft = draft();
        draft.addresses.add(Map.of("street", "1 Main St", "suburb", "Central", "city", "Auckland",
                "postcode", "1010", "country", "New Zealand", "mailing", "true"));
        draft.documents.add(Map.of("fileName", "income.pdf", "documentType", "Income", "uploadDate", "2026-10-15"));
        assertEquals("C013", new MainframeCustomerRegistrationService(gateway).save(draft));
        var customer = ArgumentCaptor.forClass(Customer.class);
        verify(gateway).createCustomer(customer.capture());
        assertEquals(LocalDate.of(1990, 12, 10), customer.getValue().getDateOfBirth());
        assertEquals("Ada", customer.getValue().getFirstName());
        var address = ArgumentCaptor.forClass(Address.class);
        verify(gateway).addAddress(address.capture());
        assertEquals("C013", address.getValue().getCustomerId());
        assertTrue(address.getValue().isMailing());
        var document = ArgumentCaptor.forClass(CustomerDocument.class);
        verify(gateway).addDocument(document.capture());
        assertEquals("C013", document.getValue().getCustomerId());
        assertEquals("income.pdf", document.getValue().getFileName());
    }

    @Test void unsupportedSectionsAreRejectedBeforeCreatingCustomer() {
        var gateway = gateway();
        var draft = draft();
        draft.phones.add(Map.of("number", "0215550101"));
        var service = new MainframeCustomerRegistrationService(gateway);
        assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(draft));
        verify(gateway, never()).createCustomer(any());
    }

    @Test void retryContinuesWithoutRecreatingCustomerOrConfirmedAddresses() throws Exception {
        var gateway = gateway();
        var draft = draft();
        draft.addresses.add(Map.of("street", "1 Main St", "suburb", "Central", "city", "Auckland",
                "postcode", "1010", "country", "New Zealand"));
        draft.documents.add(Map.of("fileName", "income.pdf", "documentType", "Income", "uploadDate", "2026-10-15"));
        when(gateway.addDocument(any())).thenThrow(new IllegalStateException("Unavailable")).thenAnswer(i -> i.getArgument(0));
        var service = new MainframeCustomerRegistrationService(gateway);
        var error = assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(draft));
        assertTrue(error.errors().get(0).message().contains("C013"));
        assertEquals("C013", service.save(draft));
        verify(gateway, times(1)).createCustomer(any());
        verify(gateway, times(1)).addAddress(any());
        verify(gateway, times(2)).addDocument(any());
    }
}
