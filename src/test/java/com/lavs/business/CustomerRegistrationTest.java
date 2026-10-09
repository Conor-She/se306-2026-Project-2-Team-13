package com.lavs.business;

import com.lavs.model.CustomerDraft;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerRegistrationTest {
    private final StubCustomerRegistrationService service = new StubCustomerRegistrationService();

    private CustomerDraft validDraft() {
        CustomerDraft draft = new CustomerDraft();
        draft.personal.putAll(Map.of("firstName", "Amelia", "lastName", "Adams", "dateOfBirth", "14-03-1990"));
        return draft;
    }

    @Test void missingAndInvalidPersonalFieldsBelongToFirstStep() {
        CustomerDraft draft = new CustomerDraft();
        assertEquals(3, service.validate(draft).size());
        assertTrue(service.validate(draft).stream().allMatch(e -> e.step() == 0));
        draft = validDraft();
        draft.personal.put("dateOfBirth", "01-01-2099");
        assertEquals("dateOfBirth", service.validate(draft).get(0).field());
        draft.personal.put("dateOfBirth", "invalid");
        assertEquals("Enter a valid date in dd-mm-yyyy format.", service.validate(draft).get(0).message());
    }

    @Test void dateOfBirthUsesStrictDayMonthYearFormat() {
        CustomerDraft draft = validDraft();
        for (String date : new String[] {"1990-03-14", "31-02-1990", "29-02-1991"}) {
            draft.personal.put("dateOfBirth", date);
            assertTrue(service.validate(draft).stream().anyMatch(error -> error.field().equals("dateOfBirth")), date);
        }
        draft.personal.put("dateOfBirth", "29-02-1992");
        assertTrue(service.validate(draft).isEmpty());
    }

    @Test void contactValidationUsesIndexedFieldsAndAllowsEmptySections() {
        CustomerDraft draft = validDraft();
        assertTrue(service.validate(draft).isEmpty());
        draft.addresses.add(Map.of("street", "12 Queen St"));
        draft.phones.add(Map.of("number", "abc"));
        draft.emails.add(Map.of("email", "invalid"));
        var errors = service.validate(draft);
        assertTrue(errors.stream().allMatch(error -> error.step() == 1));
        assertEquals(java.util.Set.of("addresses.0.city", "phones.0.number", "emails.0.email"),
            errors.stream().map(CustomerRegistrationService.ValidationError::field).collect(java.util.stream.Collectors.toSet()));
        draft.addresses.set(0, Map.of("street", "12 Queen St", "city", "Auckland"));
        draft.phones.set(0, Map.of("number", "+64 21 555 0101"));
        draft.emails.set(0, Map.of("email", "amelia@example.com"));
        assertTrue(service.validate(draft).isEmpty());
    }
    @Test void employmentIsOptionalAndValidatesPartialEntries() {
        CustomerDraft draft = validDraft();
        assertTrue(service.validate(draft).isEmpty());
        draft.employers.add(Map.of("income", "-1", "phone", "abc"));
        var errors = service.validate(draft);
        assertTrue(errors.stream().allMatch(error -> error.step() == 2));
        assertEquals(java.util.Set.of("employers.0.name", "employers.0.income", "employers.0.phone"),
            errors.stream().map(CustomerRegistrationService.ValidationError::field).collect(java.util.stream.Collectors.toSet()));
        for (String income : new String[] {"abc", "12.345"}) {
            draft.employers.set(0, Map.of("name", "Acme", "income", income));
            assertFalse(service.validate(draft).isEmpty());
        }
        for (String income : new String[] {"", "0", "50000.00"}) {
            draft.employers.set(0, Map.of("name", "Acme", "income", income, "phone", "+64 21 555 0101"));
            assertTrue(service.validate(draft).isEmpty());
        }
        assertFalse(CustomerDraft.hasEntryDetails("employers", Map.of("name", "   ")));
        assertTrue(CustomerDraft.hasEntryDetails("employers", Map.of("income", "0")));
    }
    @Test void saveValidatesAndKeepsAnIndependentSnapshot() throws Exception {
        assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(new CustomerDraft()));
        CustomerDraft draft = validDraft();
        draft.emails.add(new java.util.LinkedHashMap<>(Map.of("email", "amelia@example.com")));
        draft.employers.add(new java.util.LinkedHashMap<>(Map.of("name", "Acme", "income", "50000.00")));
        String id = service.save(draft);
        assertEquals("C011", id);
        draft.personal.put("firstName", "Changed");
        draft.emails.get(0).put("email", "changed@example.com");
        draft.employers.get(0).put("name", "Changed");
        CustomerDraft saved = service.savedCustomers().get(id);
        assertEquals("Amelia", saved.personal.get("firstName"));
        assertEquals("amelia@example.com", saved.emails.get(0).get("email"));
        assertEquals("Acme", saved.employers.get(0).get("name"));
        saved.personal.clear();
        assertEquals("Amelia", service.savedCustomers().get(id).personal.get("firstName"));
    }
}
