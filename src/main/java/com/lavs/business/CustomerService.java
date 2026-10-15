package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.Address;
import com.lavs.model.Customer;
import com.lavs.model.CustomerDetails;
import com.lavs.model.CustomerId;
import com.lavs.model.CustomerPreview;
import com.lavs.model.Email;
import com.lavs.model.Employer;
import com.lavs.model.Phone;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

// Business logic for searching, retrieving, creating and editing customers.
public class CustomerService {

    private final MainframeGateway gateway;

    public CustomerService(MainframeGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    /** Returns previews matching the gateway's search rules; an empty query lists customers. */
    public List<CustomerPreview> search(String query) {
        Objects.requireNonNull(query, "query must not be null");
        return gateway.searchCustomers(query.strip()).stream()
                .map(customer -> new CustomerPreview(new CustomerId(customer.getCustomerId()),
                        customer.getTitle(), customer.getFirstName(), customer.getLastName()))
                .toList();
    }

    /** Retrieves personal details and every address, phone, email and employer record. */
    public CustomerDetails getById(CustomerId id) {
        Objects.requireNonNull(id, "id must not be null");
        String customerId = id.value();
        Customer customer = gateway.getCustomer(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Customer '" + customerId + "' does not exist");
        }
        return new CustomerDetails(customer, gateway.getAddresses(customerId),
                gateway.getPhones(customerId), gateway.getEmails(customerId),
                gateway.getEmployers(customerId));
    }

    public Customer create(Customer customer, List<Address> addresses, List<Phone> phones,
            List<Email> emails, List<Employer> employers) {
        validateCustomer(customer, false);
        requireLists(addresses, phones, emails, employers);

        Customer created = gateway.createCustomer(customer);
        String customerId = created.getCustomerId();
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("The mainframe did not assign a customer ID");
        }

        addresses.stream().map(address -> forCustomer(address, customerId))
                .forEach(gateway::addAddress);
        phones.stream().map(phone -> forCustomer(phone, customerId))
                .forEach(gateway::addPhone);
        emails.stream().map(email -> forCustomer(email, customerId))
                .forEach(gateway::addEmail);
        employers.stream().map(employer -> forCustomer(employer, customerId))
                .forEach(gateway::addEmployer);

        return created;
    }

    public Customer update(Customer customer, List<Address> addresses, List<Phone> phones,
            List<Email> emails, List<Employer> employers) {
        validateCustomer(customer, true);
        requireLists(addresses, phones, emails, employers);

        String customerId = customer.getCustomerId();
        Customer existingCustomer = gateway.getCustomer(customerId);
        if (existingCustomer == null) {
            throw new IllegalArgumentException("Customer '" + customerId + "' does not exist");
        }

        List<Address> existingAddresses = gateway.getAddresses(customerId);
        List<Phone> existingPhones = gateway.getPhones(customerId);
        List<Email> existingEmails = gateway.getEmails(customerId);
        List<Employer> existingEmployers = gateway.getEmployers(customerId);

        List<Address> requestedAddresses = addresses.stream()
                .map(address -> forCustomer(address, customerId)).toList();
        List<Phone> requestedPhones = phones.stream()
                .map(phone -> forCustomer(phone, customerId)).toList();
        List<Email> requestedEmails = emails.stream()
                .map(email -> forCustomer(email, customerId)).toList();
        List<Employer> requestedEmployers = employers.stream()
                .map(employer -> forCustomer(employer, customerId)).toList();

        validateIds(existingAddresses, requestedAddresses, Address::getAddressId, "address");
        validateIds(existingPhones, requestedPhones, Phone::getPhoneId, "phone");
        validateIds(existingEmails, requestedEmails, Email::getEmailId, "email");
        validateIds(existingEmployers, requestedEmployers, Employer::getEmployerId, "employer");

        Customer result = sameCustomer(existingCustomer, customer)
                ? existingCustomer
                : gateway.updateCustomer(customer);

        sync(existingAddresses, requestedAddresses, Address::getAddressId,
                CustomerService::sameAddress, gateway::addAddress, gateway::updateAddress,
                id -> gateway.deleteAddress(customerId, id));
        sync(existingPhones, requestedPhones, Phone::getPhoneId,
                CustomerService::samePhone, gateway::addPhone, gateway::updatePhone,
                id -> gateway.deletePhone(customerId, id));
        sync(existingEmails, requestedEmails, Email::getEmailId,
                CustomerService::sameEmail, gateway::addEmail, gateway::updateEmail,
                id -> gateway.deleteEmail(customerId, id));
        sync(existingEmployers, requestedEmployers, Employer::getEmployerId,
                CustomerService::sameEmployer, gateway::addEmployer, gateway::updateEmployer,
                id -> gateway.deleteEmployer(customerId, id));

        return result;
    }

    private void validateCustomer(Customer customer, boolean requireId) {
        if (customer == null) {
            throw new IllegalArgumentException("customer must not be null");
        }
        requireNotBlank(customer.getFirstName(), "firstName");
        requireNotBlank(customer.getLastName(), "lastName");
        if (requireId) {
            requireNotBlank(customer.getCustomerId(), "customerId");
        }
    }

    private void requireLists(List<Address> addresses, List<Phone> phones, List<Email> emails,
            List<Employer> employers) {
        Objects.requireNonNull(addresses, "addresses must not be null");
        Objects.requireNonNull(phones, "phones must not be null");
        Objects.requireNonNull(emails, "emails must not be null");
        Objects.requireNonNull(employers, "employers must not be null");
    }

    private void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    private <T> void validateIds(List<T> existing, List<T> requested, Function<T, Long> id,
            String itemName) {
        Set<Long> existingIds = new HashSet<>();
        for (T item : existing) {
            existingIds.add(id.apply(item));
        }

        Set<Long> requestedIds = new HashSet<>();
        for (T item : requested) {
            if (item == null) {
                throw new IllegalArgumentException(itemName + " must not be null");
            }
            Long itemId = id.apply(item);
            if (itemId == null) {
                continue;
            }
            if (!existingIds.contains(itemId)) {
                throw new IllegalArgumentException(
                        itemName + " '" + itemId + "' does not belong to this customer");
            }
            if (!requestedIds.add(itemId)) {
                throw new IllegalArgumentException("Duplicate " + itemName + " ID: " + itemId);
            }
        }
    }

    private <T> void sync(List<T> existing, List<T> requested, Function<T, Long> id,
            BiPredicate<T, T> same, Consumer<T> add, Consumer<T> update,
            Consumer<Long> delete) {
        Map<Long, T> existingById = new HashMap<>();
        for (T item : existing) {
            existingById.put(id.apply(item), item);
        }

        Set<Long> retainedIds = new HashSet<>();
        for (T item : requested) {
            Long itemId = id.apply(item);
            if (itemId == null) {
                add.accept(item);
            } else {
                retainedIds.add(itemId);
                if (!same.test(existingById.get(itemId), item)) {
                    update.accept(item);
                }
            }
        }

        for (Long existingId : existingById.keySet()) {
            if (!retainedIds.contains(existingId)) {
                delete.accept(existingId);
            }
        }
    }

    private Address forCustomer(Address address, String customerId) {
        Objects.requireNonNull(address, "address must not be null");
        return new Address(address.getAddressId(), customerId, address.getStreet(),
                address.getSuburb(), address.getCity(), address.getPostCode(),
                address.getCountry(), address.isMailing());
    }

    private Phone forCustomer(Phone phone, String customerId) {
        Objects.requireNonNull(phone, "phone must not be null");
        return new Phone(phone.getPhoneId(), customerId, phone.getNumber(), phone.isPrimary(),
                phone.isSendTXT());
    }

    private Email forCustomer(Email email, String customerId) {
        Objects.requireNonNull(email, "email must not be null");
        return new Email(email.getEmailId(), customerId, email.getEmailAddress(),
                email.isPrimary());
    }

    private Employer forCustomer(Employer employer, String customerId) {
        Objects.requireNonNull(employer, "employer must not be null");
        return new Employer(employer.getEmployerId(), customerId, employer.getCompanyName(),
                employer.getJobTitle(), employer.getIncome(), employer.getEmploymentType());
    }

    private static boolean sameCustomer(Customer left, Customer right) {
        return Objects.equals(left.getCustomerId(), right.getCustomerId())
                && Objects.equals(left.getTitle(), right.getTitle())
                && Objects.equals(left.getFirstName(), right.getFirstName())
                && Objects.equals(left.getLastName(), right.getLastName())
                && Objects.equals(left.getDateOfBirth(), right.getDateOfBirth())
                && Objects.equals(left.getNationality(), right.getNationality());
    }

    private static boolean sameAddress(Address left, Address right) {
        return Objects.equals(left.getAddressId(), right.getAddressId())
                && Objects.equals(left.getCustomerId(), right.getCustomerId())
                && Objects.equals(left.getStreet(), right.getStreet())
                && Objects.equals(left.getSuburb(), right.getSuburb())
                && Objects.equals(left.getCity(), right.getCity())
                && Objects.equals(left.getPostCode(), right.getPostCode())
                && Objects.equals(left.getCountry(), right.getCountry())
                && left.isMailing() == right.isMailing();
    }

    private static boolean samePhone(Phone left, Phone right) {
        return Objects.equals(left.getPhoneId(), right.getPhoneId())
                && Objects.equals(left.getCustomerId(), right.getCustomerId())
                && Objects.equals(left.getNumber(), right.getNumber())
                && left.isPrimary() == right.isPrimary()
                && left.isSendTXT() == right.isSendTXT();
    }

    private static boolean sameEmail(Email left, Email right) {
        return Objects.equals(left.getEmailId(), right.getEmailId())
                && Objects.equals(left.getCustomerId(), right.getCustomerId())
                && Objects.equals(left.getEmailAddress(), right.getEmailAddress())
                && left.isPrimary() == right.isPrimary();
    }

    private static boolean sameEmployer(Employer left, Employer right) {
        return Objects.equals(left.getEmployerId(), right.getEmployerId())
                && Objects.equals(left.getCustomerId(), right.getCustomerId())
                && Objects.equals(left.getCompanyName(), right.getCompanyName())
                && Objects.equals(left.getJobTitle(), right.getJobTitle())
                && Double.compare(left.getIncome(), right.getIncome()) == 0
                && Objects.equals(left.getEmploymentType(), right.getEmploymentType());
    }
}
