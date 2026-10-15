package com.lavs.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.business.CoBorrowerService;
import com.lavs.business.CustomerService;
import com.lavs.business.LoanPrerequisiteChecker;
import com.lavs.business.LoanPrerequisiteException;
import com.lavs.business.LoanService;
import com.lavs.business.LoanWithSchedule;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Address;
import com.lavs.model.CoBorrower;
import com.lavs.model.Customer;
import com.lavs.model.Employer;
import com.lavs.model.Loan;
import com.lavs.model.Phone;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanApplicationWorkflowIntegrationTest {

    private FakeMainframeGateway gateway;
    private CustomerService customerService;
    private LoanService loanService;
    private CoBorrowerService coBorrowerService;

    @BeforeEach
    void connectApplicationLayers() {
        gateway = new FakeMainframeGateway();
        customerService = new CustomerService(gateway);
        loanService = new LoanService(gateway);
        coBorrowerService = new CoBorrowerService(gateway);
    }

    @Test
    void completesFullLoanApplicationWorkflow() {
        gateway.login("mortgage-manager", "password");

        Customer primaryBorrower = createEligibleCustomer("Jane", "Smith");
        Customer secondBorrower = customerService.create(
                customer("John", "Smith"), List.of(), List.of(), List.of(), List.of());

        LoanWithSchedule created = loanService.create(newLoan(primaryBorrower.getCustomerId()));
        CoBorrower coBorrower = coBorrowerService.addCoBorrower(
                created.getLoan().getLoanId(), secondBorrower.getCustomerId());
        Loan pending = loanService.changeStatus(
                created.getLoan().getLoanId(), Loan.Status.PENDING);

        assertNotNull(primaryBorrower.getCustomerId());
        assertNotNull(created.getLoan().getLoanId());
        assertFalse(created.getSchedule().isEmpty());
        assertEquals(created.getSchedule().size(),
                gateway.getRepaymentSchedule(created.getLoan().getLoanId()).size());
        assertEquals(secondBorrower.getCustomerId(), coBorrower.getCustomerId());
        assertEquals(List.of(coBorrower), gateway.getCoBorrowers(created.getLoan().getLoanId()));
        assertEquals(Loan.Status.PENDING, pending.getStatus());
        assertEquals(Loan.Status.PENDING,
                gateway.getLoan(created.getLoan().getLoanId()).getStatus());
    }

    @Test
    void surfacesMissingMailingAddressAndDoesNotCreateLoan() {
        gateway.login("mortgage-manager", "password");
        Customer customer = customerService.create(customer("Jane", "Smith"),
                List.of(address(false)), List.of(phone()), List.of(), List.of(employer()));

        LoanPrerequisiteException error = assertThrows(LoanPrerequisiteException.class,
                () -> loanService.create(newLoan(customer.getCustomerId())));

        assertEquals(LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE, error.getMessage());
        assertTrue(gateway.findLoansByCustomer(customer.getCustomerId()).isEmpty());
    }

    private Customer createEligibleCustomer(String firstName, String lastName) {
        return customerService.create(customer(firstName, lastName),
                List.of(address(true)), List.of(phone()), List.of(), List.of(employer()));
    }

    private Customer customer(String firstName, String lastName) {
        return new Customer(null, "Ms", firstName, lastName,
                LocalDate.of(1990, 5, 1), "NZ");
    }

    private Address address(boolean mailing) {
        return new Address(null, null, "5 Queen Street", "CBD", "Auckland", "1010", "NZ",
                mailing);
    }

    private Phone phone() {
        return new Phone(null, null, "0211234567", true, true);
    }

    private Employer employer() {
        return new Employer(null, null, "Acme Limited", "Engineer", 90_000, "Primary");
    }

    private Loan newLoan(String customerId) {
        return new Loan(null, customerId, Loan.Status.NEW, 12_000, 12.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 11, 1), 1_000, Loan.PaymentFrequency.MONTHLY,
                Loan.RateFrequency.MONTHLY, 12);
    }
}
