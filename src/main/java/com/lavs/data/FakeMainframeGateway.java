package com.lavs.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.lavs.model.Address;
import com.lavs.model.CoBorrower;
import com.lavs.model.Customer;
import com.lavs.model.CustomerDocument;
import com.lavs.model.CustomerNote;
import com.lavs.model.Email;
import com.lavs.model.Employer;
import com.lavs.model.Guarantor;
import com.lavs.model.Loan;
import com.lavs.model.Payment;
import com.lavs.model.Phone;
import com.lavs.model.Repayment;

// In-memory test double for MainframeGateway. Can be configured to
// simulate success, a business error, or the mainframe being unreachable,
// via simulateUnavailable() and failNextCallWith(...).
public class FakeMainframeGateway implements MainframeGateway {

    private final Map<String, Customer> customers = new HashMap<>();
    private final Map<String, Loan> loans = new HashMap<>();
    private final Map<String, List<Repayment>> repaymentSchedules = new HashMap<>();
    private final Map<String, List<String>> categoryValues = new HashMap<>();
    private final Map<String, CoBorrower> coBorrowers = new HashMap<>();
    private final Map<String, List<Address>> addresses = new HashMap<>();
    private final Map<String, List<Phone>> phones = new HashMap<>();
    private final Map<String, List<Email>> emails = new HashMap<>();
    private final Map<String, List<Employer>> employers = new HashMap<>();
    private final Map<String, List<Guarantor>> guarantors = new HashMap<>();
    private final Map<String, Payment> payments = new HashMap<>();
    private final Map<String, List<CustomerDocument>> documents = new HashMap<>();

    private boolean unavailable = false;
    private MainframeException nextFailure = null;
    private int nextCustomerNumber = 1;
    private long nextAddressNumber = 1;
    private long nextPhoneNumber = 1;
    private long nextEmailNumber = 1;
    private long nextEmployerNumber = 1;
    private int nextLoanNumber = 1;
    private int nextCoBorrowerNumber = 1;
    private int nextGuarantorNumber = 1;
    private int nextPaymentNumber = 1;
    private int nextDocumentNumber = 1;

    // Stores an item under its customer, creating that customer's list on first use
    private <T> T store(Map<String, List<T>> storage, String customerId, T item) {
        storage.computeIfAbsent(customerId, id -> new ArrayList<>()).add(item);
        return item;
    }

    // Returns a copy of a customer's items, or an empty list if there are none
    private <T> List<T> read(Map<String, List<T>> storage, String customerId) {
        return List.copyOf(storage.getOrDefault(customerId, new ArrayList<>()));
    }

    private <T> void replace(Map<String, List<T>> storage, String customerId, Long itemId,
            Function<T, Long> id, T replacement, String itemName) {
        List<T> items = storage.get(customerId);
        if (items == null) {
            throw notFound(itemName, itemId);
        }
        for (int index = 0; index < items.size(); index++) {
            if (Objects.equals(id.apply(items.get(index)), itemId)) {
                items.set(index, replacement);
                return;
            }
        }
        throw notFound(itemName, itemId);
    }

    private <T> void delete(Map<String, List<T>> storage, String customerId, Long itemId,
            Function<T, Long> id, String itemName) {
        List<T> items = storage.get(customerId);
        if (items != null && items.removeIf(item -> Objects.equals(id.apply(item), itemId))) {
            return;
        }
        throw notFound(itemName, itemId);
    }

    private MainframeException notFound(String itemName, Object itemId) {
        return new MainframeException(MainframeException.Type.BUSINESS_ERROR, 3,
                itemName + " '" + itemId + "' not found");
    }

    // Configures this fake to act as though the mainframe cannot be
    // reached, for every call, until reset
    public void simulateUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    // Configures the *next* call to throw this business error, then
    // reverts to normal behaviour. Lets a test simulate "the mainframe
    // rejected this one request" without affecting every other call.
    public void failNextCallWith(int errorCode, String message) {
        this.nextFailure = new MainframeException(MainframeException.Type.BUSINESS_ERROR,
                errorCode, message);
    }

    // Lets a test pre-load category values, since this fake has no real
    // mainframe-side constants to fall back on
    public void seedCategoryValues(String categoryType, List<String> values) {
        categoryValues.put(categoryType, values);
    }

    private void checkAvailability() {
        if (unavailable) {
            throw new MainframeException(MainframeException.Type.UNAVAILABLE, 10,
                    "The remote host has responded that it is currently unavailable");
        }
        if (nextFailure != null) {
            MainframeException failure = nextFailure;
            nextFailure = null;
            throw failure;
        }
    }

    @Override
    public void login(String username, String password) {
        checkAvailability();
    }

    @Override
    public void logout() {
        checkAvailability();
    }

    @Override
    public List<Customer> searchCustomers(String query) {
        checkAvailability();
        List<Customer> results = new ArrayList<>();
        for (Customer customer : customers.values()) {
            if (customer.getFirstName() != null && customer.getFirstName().contains(query)
                    || customer.getLastName() != null && customer.getLastName().contains(query)) {
                results.add(customer);
            }
        }
        return results;
    }

    @Override
    public Customer createCustomer(Customer customer) {
        checkAvailability();
        String customerId = customer.getCustomerId() != null
                ? customer.getCustomerId()
                : "FAKE-CUSTOMER-" + nextCustomerNumber++;
        Customer stored = new Customer(customerId, customer.getTitle(), customer.getFirstName(),
                customer.getLastName(), customer.getDateOfBirth(), customer.getNationality());
        customers.put(customerId, stored);
        return stored;
    }

    @Override
    public Customer getCustomer(String customerId) {
        checkAvailability();
        return customers.get(customerId);
    }

    @Override
    public Customer updateCustomer(Customer customer) {
        checkAvailability();
        if (customer.getCustomerId() == null
                || !customers.containsKey(customer.getCustomerId())) {
            throw notFound("Customer", customer.getCustomerId());
        }
        customers.put(customer.getCustomerId(), customer);
        return customer;
    }

    @Override
    public Address addAddress(Address address) {
        checkAvailability();
        Long addressId = address.getAddressId() != null
                ? address.getAddressId()
                : nextAddressNumber++;
        Address stored = new Address(addressId, address.getCustomerId(), address.getStreet(),
                address.getSuburb(), address.getCity(), address.getPostCode(),
                address.getCountry(), address.isMailing());
        return store(addresses, stored.getCustomerId(), stored);
    }

    @Override
    public Address updateAddress(Address address) {
        checkAvailability();
        replace(addresses, address.getCustomerId(), address.getAddressId(),
                Address::getAddressId, address, "Address");
        return address;
    }

    @Override
    public void deleteAddress(String customerId, Long addressId) {
        checkAvailability();
        delete(addresses, customerId, addressId, Address::getAddressId, "Address");
    }

    @Override
    public List<Address> getAddresses(String customerId) {
        checkAvailability();
        return read(addresses, customerId);
    }

    @Override
    public Phone addPhone(Phone phone) {
        checkAvailability();
        Long phoneId = phone.getPhoneId() != null ? phone.getPhoneId() : nextPhoneNumber++;
        Phone stored = new Phone(phoneId, phone.getCustomerId(), phone.getNumber(),
                phone.isPrimary(), phone.isSendTXT());
        return store(phones, stored.getCustomerId(), stored);
    }

    @Override
    public Phone updatePhone(Phone phone) {
        checkAvailability();
        replace(phones, phone.getCustomerId(), phone.getPhoneId(), Phone::getPhoneId,
                phone, "Phone");
        return phone;
    }

    @Override
    public void deletePhone(String customerId, Long phoneId) {
        checkAvailability();
        delete(phones, customerId, phoneId, Phone::getPhoneId, "Phone");
    }

    @Override
    public List<Phone> getPhones(String customerId) {
        checkAvailability();
        return read(phones, customerId);
    }

    @Override
    public Email addEmail(Email email) {
        checkAvailability();
        Long emailId = email.getEmailId() != null ? email.getEmailId() : nextEmailNumber++;
        Email stored = new Email(emailId, email.getCustomerId(), email.getEmailAddress(),
                email.isPrimary());
        return store(emails, stored.getCustomerId(), stored);
    }

    @Override
    public Email updateEmail(Email email) {
        checkAvailability();
        replace(emails, email.getCustomerId(), email.getEmailId(), Email::getEmailId,
                email, "Email");
        return email;
    }

    @Override
    public void deleteEmail(String customerId, Long emailId) {
        checkAvailability();
        delete(emails, customerId, emailId, Email::getEmailId, "Email");
    }

    @Override
    public List<Email> getEmails(String customerId) {
        checkAvailability();
        return read(emails, customerId);
    }

    @Override
    public Employer addEmployer(Employer employer) {
        checkAvailability();
        Long employerId = employer.getEmployerId() != null
                ? employer.getEmployerId()
                : nextEmployerNumber++;
        Employer stored = new Employer(employerId, employer.getCustomerId(),
                employer.getCompanyName(), employer.getJobTitle(), employer.getIncome(),
                employer.getEmploymentType());
        return store(employers, stored.getCustomerId(), stored);
    }

    @Override
    public Employer updateEmployer(Employer employer) {
        checkAvailability();
        replace(employers, employer.getCustomerId(), employer.getEmployerId(),
                Employer::getEmployerId, employer, "Employer");
        return employer;
    }

    @Override
    public void deleteEmployer(String customerId, Long employerId) {
        checkAvailability();
        delete(employers, customerId, employerId, Employer::getEmployerId, "Employer");
    }

    @Override
    public List<Employer> getEmployers(String customerId) {
        checkAvailability();
        return read(employers, customerId);
    }

    @Override
    public CoBorrower addCoBorrower(CoBorrower coBorrower) {
        checkAvailability();
        // The real mainframe assigns the ID, so the fake does too
        String coBorrowerId = coBorrower.getCoBorrowerId() != null
                ? coBorrower.getCoBorrowerId()
                : "FAKE-COBORROWER-" + nextCoBorrowerNumber++;
        CoBorrower stored =
                new CoBorrower(coBorrowerId, coBorrower.getLoanId(), coBorrower.getCustomerId());
        coBorrowers.put(coBorrowerId, stored);
        return stored;
    }

    @Override
    public void deleteCoBorrower(String loanId, String coBorrowerId) {
        checkAvailability();
        CoBorrower existing = coBorrowers.get(coBorrowerId);
        if (existing == null || !existing.getLoanId().equals(loanId)) {
            throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 3,
                    "Co-borrower '" + coBorrowerId + "' not found on loan '" + loanId + "'");
        }
        coBorrowers.remove(coBorrowerId);
    }

    @Override
    public List<CoBorrower> getCoBorrowers(String loanId) {
        checkAvailability();
        return coBorrowers.values().stream()
                .filter(coBorrower -> loanId.equals(coBorrower.getLoanId()))
                .toList();
    }

    @Override
    public int getCoBorrowerCount(String loanId) {
        checkAvailability();
        return (int) coBorrowers.values().stream()
                .filter(coBorrower -> loanId.equals(coBorrower.getLoanId()))
                .count();
    }

    @Override
    public CustomerNote addNote(CustomerNote note) {
        checkAvailability();
        return note;
    }

    @Override
    public CustomerNote updateNote(CustomerNote note) {
        checkAvailability();
        return note;
    }

    @Override
    public void deleteNote(String noteId) {
        checkAvailability();
    }

    @Override
    public List<CustomerDocument> getDocuments(String customerId) {
        checkAvailability();
        return read(documents, customerId);
    }

    @Override
    public CustomerDocument addDocument(CustomerDocument document) {
        checkAvailability();
        String documentId = document.getDocumentId() == null
                ? "FAKE-DOCUMENT-" + nextDocumentNumber++ : document.getDocumentId();
        CustomerDocument stored = new CustomerDocument(documentId, document.getCustomerId(),
                document.getFileName(), document.getDocumentType(), document.getUploadDate());
        return store(documents, stored.getCustomerId(), stored);
    }

    @Override
    public CustomerDocument updateDocument(CustomerDocument document) {
        checkAvailability();
        List<CustomerDocument> customerDocuments = documents.get(document.getCustomerId());
        if (customerDocuments == null || document.getDocumentId() == null) {
            throw documentNotFound(document.getDocumentId());
        }
        for (int index = 0; index < customerDocuments.size(); index++) {
            if (document.getDocumentId().equals(customerDocuments.get(index).getDocumentId())) {
                customerDocuments.set(index, document);
                return document;
            }
        }
        throw documentNotFound(document.getDocumentId());
    }

    @Override
    public void deleteDocument(String customerId, String documentId) {
        checkAvailability();
        List<CustomerDocument> customerDocuments = documents.get(customerId);
        if (customerDocuments == null || !customerDocuments.removeIf(
                document -> document.getDocumentId().equals(documentId))) {
            throw documentNotFound(documentId);
        }
    }

    @Override
    public Guarantor addGuarantor(Guarantor guarantor) {
        checkAvailability();
        String guarantorId = guarantor.getGuarantorId() != null
                ? guarantor.getGuarantorId()
                : "FAKE-GUARANTOR-" + nextGuarantorNumber++;
        Guarantor stored = new Guarantor(
                guarantorId,
                guarantor.getCustomerId(),
                guarantor.getGuarantorCustomerId(),
                guarantor.getRelationshipType());
        return store(guarantors, stored.getCustomerId(), stored);
    }

    @Override
    public Guarantor updateGuarantor(Guarantor guarantor) {
        checkAvailability();

        String existingCustomerId = null;
        Guarantor existing = null;
        for (Map.Entry<String, List<Guarantor>> entry : guarantors.entrySet()) {
            for (Guarantor stored : entry.getValue()) {
                if (stored.getGuarantorId().equals(guarantor.getGuarantorId())) {
                    existingCustomerId = entry.getKey();
                    existing = stored;
                    break;
                }
            }
            if (existing != null) {
                break;
            }
        }

        if (existing == null) {
            throw guarantorNotFound(guarantor.getGuarantorId());
        }

        guarantors.get(existingCustomerId).remove(existing);
        return store(guarantors, guarantor.getCustomerId(), guarantor);
    }

    @Override
    public void deleteGuarantor(String guarantorId) {
        checkAvailability();
        for (List<Guarantor> stored : guarantors.values()) {
            boolean removed = stored.removeIf(
                    guarantor -> guarantor.getGuarantorId().equals(guarantorId));
            if (removed) {
                return;
            }
        }
        throw guarantorNotFound(guarantorId);
    }

    @Override
    public List<Guarantor> getGuarantors(String customerId) {
        checkAvailability();
        return read(guarantors, customerId);
    }

    private MainframeException guarantorNotFound(String guarantorId) {
        return new MainframeException(
                MainframeException.Type.BUSINESS_ERROR,
                3,
                "Guarantor '" + guarantorId + "' not found");
    }

    private MainframeException documentNotFound(String documentId) {
        return new MainframeException(
                MainframeException.Type.BUSINESS_ERROR,
                3,
                "Document '" + documentId + "' not found");
    }

    @Override
    public Loan createLoan(Loan loan) {
        checkAvailability();
        // The real mainframe assigns the loan number and starts every loan as
        // New, so the fake does too
        String loanId = loan.getLoanId() != null
                ? loan.getLoanId()
                : "FAKE-LOAN-" + nextLoanNumber++;
        Loan created = copyWith(loan, loanId, Loan.Status.NEW);
        loans.put(loanId, created);
        return created;
    }

    @Override
    public Loan updateLoan(Loan loan) {
        checkAvailability();
        loans.put(loan.getLoanId(), loan);
        return loan;
    }

    @Override
    public Loan updateLoanStatus(String loanId, Loan.Status newStatus) {
        checkAvailability();
        Loan existing = loans.get(loanId);
        if (existing == null) {
            throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 3,
                    "Loan '" + loanId + "' not found");
        }
        Loan updated = copyWith(existing, loanId, newStatus);
        loans.put(loanId, updated);
        return updated;
    }

    @Override
    public Loan getLoan(String loanId) {
        checkAvailability();
        Loan loan = loans.get(loanId);
        if (loan == null) {
            throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 3,
                    "Loan '" + loanId + "' not found");
        }
        return loan;
    }

    @Override
    public void saveRepaymentSchedule(String loanId, List<Repayment> schedule) {
        checkAvailability();
        repaymentSchedules.put(loanId, schedule);
    }

    @Override
    public List<Loan> findLoansByCustomer(String customerId) {
        checkAvailability();
        List<Loan> results = new ArrayList<>();
        for (Loan loan : loans.values()) {
            if (loan.getCustomerId().equals(customerId)) {
                results.add(loan);
            }
        }
        return results;
    }

    @Override
    public List<Repayment> getRepaymentSchedule(String loanId) {
        checkAvailability();
        return repaymentSchedules.getOrDefault(loanId, new ArrayList<>());
    }

    @Override
    public Payment addPayment(Payment payment) {
        checkAvailability();
        // The real mainframe assigns the ID, so the fake does too
        String paymentId = payment.getPaymentId() != null
                ? payment.getPaymentId()
                : "FAKE-PAYMENT-" + nextPaymentNumber++;
        Payment stored = withPaymentId(payment, paymentId);
        payments.put(paymentId, stored);
        return stored;
    }

    @Override
    public Payment updatePayment(Payment payment) {
        checkAvailability();
        if (!payments.containsKey(payment.getPaymentId())) {
            throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 3,
                    "Payment '" + payment.getPaymentId() + "' not found");
        }
        payments.put(payment.getPaymentId(), payment);
        return payment;
    }

    @Override
    public void deletePayment(String loanId, String paymentId) {
        checkAvailability();
        Payment existing = payments.get(paymentId);
        if (existing == null || !existing.getLoanId().equals(loanId)) {
            throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 3,
                    "Payment '" + paymentId + "' not found on loan '" + loanId + "'");
        }
        payments.remove(paymentId);
    }

    @Override
    public List<Payment> getPayments(String loanId) {
        checkAvailability();
        return payments.values().stream()
                .filter(payment -> loanId.equals(payment.getLoanId()))
                .toList();
    }

    // Payment is immutable, so "changing" the ID means building a copy
    private Payment withPaymentId(Payment payment, String paymentId) {
        return new Payment(paymentId, payment.getLoanId(), payment.getRepaymentId(),
                payment.getPaymentDate(), payment.getAmountPaid());
    }

    @Override
    public List<String> getCategoryValues(String categoryType) {
        checkAvailability();
        return categoryValues.getOrDefault(categoryType, new ArrayList<>());
    }

    // Loan is immutable, so "changing" a field means building a new Loan
    private Loan copyWith(Loan loan, String loanId, Loan.Status status) {
        return new Loan(loanId, loan.getCustomerId(), status, loan.getPrincipal(),
                loan.getInterestRate(), loan.getType(), loan.isFloating(), loan.getStartDate(),
                loan.getRenewDate(), loan.getPaymentAmount(), loan.getPaymentFrequency(),
                loan.getRateFrequency(), loan.getTermMonths());
    }
}
