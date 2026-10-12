package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeException;
import com.lavs.model.CoBorrower;
import com.lavs.model.Loan;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoBorrowerServiceTest {

    private FakeMainframeGateway gateway;
    private CoBorrowerService service;
    private String loanId;

    // Every test starts with a fresh loan whose primary borrower is C001
    @BeforeEach
    void setUp() {
        gateway = new FakeMainframeGateway();
        service = new CoBorrowerService(gateway);
        loanId = gateway.createLoan(new Loan(null, "C001", Loan.Status.NEW, 12_000, 12.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                1_000, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12))
                .getLoanId();
    }

    // Adds co-borrowers C002, C003, ... through the service, so each add is
    // checked against the limit like a real one
    private List<CoBorrower> addCoBorrowers(int howMany) {
        List<CoBorrower> added = new ArrayList<>();
        for (int i = 0; i < howMany; i++) {
            added.add(service.addCoBorrower(loanId, "C00" + (i + 2)));
        }
        return added;
    }

    // --- Adding: with 0, 1 and 2 existing co-borrowers, all below the limit ---

    @Test
    void addsTheFirstCoBorrowerWhenThereAreNone() {
        CoBorrower added = service.addCoBorrower(loanId, "C002");

        assertNotNull(added.getCoBorrowerId());
        assertEquals(loanId, added.getLoanId());
        assertEquals("C002", added.getCustomerId());
        assertEquals(1, gateway.getCoBorrowerCount(loanId));
    }

    @Test
    void addsASecondCoBorrower() {
        addCoBorrowers(1);

        assertDoesNotThrow(() -> service.addCoBorrower(loanId, "C003"));

        assertEquals(2, gateway.getCoBorrowerCount(loanId));
    }

    // The boundary: with two co-borrowers a third is still allowed, because
    // the check is count < 3, not count <= 3
    @Test
    void addsTheThirdCoBorrowerWhichIsTheLastAllowed() {
        addCoBorrowers(2);

        assertDoesNotThrow(() -> service.addCoBorrower(loanId, "C004"));

        assertEquals(3, gateway.getCoBorrowerCount(loanId));
    }

    // --- Adding: the limit ---

    // With three co-borrowers (four people in total) a fourth is refused
    @Test
    void rejectsAFourthCoBorrower() {
        addCoBorrowers(3);

        CoBorrowerLimitException exception = assertThrows(CoBorrowerLimitException.class,
                () -> service.addCoBorrower(loanId, "C005"));

        assertEquals(CoBorrowerService.LIMIT_MESSAGE, exception.getMessage());
        assertEquals(3, gateway.getCoBorrowerCount(loanId));
    }

    // A loan that somehow already has more than the limit (e.g. data entered
    // elsewhere) must be refused too, not just one that is exactly at it
    @Test
    void rejectsWhenTheLoanIsAlreadyOverTheLimit() {
        for (int i = 0; i < 4; i++) {
            gateway.addCoBorrower(new CoBorrower(null, loanId, "C00" + (i + 2)));
        }

        assertThrows(CoBorrowerLimitException.class,
                () -> service.addCoBorrower(loanId, "C006"));
    }

    // --- Adding: other reasons it is refused ---

    // The primary borrower is already on the loan, so can't also be a co-borrower
    @Test
    void rejectsThePrimaryBorrowerAsACoBorrower() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.addCoBorrower(loanId, "C001"));

        assertEquals(CoBorrowerService.PRIMARY_BORROWER_MESSAGE, exception.getMessage());
        assertEquals(0, gateway.getCoBorrowerCount(loanId));
    }

    @Test
    void rejectsMissingIds() {
        assertThrows(IllegalArgumentException.class, () -> service.addCoBorrower(null, "C002"));
        assertThrows(IllegalArgumentException.class, () -> service.addCoBorrower(" ", "C002"));
        assertThrows(IllegalArgumentException.class, () -> service.addCoBorrower(loanId, null));
        assertThrows(IllegalArgumentException.class, () -> service.addCoBorrower(loanId, " "));
    }

    // Mainframe errors pass through unchanged for the UI to handle
    @Test
    void addingToAnUnknownLoanIsABusinessError() {
        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.addCoBorrower("MISSING", "C002"));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
    }

    @Test
    void passesMainframeUnavailableThrough() {
        gateway.simulateUnavailable(true);

        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.addCoBorrower(loanId, "C002"));

        assertEquals(MainframeException.Type.UNAVAILABLE, exception.getType());
    }

    // --- Removing ---

    // Removal has no limit check: it works at every count, down to none
    @Test
    void removesCoBorrowersAtEveryCountDownToNone() {
        List<CoBorrower> added = addCoBorrowers(3);

        for (int remaining = 2; remaining >= 0; remaining--) {
            service.removeCoBorrower(added.get(remaining).getCoBorrowerId());

            assertEquals(remaining, gateway.getCoBorrowerCount(loanId));
        }
    }

    // Removing one at the limit frees a place, so another can be added
    @Test
    void removalAtTheLimitFreesASlot() {
        List<CoBorrower> added = addCoBorrowers(3);
        assertThrows(CoBorrowerLimitException.class,
                () -> service.addCoBorrower(loanId, "C005"));

        service.removeCoBorrower(added.get(0).getCoBorrowerId());

        assertDoesNotThrow(() -> service.addCoBorrower(loanId, "C005"));
        assertEquals(3, gateway.getCoBorrowerCount(loanId));
    }

    @Test
    void removingAnUnknownCoBorrowerIsABusinessError() {
        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.removeCoBorrower("MISSING"));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
    }

    @Test
    void removeRejectsMissingId() {
        assertThrows(IllegalArgumentException.class, () -> service.removeCoBorrower(null));
        assertThrows(IllegalArgumentException.class, () -> service.removeCoBorrower(" "));
    }
}