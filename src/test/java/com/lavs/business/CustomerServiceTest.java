package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Address;
import com.lavs.model.Customer;
import com.lavs.model.Email;
import com.lavs.model.Employer;
import com.lavs.model.Phone;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {

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
}
