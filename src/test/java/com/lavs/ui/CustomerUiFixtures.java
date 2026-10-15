package com.lavs.ui;

import java.util.*;
import java.lang.reflect.Field;
import static com.lavs.ui.CustomerRecordEditor.Kind;

/** Explicit data for tests of the standalone local editor; never shipped in the app. */
final class CustomerUiFixtures {
    @SuppressWarnings("unchecked")
    static void seed(CustomerDetailsController controller, CustomersController.CustomerSummary customer) throws Exception {
        controller.setCustomer(customer);
        Field editingField = CustomerDetailsController.class.getDeclaredField("editing");
        editingField.setAccessible(true);
        Object editing = editingField.get(controller);
        Field stateField = CustomerDetailsEditing.class.getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(editing);
        Field detailsField = state.getClass().getDeclaredField("details");
        detailsField.setAccessible(true);
        Map<Kind, Map<String, String>> details = (Map<Kind, Map<String, String>>) detailsField.get(state);
        Field recordsField = state.getClass().getDeclaredField("records");
        recordsField.setAccessible(true);
        Map<Kind, List<Map<String, String>>> records = (Map<Kind, List<Map<String, String>>>) recordsField.get(state);
            details.put(Kind.PERSONAL, values("title", "Mrs", "firstName", firstName(customer.name()), "lastName", lastName(customer.name()), "birthDate", "14-03-1990", "nationality", "New Zealand"));
            details.put(Kind.CONTACT, values("email", customer.email(), "phone", customer.phone(), "address", "12 Koru Street, Whero, Auckland, 0945"));
            details.put(Kind.EMPLOYMENT, values("employer", "Smith Consulting", "role", "Consultant", "employmentType", "Full-time", "income", "85000"));
            records.put(Kind.CONTACT_RECORD, new ArrayList<>());
            records.put(Kind.EMPLOYMENT, new ArrayList<>());
            records.put(Kind.LOAN, new ArrayList<>(List.of(
                    values("id", "LN00123", "type", "Home Loan", "amount", "450000", "rate", "4.95", "status", "Active"),
                    values("id", "LN00456", "type", "Personal Loan", "amount", "15000", "rate", "12.50", "status", "Active"),
                    values("id", "LN00789", "type", "Car Loan", "amount", "30000", "rate", "6.75", "status", "Active"))));
            records.put(Kind.NOTE, new ArrayList<>(List.of(
                    values("title", "Loan discussion", "text", "Customer is looking to consolidate existing loans.", "date", "2025-09-12", "author", "Jack Slater"),
                    values("title", "Follow up", "text", "Called customer to confirm documents received.", "date", "2025-08-03", "author", "Sarah Lee"),
                    values("title", "General note", "text", "Customer is happy with the current service.", "date", "2025-06-21", "author", "Jack Slater"))));
            records.put(Kind.DOCUMENT, new ArrayList<>(List.of(
                    values("fileName", "ID - Passport.pdf", "type", "Identity", "date", "2025-09-12"),
                    values("fileName", "Proof of Address.pdf", "type", "Address", "date", "2025-08-03"),
                    values("fileName", "Payslip.pdf", "type", "Income", "date", "2025-06-21"),
                    values("fileName", "Bank Statement.pdf", "type", "Bank Statement", "date", "2025-05-10"))));
            records.put(Kind.GUARANTOR, new ArrayList<>(List.of(values("firstName", "Michael", "lastName", "Johnson", "relationship", "Brother", "phone", "021 987 6543", "email", "michael.johnson@email.com", "status", "Active"))));
        controller.setCustomer(customer);
    }
    private static String firstName(String name) { return name.split(" ", 2)[0]; }
    private static String lastName(String name) { return name.contains(" ") ? name.split(" ", 2)[1] : ""; }
    private static Map<String, String> values(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }
}
