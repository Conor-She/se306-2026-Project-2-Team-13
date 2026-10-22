package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeGateway;
import com.lavs.model.Address;
import com.lavs.model.Customer;
import com.lavs.model.CustomerDetails;
import com.lavs.model.CustomerId;
import com.lavs.model.CustomerPreview;
import com.lavs.model.Email;
import com.lavs.model.Employer;
import com.lavs.model.Phone;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// AI-assisted: Helped write the customer date of birth tests.
class CustomerServiceTest {

    @Test
    void searchReturnsNoPreviewsWhenNothingMatches() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        gateway.createCustomer(newCustomer());

        assertEquals(List.of(), new CustomerService(gateway).search("Nobody"));
    }

    @Test
    void searchReturnsOnlyTheMatchingPreviewWithoutRetrievingDetails() {
        MainframeGateway gateway = mock(MainframeGateway.class);
        Customer customer = new Customer("C1", "Ms", "Jane", "Smith",
                LocalDate.of(1990, 5, 1), "NZ");
        when(gateway.searchCustomers("Jane")).thenReturn(List.of(customer));

        assertEquals(List.of(new CustomerPreview(new CustomerId("C1"), "Ms", "Jane", "Smith", LocalDate.of(1990, 5, 1))),
                new CustomerService(gateway).search(" Jane "));

        verify(gateway).searchCustomers("Jane");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void searchReturnsEveryMatchingPreviewAndExcludesOtherCustomers() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        Customer jane = gateway.createCustomer(newCustomer());
        Customer john = gateway.createCustomer(new Customer(null, "Mr", "John", "Smith",
                LocalDate.of(1985, 1, 1), "NZ"));
        gateway.createCustomer(new Customer(null, "Ms", "Alice", "Jones", null, "NZ"));

        var results = new CustomerService(gateway).search("Smith");

        assertEquals(2, results.size());
        assertEquals(java.util.Set.of(new CustomerId(jane.getCustomerId()),
                        new CustomerId(john.getCustomerId())),
                results.stream().map(CustomerPreview::customerId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void emptySearchListsCustomersAndNullQueryIsRejected() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        gateway.createCustomer(newCustomer());
        CustomerService service = new CustomerService(gateway);

        assertEquals(1, service.search(" ").size());
        assertThrows(NullPointerException.class, () -> service.search(null));
    }

    @Test
    void retrievalIncludesPersonalDetailsAndAllChildRecordsForOnlyTheRequestedCustomer() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        CustomerService service = new CustomerService(gateway);
        Customer customer = service.create(newCustomer(),
                List.of(new Address(null, null, "5 Queen St", "CBD", "Auckland", "1010", "NZ", true),
                        new Address(null, null, "6 King St", "CBD", "Auckland", "1010", "NZ", false)),
                List.of(new Phone(null, null, "0211234567", true, true),
                        new Phone(null, null, "091234567", false, false)),
                List.of(new Email(null, null, "jane@example.com", true),
                        new Email(null, null, "work@example.com", false)),
                List.of(new Employer(null, null, "Acme", "Engineer", 90_000, "Primary"),
                        new Employer(null, null, "Other", "Consultant", 10_000, "Secondary")));
        Customer other = gateway.createCustomer(newCustomer());
        gateway.addPhone(new Phone(null, other.getCustomerId(), "099999999", true, false));
        String id = customer.getCustomerId();

        CustomerDetails details = service.getById(new CustomerId(id));

        assertEquals(customer, details.customer());
        assertEquals(LocalDate.of(1990, 5, 1), details.customer().getDateOfBirth());
        assertEquals("NZ", details.customer().getNationality());
        assertEquals(gateway.getAddresses(id), details.addresses());
        assertEquals(gateway.getPhones(id), details.phones());
        assertEquals(gateway.getEmails(id), details.emails());
        assertEquals(gateway.getEmployers(id), details.employers());
    }

    @Test
    void retrievalAllowsEmptyChildCollections() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        Customer customer = gateway.createCustomer(newCustomer());

        CustomerDetails details = new CustomerService(gateway)
                .getById(new CustomerId(customer.getCustomerId()));

        assertEquals(List.of(), details.addresses());
        assertEquals(List.of(), details.phones());
        assertEquals(List.of(), details.emails());
        assertEquals(List.of(), details.employers());
    }

    @Test
    void retrievalRejectsMissingCustomerWithoutReadingChildren() {
        MainframeGateway gateway = mock(MainframeGateway.class);

        assertThrows(IllegalArgumentException.class,
                () -> new CustomerService(gateway).getById(new CustomerId("missing")));

        verify(gateway).getCustomer("missing");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void retrievalRejectsInvalidIds() {
        CustomerService service = new CustomerService(new FakeMainframeGateway());
        assertThrows(NullPointerException.class, () -> service.getById(null));
        assertThrows(IllegalArgumentException.class, () -> new CustomerId(null));
        assertThrows(IllegalArgumentException.class, () -> new CustomerId(" "));
    }

    private Customer newCustomer() {
        return new Customer(null, "Ms", "Jane", "Smith", LocalDate.of(1990, 5, 1), "NZ");
    }

    @Test
    void createsCustomerAndAllProvidedDetails() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        CustomerService service = new CustomerService(gateway);

        Customer created = service.create(newCustomer(),
                List.of(new Address(null, null, "5 Queen St", "CBD", "Auckland", "1010",
                        "NZ", true)),
                List.of(new Phone(null, null, "0211234567", true, true)),
                List.of(new Email(null, null, "jane@example.com", true)),
                List.of(new Employer(null, null, "Acme Ltd", "Engineer", 90_000,
                        "Primary")));

        assertNotNull(created.getCustomerId());
        assertNotNull(gateway.getAddresses(created.getCustomerId()).get(0).getAddressId());
        assertNotNull(gateway.getPhones(created.getCustomerId()).get(0).getPhoneId());
        assertNotNull(gateway.getEmails(created.getCustomerId()).get(0).getEmailId());
        assertNotNull(gateway.getEmployers(created.getCustomerId()).get(0).getEmployerId());
    }

    @Test
    void rejectsMissingFirstNameBeforeCreatingAnything() {
        FakeMainframeGateway gateway = spy(new FakeMainframeGateway());
        CustomerService service = new CustomerService(gateway);
        Customer customer = newCustomer();
        customer.setFirstName(" ");

        assertThrows(IllegalArgumentException.class,
                () -> service.create(customer, List.of(), List.of(), List.of(), List.of()));

        verify(gateway, never()).createCustomer(any());
    }

    @Test
    void rejectsMissingLastNameBeforeCreatingAnything() {
        FakeMainframeGateway gateway = spy(new FakeMainframeGateway());
        CustomerService service = new CustomerService(gateway);
        Customer customer = newCustomer();
        customer.setLastName(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(customer, List.of(), List.of(), List.of(), List.of()));

        verify(gateway, never()).createCustomer(any());
    }

    @Test
    void allowsCustomerWithNoEmployers() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        CustomerService service = new CustomerService(gateway);

        Customer created = service.create(newCustomer(), List.of(), List.of(), List.of(),
                List.of());

        assertNotNull(created.getCustomerId());
        assertEquals(List.of(), gateway.getEmployers(created.getCustomerId()));
    }

    @Test
    void editingUpdatesOnlyTheChangedDetails() {
        FakeMainframeGateway gateway = spy(new FakeMainframeGateway());
        CustomerService service = new CustomerService(gateway);
        Customer customer = gateway.createCustomer(newCustomer());
        String customerId = customer.getCustomerId();
        Address address = gateway.addAddress(new Address(null, customerId, "5 Queen St", "CBD",
                "Auckland", "1010", "NZ", true));
        Phone phone = gateway.addPhone(
                new Phone(null, customerId, "0211234567", true, true));
        Email email = gateway.addEmail(
                new Email(null, customerId, "old@example.com", true));
        Employer employer = gateway.addEmployer(
                new Employer(null, customerId, "Acme Ltd", "Engineer", 90_000, "Primary"));
        clearInvocations(gateway);

        service.update(customer, List.of(address), List.of(phone),
                List.of(new Email(email.getEmailId(), customerId, "new@example.com", true)),
                List.of(employer));

        verify(gateway, never()).updateCustomer(any());
        verify(gateway, never()).updateAddress(any());
        verify(gateway, never()).updatePhone(any());
        verify(gateway).updateEmail(any());
        verify(gateway, never()).updateEmployer(any());
        assertEquals("new@example.com", gateway.getEmails(customerId).get(0).getEmailAddress());
    }

    @Test
    void editingCanAddAndDeleteDetails() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        CustomerService service = new CustomerService(gateway);
        Customer customer = gateway.createCustomer(newCustomer());
        String customerId = customer.getCustomerId();
        Address oldAddress = gateway.addAddress(new Address(null, customerId, "Old St", "CBD",
                "Auckland", "1010", "NZ", true));

        service.update(customer,
                List.of(new Address(null, customerId, "New St", "CBD", "Auckland", "1010",
                        "NZ", true)),
                List.of(), List.of(), List.of());

        assertEquals(1, gateway.getAddresses(customerId).size());
        assertEquals("New St", gateway.getAddresses(customerId).get(0).getStreet());
        assertThrows(RuntimeException.class,
                () -> gateway.updateAddress(oldAddress));
    }

    @Test
    void editingCanRemoveEveryEmployer() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        CustomerService service = new CustomerService(gateway);
        Customer customer = gateway.createCustomer(newCustomer());
        String customerId = customer.getCustomerId();
        gateway.addEmployer(new Employer(null, customerId, "Acme Ltd", "Engineer", 90_000,
                "Primary"));

        service.update(customer, List.of(), List.of(), List.of(), List.of());

        assertEquals(List.of(), gateway.getEmployers(customerId));
    }

    @Test
    void editingCustomerReadWithDateOfBirthRetainsItWhenChangingTheirName() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        CustomerService service = new CustomerService(gateway);
        Customer stored = gateway.createCustomer(newCustomer());
        Customer loaded = service.getById(new CustomerId(stored.getCustomerId())).customer();
        Customer renamed = new Customer(loaded.getCustomerId(), loaded.getTitle(),
                "Janet", loaded.getLastName(), loaded.getDateOfBirth(), loaded.getNationality());

        service.update(renamed, List.of(), List.of(), List.of(), List.of());

        assertEquals(LocalDate.of(1990, 5, 1), service.getById(new CustomerId(stored.getCustomerId()))
                .customer().getDateOfBirth());
    }
}
