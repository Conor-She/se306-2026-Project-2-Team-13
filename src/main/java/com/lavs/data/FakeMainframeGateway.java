package com.lavs.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private boolean unavailable = false;
    private MainframeException nextFailure = null;
    private int nextLoanNumber = 1;
    private int nextCoBorrowerNumber = 1;

    // Stores an item under its customer, creating that customer's list on first use
    private <T> T store(Map<String, List<T>> storage, String customerId, T item) {
        storage.computeIfAbsent(customerId, id -> new ArrayList<>()).add(item);
        return item;
    }

    // Returns a copy of a customer's items, or an empty list if there are none
    private <T> List<T> read(Map<String, List<T>> storage, String customerId) {
        return List.copyOf(storage.getOrDefault(customerId, new ArrayList<>()));
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
        customers.put(customer.getCustomerId(), customer);
        return customer;
    }

    @Override
    public Customer getCustomer(String customerId) {
        checkAvailability();
        return customers.get(customerId);
    }

    @Override
    public Customer updateCustomer(Customer customer) {
        checkAvailability();
        customers.put(customer.getCustomerId(), customer);
        return customer;
    }

    @Override
    public Address addAddress(Address address) {
        checkAvailability();
        return store(addresses, address.getCustomerId(), address);
    }

    @Override
    public Address updateAddress(Address address) {
        checkAvailability();
        return address;
    }

    @Override
    public void deleteAddress(String addressId) {
        checkAvailability();
    }

    @Override
    public List<Address> getAddresses(String customerId) {
        checkAvailability();
        return read(addresses, customerId);
    }

    @Override
    public Phone addPhone(Phone phone) {
        checkAvailability();
        return store(phones, phone.getCustomerId(), phone);
    }

    @Override
    public Phone updatePhone(Phone phone) {
        checkAvailability();
        return phone;
    }

    @Override
    public void deletePhone(String phoneId) {
        checkAvailability();
    }

    @Override
    public List<Phone> getPhones(String customerId) {
        checkAvailability();
        return read(phones, customerId);
    }

    @Override
    public Email addEmail(Email email) {
        checkAvailability();
        return store(emails, email.getCustomerId(), email);
    }

    @Override
    public Email updateEmail(Email email) {
        checkAvailability();
        return email;
    }

    @Override
    public void deleteEmail(String emailId) {
        checkAvailability();
    }

    @Override
    public List<Email> getEmails(String customerId) {
        checkAvailability();
        return read(emails, customerId);
    }

    @Override
    public Employer addEmployer(Employer employer) {
        checkAvailability();
        return store(employers, employer.getCustomerId(), employer);
    }

    @Override
    public Employer updateEmployer(Employer employer) {
        checkAvailability();
        return employer;
    }

    @Override
    public void deleteEmployer(String employerId) {
        checkAvailability();
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
    public void deleteCoBorrower(String coBorrowerId) {
        checkAvailability();
        if (coBorrowers.remove(coBorrowerId) == null) {
            throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 3,
                    "Co-borrower '" + coBorrowerId + "' not found");
        }
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
    public CustomerDocument addDocument(CustomerDocument document) {
        checkAvailability();
        return document;
    }

    @Override
    public CustomerDocument updateDocument(CustomerDocument document) {
        checkAvailability();
        return document;
    }

    @Override
    public void deleteDocument(String documentId) {
        checkAvailability();
    }

    @Override
    public Guarantor addGuarantor(Guarantor guarantor) {
        checkAvailability();
        return store(guarantors, guarantor.getCustomerId(), guarantor);
    }

    @Override
    public void deleteGuarantor(String guarantorId) {
        checkAvailability();
    }

    @Override
    public List<Guarantor> getGuarantors(String customerId) {
        checkAvailability();
        return read(guarantors, customerId);
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
        return payment;
    }

    @Override
    public Payment updatePayment(Payment payment) {
        checkAvailability();
        return payment;
    }

    @Override
    public void deletePayment(String paymentId) {
        checkAvailability();
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