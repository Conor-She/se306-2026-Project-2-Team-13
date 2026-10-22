package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeException;
import com.lavs.model.CoBorrower;
import com.lavs.model.Customer;
import com.lavs.model.Email;
import com.lavs.model.Loan;
import com.lavs.model.Payment;
import com.lavs.model.Phone;
import com.lavs.model.Repayment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// AI-assisted: Helped write the loan browsing service tests.
class LoanBrowsingServiceTest {

    private FakeMainframeGateway gateway;
    private LoanBrowsingService service;

    @BeforeEach
    void setUp() {
        gateway = new FakeMainframeGateway();
        service = new LoanBrowsingService(gateway);
        gateway.createCustomer(new Customer("C1", "Ms", "Sarah", "Johnson", LocalDate.of(1990, 1, 1), "NZ"));
        gateway.createCustomer(new Customer("C2", "Mr", "Alex", "Smith", LocalDate.of(1985, 5, 5), "NZ"));
    }

    private Loan loan(String loanId, String customerId, double principal) {
        return gateway.createLoan(new Loan(loanId, customerId, Loan.Status.NEW, principal, 6.5,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 500,
                Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12));
    }

    @Test
    void searchListsTheLoansOfMatchingCustomersWithTheirNames() {
        loan("L2", "C1", 20000);
        loan("L1", "C1", 10000);
        loan("L3", "C2", 5000);

        LoanBrowsingService.LoanSearchResult result = service.search("Johnson");

        assertEquals(List.of("L1", "L2"),
                result.loans().stream().map(listing -> listing.loan().getLoanId()).toList());
        assertTrue(result.loans().stream().allMatch(listing -> "Sarah Johnson".equals(listing.customerName())));
        assertFalse(result.truncated());
    }

    @Test
    void searchFindsALoanByItsNumber() {
        loan("L3", "C2", 5000);

        LoanBrowsingService.LoanSearchResult result = service.search(" L3 ");

        assertEquals(1, result.loans().size());
        assertEquals("Alex Smith", result.loans().get(0).customerName());
    }

    @Test
    void searchFindsLoansByPrimaryBorrowerCustomerId() {
        loan("L1", "C1", 10000);

        LoanBrowsingService.LoanSearchResult result = service.search("C1");

        assertEquals(List.of("L1"), result.loans().stream()
                .map(listing -> listing.loan().getLoanId()).toList());
    }

    @Test
    void blankOrUnmatchedSearchesReturnNoLoans() {
        loan("L1", "C1", 10000);

        assertTrue(service.search("   ").loans().isEmpty());
        assertTrue(service.search("Nobody").loans().isEmpty());
    }

    @Test
    void customersWithoutLoansGiveAnEmptyResult() {
        assertTrue(service.search("Smith").loans().isEmpty());
    }

    @Test
    void anUnavailableMainframeIsReportedNotHidden() {
        gateway.simulateUnavailable(true);

        MainframeException failure = assertThrows(MainframeException.class, () -> service.search("Smith"));
        assertEquals(MainframeException.Type.UNAVAILABLE, failure.getType());
    }

    @Test
    void veryBroadSearchesAreCappedAndFlagged() {
        for (int i = 0; i < LoanBrowsingService.MAX_CUSTOMERS_PER_SEARCH + 2; i++) {
            gateway.createCustomer(new Customer("X" + i, "Mr", "Common", "Name" + i, null, "NZ"));
        }

        assertTrue(service.search("Common").truncated());
    }

    @Test
    void recordsForLoadsTheLoansPaymentsAndCoBorrowerContactDetails() {
        loan("L1", "C1", 10000);
        loan("L9", "C1", 10000);
        gateway.addPayment(new Payment("P1", "L1", null, LocalDate.of(2026, 2, 1), 500));
        gateway.addPayment(new Payment("P2", "L9", null, LocalDate.of(2026, 2, 1), 500));
        gateway.saveRepaymentSchedule("L1", List.of(
                new Repayment("R1", "L1", LocalDate.of(2026, 2, 1), 550, 500, 50, 9500)));
        gateway.addCoBorrower(new CoBorrower("CB1", "L1", "C2"));
        gateway.addPhone(new Phone(null, "C2", "021 000 0000", false, false));
        gateway.addPhone(new Phone(null, "C2", "021 111 1111", true, false));
        gateway.addEmail(new Email(null, "C2", "alex@example.com", true));

        LoanBrowsingService.LoanRecords records = service.recordsFor("L1");

        assertEquals(List.of("P1"), records.payments().stream().map(Payment::getPaymentId).toList());
        assertEquals(List.of("R1"), records.repaymentDetails().getSchedule().stream()
                .map(Repayment::getRepaymentId).toList());
        assertEquals(550, records.repaymentDetails().getSummary().getTotalRepayable());
        assertEquals(List.of(new LoanBrowsingService.CoBorrowerDetails(
                "CB1", "C2", "Alex", "Smith", "021 111 1111", "alex@example.com")), records.coBorrowers());
    }

    @Test
    void recordsForALoanWithNothingRecordedAreEmpty() {
        loan("L1", "C1", 10000);

        LoanBrowsingService.LoanRecords records = service.recordsFor("L1");

        assertTrue(records.payments().isEmpty());
        assertTrue(records.coBorrowers().isEmpty());
        assertTrue(records.repaymentDetails().getSchedule().isEmpty());
    }

    @Test
    void recordsForRequiresALoanId() {
        assertThrows(IllegalArgumentException.class, () -> service.recordsFor(" "));
    }
}
