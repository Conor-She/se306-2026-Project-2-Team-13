package com.lavs.data;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import mainframe.client.messages.customer.AddEmail;
import mainframe.client.messages.customer.AddEmployer;
import mainframe.client.messages.customer.AddPhoneNumber;
import mainframe.client.messages.customer.AddRelationship;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.DeleteAddress;
import mainframe.client.messages.customer.DeleteDocument;
import mainframe.client.messages.customer.DeleteEmail;
import mainframe.client.messages.customer.DeleteEmployer;
import mainframe.client.messages.customer.DeletePhoneNumber;
import mainframe.client.messages.customer.DeleteRelationship;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customer.Update;
import mainframe.client.messages.customer.UpdateAddress;
import mainframe.client.messages.customer.UpdateDocument;
import mainframe.client.messages.customer.UpdateEmail;
import mainframe.client.messages.customer.UpdateEmployer;
import mainframe.client.messages.customer.UpdatePhoneNumber;
import mainframe.client.messages.customer.UpdateRelationship;
import mainframe.client.messages.loan.AddCoborrower;
import mainframe.client.messages.loan.AddPayment;
import mainframe.client.messages.loan.DeleteCoborrower;
import mainframe.client.messages.loan.DeletePayment;
import mainframe.client.messages.loan.UpdateStatus;


// Real MainframeGateway implementation, wrapping a Connection (e.g.
// HttpConnection). Translates between domain objects and the mainframe's
// Message classes.
public class MainframeGatewayImpl implements MainframeGateway {

    private static final long CATEGORY_PAGE_SIZE = 100;
    static final String DEFAULT_PHONE_COUNTRY_CODE = "+64";

    private final Connection connection;
    private final Long organisationNumber;

    public MainframeGatewayImpl(Connection connection, long organisationNumber) {
        this.connection = connection;
        this.organisationNumber = organisationNumber;
    }

    public MainframeGatewayImpl(Connection connection, String organisationName) {
        this.connection = connection;
        this.organisationNumber = null;
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
        long organisationId = requireOrganisationNumber();
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
                customer.getDateOfBirth(), // The create response may omit optional fields.
                message.getNationality());
    }

    // The organisation number is needed to create a customer. A gateway built
    // from a name doesn't have one.
    private long requireOrganisationNumber() {
        if (organisationNumber == null) {
            throw new IllegalStateException(
                    "Creating a customer needs the organisation number. Build the gateway "
                            + "with the number, not the name.");
        }
        return organisationNumber;
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
        List<Customer> results = new ArrayList<>();
        long page = 1;
        while (true) {
            Search message = new Search(page, query, CATEGORY_PAGE_SIZE);
            sendOrThrow(message);
            for (Search.Item item : message.getItems()) {
                // Search responses may omit the optional date of birth.
                results.add(new Customer(item.getNumber(), item.getTitle(), item.getFirstName(),
                        item.getFamilyName(), parseDate(item.getDateOfBirth()), null));
            }
            if (message.getItems().length == 0 || page * CATEGORY_PAGE_SIZE >= message.getTotal()) {
                return results;
            }
            page++;
        }
    }

    @Override
    public Customer getCustomer(String customerId) {
        Retrieve message = new Retrieve(customerId);
        sendOrThrow(message);

        return new Customer(message.getNumber(), message.getTitle(), message.getFirstName(),
                message.getFamilyName(),
                parseDate(message.getDateOfBirth()),
                message.getNationality());
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
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
        Long addressId = address.getAddressId();
        if (addressId == null) {
            throw new IllegalArgumentException("An existing address with an addressId is required");
        }

        mainframe.client.messages.customerAddress.Retrieve current =
                new mainframe.client.messages.customerAddress.Retrieve(
                        addressId, address.getCustomerId());
        sendOrThrow(current);

        UpdateAddress message = new UpdateAddress(
                address.getCity(), address.getCountry(), address.getCustomerId(), addressId,
                address.isMailing(), current.getIsPrimary(), address.getStreet(),
                current.getLine2(), current.getOtherType(), address.getPostCode(),
                address.getSuburb(), current.getType());
        sendOrThrow(message);

        return new Address(message.getId(), message.getCustomer(), message.getLine1(),
                message.getSuburb(), message.getCity(), message.getPostCode(),
                message.getCountry(), message.getIsMailing());
    }

    @Override
    public void deleteAddress(String customerId, Long addressId) {
        sendOrThrow(new DeleteAddress(customerId, addressId));
    }

    // The mainframe's IDs are numbers, but LAVS keeps them as text
    private long parseNumericId(String id, String name) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException invalidId) {
            throw new IllegalArgumentException(name + " must be a numeric mainframe ID",
                    invalidId);
        }
    }

    @Override
    public Phone addPhone(Phone phone) {
        AddPhoneNumber message = new AddPhoneNumber(
                phone.isSendTXT(), DEFAULT_PHONE_COUNTRY_CODE, phone.getCustomerId(),
                phone.isPrimary(), phone.getNumber(), null, AddPhoneNumber.TYPE_MOBILE);
        sendOrThrow(message);

        return new Phone(message.getId(), message.getCustomer(), message.getNumber(),
                message.getIsPrimary(), message.getCanTxt());
    }

    @Override
    public Phone updatePhone(Phone phone) {
        Long phoneId = phone.getPhoneId();
        if (phoneId == null) {
            throw new IllegalArgumentException("An existing phone with a phoneId is required");
        }

        mainframe.client.messages.customerPhoneNumber.Retrieve current =
                new mainframe.client.messages.customerPhoneNumber.Retrieve(
                        phoneId, phone.getCustomerId());
        sendOrThrow(current);

        UpdatePhoneNumber message = new UpdatePhoneNumber(
                phone.isSendTXT(), current.getCountryCode(), phone.getCustomerId(), phoneId,
                phone.isPrimary(), phone.getNumber(), current.getOtherType(), current.getType());
        sendOrThrow(message);

        return new Phone(message.getId(), message.getCustomer(), message.getNumber(),
                message.getIsPrimary(), message.getCanTxt());
    }

    @Override
    public void deletePhone(String customerId, Long phoneId) {
        sendOrThrow(new DeletePhoneNumber(customerId, phoneId));
    }

    @Override
    public Email addEmail(Email email) {
        AddEmail message = new AddEmail(email.getEmailAddress(), email.getCustomerId(),
                email.isPrimary(), null, AddEmail.TYPE_PERSONAL);
        sendOrThrow(message);

        return new Email(message.getId(), message.getCustomer(), message.getAddress(),
                message.getIsPrimary());
    }

    @Override
    public Email updateEmail(Email email) {
        Long emailId = email.getEmailId();
        if (emailId == null) {
            throw new IllegalArgumentException("An existing email with an emailId is required");
        }

        mainframe.client.messages.customerEmail.Retrieve current =
                new mainframe.client.messages.customerEmail.Retrieve(
                        emailId, email.getCustomerId());
        sendOrThrow(current);

        UpdateEmail message = new UpdateEmail(email.getEmailAddress(), email.getCustomerId(),
                emailId, email.isPrimary(), current.getOtherType(), current.getType());
        sendOrThrow(message);

        return new Email(message.getId(), message.getCustomer(), message.getAddress(),
                message.getIsPrimary());
    }

    @Override
    public void deleteEmail(String customerId, Long emailId) {
        sendOrThrow(new DeleteEmail(customerId, emailId));
    }

    @Override
    public List<Address> getAddresses(String customerId) {
        List<Address> addresses = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.customerAddress.List message =
                    new mainframe.client.messages.customerAddress.List(customerId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.customerAddress.List.Item[] items = message.getItems();
            for (mainframe.client.messages.customerAddress.List.Item item : items) {
                addresses.add(getAddress(customerId, item.getId()));
            }
            if (items.length == 0 || addresses.size() >= message.getTotal()) {
                return addresses;
            }
            page++;
        }
    }

    // One address in full. Only the fields LAVS models are kept: the second
    // address line, the type and the primary flag are not.
    private Address getAddress(String customerId, long addressId) {
        mainframe.client.messages.customerAddress.Retrieve message =
                new mainframe.client.messages.customerAddress.Retrieve(addressId, customerId);
        sendOrThrow(message);

        return new Address(message.getId(), customerId, message.getLine1(), message.getSuburb(),
                message.getCity(), message.getPostCode(), message.getCountry(),
                message.getIsMailing());
    }

    @Override
    public List<Phone> getPhones(String customerId) {
        List<Phone> phones = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.customerPhoneNumber.List message =
                    new mainframe.client.messages.customerPhoneNumber.List(customerId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.customerPhoneNumber.List.Item[] items = message.getItems();
            for (mainframe.client.messages.customerPhoneNumber.List.Item item : items) {
                phones.add(getPhone(customerId, item.getId()));
            }
            if (items.length == 0 || phones.size() >= message.getTotal()) {
                return phones;
            }
            page++;
        }
    }

    private Phone getPhone(String customerId, long phoneId) {
        mainframe.client.messages.customerPhoneNumber.Retrieve message =
                new mainframe.client.messages.customerPhoneNumber.Retrieve(phoneId, customerId);
        sendOrThrow(message);

        return new Phone(message.getId(), customerId, message.getNumber(),
                message.getIsPrimary(), message.getCanTxt());
    }

    @Override
    public List<Email> getEmails(String customerId) {
        List<Email> emails = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.customerEmail.List message =
                    new mainframe.client.messages.customerEmail.List(customerId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.customerEmail.List.Item[] items = message.getItems();
            for (mainframe.client.messages.customerEmail.List.Item item : items) {
                emails.add(new Email(item.getId(), customerId, item.getAddress(),
                        "y".equals(item.getIsPrimary())));
            }
            if (items.length == 0 || emails.size() >= message.getTotal()) {
                return emails;
            }
            page++;
        }
    }

    @Override
    public Employer addEmployer(Employer employer) {
        AddEmployer message = new AddEmployer(employer.getCustomerId(),
                employer.getCompanyName(), employer.getJobTitle(), employer.getIncome(),
                AddEmployer.TYPE_PRIMARY);
        sendOrThrow(message);

        return new Employer(message.getId(), message.getCustomer(), message.getName(),
                message.getPosition(), message.getSalary(), employer.getEmploymentType());
    }

    @Override
    public Employer updateEmployer(Employer employer) {
        Long employerId = employer.getEmployerId();
        if (employerId == null) {
            throw new IllegalArgumentException(
                    "An existing employer with an employerId is required");
        }

        mainframe.client.messages.customerEmployer.Retrieve current =
                new mainframe.client.messages.customerEmployer.Retrieve(
                        employerId, employer.getCustomerId());
        sendOrThrow(current);

        UpdateEmployer message = new UpdateEmployer(employer.getCustomerId(), employerId,
                employer.getCompanyName(), employer.getJobTitle(), employer.getIncome(),
                current.getType());
        sendOrThrow(message);

        return new Employer(message.getId(), message.getCustomer(), message.getName(),
                message.getPosition(), message.getSalary(), employer.getEmploymentType());
    }

    @Override
    public void deleteEmployer(String customerId, Long employerId) {
        sendOrThrow(new DeleteEmployer(customerId, employerId));
    }

    @Override
    public CustomerNote addNote(CustomerNote note) {
        var message = new mainframe.client.messages.customer.AddNote(note.getCustomerId(), note.getNoteText(), note.getType());
        sendOrThrow(message);
        if (message.getId() == null || message.getId() <= 0)
            throw new MainframeException(MainframeException.Type.BUSINESS_ERROR, 0, "The mainframe did not return a note ID");
        return new CustomerNote(message.getId().toString(), note.getCustomerId(), note.getNoteText(),
                note.getDateAdded(), note.getType(), message.getAuthor());
    }

    @Override
    public CustomerNote updateNote(CustomerNote note) {
        var message = new mainframe.client.messages.customer.UpdateNote(note.getCustomerId(),
                Long.parseLong(note.getNoteId()), note.getNoteText(), note.getType());
        sendOrThrow(message);
        return new CustomerNote(note.getNoteId(), note.getCustomerId(), note.getNoteText(),
                note.getDateAdded(), note.getType(), message.getAuthor());
    }

    @Override
    public void deleteNote(String customerId, String noteId) {
        sendOrThrow(new mainframe.client.messages.customer.DeleteNote(customerId, Long.parseLong(noteId)));
    }

    @Override
    public List<CustomerNote> getNotes(String customerId) {
        List<CustomerNote> notes = new ArrayList<>();
        long page = 1;
        long seen = 0;
        while (true) {
            var message = new mainframe.client.messages.customerNote.List(customerId, page, 100);
            sendOrThrow(message);
            for (var item : message.getItems()) {
                var retrieve = new mainframe.client.messages.customerNote.Retrieve(item.getId(), customerId);
                sendOrThrow(retrieve);
                String date = retrieve.getDateAdded();
                notes.add(new CustomerNote(item.getId().toString(), customerId, retrieve.getLines(),
                        date == null || date.isBlank() ? null : LocalDate.parse(date,
                                date.contains("T") ? DateTimeFormatter.ISO_DATE_TIME : DateTimeFormatter.ISO_LOCAL_DATE),
                        retrieve.getType(), retrieve.getAuthor()));
            }
            seen += message.getItems().length;
            if (message.getItems().length == 0 || seen >= message.getTotal()) return List.copyOf(notes);
            page++;
        }
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
        AddRelationship message = new AddRelationship(guarantor.getCustomerId(),
                toMainframeOtherType(guarantor.getRelationshipType()),
                guarantor.getGuarantorCustomerId(),
                toMainframeRelationshipType(guarantor.getRelationshipType()));
        sendOrThrow(message);

        return new Guarantor(String.valueOf(message.getId()), guarantor.getCustomerId(),
                guarantor.getGuarantorCustomerId(), guarantor.getRelationshipType());
    }

    @Override
    public Guarantor updateGuarantor(Guarantor guarantor) {
        UpdateRelationship message = new UpdateRelationship(guarantor.getCustomerId(),
                parseNumericId(guarantor.getGuarantorId(), "Guarantor ID"),
                toMainframeOtherType(guarantor.getRelationshipType()),
                guarantor.getGuarantorCustomerId(),
                toMainframeRelationshipType(guarantor.getRelationshipType()));
        sendOrThrow(message);

        return new Guarantor(guarantor.getGuarantorId(), guarantor.getCustomerId(),
                guarantor.getGuarantorCustomerId(), guarantor.getRelationshipType());
    }

    @Override
    public void deleteGuarantor(String customerId, String guarantorId) {
        sendOrThrow(new DeleteRelationship(customerId,
                parseNumericId(guarantorId, "Guarantor ID")));
    }

    @Override
    public List<Employer> getEmployers(String customerId) {
        List<Employer> employers = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.customerEmployer.List message =
                    new mainframe.client.messages.customerEmployer.List(customerId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.customerEmployer.List.Item[] items = message.getItems();
            for (mainframe.client.messages.customerEmployer.List.Item item : items) {
                employers.add(getEmployer(customerId, item.getId()));
            }
            if (items.length == 0 || employers.size() >= message.getTotal()) {
                return employers;
            }
            page++;
        }
    }

    private Employer getEmployer(String customerId, long employerId) {
        mainframe.client.messages.customerEmployer.Retrieve message =
                new mainframe.client.messages.customerEmployer.Retrieve(employerId, customerId);
        sendOrThrow(message);

        return new Employer(message.getId(), customerId, message.getName(),
                message.getPosition(), message.getSalary(), null);
    }

    @Override
    public List<Guarantor> getGuarantors(String customerId) {
        List<Guarantor> guarantors = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.customerRelationship.List message =
                    new mainframe.client.messages.customerRelationship.List(
                            customerId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.customerRelationship.List.Item[] items =
                    message.getItems();
            for (mainframe.client.messages.customerRelationship.List.Item item : items) {
                guarantors.add(toGuarantor(item.getId(), customerId,
                        item.getSecondaryCustomerNumber(), item.getType(),
                        otherTypeOf(customerId, item.getId(), item.getType())));
            }
            if (items.length == 0 || guarantors.size() >= message.getTotal()) {
                return guarantors;
            }
            page++;
        }
    }

    // The mainframe's list doesn't have a relationship's other-type text, so it
    // is only fetched for the ones that need it (type Other)
    private String otherTypeOf(String customerId, long relationshipId, String type) {
        if (!AddRelationship.TYPE_OTHER.equals(type)) {
            return null;
        }
        mainframe.client.messages.customerRelationship.Retrieve message =
                new mainframe.client.messages.customerRelationship.Retrieve(
                        relationshipId, customerId);
        sendOrThrow(message);
        return message.getOtherType();
    }

    // The mainframe's relationship type is a fixed set (Parent, Spouse,
    // LegalAuthority, Other) while LAVS's is free text. Text that matches a
    // fixed type, ignoring case and spaces, uses it; anything else is Other.
    private String toMainframeRelationshipType(String relationshipType) {
        String normalised = relationshipType.replace(" ", "").toLowerCase();
        for (String known : List.of(AddRelationship.TYPE_PARENT, AddRelationship.TYPE_SPOUSE,
                AddRelationship.TYPE_LEGALAUTHORITY)) {
            if (known.toLowerCase().equals(normalised)) {
                return known;
            }
        }
        return AddRelationship.TYPE_OTHER;
    }

    // The other-type text goes with the type Other, and carries the free text
    private String toMainframeOtherType(String relationshipType) {
        return AddRelationship.TYPE_OTHER.equals(toMainframeRelationshipType(relationshipType))
                ? relationshipType
                : null;
    }

    // Back to LAVS: for the type Other, the relationship type is the text that
    // was stored with it
    private Guarantor toGuarantor(Long id, String customerId, String guarantorCustomerId,
            String type, String otherType) {
        boolean hasOtherText = AddRelationship.TYPE_OTHER.equals(type)
                && otherType != null && !otherType.isBlank();
        return new Guarantor(String.valueOf(id), customerId, guarantorCustomerId,
                hasOtherText ? otherType : type);
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

        Loan stored = getLoan(loan.getLoanId());
        Loan.Type type = stored.getType() == Loan.Type.INTEREST_ONLY
                ? Loan.Type.INTEREST_ONLY
                : loan.getType();
        return new Loan(stored.getLoanId(), stored.getCustomerId(), stored.getStatus(),
                stored.getPrincipal(), stored.getInterestRate(), type, stored.isFloating(),
                stored.getStartDate(), stored.getRenewDate(), stored.getPaymentAmount(),
                stored.getPaymentFrequency(), stored.getRateFrequency(), loan.getTermMonths());
    }

    @Override
    public Loan updateLoanStatus(String loanId, Loan.Status newStatus) {
        UpdateStatus message = new UpdateStatus(loanId, toMainframeStatus(newStatus));
        sendOrThrow(message);

        // The reply only has the loan number and status,
        // so the loan is re-fetched using the number we sent
        return getLoan(loanId);
    }

    @Override
    public CoBorrower addCoBorrower(CoBorrower coBorrower) {
        AddCoborrower message = new AddCoborrower(
                coBorrower.getCustomerId(), coBorrower.getLoanId(), AddCoborrower.TYPE_COBORROWER);
        sendOrThrow(message);

        return new CoBorrower(String.valueOf(message.getId()), coBorrower.getLoanId(),
                coBorrower.getCustomerId());
    }

    @Override
    public void deleteCoBorrower(String loanId, String coBorrowerId) {
        sendOrThrow(new DeleteCoborrower(Long.parseLong(coBorrowerId), loanId));
    }

    @Override
    public List<CoBorrower> getCoBorrowers(String loanId) {
        List<CoBorrower> coBorrowers = new ArrayList<>();
        long page = 1;
        long seen = 0;
        while (true) {
            mainframe.client.messages.loanCoborrower.List message =
                    new mainframe.client.messages.loanCoborrower.List(loanId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.loanCoborrower.List.Item[] items = message.getItems();
            for (mainframe.client.messages.loanCoborrower.List.Item item : items) {
                if (AddCoborrower.TYPE_COBORROWER.equals(item.getType())) {
                    coBorrowers.add(new CoBorrower(String.valueOf(item.getId()), loanId,
                            item.getCoborrowerNumber()));
                }
            }
            seen += items.length;
            if (items.length == 0 || seen >= message.getTotal()) {
                return coBorrowers;
            }
            page++;
        }
    }

    @Override
    public int getCoBorrowerCount(String loanId) {
        return getCoBorrowers(loanId).size();
    }

    // The mainframe builds its own schedule from the loan's terms and has no
    // message for storing one, so there is nothing to save. The schedule LAVS
    // calculates is shown when the loan is created; the one read back later is
    // the mainframe's (see getRepaymentSchedule).
    @Override
    public void saveRepaymentSchedule(String loanId, List<Repayment> schedule) {
        // Intentionally empty
    }

    // The mainframe's list only has each loan's number, principal, start date
    // and status, so each loan is then fetched in full
    @Override
    public List<Loan> findLoansByCustomer(String customerId) {
        List<Loan> loans = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.loan.List message =
                    new mainframe.client.messages.loan.List(customerId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.loan.List.Item[] items = message.getItems();
            for (mainframe.client.messages.loan.List.Item item : items) {
                loans.add(getLoan(item.getLoanNumber()));
            }
            if (items.length == 0 || loans.size() >= message.getTotal()) {
                return loans;
            }
            page++;
        }
    }

    @Override
    public List<Repayment> getRepaymentSchedule(String loanId) {
        List<Repayment> schedule = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.loanRepaymentSchedule.List message =
                    new mainframe.client.messages.loanRepaymentSchedule.List(loanId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.loanRepaymentSchedule.List.Item[] items =
                    message.getItems();
            for (mainframe.client.messages.loanRepaymentSchedule.List.Item item : items) {
                schedule.add(new Repayment(loanId + "-repayment-" + item.getPaymentNumber(),
                        loanId, java.time.LocalDate.parse(item.getDate()), item.getAmountPaid(),
                        item.getPrincipalAmount(), item.getInterestAmount(),
                        item.getAmountRemaining()));
            }
            if (items.length == 0 || schedule.size() >= message.getTotal()) {
                return schedule;
            }
            page++;
        }
    }

    @Override
    public Payment addPayment(Payment payment) {
        AddPayment message = new AddPayment(
                payment.getAmountPaid(), payment.getPaymentDate().toString(),
                payment.getLoanId());
        sendOrThrow(message);

        // The mainframe has no field for a repayment, so that link isn't kept
        return new Payment(String.valueOf(message.getId()), payment.getLoanId(), null,
                payment.getPaymentDate(), payment.getAmountPaid());
    }

    // The mainframe has no update message, so an edit adds the new version and
    // then deletes the old one, and the new payment gets a new ID. Adding first
    // means a failure can't lose a payment; the cost is that a failed delete
    // leaves the old payment alongside the new one.
    @Override
    public Payment updatePayment(Payment payment) {
        Payment replacement = addPayment(new Payment(null, payment.getLoanId(), null,
                payment.getPaymentDate(), payment.getAmountPaid()));
        deletePayment(payment.getLoanId(), payment.getPaymentId());
        return replacement;
    }

    @Override
    public void deletePayment(String loanId, String paymentId) {
        sendOrThrow(new DeletePayment(Long.parseLong(paymentId), loanId));
    }

    // Reads every page of a loan's payments. The mainframe's ID is a number, kept here as text.
    @Override
    public List<Payment> getPayments(String loanId) {
        List<Payment> payments = new ArrayList<>();
        long page = 1;
        while (true) {
            mainframe.client.messages.loanPayment.List message =
                    new mainframe.client.messages.loanPayment.List(loanId, page, 100);
            sendOrThrow(message);

            mainframe.client.messages.loanPayment.List.Item[] items = message.getItems();
            for (mainframe.client.messages.loanPayment.List.Item item : items) {
                payments.add(new Payment(String.valueOf(item.getId()), loanId, null,
                        java.time.LocalDate.parse(item.getDate()), item.getAmountPaid()));
            }
            if (items.length == 0 || payments.size() >= message.getTotal()) {
                return payments;
            }
            page++;
        }
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
