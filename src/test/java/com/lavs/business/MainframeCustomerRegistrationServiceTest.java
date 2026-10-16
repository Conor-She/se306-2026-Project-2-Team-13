package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.data.FakeMainframeGateway;
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
        draft.phones.add(Map.of("type", "Mobile", "number", "0215550101", "primary", "true",
                "texts", "true"));
        draft.emails.add(Map.of("email", "ada@example.com", "primary", "true"));
        draft.employers.add(Map.of("name", "Analytical Engines Ltd", "role", "Engineer",
                "employmentType", "Full-time", "income", "85000.50",
                "phone", "+64 9 555 0101"));
        draft.notes.add(Map.of("text", "Prefers email contact"));
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
        var phone = ArgumentCaptor.forClass(Phone.class);
        verify(gateway).addPhone(phone.capture());
        assertEquals("C013", phone.getValue().getCustomerId());
        assertEquals("0215550101", phone.getValue().getNumber());
        assertTrue(phone.getValue().isPrimary());
        assertTrue(phone.getValue().isSendTXT());
        var email = ArgumentCaptor.forClass(Email.class);
        verify(gateway).addEmail(email.capture());
        assertEquals("ada@example.com", email.getValue().getEmailAddress());
        assertTrue(email.getValue().isPrimary());
        var employer = ArgumentCaptor.forClass(Employer.class);
        verify(gateway).addEmployer(employer.capture());
        assertEquals("Analytical Engines Ltd", employer.getValue().getCompanyName());
        assertEquals(85000.50, employer.getValue().getIncome());
        var note = ArgumentCaptor.forClass(CustomerNote.class);
        verify(gateway).addNote(note.capture());
        assertEquals("C013", note.getValue().getCustomerId());
        assertEquals("Prefers email contact", note.getValue().getNoteText());
        var document = ArgumentCaptor.forClass(CustomerDocument.class);
        verify(gateway).addDocument(document.capture());
        assertEquals("C013", document.getValue().getCustomerId());
        assertEquals("income.pdf", document.getValue().getFileName());
    }

    @Test void invalidPhoneAndEmailAreRejectedBeforeCreatingCustomer() {
        var gateway = gateway();
        var draft = draft();
        draft.phones.add(Map.of("number", "invalid"));
        draft.emails.add(Map.of("email", "invalid"));
        var service = new MainframeCustomerRegistrationService(gateway);
        var error = assertThrows(CustomerRegistrationService.RegistrationException.class,
                () -> service.save(draft));
        assertTrue(error.errors().stream().anyMatch(item -> item.field().equals("phones.0.number")));
        assertTrue(error.errors().stream().anyMatch(item -> item.field().equals("emails.0.email")));
        verify(gateway, never()).createCustomer(any());
    }

    @Test void optionalSectionsCanBeLeftEmpty() throws Exception {
        var gateway = gateway();

        assertEquals("C013", new MainframeCustomerRegistrationService(gateway).save(draft()));

        verify(gateway).createCustomer(any());
        verify(gateway, never()).addAddress(any());
        verify(gateway, never()).addPhone(any());
        verify(gateway, never()).addEmail(any());
        verify(gateway, never()).addEmployer(any());
        verify(gateway, never()).addNote(any());
        verify(gateway, never()).addDocument(any());
    }

    @Test void savedContactEmploymentAndNoteValuesRemainAvailable() throws Exception {
        var gateway = new FakeMainframeGateway();
        var draft = draft();
        draft.phones.add(Map.of("number", "0215550101", "primary", "true", "texts", "true"));
        draft.emails.add(Map.of("email", "ada@example.com", "primary", "true"));
        draft.employers.add(Map.of("name", "Analytical Engines Ltd", "role", "Engineer",
                "employmentType", "Full-time", "income", "85000.50",
                "phone", "+64 9 555 0101"));
        draft.notes.add(Map.of("text", "Prefers email contact"));

        String customerId = new MainframeCustomerRegistrationService(gateway).save(draft);

        assertEquals("0215550101", gateway.getPhones(customerId).get(0).getNumber());
        assertEquals("ada@example.com", gateway.getEmails(customerId).get(0).getEmailAddress());
        assertEquals("Analytical Engines Ltd",
                gateway.getEmployers(customerId).get(0).getCompanyName());
        assertEquals("Prefers email contact", gateway.getNotes(customerId).get(0).getNoteText());
    }

    @Test void noteRetryKeepsCreatedCustomerAndConfirmedNotes() throws Exception {
        var gateway = gateway();
        var draft = draft();
        draft.notes.add(Map.of("text", "First"));
        draft.notes.add(Map.of("text", "Second"));
        when(gateway.addNote(any())).thenAnswer(call -> call.getArgument(0))
                .thenThrow(new IllegalStateException("Unavailable"))
                .thenAnswer(call -> call.getArgument(0));
        var service = new MainframeCustomerRegistrationService(gateway);

        assertThrows(CustomerRegistrationService.RegistrationException.class,
                () -> service.save(draft));
        var changed = draft.copy();
        changed.notes.set(0, Map.of("text", "Changed"));
        assertThrows(CustomerRegistrationService.RegistrationException.class,
                () -> service.save(changed));
        assertEquals("C013", service.save(draft));

        verify(gateway, times(1)).createCustomer(any());
        var notes = ArgumentCaptor.forClass(CustomerNote.class);
        verify(gateway, times(3)).addNote(notes.capture());
        assertEquals(java.util.List.of("First", "Second", "Second"), notes.getAllValues()
                .stream().map(CustomerNote::getNoteText).toList());
    }

    @Test void retryContinuesWithoutRecreatingCustomerOrConfirmedAddresses() throws Exception {
        var gateway = gateway();
        var draft = draft();
        draft.addresses.add(Map.of("street", "1 Main St", "suburb", "Central", "city", "Auckland",
                "postcode", "1010", "country", "New Zealand"));
        draft.phones.add(Map.of("number", "0215550101"));
        draft.emails.add(Map.of("email", "ada@example.com"));
        draft.employers.add(Map.of("name", "Analytical Engines Ltd", "role", "Engineer",
                "employmentType", "Full-time", "income", "85000.50",
                "phone", "+64 9 555 0101"));
        draft.notes.add(Map.of("text", "Prefers email contact"));
        draft.documents.add(Map.of("fileName", "income.pdf", "documentType", "Income", "uploadDate", "2026-10-15"));
        when(gateway.addDocument(any())).thenThrow(new IllegalStateException("Unavailable")).thenAnswer(i -> i.getArgument(0));
        var service = new MainframeCustomerRegistrationService(gateway);
        var error = assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(draft));
        assertTrue(error.errors().get(0).message().contains("C013"));
        assertEquals("C013", service.save(draft));
        verify(gateway, times(1)).createCustomer(any());
        verify(gateway, times(1)).addAddress(any());
        verify(gateway, times(1)).addPhone(any());
        verify(gateway, times(1)).addEmail(any());
        verify(gateway, times(1)).addEmployer(any());
        verify(gateway, times(1)).addNote(any());
        verify(gateway, times(2)).addDocument(any());
    }
}
