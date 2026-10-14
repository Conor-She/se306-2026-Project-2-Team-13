package com.lavs.data;

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
import java.util.List;

// The Gateway/Adapter interface the Business Logic Layer depends on.
public interface MainframeGateway {

    // Log In / Log Out
    void login(String username, String password);
    void logout();

    // Search Customers
    List<Customer> searchCustomers(String query);

    // Create a Customer
    Customer createCustomer(Customer customer);

    // Manage Customers
    Customer getCustomer(String customerId);
    Customer updateCustomer(Customer customer);
    Address addAddress(Address address);
    Address updateAddress(Address address);
    void deleteAddress(String addressId);
    Phone addPhone(Phone phone);
    Phone updatePhone(Phone phone);
    void deletePhone(String phoneId);
    Email addEmail(Email email);
    Email updateEmail(Email email);
    void deleteEmail(String emailId);
    Employer addEmployer(Employer employer);
    Employer updateEmployer(Employer employer);
    void deleteEmployer(String employerId);

    // Read a customer's child records (for checking a loan's prerequisites)
    List<Address> getAddresses(String customerId);
    List<Phone> getPhones(String customerId);
    List<Email> getEmails(String customerId);
    List<Employer> getEmployers(String customerId);
    List<Guarantor> getGuarantors(String customerId);

    // Manage Notes, Documents & Guarantors
    CustomerNote addNote(CustomerNote note);
    CustomerNote updateNote(CustomerNote note);
    void deleteNote(String noteId);
    List<CustomerDocument> getDocuments(String customerId);
    CustomerDocument addDocument(CustomerDocument document);
    CustomerDocument updateDocument(CustomerDocument document);
    void deleteDocument(String customerId, String documentId);
    Guarantor addGuarantor(Guarantor guarantor);
    Guarantor updateGuarantor(Guarantor guarantor);
    void deleteGuarantor(String guarantorId);

    // Create a Loan
    Loan createLoan(Loan loan);

    // Manage Loans
    Loan getLoan(String loanId);
    Loan updateLoan(Loan loan);
    Loan updateLoanStatus(String loanId, Loan.Status newStatus);

    // Manage Co-borrowers
    CoBorrower addCoBorrower(CoBorrower coBorrower);
    void deleteCoBorrower(String coBorrowerId);
    int getCoBorrowerCount(String loanId);
    List<CoBorrower> getCoBorrowers(String loanId);

    // Generate Repayment Schedule & Summary
    // (LAVS calculates the schedule itself - this just persists it)
    void saveRepaymentSchedule(String loanId, List<Repayment> schedule);

    // Find Loan
    List<Loan> findLoansByCustomer(String customerId);
    List<Repayment> getRepaymentSchedule(String loanId);

    // Manage Payments
    Payment addPayment(Payment payment);
    Payment updatePayment(Payment payment);
    void deletePayment(String paymentId);

    // Retrieve Category Values
    List<String> getCategoryValues(String categoryType);
}
