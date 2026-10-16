package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.Address;
import com.lavs.model.Customer;
import com.lavs.model.CustomerDocument;
import com.lavs.model.CustomerDraft;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Registers customers through the signed-in gateway. Documents store metadata only. */
public class MainframeCustomerRegistrationService implements CustomerRegistrationService {
    private final MainframeGateway gateway;
    private final CustomerRegistrationService validator = new StubCustomerRegistrationService();
    private CustomerDraft pending;
    private String customerId;
    private int addressIndex;
    private int documentIndex;
    private int noteIndex;

    public MainframeCustomerRegistrationService(MainframeGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    @Override public List<ValidationError> validate(CustomerDraft draft) {
        var errors = new ArrayList<>(validator.validate(draft));
        if (!draft.phones.isEmpty()) errors.add(new ValidationError(1, "", "Phone saving is not available yet. Remove phone entries to continue."));
        if (!draft.emails.isEmpty()) errors.add(new ValidationError(1, "", "Email saving is not available yet. Remove email entries to continue."));
        if (!draft.employers.isEmpty()) errors.add(new ValidationError(2, "", "Employer saving is not available yet. Remove employer entries to continue."));
        return List.copyOf(errors);
    }

    @Override public synchronized String save(CustomerDraft draft) throws RegistrationException {
        var errors = validate(draft);
        if (!errors.isEmpty()) throw new RegistrationException(errors);
        // Keep confirmed progress so retrying child records does not create another customer.
        if (pending != null && (!pending.personal.equals(draft.personal)
                || !pending.addresses.equals(draft.addresses) || !pending.documents.equals(draft.documents)
                || !pending.notes.equals(draft.notes))) {
            throw failure("Customer " + customerId + " already exists. Restore the submitted details before retrying the remaining records.");
        }
        try {
            if (customerId == null) {
                var p = draft.personal;
                Customer saved = gateway.createCustomer(new Customer(null, p.get("title"),
                        p.get("firstName"), p.get("lastName"),
                        LocalDate.parse(p.get("dateOfBirth"), DateTimeFormatter.ofPattern("dd-MM-uuuu")),
                        p.get("nationality")));
                customerId = Objects.requireNonNull(saved.getCustomerId(), "Missing customer ID");
                pending = draft.copy();
            }
            while (addressIndex < draft.addresses.size()) {
                var a = draft.addresses.get(addressIndex);
                gateway.addAddress(new Address(null, customerId, a.get("street"), a.get("suburb"),
                        a.get("city"), a.get("postcode"), a.get("country"), Boolean.parseBoolean(a.get("mailing"))));
                addressIndex++;
            }
            while (documentIndex < draft.documents.size()) {
                var d = draft.documents.get(documentIndex);
                gateway.addDocument(new CustomerDocument(null, customerId, d.get("fileName"),
                        d.get("documentType"), LocalDate.parse(d.get("uploadDate"))));
                documentIndex++;
            }
            var notes = new CustomerNoteService(gateway);
            while (noteIndex < draft.notes.size()) {
                notes.addNote(customerId, draft.notes.get(noteIndex).get("text"));
                noteIndex++;
            }
        } catch (RuntimeException exception) {
            RegistrationException failure = failure(customerId == null
                    ? "Customer creation could not be confirmed. Check the organisation ID, connection and customer list before retrying."
                    : "Customer " + customerId + " was created, but some related records could not be confirmed. Your details are kept. Check saved records before retrying.");
            failure.initCause(exception);
            throw failure;
        }
        String result = customerId;
        customerId = null;
        pending = null;
        addressIndex = documentIndex = noteIndex = 0;
        return result;
    }

    private static RegistrationException failure(String message) {
        return new RegistrationException(List.of(new ValidationError(5, "", message)));
    }
}
