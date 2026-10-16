package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Customer;
import com.lavs.model.CustomerDetails;
import com.lavs.model.CustomerId;
import com.lavs.model.Loan;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerServiceLoansTest {

    private FakeMainframeGateway gateway;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        gateway = new FakeMainframeGateway();
        service = new CustomerService(gateway);
        gateway.createCustomer(
                new Customer("C001", "Ms", "Amelia", "Adams", LocalDate.of(1990, 1, 1), "NZ"));
    }

    private Loan loanFor(String customerId) {
        return gateway.createLoan(new Loan(null, customerId, Loan.Status.NEW, 12_000, 12.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                1_066.19, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12));
    }

    // A loan saved earlier is found again when the customer is opened, which is what makes
    // a loan survive a restart
    @Test
    void customerDetailsIncludeTheLoansSavedOnTheMainframe() {
        Loan saved = loanFor("C001");

        CustomerDetails details = service.getById(new CustomerId("C001"));

        assertEquals(1, details.loans().size());
        assertEquals(saved.getLoanId(), details.loans().get(0).getLoanId());
    }

    @Test
    void onlyTheCustomersOwnLoansAreIncludedInLoanNumberOrder() {
        Loan first = loanFor("C001");
        loanFor("C002");
        Loan second = loanFor("C001");

        CustomerDetails details = service.getById(new CustomerId("C001"));

        assertEquals(java.util.List.of(first.getLoanId(), second.getLoanId()),
                details.loans().stream().map(Loan::getLoanId).toList());
    }

    @Test
    void aCustomerWithNoLoansHasAnEmptyList() {
        assertTrue(service.getById(new CustomerId("C001")).loans().isEmpty());
    }
}