package com.lavs.data;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

import mainframe.client.Connection;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.StatusMessage;
import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.AddDocumentMetadata;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.DeleteDocument;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customer.UpdateDocument;
import mainframe.client.messages.customer.Update;
import mainframe.client.messages.loan.UpdateStatus;

// Real MainframeGateway implementation, wrapping a Connection (e.g.
// HttpConnection). Translates between domain objects and the mainframe's
// Message classes.
public class MainframeGatewayImpl implements MainframeGateway {

    private static final long CATEGORY_PAGE_SIZE = 100;

    private final Connection connection;
    private final String organisationName;

    public MainframeGatewayImpl(Connection connection, String organisationName) {
        this.connection = connection;
        this.organisationName = organisationName;
    }

    @Override
    public void login(String username, String password) {
        try {
            throwIfFailed(connection.Login(username, password));
        } catch (IOException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void logout() {
        connection.Logout();
    }

    @Override
    public Customer createCustomer(Customer customer) {
        long organisationId = findOrganisationId(organisationName);

        Create message = new Create(
                customer.getDateOfBirth() == null ? null : customer.getDateOfBirth().toString(),
                customer.getLastName(),
                customer.getFirstName(),
                customer.getNationality(),
                organisationId,
                customer.getTitle());

        sendOrThrow(message);

        return new Customer(message.getNumber(), message.getTitle(), message.getFirstName(),
                message.getFamilyName(),
                customer.getDateOfBirth(), // mainframe returns a String, Customer wants LocalDate -
                                            // needs a parser once dateOfBirth format is confirmed
                message.getNationality());
    }

    @Override
    public Address addAddress(Address address) {
        AddAddress message = new AddAddress(
                address.getCity(), address.getCountry(), address.getCustomerId(),
                address.isMailing(),
                false, // isPrimary - our Address domain class has no isPrimary field, per our
                       // earlier decision to drop it
                address.getStreet(), null, null, address.getPostCode(), address.getSuburb(),
                AddAddress.TYPE_RESIDENTIAL);

        sendOrThrow(message);

        return new Address(message.getId(), message.getCustomer(), message.getLine1(),
                message.getSuburb(), message.getCity(), message.getPostCode(),
                message.getCountry(), message.getIsMailing());
    }

        
    @Override
    public List<Customer> searchCustomers(String query) {
        Search message = new Search(1, query, 50);
        sendOrThrow(message);

        List<Customer> results = new ArrayList<>();
        for (Search.Item item : message.getItems()) {
            results.add(new Customer(item.getNumber(), item.getTitle(), item.getFirstName(),
                    item.getFamilyName(), null, // dateOfBirth parsing TBD - see createCustomer's TODO
                    null)); // nationality not returned by Search - only available via Retrieve
        }
        return results;
    }

    @Override
    public Customer getCustomer(String customerId) {
        Retrieve message = new Retrieve(customerId);
        sendOrThrow(message);

        return new Customer(message.getNumber(), message.getTitle(), message.getFirstName(),
                message.getFamilyName(), null, // dateOfBirth parsing TBD
                message.getNationality());
    }

    @Override
    public Customer updateCustomer(Customer customer) {
        Update message = new Update(
                customer.getDateOfBirth() == null ? null : customer.getDateOfBirth().toString(),
                customer.getLastName(), customer.getFirstName(), customer.getNationality(),
                customer.getCustomerId(), customer.getTitle());

        sendOrThrow(message);

        return new Customer(message.getNumber(), message.getTitle(), message.getFirstName(),
                message.getFamilyName(), customer.getDateOfBirth(), message.getNationality());
    }

    @Override
    public Address updateAddress(Address address) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.UpdateAddress");
    }

    @Override
    public void deleteAddress(String addressId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.DeleteAddress");
    }

    @Override
    public Phone addPhone(Phone phone) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.AddPhoneNumber");
    }

    @Override
    public Phone updatePhone(Phone phone) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.UpdatePhoneNumber");
    }

    @Override
    public void deletePhone(String phoneId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.DeletePhoneNumber");
    }

    @Override
    public Email addEmail(Email email) {
        throw new UnsupportedOperationException("Not yet implemented - needs customer.AddEmail");
    }

    @Override
    public Email updateEmail(Email email) {
        throw new UnsupportedOperationException("Not yet implemented - needs customer.UpdateEmail");
    }

    @Override
    public void deleteEmail(String emailId) {
        throw new UnsupportedOperationException("Not yet implemented - needs customer.DeleteEmail");
    }

    @Override
    public List<Address> getAddresses(String customerId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customerAddress retrieval");
    }

    @Override
    public List<Phone> getPhones(String customerId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customerPhoneNumber retrieval");
    }

    @Override
    public List<Email> getEmails(String customerId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customerEmail retrieval");
    }

    @Override
    public Employer addEmployer(Employer employer) {
        throw new UnsupportedOperationException("Not yet implemented - needs customer.AddEmployer");
    }

    @Override
    public Employer updateEmployer(Employer employer) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.UpdateEmployer");
    }

    @Override
    public void deleteEmployer(String employerId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.DeleteEmployer");
    }

    @Override
    public CustomerNote addNote(CustomerNote note) {
        throw new UnsupportedOperationException("Not yet implemented - needs customer.AddNote");
    }

    @Override
    public CustomerNote updateNote(CustomerNote note) {
        throw new UnsupportedOperationException("Not yet implemented - needs customer.UpdateNote");
    }

    @Override
    public void deleteNote(String noteId) {
        throw new UnsupportedOperationException("Not yet implemented - needs customer.DeleteNote");
    }

    @Override
    public List<CustomerDocument> getDocuments(String customerId) {
        List<CustomerDocument> documents = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.customerDocument.List message =
                    new mainframe.client.messages.customerDocument.List(customerId, page, 100);
            sendOrThrow(message);
            mainframe.client.messages.customerDocument.List.Item[] items = message.getItems();
            for (mainframe.client.messages.customerDocument.List.Item item : items) {
                documents.add(new CustomerDocument(Long.toString(item.getId()), customerId,
                        item.getFileName(), item.getType(), LocalDate.parse(item.getUploadDate())));
            }
            if (items.length == 0 || page * 100 >= message.getTotal()) {
                return List.copyOf(documents);
            }
            page++;
        }
    }

    @Override
    public CustomerDocument addDocument(CustomerDocument document) {
        AddDocumentMetadata message = new AddDocumentMetadata(document.getCustomerId(),
                document.getFileName(), document.getDocumentType(),
                document.getUploadDate().toString());
        sendOrThrow(message);
        if (message.getId() == null) {
            throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 0,
                    "The mainframe did not return an ID for the new document");
        }
        return new CustomerDocument(Long.toString(message.getId()), document.getCustomerId(),
                document.getFileName(), document.getDocumentType(), document.getUploadDate());
    }

    @Override
    public CustomerDocument updateDocument(CustomerDocument document) {
        long id = parseDocumentId(document.getDocumentId());
        UpdateDocument message = new UpdateDocument(document.getCustomerId(), id,
                document.getFileName(), document.getDocumentType(),
                document.getUploadDate().toString());
        sendOrThrow(message);
        return document;
    }

    @Override
    public void deleteDocument(String customerId, String documentId) {
        DeleteDocument message = new DeleteDocument(customerId, parseDocumentId(documentId));
        sendOrThrow(message);
    }

    private long parseDocumentId(String documentId) {
        try {
            return Long.parseLong(documentId);
        } catch (NumberFormatException invalidId) {
            throw new IllegalArgumentException("Document ID must be a numeric mainframe ID",
                    invalidId);
        }
    }

    @Override
    public Guarantor addGuarantor(Guarantor guarantor) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.AddRelationship");
    }

    @Override
    public Guarantor updateGuarantor(Guarantor guarantor) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.UpdateRelationship");
    }

    @Override
    public void deleteGuarantor(String guarantorId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.DeleteRelationship");
    }

    @Override
    public List<Employer> getEmployers(String customerId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customerEmployer retrieval");
    }

    @Override
    public List<Guarantor> getGuarantors(String customerId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customerRelationship retrieval");
    }

    @Override
    public Loan createLoan(Loan loan) {
        mainframe.client.messages.loan.Create message = new mainframe.client.messages.loan.Create(
                loan.getCustomerId(), loan.getPaymentAmount(),
                toMainframePaymentFrequency(loan.getPaymentFrequency()), loan.getPrincipal(),
                loan.getInterestRate(), toMainframeRateFrequency(loan.getRateFrequency()),
                toMainframeRateType(loan), loan.getRenewDate().toString(),
                loan.getStartDate().toString());

        sendOrThrow(message);

        // The mainframe doesn't store Type or TermMonths, so carry them over
        // from the loan the MMM just entered
        return toLoan(message.getLoanNumber(), message.getCustomerNumber(), message.getStatus(),
                message.getPrincipal(), message.getRateAmount(), message.getRateType(),
                message.getStartDate(), message.getRenewalDate(), message.getPaymentAmount(),
                message.getPaymentFrequency(), message.getRateFrequency(), loan.getType(),
                loan.getTermMonths());
    }

    @Override
    public Loan getLoan(String loanId) {
        mainframe.client.messages.loan.Retrieve message =
                new mainframe.client.messages.loan.Retrieve(loanId);
        sendOrThrow(message);

        // Type (except Interest-Only) and TermMonths can't be recovered from
        // the mainframe, so they come back as null
        return toLoan(message.getLoanNumber(), message.getCustomerNumber(), message.getStatus(),
                message.getPrincipal(), message.getRateAmount(), message.getRateType(),
                message.getStartDate(), message.getRenewalDate(), message.getPaymentAmount(),
                message.getPaymentFrequency(), message.getRateFrequency(), null, null);
    }

    @Override
    public Loan updateLoan(Loan loan) {
        mainframe.client.messages.loan.Update message = new mainframe.client.messages.loan.Update(
                loan.getCustomerId(), loan.getLoanId(), loan.getPaymentAmount(),
                toMainframePaymentFrequency(loan.getPaymentFrequency()), loan.getPrincipal(),
                loan.getInterestRate(), toMainframeRateFrequency(loan.getRateFrequency()),
                toMainframeRateType(loan), loan.getRenewDate().toString(),
                loan.getStartDate().toString());

        sendOrThrow(message);

        return toLoan(message.getLoanNumber(), message.getCustomerNumber(), message.getStatus(),
                message.getPrincipal(), message.getRateAmount(), message.getRateType(),
                message.getStartDate(), message.getRenewalDate(), message.getPaymentAmount(),
                message.getPaymentFrequency(), message.getRateFrequency(), loan.getType(),
                loan.getTermMonths());
    }

    @Override
    public Loan updateLoanStatus(String loanId, Loan.Status newStatus) {
        UpdateStatus message = new UpdateStatus(loanId, toMainframeStatus(newStatus));
        sendOrThrow(message);

        // UpdateStatus only returns loanNumber and status, not the full loan,
        // so we re-fetch to return a complete Loan matching the other methods
        return getLoan(message.getLoanNumber());
    }

    @Override
    public CoBorrower addCoBorrower(CoBorrower coBorrower) {
        throw new UnsupportedOperationException("Not yet implemented - needs loanCoborrower.Add");
    }

    @Override
    public void deleteCoBorrower(String coBorrowerId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs loanCoborrower.Delete");
    }

    @Override
    public List<CoBorrower> getCoBorrowers(String loanId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs loanCoborrower retrieval");
    }

    @Override
    public int getCoBorrowerCount(String loanId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs loanCoborrower retrieval");
    }

    @Override
    public void saveRepaymentSchedule(String loanId, List<Repayment> schedule) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs loanRepaymentSchedule messages");
    }

    @Override
    public List<Loan> findLoansByCustomer(String customerId) {
        throw new UnsupportedOperationException("Not yet implemented - needs loan.Search");
    }

    @Override
    public List<Repayment> getRepaymentSchedule(String loanId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs loanRepaymentSchedule retrieval");
    }

    @Override
    public Payment addPayment(Payment payment) {
        throw new UnsupportedOperationException("Not yet implemented - needs loanPayment.Add");
    }

    @Override
    public Payment updatePayment(Payment payment) {
        throw new UnsupportedOperationException("Not yet implemented - needs loanPayment.Update");
    }

    @Override
    public void deletePayment(String paymentId) {
        throw new UnsupportedOperationException("Not yet implemented - needs loanPayment.Delete");
    }

    @Override
    public List<String> getCategoryValues(String categoryType) {
        Long categoryId = findCategoryId(categoryType);
        if (categoryId == null) {
            return List.of();
        }

        List<mainframe.client.messages.constantItem.List.Item> items = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.constantItem.List message =
                    new mainframe.client.messages.constantItem.List(
                            categoryId, page, CATEGORY_PAGE_SIZE);
            sendOrThrow(message);
            List<mainframe.client.messages.constantItem.List.Item> pageItems =
                    List.of(message.getItems());
            items.addAll(pageItems);

            if (pageItems.isEmpty() || page * CATEGORY_PAGE_SIZE >= message.getTotal()) {
                break;
            }
            page++;
        }

        items.sort(Comparator.comparingLong(item -> item.getOrder()));
        return items.stream().map(item -> item.getName()).toList();
    }

    private Long findCategoryId(String categoryType) {
        long page = 1;
        while (true) {
            mainframe.client.messages.constantCategory.List message =
                    new mainframe.client.messages.constantCategory.List(
                            page, CATEGORY_PAGE_SIZE);
            sendOrThrow(message);
            mainframe.client.messages.constantCategory.List.Item[] items = message.getItems();

            for (mainframe.client.messages.constantCategory.List.Item item : items) {
                if (categoryType.equalsIgnoreCase(item.getName())) {
                    return item.getId();
                }
            }

            if (items.length == 0 || page * CATEGORY_PAGE_SIZE >= message.getTotal()) {
                return null;
            }
            page++;
        }
    }

    private void sendOrThrow(Message message) {
            try {
                throwIfFailed(message.send(connection));
            } catch (IOException e) {
                throw unavailable(e);
            }
    }

    private void throwIfFailed(Status status) {
            if (!status.getWasSuccessful()) {
                boolean isUnavailable = status.getErrorCode()
                        == StatusMessage.NETWORK_FAILURE_UNAVAILABLE.getCode();
                MainframeException.Type type = isUnavailable
                        ? MainframeException.Type.UNAVAILABLE
                        : MainframeException.Type.BUSINESS_ERROR;
                throw new MainframeException(type, status.getErrorCode(), status.getErrorMessage());
            }
    }

    private MainframeException unavailable(IOException cause) {
            return new MainframeException(MainframeException.Type.UNAVAILABLE,
                    StatusMessage.NETWORK_FAILURE_UNAVAILABLE.getCode(), cause.getMessage());
    }

    private long findOrganisationId(String organisationName) {
        mainframe.client.messages.organisation.List message =
                new mainframe.client.messages.organisation.List(1, 100);
        sendOrThrow(message);

        for (mainframe.client.messages.organisation.List.Item item : message.getItems()) {
            if (organisationName.equals(item.getName())) {
                return item.getId();
            }
        }

        throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 0,
                "Organisation '" + organisationName + "' not found on the mainframe");
    }


    // Interest-Only is the one loan type the mainframe can represent. Table
    // Loan vs Reducing Balance is not stored by the mainframe (team decision),
    // so both are sent as Fixed or Floating
    private String toMainframeRateType(Loan loan) {
        if (loan.getType() == Loan.Type.INTEREST_ONLY) {
            return mainframe.client.messages.loan.Create.RATETYPE_INTERESTONLY;
        }
        return loan.isFloating()
                ? mainframe.client.messages.loan.Create.RATETYPE_FLOATING
                : mainframe.client.messages.loan.Create.RATETYPE_FIXED;
    }

    private String toMainframeRateFrequency(Loan.RateFrequency frequency) {
        return switch (frequency) {
            case DAILY -> mainframe.client.messages.loan.Create.RATEFREQUENCY_DAILY;
            case WEEKLY -> mainframe.client.messages.loan.Create.RATEFREQUENCY_WEEKLY;
            case FORTNIGHTLY -> mainframe.client.messages.loan.Create.RATEFREQUENCY_FORTNIGHTLY;
            case MONTHLY -> mainframe.client.messages.loan.Create.RATEFREQUENCY_MONTHLY;
            case YEARLY -> mainframe.client.messages.loan.Create.RATEFREQUENCY_YEARLY;
        };
    }

    private String toMainframePaymentFrequency(Loan.PaymentFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> mainframe.client.messages.loan.Create.PAYMENTFREQUENCY_WEEKLY;
            case FORTNIGHTLY -> mainframe.client.messages.loan.Create.PAYMENTFREQUENCY_FORTNIGHTLY;
            case MONTHLY -> mainframe.client.messages.loan.Create.PAYMENTFREQUENCY_MONTHLY;
            case YEARLY -> mainframe.client.messages.loan.Create.PAYMENTFREQUENCY_YEARLY;
            case ON_RENEWAL -> mainframe.client.messages.loan.Create.PAYMENTFREQUENCY_ONRENEWAL;
        };
    }

    private String toMainframeStatus(Loan.Status status) {
        return switch (status) {
            case NEW -> UpdateStatus.STATUS_NEW;
            case PENDING -> UpdateStatus.STATUS_PENDING;
            case ACTIVE -> UpdateStatus.STATUS_ACTIVE;
            case DEFAULTED -> UpdateStatus.STATUS_DEFAULTED;
            case CANCELLED -> UpdateStatus.STATUS_CANCELLED;
        };
    }

    // Shared by createLoan/getLoan/updateLoan, since all three return the same
    // shape of data. knownType and knownTermMonths are what the caller already
    // has (they are null when re-fetching), because the mainframe can't supply
    // them.
    private Loan toLoan(String loanNumber, String customerNumber, String status, double principal,
            double rateAmount, String rateType, String startDate, String renewalDate,
            double paymentAmount, String paymentFrequency, String rateFrequency,
            Loan.Type knownType, Integer knownTermMonths) {
        // Interest-Only is the one type the mainframe does report back
        boolean isInterestOnly =
                mainframe.client.messages.loan.Create.RATETYPE_INTERESTONLY.equals(rateType);
        Loan.Type type = isInterestOnly ? Loan.Type.INTEREST_ONLY : knownType;
        boolean isFloating =
                mainframe.client.messages.loan.Create.RATETYPE_FLOATING.equals(rateType);

        return new Loan(loanNumber, customerNumber, parseStatus(status), principal, rateAmount,
                type, isFloating, java.time.LocalDate.parse(startDate),
                java.time.LocalDate.parse(renewalDate), paymentAmount,
                parsePaymentFrequency(paymentFrequency), parseRateFrequency(rateFrequency),
                knownTermMonths);
    }

    private Loan.Status parseStatus(String status) {
        return switch (status) {
            case "New" -> Loan.Status.NEW;
            case "Pending" -> Loan.Status.PENDING;
            case "Active" -> Loan.Status.ACTIVE;
            case "Defaulted" -> Loan.Status.DEFAULTED;
            case "Cancelled" -> Loan.Status.CANCELLED;
            default -> throw new IllegalArgumentException("Unrecognised loan status: " + status);
        };
    }

    private Loan.RateFrequency parseRateFrequency(String frequency) {
        return switch (frequency) {
            case "Daily" -> Loan.RateFrequency.DAILY;
            case "Weekly" -> Loan.RateFrequency.WEEKLY;
            case "Fortnightly" -> Loan.RateFrequency.FORTNIGHTLY;
            case "Monthly" -> Loan.RateFrequency.MONTHLY;
            case "Yearly" -> Loan.RateFrequency.YEARLY;
            default -> throw new IllegalArgumentException(
                    "Unrecognised rate frequency: " + frequency);
        };
    }

    private Loan.PaymentFrequency parsePaymentFrequency(String frequency) {
        return switch (frequency) {
            case "Weekly" -> Loan.PaymentFrequency.WEEKLY;
            case "Fortnightly" -> Loan.PaymentFrequency.FORTNIGHTLY;
            case "Monthly" -> Loan.PaymentFrequency.MONTHLY;
            case "Yearly" -> Loan.PaymentFrequency.YEARLY;
            case "Onrenewal" -> Loan.PaymentFrequency.ON_RENEWAL;
            default -> throw new IllegalArgumentException(
                    "Unrecognised payment frequency: " + frequency);
        };
    }
}
