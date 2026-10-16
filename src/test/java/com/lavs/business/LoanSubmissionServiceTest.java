package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.business.LoanSubmissionService.SavedLoan;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeException;
import com.lavs.model.Address;
import com.lavs.model.CoBorrower;
import com.lavs.model.Employer;
import com.lavs.model.Loan;
import com.lavs.model.LoanDraft;
import com.lavs.model.Phone;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanSubmissionServiceTest {

    private FakeMainframeGateway gateway;
    private LoanSubmissionService service;

    @BeforeEach
    void setUp() {
        gateway = new FakeMainframeGateway();
        service = new LoanSubmissionService(gateway);
    }

    // --- Helpers ---

    // A customer who meets every prerequisite: a mailing address, a phone and an employer
    private static void addEligibleCustomer(FakeMainframeGateway target) {
        target.addAddress(
                new Address(1L, "C001", "5 Queen St", "CBD", "Auckland", "1010", "NZ", true));
        target.addPhone(new Phone(1L, "C001", "0211234567", true, true));
        target.addEmployer(new Employer(1L, "C001", "Acme Ltd", "Engineer", 90000, "Full-time"));
    }

    // A finished wizard: a 12 000 table loan over 12 months
    private LoanDraft draft() {
        LoanDraft draft = new LoanDraft();
        draft.primaryBorrower = new LoanDraft.Borrower("C001", "Jane Smith");
        draft.details.put("principal", "12000");
        draft.details.put("interestRate", "12");
        draft.details.put("loanType", "Table Loan");
        draft.details.put("status", "New");
        draft.details.put("paymentFrequency", "Monthly");
        draft.details.put("rateFrequency", "Monthly");
        draft.details.put("term", "12");
        draft.startDate = LocalDate.of(2026, 1, 1);
        draft.renewDate = LocalDate.of(2028, 1, 1);
        return draft;
    }

    private static LoanDraft.Borrower borrower(String customerId) {
        return new LoanDraft.Borrower(customerId, "Customer " + customerId);
    }

    private static void assertNothingWasCreated(FakeMainframeGateway target) {
        assertTrue(target.findLoansByCustomer("C001").isEmpty());
    }

    // --- Saving through the mainframe ---

    @Test
    void savesTheLoanThroughTheMainframe() {
        addEligibleCustomer(gateway);

        SavedLoan saved = service.submit(draft());

        Loan stored = gateway.getLoan(saved.loanId());
        assertEquals("C001", stored.getCustomerId());
        assertEquals(Loan.Status.NEW, stored.getStatus());
        assertEquals(12000, stored.getPrincipal());
        assertEquals(12, saved.schedule().size());
        assertEquals(12, gateway.getRepaymentSchedule(saved.loanId()).size());
    }

    // The wizard has no payment amount, so the mainframe gets the first instalment
    @Test
    void givesTheMainframeTheFirstInstalmentAsThePaymentAmount() {
        addEligibleCustomer(gateway);

        SavedLoan saved = service.submit(draft());

        assertEquals(1066.19, gateway.getLoan(saved.loanId()).getPaymentAmount(), 0.001);
    }

    // A "restart" is a brand new service reading from the same mainframe
    @Test
    void theLoanCanBeFoundAgainAfterARestart() {
        addEligibleCustomer(gateway);
        SavedLoan saved = service.submit(draft());

        LoanService afterRestart = new LoanService(gateway);

        Set<String> found = afterRestart.findByCustomer("C001").stream()
                .map(Loan::getLoanId).collect(Collectors.toSet());
        assertEquals(Set.of(saved.loanId()), found);
        assertEquals(12, afterRestart.getFullDetails(saved.loanId()).getSchedule().size());
    }

    @Test
    void addsTheCoBorrowersToTheLoan() {
        addEligibleCustomer(gateway);
        LoanDraft draft = draft();
        draft.coborrowers.add(borrower("C002"));
        draft.coborrowers.add(borrower("C003"));

        SavedLoan saved = service.submit(draft);

        Set<String> coBorrowers = gateway.getCoBorrowers(saved.loanId()).stream()
                .map(CoBorrower::getCustomerId).collect(Collectors.toSet());
        assertEquals(Set.of("C002", "C003"), coBorrowers);
    }

    // --- The wizard's own rules, with their original messages ---

    @Test
    void rejectsMoreThanThreeCoBorrowers() {
        addEligibleCustomer(gateway);
        LoanDraft draft = draft();
        for (String id : List.of("C002", "C003", "C004", "C005")) {
            draft.coborrowers.add(borrower(id));
        }

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.submit(draft));

        assertEquals("A loan can have at most three co-borrowers.", exception.getMessage());
        assertNothingWasCreated(gateway);
    }

    @Test
    void rejectsALoanThatDoesNotStartAsNew() {
        addEligibleCustomer(gateway);
        LoanDraft draft = draft();
        draft.details.put("status", "Pending");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.submit(draft));

        assertEquals("A new loan must start with status New.", exception.getMessage());
        assertNothingWasCreated(gateway);
    }

    @Test
    void rejectsAMissingPrimaryBorrower() {
        LoanDraft draft = draft();
        draft.primaryBorrower = null;

        assertThrows(IllegalArgumentException.class, () -> service.submit(draft));
        assertNothingWasCreated(gateway);
    }

    @Test
    void rejectsUnusableNumbersLikeThePreviewDoes() {
        addEligibleCustomer(gateway);
        LoanDraft draft = draft();
        draft.details.put("principal", "lots");

        assertThrows(IllegalArgumentException.class, () -> service.submit(draft));
        assertNothingWasCreated(gateway);
    }

    @Test
    void rejectsAMissingRenewalDate() {
        addEligibleCustomer(gateway);
        LoanDraft draft = draft();
        draft.renewDate = null;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.submit(draft));

        assertEquals(LoanSubmissionService.RENEWAL_DATE_MESSAGE, exception.getMessage());
        assertNothingWasCreated(gateway);
    }

    @Test
    void rejectsThePrimaryBorrowerAsACoBorrower() {
        addEligibleCustomer(gateway);
        LoanDraft draft = draft();
        draft.coborrowers.add(borrower("C001"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.submit(draft));

        assertEquals(LoanSubmissionService.PRIMARY_AS_COBORROWER_MESSAGE, exception.getMessage());
        assertNothingWasCreated(gateway);
    }

    @Test
    void rejectsTheSameCoBorrowerTwice() {
        addEligibleCustomer(gateway);
        LoanDraft draft = draft();
        draft.coborrowers.add(borrower("C002"));
        draft.coborrowers.add(borrower("C002"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.submit(draft));

        assertEquals(LoanSubmissionService.DUPLICATE_COBORROWER_MESSAGE, exception.getMessage());
        assertNothingWasCreated(gateway);
    }

    // --- Failures: a clear error, and no loan is created ---

    // The real prerequisite checks now run, instead of the stub's fixtures
    @Test
    void blocksACustomerWhoFailsAPrerequisite() {
        gateway.addAddress(
                new Address(1L, "C001", "5 Queen St", "CBD", "Auckland", "1010", "NZ", false));
        gateway.addPhone(new Phone(1L, "C001", "0211234567", true, true));
        gateway.addEmployer(new Employer(1L, "C001", "Acme Ltd", "Engineer", 90000, "Full-time"));

        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> service.submit(draft()));

        assertEquals(LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE, exception.getMessage());
        assertNothingWasCreated(gateway);
    }

    @Test
    void reportsAnUnavailableMainframeAndCreatesNothing() {
        addEligibleCustomer(gateway);
        gateway.simulateUnavailable(true);

        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.submit(draft()));

        assertEquals(MainframeException.Type.UNAVAILABLE, exception.getType());
        gateway.simulateUnavailable(false);
        assertNothingWasCreated(gateway);
    }

    @Test
    void reportsABusinessErrorFromTheMainframeAndCreatesNothing() {
        addEligibleCustomer(gateway);
        gateway.failNextCallWith(3, "The mainframe rejected the request");

        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.submit(draft()));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
        assertNothingWasCreated(gateway);
    }

    // --- A co-borrower fails after the loan exists ---

    // The mainframe can't delete a loan, so a loan left without its co-borrowers is cancelled
    @Test
    void cancelsTheLoanWhenACoBorrowerCannotBeAdded() {
        FakeMainframeGateway failing = new FakeMainframeGateway() {
            @Override
            public CoBorrower addCoBorrower(CoBorrower coBorrower) {
                throw new MainframeException(MainframeException.Type.UNAVAILABLE, 10, "down");
            }
        };
        addEligibleCustomer(failing);
        LoanDraft draft = draft();
        draft.coborrowers.add(borrower("C002"));

        LoanSubmissionException exception = assertThrows(LoanSubmissionException.class,
                () -> new LoanSubmissionService(failing).submit(draft));

        assertTrue(exception.getMessage().contains("has been cancelled"));
        assertTrue(exception.getCause() instanceof MainframeException);
        Loan left = failing.findLoansByCustomer("C001").get(0);
        assertEquals(Loan.Status.CANCELLED, left.getStatus());
    }

    @Test
    void saysSoWhenTheLoanCannotBeCancelledEither() {
        FakeMainframeGateway failing = new FakeMainframeGateway() {
            @Override
            public CoBorrower addCoBorrower(CoBorrower coBorrower) {
                throw new MainframeException(MainframeException.Type.UNAVAILABLE, 10, "down");
            }

            @Override
            public Loan updateLoanStatus(String loanId, Loan.Status newStatus) {
                throw new MainframeException(MainframeException.Type.UNAVAILABLE, 10, "down");
            }
        };
        addEligibleCustomer(failing);
        LoanDraft draft = draft();
        draft.coborrowers.add(borrower("C002"));

        LoanSubmissionException exception = assertThrows(LoanSubmissionException.class,
                () -> new LoanSubmissionService(failing).submit(draft));

        assertTrue(exception.getMessage().contains("could not be cancelled"));
        assertEquals(Loan.Status.NEW, failing.findLoansByCustomer("C001").get(0).getStatus());
    }

        // Ported from SessionLoanStoreTest: the saved loan keeps its own copy of the draft
    @Test
    void theSavedLoanKeepsItsOwnCopyOfTheDraft() {
        addEligibleCustomer(gateway);
        LoanDraft draft = draft();

        SavedLoan saved = service.submit(draft);
        draft.details.put("principal", "1");
        saved.draft().details.clear();

        assertEquals("12000", saved.draft().details.get("principal"));
    }

    @Test
    void theScheduleBelongsToTheNewLoanAndIsPaidOffAtTheEnd() {
        addEligibleCustomer(gateway);

        SavedLoan saved = service.submit(draft());

        assertEquals(saved.loanId(), saved.schedule().get(0).getLoanId());
        assertEquals(0, saved.schedule().get(11).getRemainingBalance());
    }

    @Test
    void eachLoanGetsItsOwnId() {
        addEligibleCustomer(gateway);

        SavedLoan first = service.submit(draft());
        SavedLoan second = service.submit(draft());

        assertNotEquals(first.loanId(), second.loanId());
    }
}