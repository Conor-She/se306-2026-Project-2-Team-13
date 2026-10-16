package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.Address;
import com.lavs.model.Customer;
import com.lavs.model.CustomerDocument;
import com.lavs.model.CustomerDraft;
import com.lavs.model.CustomerNote;
import com.lavs.model.Email;
import com.lavs.model.Employer;
import com.lavs.model.Phone;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/** Registers customers through the signed-in gateway. Documents store metadata only. */
public class MainframeCustomerRegistrationService implements CustomerRegistrationService {
    private final MainframeGateway gateway;
    private final CustomerRegistrationService validator = new StubCustomerRegistrationService();
    private CustomerDraft pending;
    private String customerId;
    private int addressIndex;
    private int phoneIndex;
    private int emailIndex;
    private int employerIndex;
    private int noteIndex;
    private int documentIndex;

    public MainframeCustomerRegistrationService(MainframeGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    @Override public List<ValidationError> validate(CustomerDraft draft) {
        return validator.validate(draft);
    }

    @Override public synchronized String save(CustomerDraft draft) throws RegistrationException {
        var errors = validate(draft);
        if (!errors.isEmpty()) throw new RegistrationException(errors);
        // Keep confirmed progress so retrying child records does not create another customer.
        if (pending != null && !sameDraft(pending, draft)) {
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
            while (phoneIndex < draft.phones.size()) {
                var p = draft.phones.get(phoneIndex);
                gateway.addPhone(new Phone(null, customerId, p.get("number"),
                        Boolean.parseBoolean(p.get("primary")),
                        Boolean.parseBoolean(p.get("texts"))));
                phoneIndex++;
            }
            while (emailIndex < draft.emails.size()) {
                var e = draft.emails.get(emailIndex);
                gateway.addEmail(new Email(null, customerId, e.get("email"),
                        Boolean.parseBoolean(e.get("primary"))));
                emailIndex++;
            }
            while (employerIndex < draft.employers.size()) {
                var e = draft.employers.get(employerIndex);
                gateway.addEmployer(new Employer(null, customerId, e.get("name"), e.get("role"),
                        Double.parseDouble(e.get("income")), e.get("employmentType")));
                employerIndex++;
            }
            while (noteIndex < draft.notes.size()) {
                var n = draft.notes.get(noteIndex);
                gateway.addNote(new CustomerNote(null, customerId, n.get("text"),
                        LocalDate.now()));
                noteIndex++;
            }
            while (documentIndex < draft.documents.size()) {
                var d = draft.documents.get(documentIndex);
                gateway.addDocument(new CustomerDocument(null, customerId, d.get("fileName"),
                        d.get("documentType"), LocalDate.parse(d.get("uploadDate"))));
                documentIndex++;
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
        addressIndex = phoneIndex = emailIndex = employerIndex = noteIndex = documentIndex = 0;
        return result;
    }

    private static boolean sameDraft(CustomerDraft left, CustomerDraft right) {
        return left.personal.equals(right.personal)
                && left.addresses.equals(right.addresses)
                && left.phones.equals(right.phones)
                && left.emails.equals(right.emails)
                && left.employers.equals(right.employers)
                && left.notes.equals(right.notes)
                && left.documents.equals(right.documents);
    }

    private static RegistrationException failure(String message) {
        return new RegistrationException(List.of(new ValidationError(5, "", message)));
    }
}
