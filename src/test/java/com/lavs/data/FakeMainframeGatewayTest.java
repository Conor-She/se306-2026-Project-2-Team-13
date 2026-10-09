package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.model.CoBorrower;
import com.lavs.model.Customer;
import com.lavs.model.Loan;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeMainframeGatewayTest {

    private final FakeMainframeGateway gateway = new FakeMainframeGateway();

    private Customer buildCustomer() {
        return new Customer("C001", "Ms", "Jane", "Smith", LocalDate.of(1990, 5, 1), "NZ");
    }

    // A loan with no ID, as the Business Logic Layer would build it before
    // the mainframe has assigned one
    private Loan buildNewLoan() {
        return new Loan(null, "C001", Loan.Status.NEW, 500000, 5.5, Loan.Type.TABLE_LOAN, false,
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), 2500,
                Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 360);
    }

    // --- Success ---

    // A customer that is created can be fetched back and found by search
    @Test
    void storesAndReturnsCustomers() {
        gateway.createCustomer(buildCustomer());

        assertEquals("Jane", gateway.getCustomer("C001").getFirstName());
        assertEquals(1, gateway.searchCustomers("Jane").size());
        assertTrue(gateway.searchCustomers("Nobody").isEmpty());
    }

    // The mainframe assigns loan numbers and starts every loan as New, so
    // the fake must do the same for Business Logic tests to be realistic
    @Test
    void assignsLoanIdAndStartsLoansAsNew() {
        Loan created = gateway.createLoan(buildNewLoan());

        assertNotNull(created.getLoanId());
        assertEquals(Loan.Status.NEW, created.getStatus());
        assertEquals(created.getLoanId(), gateway.getLoan(created.getLoanId()).getLoanId());
    }

    // Changing a status must actually change what is read back afterwards
    @Test
    void updatesLoanStatus() {
        Loan created = gateway.createLoan(buildNewLoan());

        gateway.updateLoanStatus(created.getLoanId(), Loan.Status.PENDING);

        assertEquals(Loan.Status.PENDING, gateway.getLoan(created.getLoanId()).getStatus());
    }

    // The co-borrower count is what the 3-person limit will be checked against
    @Test
    void countsCoBorrowersPerLoan() {
        Loan created = gateway.createLoan(buildNewLoan());
        assertEquals(0, gateway.getCoBorrowerCount(created.getLoanId()));

        gateway.addCoBorrower(new CoBorrower("CB1", created.getLoanId(), "C002"));
        gateway.addCoBorrower(new CoBorrower("CB2", created.getLoanId(), "C003"));

        assertEquals(2, gateway.getCoBorrowerCount(created.getLoanId()));
    }

    // Category values are seeded by the test, since the fake has no real constants
    @Test
    void returnsSeededCategoryValues() {
        gateway.seedCategoryValues("EmploymentType", List.of("Full-time", "Part-time"));

        assertEquals(2, gateway.getCategoryValues("EmploymentType").size());
        assertTrue(gateway.getCategoryValues("Unknown").isEmpty());
    }

    // --- Business error ---

    // A configured business error hits only the next call, then the fake
    // goes back to normal
    @Test
    void businessErrorAppliesToTheNextCallOnly() {
        gateway.failNextCallWith(3, "The server could not process the message");

        MainframeException exception = assertThrows(MainframeException.class,
                () -> gateway.createCustomer(buildCustomer()));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
        assertEquals(3, exception.getErrorCode());
        assertDoesNotThrow(() -> gateway.createCustomer(buildCustomer()));
    }

    // Changing the status of a loan that doesn't exist is a business error
    @Test
    void rejectsStatusChangeForUnknownLoan() {
        MainframeException exception = assertThrows(MainframeException.class,
                () -> gateway.updateLoanStatus("MISSING", Loan.Status.PENDING));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
    }

    // --- Mainframe unreachable ---

    // While unavailable, every call fails with the "unavailable" type and
    // code 10, until the test switches it back off
    @Test
    void unavailableMainframeFailsEveryCallUntilReset() {
        gateway.simulateUnavailable(true);

        MainframeException exception = assertThrows(MainframeException.class,
                () -> gateway.login("user", "pass"));
        assertThrows(MainframeException.class, () -> gateway.searchCustomers("Jane"));

        assertEquals(MainframeException.Type.UNAVAILABLE, exception.getType());
        assertEquals(10, exception.getErrorCode());

        gateway.simulateUnavailable(false);
        assertDoesNotThrow(() -> gateway.login("user", "pass"));
    }
}