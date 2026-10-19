package com.lavs.business;

import com.lavs.data.MainframeException;
import com.lavs.data.MainframeGateway;
import com.lavs.model.CoBorrower;
import com.lavs.model.Customer;
import com.lavs.model.Email;
import com.lavs.model.Loan;
import com.lavs.model.Payment;
import com.lavs.model.Phone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Read-only lookups behind the Loans page: finding loans and loading the
// payments and co-borrowers shown on a loan's details.
// The mainframe has no "list every loan" message: loans can only be listed
// per customer, and customer search needs a non-blank query. So loans are
// found by searching customers (by name or ID) and listing each match's loans,
// plus a direct lookup when the query is a loan number.
public class LoanBrowsingService {

    // Each matching customer costs one list call plus one call per loan, so a
    // very broad search is capped to keep the page responsive
    public static final int MAX_CUSTOMERS_PER_SEARCH = 25;

    // One loan as listed on the Loans page, with its primary borrower's name
    public record LoanListing(Loan loan, String customerName) {
        public LoanListing {
            Objects.requireNonNull(loan, "loan must not be null");
            customerName = customerName == null ? "" : customerName;
        }
    }

    // The loans found for a query. Truncated means more customers matched than
    // were searched, so the user should narrow the query.
    public record LoanSearchResult(List<LoanListing> loans, boolean truncated) {
        public LoanSearchResult {
            loans = List.copyOf(loans);
        }
    }

    // A co-borrower with the contact details shown in the co-borrowers table
    public record CoBorrowerDetails(String coBorrowerId, String customerId, String firstName,
            String lastName, String phone, String email) {
    }

    // Everything loaded when the loan details page opens
    public record LoanRecords(List<Payment> payments, List<CoBorrowerDetails> coBorrowers,
            LoanWithSchedule repaymentDetails) {
        public LoanRecords {
            payments = List.copyOf(payments);
            coBorrowers = List.copyOf(coBorrowers);
            Objects.requireNonNull(repaymentDetails, "repaymentDetails must not be null");
        }
    }

    private final MainframeGateway gateway;

    public LoanBrowsingService(MainframeGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    // Loans whose primary borrower matches the query by name or customer ID,
    // and the loan whose number is the query, if there is one
    public LoanSearchResult search(String query) {
        Objects.requireNonNull(query, "query must not be null");
        String trimmed = query.strip();
        if (trimmed.isEmpty()) {
            return new LoanSearchResult(List.of(), false);
        }

        Map<String, LoanListing> found = new LinkedHashMap<>();
        if (!trimmed.contains(" ")) {
            findLoanByNumber(trimmed).ifPresent(listing ->
                    found.put(listing.loan().getLoanId(), listing));
        }

        List<Customer> customers = gateway.searchCustomers(trimmed);
        boolean truncated = customers.size() > MAX_CUSTOMERS_PER_SEARCH;
        for (Customer customer : customers.subList(0,
                Math.min(customers.size(), MAX_CUSTOMERS_PER_SEARCH))) {
            for (Loan loan : gateway.findLoansByCustomer(customer.getCustomerId())) {
                found.putIfAbsent(loan.getLoanId(), new LoanListing(loan, nameOf(customer)));
            }
        }

        List<LoanListing> loans = new ArrayList<>(found.values());
        loans.sort(Comparator.comparing(listing -> listing.loan().getLoanId()));
        return new LoanSearchResult(loans, truncated);
    }

    // The payments and co-borrowers recorded against a loan
    public LoanRecords recordsFor(String loanId) {
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalArgumentException("loanId must not be null or blank");
        }
        LoanWithSchedule repaymentDetails = new LoanService(gateway).getFullDetails(loanId);
        List<Payment> payments = gateway.getPayments(loanId);
        List<CoBorrowerDetails> coBorrowers = new ArrayList<>();
        for (CoBorrower coBorrower : gateway.getCoBorrowers(loanId)) {
            coBorrowers.add(detailsOf(coBorrower));
        }
        return new LoanRecords(payments, coBorrowers, repaymentDetails);
    }

    private java.util.Optional<LoanListing> findLoanByNumber(String loanId) {
        try {
            Loan loan = gateway.getLoan(loanId);
            return java.util.Optional.of(new LoanListing(loan, nameOrBlank(loan.getCustomerId())));
        } catch (MainframeException exception) {
            // "Not a loan number" is expected for most queries; anything else
            // (the mainframe being down) must still reach the caller
            if (exception.getType() == MainframeException.Type.BUSINESS_ERROR) {
                return java.util.Optional.empty();
            }
            throw exception;
        }
    }

    private CoBorrowerDetails detailsOf(CoBorrower coBorrower) {
        String customerId = coBorrower.getCustomerId();
        Customer customer = gateway.getCustomer(customerId);
        String phone = gateway.getPhones(customerId).stream()
                .sorted(Comparator.comparing(Phone::isPrimary).reversed())
                .map(Phone::getNumber).filter(Objects::nonNull).findFirst().orElse("");
        String email = gateway.getEmails(customerId).stream()
                .sorted(Comparator.comparing(Email::isPrimary).reversed())
                .map(Email::getEmailAddress).filter(Objects::nonNull).findFirst().orElse("");
        return new CoBorrowerDetails(coBorrower.getCoBorrowerId(), customerId,
                text(customer.getFirstName()), text(customer.getLastName()), phone, email);
    }

    private String nameOrBlank(String customerId) {
        try {
            return nameOf(gateway.getCustomer(customerId));
        } catch (MainframeException exception) {
            if (exception.getType() == MainframeException.Type.BUSINESS_ERROR) {
                return "";
            }
            throw exception;
        }
    }

    private static String nameOf(Customer customer) {
        return (text(customer.getFirstName()) + " " + text(customer.getLastName())).strip();
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
