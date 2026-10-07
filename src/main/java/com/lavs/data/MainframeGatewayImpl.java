package com.lavs.data;

import com.lavs.model.Address;
import com.lavs.model.Customer;
import mainframe.client.Connection;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.StatusMessage;
import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Update;
import java.util.ArrayList;

import java.io.IOException;
import com.lavs.model.CoBorrower;
import com.lavs.model.CustomerDocument;
import com.lavs.model.CustomerNote;
import com.lavs.model.Email;
import com.lavs.model.Employer;
import com.lavs.model.Guarantor;
import com.lavs.model.Loan;
import com.lavs.model.Payment;
import com.lavs.model.Phone;
import com.lavs.model.Repayment;

import java.util.ArrayList;
import java.util.List;

// Real MainframeGateway implementation, wrapping a Connection (e.g.
// HttpConnection). Translates between domain objects and the mainframe's
// Message classes.
public class MainframeGatewayImpl implements MainframeGateway {

    private final Connection connection;

    public MainframeGatewayImpl(Connection connection) {
        this.connection = connection;
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
        // TODO: 'organisation' is required by the mainframe but its source
        // is still unconfirmed - see OrganisationTests / the organisation
        // package for how to retrieve our team's assigned organisation ID
        long organisation = 0L;

        Create message = new Create(
                customer.getDateOfBirth() == null ? null : customer.getDateOfBirth().toString(),
                customer.getLastName(),
                customer.getFirstName(),
                customer.getNationality(),
                organisation,
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
    public CustomerDocument addDocument(CustomerDocument document) {
        throw new UnsupportedOperationException("Not yet implemented - needs customer.AddDocument");
    }

    @Override
    public CustomerDocument updateDocument(CustomerDocument document) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.UpdateDocument");
    }

    @Override
    public void deleteDocument(String documentId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.DeleteDocument");
    }

    @Override
    public Guarantor addGuarantor(Guarantor guarantor) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.AddRelationship");
    }

    @Override
    public void deleteGuarantor(String guarantorId) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.DeleteRelationship");
    }

    @Override
    public Loan createLoan(Loan loan) {
        throw new UnsupportedOperationException("Not yet implemented - needs loan.Create");
    }

    @Override
    public Loan getLoan(String loanId) {
        throw new UnsupportedOperationException("Not yet implemented - needs loan.Retrieve");
    }

    @Override
    public Loan updateLoan(Loan loan) {
        throw new UnsupportedOperationException("Not yet implemented - needs loan.Update");
    }

    @Override
    public Loan updateLoanStatus(String loanId, Loan.Status newStatus) {
        throw new UnsupportedOperationException(
                "Not yet implemented - needs customer.UpdateStatus or loan equivalent");
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
        throw new UnsupportedOperationException(
                "Not yet implemented - needs constantCategory/constantItem messages");
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
}