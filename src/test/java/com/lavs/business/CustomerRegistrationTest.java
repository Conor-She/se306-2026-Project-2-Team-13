package com.lavs.business;

import com.lavs.model.CustomerDraft;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerRegistrationTest {
    private final StubCustomerRegistrationService service = new StubCustomerRegistrationService();

    private CustomerDraft validDraft() {
        CustomerDraft draft = new CustomerDraft();
        draft.personal.putAll(Map.of("title", "Ms", "firstName", "Amelia", "lastName", "Adams",
                "dateOfBirth", "14-03-1990", "nationality", "New Zealand"));
        return draft;
    }

    @Test void missingAndInvalidPersonalFieldsBelongToFirstStep() {
        CustomerDraft draft = new CustomerDraft();
        assertEquals(5, service.validate(draft).size());
        assertTrue(service.validate(draft).stream().allMatch(e -> e.step() == 0));
        draft = validDraft();
        draft.personal.put("dateOfBirth", "01-01-2099");
        assertEquals("dateOfBirth", service.validate(draft).get(0).field());
        draft.personal.put("dateOfBirth", "invalid");
        assertEquals("Enter a valid date in dd-mm-yyyy format.", service.validate(draft).get(0).message());
    }

    @Test void everyPersonalFieldIsRequiredBeforeSaving() {
        for (String field : java.util.List.of("title", "firstName", "lastName", "dateOfBirth", "nationality")) {
            CustomerDraft draft = validDraft();
            draft.personal.put(field, "   ");
            var errors = service.validate(draft);
            assertEquals(1, errors.size(), field);
            assertEquals(field, errors.get(0).field());
            assertEquals(0, errors.get(0).step());
            assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(draft), field);
        }
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
        assertEquals(java.util.Set.of("addresses.0.suburb", "addresses.0.city", "addresses.0.postcode",
                "addresses.0.country", "phones.0.number", "emails.0.email"),
            errors.stream().map(CustomerRegistrationService.ValidationError::field).collect(java.util.stream.Collectors.toSet()));
        draft.addresses.set(0, validAddress());
        draft.phones.set(0, Map.of("number", "+64 21 555 0101"));
        draft.emails.set(0, Map.of("email", "amelia@example.com"));
        assertTrue(service.validate(draft).isEmpty());
    }
    @Test void employmentIsOptionalAndValidatesPartialEntries() {
        CustomerDraft draft = validDraft();
        assertTrue(service.validate(draft).isEmpty());
        draft.employers.add(Map.of("income", "-1"));
        var errors = service.validate(draft);
        assertTrue(errors.stream().allMatch(error -> error.step() == 2));
        assertEquals(java.util.Set.of("employers.0.name", "employers.0.role", "employers.0.employmentType", "employers.0.income", "employers.0.phone"),
            errors.stream().map(CustomerRegistrationService.ValidationError::field).collect(java.util.stream.Collectors.toSet()));
        for (String income : new String[] {"abc", "12.345"}) {
            draft.employers.set(0, validEmployer(income));
            assertFalse(service.validate(draft).isEmpty());
        }
        for (String income : new String[] {"0", "50000.00"}) {
            draft.employers.set(0, validEmployer(income));
            assertTrue(service.validate(draft).isEmpty());
        }
        assertFalse(CustomerDraft.hasEntryDetails("employers", Map.of("name", "   ")));
        assertTrue(CustomerDraft.hasEntryDetails("employers", Map.of("income", "0")));
    }
    private Map<String, String> validAddress() {
        return Map.of("street", "12 Queen St", "suburb", "Central", "city", "Auckland",
                "postcode", "1010", "country", "New Zealand");
    }

    private Map<String, String> validEmployer(String income) {
        return Map.of("name", "Acme", "role", "Engineer", "employmentType", "Full-time", "income", income,
                "phone", "+64 9 555 0101");
    }

    @Test void everyAddressAndEmployerFieldIsRequiredBeforeSaving() {
        for (String kind : java.util.List.of("addresses", "employers")) {
            Map<String, String> valid = kind.equals("addresses") ? validAddress() : validEmployer("50000.00");
            for (String field : valid.keySet()) {
                CustomerDraft draft = validDraft();
                Map<String, String> entry = new java.util.LinkedHashMap<>(valid);
                entry.put(field, "   ");
                (kind.equals("addresses") ? draft.addresses : draft.employers).add(entry);
                var errors = service.validate(draft);
                assertEquals(1, errors.size(), kind + "." + field);
                assertEquals(kind + ".0." + field, errors.get(0).field());
                assertEquals(kind.equals("addresses") ? 1 : 2, errors.get(0).step());
                assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(draft));
            }
        }
    }

    @Test void employerPhoneIsValidatedAndAddressIsPreserved() throws Exception {
        CustomerDraft draft = validDraft();
        Map<String, String> employer = new java.util.LinkedHashMap<>(validEmployer("50000.00"));
        employer.put("address", "12 Queen St, Auckland");
        employer.put("phone", "invalid");
        draft.employers.add(employer);
        assertEquals("employers.0.phone", service.validate(draft).get(0).field());
        assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(draft));
        employer.put("phone", "+64 9 555 0101");
        String id = service.save(draft);
        assertEquals("12 Queen St, Auckland", service.savedCustomers().get(id).employers.get(0).get("address"));
        assertEquals("+64 9 555 0101", service.savedCustomers().get(id).employers.get(0).get("phone"));
        assertTrue(CustomerDraft.hasEntryDetails("employers", Map.of("address", "12 Queen St")));
        assertTrue(CustomerDraft.hasEntryDetails("employers", Map.of("phone", "095550101")));
    }

    @Test void notesAreOptionalAndBlankNotesBelongToNotesStep() {
        CustomerDraft draft = validDraft();
        assertTrue(service.validate(draft).isEmpty());
        draft.notes.add(Map.of("text", "   "));
        var errors = service.validate(draft);
        assertEquals(1, errors.size());
        assertEquals(3, errors.get(0).step());
        assertEquals("notes.0.text", errors.get(0).field());
        assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(draft));
        assertFalse(CustomerDraft.hasEntryDetails("notes", Map.of("text", "   ")));
    }

    @Test void multipleNotesAreSavedInOrderAndCopiedIndependently() throws Exception {
        CustomerDraft draft = validDraft();
        draft.notes.add(new java.util.LinkedHashMap<>(Map.of("text", "First note\nSecond line")));
        draft.notes.add(new java.util.LinkedHashMap<>(Map.of("text", "Another note")));
        String id = service.save(draft);
        draft.notes.get(0).put("text", "Changed");
        draft.notes.remove(1);
        CustomerDraft saved = service.savedCustomers().get(id);
        assertEquals(2, saved.notes.size());
        assertEquals("First note\nSecond line", saved.notes.get(0).get("text"));
        assertEquals("Another note", saved.notes.get(1).get("text"));
        saved.notes.get(0).put("text", "Changed again");
        assertEquals("First note\nSecond line", service.savedCustomers().get(id).notes.get(0).get("text"));
        CustomerDraft notesOnly = new CustomerDraft();
        notesOnly.notes.add(Map.of("text", "Unsaved note"));
        assertTrue(notesOnly.hasData());
    }

    @Test void saveValidatesAndKeepsAnIndependentSnapshot() throws Exception {
        assertThrows(CustomerRegistrationService.RegistrationException.class, () -> service.save(new CustomerDraft()));
        CustomerDraft draft = validDraft();
        draft.emails.add(new java.util.LinkedHashMap<>(Map.of("email", "amelia@example.com")));
        draft.employers.add(new java.util.LinkedHashMap<>(validEmployer("50000.00")));
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
