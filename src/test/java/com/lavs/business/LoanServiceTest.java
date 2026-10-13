package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeException;
import com.lavs.model.Address;
import com.lavs.model.Employer;
import com.lavs.model.Guarantor;
import com.lavs.model.Loan;
import com.lavs.model.Phone;
import com.lavs.model.Repayment;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class LoanServiceTest {

    private static final double MONEY_TOLERANCE = 0.001;

    private final FakeMainframeGateway gateway = new FakeMainframeGateway();
    private final LoanService service = new LoanService(gateway);

    // --- Helpers ---

    private void addAddress(boolean isMailing) {
        gateway.addAddress(
                new Address(1L, "C001", "5 Queen St", "CBD", "Auckland", "1010", "NZ", isMailing));
    }

    private void addPhone() {
        gateway.addPhone(new Phone(1L, "C001", "0211234567", true, true));
    }

    private void addEmployer() {
        gateway.addEmployer(new Employer(1L, "C001", "Acme Ltd", "Engineer", 90000, "Full-time"));
    }

    // Everything loan creation needs: a mailing address, a phone and a job
    // (so no guarantor is required)
    private void givenEligibleCustomer() {
        addAddress(true);
        addPhone();
        addEmployer();
    }

    private Loan loan(String loanId, Loan.Status status, Loan.Type type, double principal,
            Loan.PaymentFrequency paymentFrequency) {
        return new Loan(loanId, "C001", status, principal, 12.0, type, false,
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), 1_000, paymentFrequency,
                Loan.RateFrequency.MONTHLY, 12);
    }

    // A valid new table loan, as the MMM would enter it (no ID yet)
    private Loan newLoan() {
        return loan(null, Loan.Status.NEW, Loan.Type.TABLE_LOAN, 12_000,
                Loan.PaymentFrequency.MONTHLY);
    }

    private Repayment firstRepayment(Loan.Type type) {
        Loan loan = loan(null, Loan.Status.NEW, type, 12_000, Loan.PaymentFrequency.MONTHLY);
        return service.create(loan).getSchedule().get(0);
    }

    private void assertNoLoanWasCreated() {
        assertTrue(gateway.findLoansByCustomer("C001").isEmpty());
    }

    // --- Valid creation ---

    // The loan comes back with its schedule, and both are saved
    @Test
    void createsLoanWithItsScheduleAndSavesBoth() {
        givenEligibleCustomer();

        LoanWithSchedule result = service.create(newLoan());
        String loanId = result.getLoan().getLoanId();

        assertNotNull(loanId);
        assertEquals(Loan.Status.NEW, result.getLoan().getStatus());
        assertEquals(12, result.getSchedule().size());
        assertEquals(12, gateway.getRepaymentSchedule(loanId).size());
        assertEquals(loanId, gateway.getLoan(loanId).getLoanId());
    }

    // Each loan type must be scheduled by its own calculator
    @Test
    void usesTheCalculatorForTheLoansType() {
        givenEligibleCustomer();

        assertEquals(946.19, firstRepayment(Loan.Type.TABLE_LOAN).getPrincipalComponent(),
                MONEY_TOLERANCE);
        assertEquals(1_000.00, firstRepayment(Loan.Type.REDUCING_BALANCE).getPrincipalComponent(),
                MONEY_TOLERANCE);
        assertEquals(0.00, firstRepayment(Loan.Type.INTEREST_ONLY).getPrincipalComponent(),
                MONEY_TOLERANCE);
    }

    // An unemployed customer is fine once they have a guarantor
    @Test
    void createsLoanForUnemployedCustomerWithGuarantor() {
        addAddress(true);
        addPhone();
        gateway.addGuarantor(new Guarantor("G001", "C001", "C002", "Parent"));

        LoanWithSchedule result = service.create(newLoan());

        assertNotNull(result.getLoan().getLoanId());
    }

    // --- Blocked creation: each prerequisite failing ---

    @Test
    void blocksCreationWithoutMailingAddress() {
        addAddress(false);
        addPhone();
        addEmployer();

        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> service.create(newLoan()));

        assertEquals(LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE, exception.getMessage());
        assertNoLoanWasCreated();
    }

    @Test
    void blocksCreationWithoutPhoneOrEmail() {
        addAddress(true);
        addEmployer();

        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> service.create(newLoan()));

        assertEquals(LoanPrerequisiteChecker.CONTACT_MESSAGE, exception.getMessage());
        assertNoLoanWasCreated();
    }

    @Test
    void blocksCreationForUnemployedCustomerWithoutGuarantor() {
        addAddress(true);
        addPhone();

        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> service.create(newLoan()));

        assertEquals(GuarantorRequirementValidator.GUARANTOR_REQUIRED_MESSAGE,
                exception.getMessage());
        assertNoLoanWasCreated();
    }

    // --- Other reasons creation is refused ---

    @Test
    void rejectsNullLoan() {
        assertThrows(IllegalArgumentException.class, () -> service.create(null));
    }

    // A new loan always starts as New, so anything else is a mistake
    @Test
    void rejectsLoanThatIsNotNew() {
        givenEligibleCustomer();
        Loan pending = loan(null, Loan.Status.PENDING, Loan.Type.TABLE_LOAN, 12_000,
                Loan.PaymentFrequency.MONTHLY);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.create(pending));

        assertEquals(LoanService.NEW_LOAN_MESSAGE, exception.getMessage());
        assertNoLoanWasCreated();
    }

    // Terms the calculators can't schedule must be refused before anything
    // is created on the mainframe
    @Test
    void doesNotCreateALoanWhoseScheduleCannotBeCalculated() {
        givenEligibleCustomer();
        Loan onRenewal = loan(null, Loan.Status.NEW, Loan.Type.TABLE_LOAN, 12_000,
                Loan.PaymentFrequency.ON_RENEWAL);

        assertThrows(IllegalArgumentException.class, () -> service.create(onRenewal));

        assertNoLoanWasCreated();
    }

    // Without a type there is no way to choose a calculator
    @Test
    void doesNotCreateALoanWithoutAType() {
        givenEligibleCustomer();
        Loan noType = loan(null, Loan.Status.NEW, null, 12_000, Loan.PaymentFrequency.MONTHLY);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.create(noType));

        assertEquals(LoanService.LOAN_TYPE_REQUIRED_MESSAGE, exception.getMessage());
        assertNoLoanWasCreated();
    }

    // Mainframe failures pass through unchanged for the UI to handle
    @Test
    void passesMainframeUnavailableThrough() {
        gateway.simulateUnavailable(true);

        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.create(newLoan()));

        assertEquals(MainframeException.Type.UNAVAILABLE, exception.getType());
    }

    // --- Editing a loan ---

    // Changing the terms must regenerate the schedule and save the new one
    @Test
    void updateRegeneratesAndSavesTheSchedule() {
        givenEligibleCustomer();
        LoanWithSchedule created = service.create(newLoan());
        String loanId = created.getLoan().getLoanId();
        Loan edited = loan(loanId, Loan.Status.NEW, Loan.Type.TABLE_LOAN, 24_000,
                Loan.PaymentFrequency.MONTHLY);

        LoanWithSchedule result = service.update(edited);

        assertEquals(24_000, result.getLoan().getPrincipal());
        assertNotEquals(created.getSchedule().get(0).getAmountDue(),
                result.getSchedule().get(0).getAmountDue());
        assertEquals(result.getSchedule().get(0).getAmountDue(),
                gateway.getRepaymentSchedule(loanId).get(0).getAmountDue());
    }

    // Status only changes through changeStatus
    @Test
    void updateCannotChangeTheStatus() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();
        Loan pending = loan(loanId, Loan.Status.PENDING, Loan.Type.TABLE_LOAN, 12_000,
                Loan.PaymentFrequency.MONTHLY);

        InvalidStatusTransitionException exception = assertThrows(
                InvalidStatusTransitionException.class, () -> service.update(pending));

        assertEquals(LoanService.STATUS_VIA_CHANGE_STATUS_MESSAGE, exception.getMessage());
    }

    @Test
    void updateRequiresAnExistingLoanId() {
        assertThrows(IllegalArgumentException.class, () -> service.update(null));
        assertThrows(IllegalArgumentException.class, () -> service.update(newLoan()));
    }

    @Test
    void updateOfUnknownLoanIsABusinessError() {
        Loan unknown = loan("MISSING", Loan.Status.NEW, Loan.Type.TABLE_LOAN, 12_000,
                Loan.PaymentFrequency.MONTHLY);

        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.update(unknown));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
    }

    // New terms that can't be scheduled must leave the stored loan untouched
    @Test
    void updateWithUnschedulableTermsChangesNothing() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();
        Loan edited = loan(loanId, Loan.Status.NEW, Loan.Type.TABLE_LOAN, 24_000,
                Loan.PaymentFrequency.ON_RENEWAL);

        assertThrows(IllegalArgumentException.class, () -> service.update(edited));

        assertEquals(12_000, gateway.getLoan(loanId).getPrincipal());
    }

    // --- Status changes ---

    @Test
    void changesStatusFromNewToPending() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();

        Loan result = service.changeStatus(loanId, Loan.Status.PENDING);

        assertEquals(Loan.Status.PENDING, result.getStatus());
        assertEquals(Loan.Status.PENDING, gateway.getLoan(loanId).getStatus());
    }

    // Every pair of statuses except New to Pending must be refused, and
    // must leave the loan's status as it was
    @Test
    void rejectsEveryOtherStatusChange() {
        givenEligibleCustomer();

        for (Loan.Status from : Loan.Status.values()) {
            for (Loan.Status to : Loan.Status.values()) {
                if (from == Loan.Status.NEW && to == Loan.Status.PENDING) {
                    continue;
                }
                String loanId = service.create(newLoan()).getLoan().getLoanId();
                gateway.updateLoanStatus(loanId, from);

                assertThrows(InvalidStatusTransitionException.class,
                        () -> service.changeStatus(loanId, to));
                assertEquals(from, gateway.getLoan(loanId).getStatus());
            }
        }
    }

    @Test
    void rejectsNullNewStatus() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();

        assertThrows(InvalidStatusTransitionException.class,
                () -> service.changeStatus(loanId, null));
    }

    @Test
    void statusChangeOfUnknownLoanIsABusinessError() {
        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.changeStatus("MISSING", Loan.Status.PENDING));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
    }

    // Editing can't move a loan to a different customer, which would skip
    // that customer's prerequisite checks
    @Test
    void updateCannotChangeTheCustomer() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();
        Loan reassigned = new Loan(loanId, "C999", Loan.Status.NEW, 12_000, 12.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                1_000, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.update(reassigned));

        assertEquals(LoanService.CUSTOMER_CHANGE_MESSAGE, exception.getMessage());
        assertEquals("C001", gateway.getLoan(loanId).getCustomerId());
    }

    // The mainframe doesn't store the term, so the MMM must enter one: a
    // loan without it can't be scheduled and must not be created
    @Test
    void doesNotCreateALoanWithoutATerm() {
        givenEligibleCustomer();
        Loan noTerm = new Loan(null, "C001", Loan.Status.NEW, 12_000, 12.0, Loan.Type.TABLE_LOAN,
                false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), 1_000,
                Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.create(noTerm));

        assertEquals(LoanService.TERM_REQUIRED_MESSAGE, exception.getMessage());
        assertNoLoanWasCreated();
    }

    // The same applies when editing: a loan re-fetched from the mainframe has
    // no term, so an edit has to supply one again
    @Test
    void doesNotUpdateALoanWithoutATerm() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();
        Loan noTerm = new Loan(loanId, "C001", Loan.Status.NEW, 24_000, 12.0, Loan.Type.TABLE_LOAN,
                false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), 1_000,
                Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.update(noTerm));

        assertEquals(LoanService.TERM_REQUIRED_MESSAGE, exception.getMessage());

        assertEquals(12_000, gateway.getLoan(loanId).getPrincipal());
    }

    // --- Finding a customer's loans: none, one, and several ---

    @Test
    void findsNoLoansForACustomerWithNone() {
        assertTrue(service.findByCustomer("C001").isEmpty());
    }

    @Test
    void findsTheOneLoanOfACustomerWithOne() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();

        List<Loan> loans = service.findByCustomer("C001");

        assertEquals(1, loans.size());
        assertEquals(loanId, loans.get(0).getLoanId());
    }

    // Compared as sets, because the order loans come back in isn't guaranteed
    @Test
    void findsEveryLoanOfACustomerWithSeveral() {
        givenEligibleCustomer();
        String first = service.create(newLoan()).getLoan().getLoanId();
        String second = service.create(newLoan()).getLoan().getLoanId();
        String third = service.create(newLoan()).getLoan().getLoanId();

        Set<String> found = service.findByCustomer("C001").stream()
                .map(Loan::getLoanId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(first, second, third), found);
    }

    // Another customer's loan must not show up
    @Test
    void findsOnlyTheRequestedCustomersLoans() {
        givenEligibleCustomer();
        String mine = service.create(newLoan()).getLoan().getLoanId();
        gateway.createLoan(new Loan(null, "C002", Loan.Status.NEW, 5_000, 12.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                500, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12));

        List<Loan> loans = service.findByCustomer("C001");

        assertEquals(1, loans.size());
        assertEquals(mine, loans.get(0).getLoanId());
    }

    @Test
    void findByCustomerRejectsMissingId() {
        assertThrows(IllegalArgumentException.class, () -> service.findByCustomer(null));
        assertThrows(IllegalArgumentException.class, () -> service.findByCustomer(" "));
    }

    // --- Full loan details ---

    // The loan, every row of its schedule, and the summary. The expected totals
    // come from the schedule's rows: 11 payments of 1,066.19 and a final one of
    // 1,066.14 make 12,794.23, which is 12,000 borrowed plus 794.23 interest.
    @Test
    void returnsTheLoanItsFullScheduleAndSummary() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();

        LoanWithSchedule details = service.getFullDetails(loanId);

        assertEquals(loanId, details.getLoan().getLoanId());
        assertEquals(12, details.getSchedule().size());
        assertEquals(794.23, details.getSummary().getTotalInterest(), MONEY_TOLERANCE);
        assertEquals(12_794.23, details.getSummary().getTotalRepayable(), MONEY_TOLERANCE);
    }

    // Whatever the schedule is, what is repaid must be the amount borrowed plus
    // the interest
    @Test
    void summaryTotalsAreConsistentWithTheLoan() {
        givenEligibleCustomer();
        String loanId = service.create(newLoan()).getLoan().getLoanId();

        LoanWithSchedule details = service.getFullDetails(loanId);

        assertEquals(12_000 + details.getSummary().getTotalInterest(),
                details.getSummary().getTotalRepayable(), MONEY_TOLERANCE);
    }

    // The summary is shown as soon as a loan is created, not only when it is
    // fetched later
    @Test
    void createdLoanComesWithItsSummary() {
        givenEligibleCustomer();

        LoanWithSchedule created = service.create(newLoan());

        assertEquals(794.23, created.getSummary().getTotalInterest(), MONEY_TOLERANCE);
        assertEquals(12_794.23, created.getSummary().getTotalRepayable(), MONEY_TOLERANCE);
    }

    // The schedule reads in due-date order, even if it comes back reversed
    @Test
    void returnsTheScheduleInDueDateOrder() {
        givenEligibleCustomer();
        LoanWithSchedule created = service.create(newLoan());
        String loanId = created.getLoan().getLoanId();
        List<Repayment> reversed = new ArrayList<>(created.getSchedule());
        Collections.reverse(reversed);
        gateway.saveRepaymentSchedule(loanId, reversed);

        List<Repayment> schedule = service.getFullDetails(loanId).getSchedule();

        assertEquals(
                created.getSchedule().stream().map(Repayment::getDueDate).toList(),
                schedule.stream().map(Repayment::getDueDate).toList());
    }

    // A loan with no saved schedule comes back with an empty schedule and zero
    // totals, not an error
    @Test
    void returnsAnEmptyScheduleAndZeroTotalsWhenNoneWasSaved() {
        String loanId = gateway.createLoan(newLoan()).getLoanId();

        LoanWithSchedule details = service.getFullDetails(loanId);

        assertTrue(details.getSchedule().isEmpty());
        assertEquals(0.0, details.getSummary().getTotalInterest());
        assertEquals(0.0, details.getSummary().getTotalRepayable());
    }

    @Test
    void fullDetailsOfAnUnknownLoanIsABusinessError() {
        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.getFullDetails("MISSING"));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
    }

    @Test
    void fullDetailsRejectsMissingId() {
        assertThrows(IllegalArgumentException.class, () -> service.getFullDetails(null));
        assertThrows(IllegalArgumentException.class, () -> service.getFullDetails(" "));
    }
}