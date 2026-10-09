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
}
