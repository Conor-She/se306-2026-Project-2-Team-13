package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeException;
import com.lavs.model.Loan;
import com.lavs.model.Payment;
import com.lavs.model.Repayment;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private static final LocalDate DUE = LocalDate.of(2026, 2, 1);
    private static final LocalDate BEFORE_DUE = LocalDate.of(2026, 1, 20);
    private static final LocalDate AFTER_DUE = LocalDate.of(2026, 2, 10);

    private FakeMainframeGateway gateway;
    private PaymentService service;
    private String loanId;

    // Every test starts with a loan whose schedule is two repayments of 100.00,
    // R1 due on DUE and R2 a month later
    @BeforeEach
    void setUp() {
        gateway = new FakeMainframeGateway();
        service = new PaymentService(gateway);
        loanId = createLoanWithSchedule("R");
    }

    // --- Helpers ---

    private Repayment repayment(String id, String forLoanId, LocalDate due, double amountDue) {
        return new Repayment(id, forLoanId, due, amountDue, amountDue, 0.0, 0.0);
    }

    // Creates a loan on the fake mainframe with two repayments, whose IDs are
    // the prefix followed by 1 and 2 (so a second loan can use different ones)
    private String createLoanWithSchedule(String prefix) {
        String id = gateway.createLoan(new Loan(null, "C001", Loan.Status.NEW, 200, 0.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                100, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 2)).getLoanId();
        gateway.saveRepaymentSchedule(id, List.of(
                repayment(prefix + "1", id, DUE, 100.00),
                repayment(prefix + "2", id, DUE.plusMonths(1), 100.00)));
        return id;
    }

    // A payment on the main loan, with no ID yet as the MMM would enter it
    private Payment payment(String repaymentId, LocalDate date, double amount) {
        return new Payment(null, loanId, repaymentId, date, amount);
    }

    // The status of R1 (100.00 due on DUE) given these payments, judged on a date
    private RepaymentStatus status(List<Payment> payments, LocalDate asOf) {
        return service.statusOf(repayment("R1", loanId, DUE, 100.00), payments, asOf);
    }

    // --- Recording a payment: linked to a repayment, or not ---

    @Test
    void recordsAPaymentLinkedToARepayment() {
        Payment added = service.addPayment(payment("R1", DUE, 100.00));

        assertNotNull(added.getPaymentId());
        assertEquals("R1", added.getRepaymentId());
        assertEquals(1, service.getPayments(loanId).size());
    }

    @Test
    void recordsAPaymentWithoutARepayment() {
        Payment added = service.addPayment(payment(null, DUE, 250.00));

        assertNotNull(added.getPaymentId());
        assertNull(added.getRepaymentId());
        assertEquals(1, service.getPayments(loanId).size());
    }

    // --- Recording a payment: what is refused ---

    @Test
    void rejectsAPaymentLinkedToAnUnknownRepayment() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.addPayment(payment("R9", DUE, 100.00)));

        assertEquals(PaymentService.REPAYMENT_NOT_ON_LOAN_MESSAGE, exception.getMessage());
        assertTrue(service.getPayments(loanId).isEmpty());
    }

    // X1 is a real repayment, but it belongs to a different loan
    @Test
    void rejectsAPaymentLinkedToAnotherLoansRepayment() {
        createLoanWithSchedule("X");

        assertThrows(IllegalArgumentException.class,
                () -> service.addPayment(payment("X1", DUE, 100.00)));
    }

    @Test
    void rejectsAPaymentForAnUnknownLoan() {
        Payment forUnknownLoan = new Payment(null, "MISSING", null, DUE, 100.00);

        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.addPayment(forUnknownLoan));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
    }

    @Test
    void rejectsAPaymentWithoutADate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.addPayment(payment("R1", null, 100.00)));

        assertEquals(PaymentService.DATE_REQUIRED_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsANullPayment() {
        assertThrows(IllegalArgumentException.class, () -> service.addPayment(null));
    }

    // --- Editing a payment ---

    @Test
    void updatesAnExistingPayment() {
        Payment added = service.addPayment(payment("R1", DUE, 100.00));
        Payment edited = new Payment(added.getPaymentId(), loanId, "R1", DUE, 80.00);

        Payment result = service.updatePayment(edited);

        assertEquals(80.00, result.getAmountPaid());
        assertEquals(80.00, service.getPayments(loanId).get(0).getAmountPaid());
    }

    @Test
    void updateRequiresAnExistingPaymentId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updatePayment(payment("R1", DUE, 100.00)));

        assertEquals(PaymentService.EXISTING_PAYMENT_REQUIRED_MESSAGE, exception.getMessage());
    }

    // The payment is on the first loan, so editing it onto another loan is refused
    @Test
    void updateCannotMoveAPaymentToAnotherLoan() {
        String otherLoanId = createLoanWithSchedule("X");
        Payment added = service.addPayment(payment("R1", DUE, 100.00));
        Payment moved = new Payment(added.getPaymentId(), otherLoanId, null, DUE, 100.00);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updatePayment(moved));

        assertEquals(PaymentService.PAYMENT_NOT_ON_LOAN_MESSAGE, exception.getMessage());
        assertEquals(1, service.getPayments(loanId).size());
    }

    // --- Deleting and listing payments ---

    @Test
    void deletesAPayment() {
        Payment added = service.addPayment(payment("R1", DUE, 100.00));

        service.deletePayment(added.getPaymentId());

        assertTrue(service.getPayments(loanId).isEmpty());
    }

    @Test
    void deletingAnUnknownPaymentIsABusinessError() {
        MainframeException exception = assertThrows(MainframeException.class,
                () -> service.deletePayment("MISSING"));

        assertEquals(MainframeException.Type.BUSINESS_ERROR, exception.getType());
    }

    @Test
    void deleteRejectsMissingId() {
        assertThrows(IllegalArgumentException.class, () -> service.deletePayment(null));
        assertThrows(IllegalArgumentException.class, () -> service.deletePayment(" "));
    }

    @Test
    void listsOnlyTheRequestedLoansPayments() {
        String otherLoanId = createLoanWithSchedule("X");
        service.addPayment(payment("R1", DUE, 50.00));
        service.addPayment(payment(null, DUE, 25.00));
        service.addPayment(new Payment(null, otherLoanId, "X1", DUE, 10.00));

        assertEquals(2, service.getPayments(loanId).size());
        assertEquals(1, service.getPayments(otherLoanId).size());
    }

    @Test
    void listingRejectsMissingIdAndUnknownLoan() {
        assertThrows(IllegalArgumentException.class, () -> service.getPayments(null));
        assertThrows(MainframeException.class, () -> service.getPayments("MISSING"));
    }

    // --- Status: full payment, partial payment, no payment ---

    @Test
    void payingExactlyTheAmountDueIsOnTime() {
        assertEquals(RepaymentStatus.ON_TIME,
                status(List.of(payment("R1", DUE, 100.00)), AFTER_DUE));
    }

    @Test
    void payingLessThanTheAmountDueIsPartial() {
        assertEquals(RepaymentStatus.PARTIAL,
                status(List.of(payment("R1", DUE, 40.00)), AFTER_DUE));
    }

    @Test
    void payingNothingOnceTheDueDateHasPassedIsMissed() {
        assertEquals(RepaymentStatus.MISSED, status(List.of(), AFTER_DUE));
    }

    // Paying more than is due still meets the repayment
    @Test
    void payingMoreThanTheAmountDueIsStillOnTime() {
        assertEquals(RepaymentStatus.ON_TIME,
                status(List.of(payment("R1", DUE, 150.00)), AFTER_DUE));
    }

    // --- Status: repayments that aren't due yet ---

    @Test
    void nothingPaidBeforeTheDueDateIsUpcoming() {
        assertEquals(RepaymentStatus.UPCOMING, status(List.of(), BEFORE_DUE));
    }

    // On the due date itself the repayment hasn't been missed yet
    @Test
    void nothingPaidOnTheDueDateIsStillUpcoming() {
        assertEquals(RepaymentStatus.UPCOMING, status(List.of(), DUE));
    }

    // Paying early counts: only a repayment with nothing paid is upcoming
    @Test
    void payingBeforeTheDueDateStillCounts() {
        assertEquals(RepaymentStatus.ON_TIME,
                status(List.of(payment("R1", BEFORE_DUE, 100.00)), BEFORE_DUE));
        assertEquals(RepaymentStatus.PARTIAL,
                status(List.of(payment("R1", BEFORE_DUE, 40.00)), BEFORE_DUE));
    }

    // --- Status: payments split over time ---

    // Amounts are compared to the cent: 0.10 + 0.20 is 0.30000000000000004 as
    // plain doubles, which must still count as exactly 0.30
    @Test
    void splitPaymentsAreAddedUpExactly() {
        Repayment small = repayment("R1", loanId, DUE, 0.30);

        RepaymentStatus result = service.statusOf(small,
                List.of(payment("R1", DUE, 0.10), payment("R1", DUE, 0.20)), AFTER_DUE);

        assertEquals(RepaymentStatus.ON_TIME, result);
    }

    @Test
    void severalPaymentsReachingTheAmountDueAreOnTime() {
        assertEquals(RepaymentStatus.ON_TIME, status(
                List.of(payment("R1", DUE, 60.00), payment("R1", DUE, 40.00)), AFTER_DUE));
    }

    @Test
    void severalPaymentsFallingShortOfTheAmountDueArePartial() {
        assertEquals(RepaymentStatus.PARTIAL, status(
                List.of(payment("R1", DUE, 30.00), payment("R1", DUE, 30.00)), AFTER_DUE));
    }

    // --- Status: what doesn't count, and edge cases ---

    // An unlinked payment, or one linked to R2, is nothing towards R1
    @Test
    void paymentsWithoutALinkOrLinkedElsewhereDoNotCount() {
        List<Payment> payments = List.of(
                payment(null, DUE, 100.00), payment("R2", DUE, 100.00));

        assertEquals(RepaymentStatus.MISSED, status(payments, AFTER_DUE));
    }

    // Only the amounts are compared, so a payment made long after the due date
    // still meets the repayment
    @Test
    void theDateOfAPaymentDoesNotChangeTheStatus() {
        LocalDate muchLater = LocalDate.of(2027, 6, 1);

        assertEquals(RepaymentStatus.ON_TIME,
                status(List.of(payment("R1", muchLater, 100.00)), AFTER_DUE));
    }

    // e.g. an interest-only loan at 0% interest owes nothing for some
    // instalments, so they are neither missed nor coming up
    @Test
    void aRepaymentWithNothingOwedIsOnTime() {
        Repayment free = repayment("R1", loanId, DUE, 0.00);

        assertEquals(RepaymentStatus.ON_TIME, service.statusOf(free, List.of(), AFTER_DUE));
        assertEquals(RepaymentStatus.ON_TIME, service.statusOf(free, List.of(), BEFORE_DUE));
    }

    @Test
    void statusOfRejectsMissingInputs() {
        Repayment due = repayment("R1", loanId, DUE, 100.00);

        assertThrows(IllegalArgumentException.class,
                () -> service.statusOf(null, List.of(), AFTER_DUE));
        assertThrows(IllegalArgumentException.class,
                () -> service.statusOf(due, null, AFTER_DUE));
        assertThrows(IllegalArgumentException.class,
                () -> service.statusOf(due, List.of(), null));
    }

    // --- Status for a whole loan ---

    // R1 is paid, and R2 isn't due until a month later
    @Test
    void reportsEveryRepaymentInDueDateOrder() {
        service.addPayment(payment("R1", DUE, 100.00));

        Map<String, RepaymentStatus> statuses = service.statusesFor(loanId, DUE.plusDays(14));

        assertEquals(List.of("R1", "R2"), List.copyOf(statuses.keySet()));
        assertEquals(RepaymentStatus.ON_TIME, statuses.get("R1"));
        assertEquals(RepaymentStatus.UPCOMING, statuses.get("R2"));
    }

    // Nothing is paid: R1 is overdue so it is missed, R2 isn't due yet
    @Test
    void separatesMissedFromComingUp() {
        Map<String, RepaymentStatus> statuses = service.statusesFor(loanId, DUE.plusDays(14));

        assertEquals(RepaymentStatus.MISSED, statuses.get("R1"));
        assertEquals(RepaymentStatus.UPCOMING, statuses.get("R2"));
    }

    @Test
    void statusesForAnUnknownLoanIsABusinessError() {
        assertThrows(MainframeException.class, () -> service.statusesFor("MISSING", DUE));
    }

    @Test
    void statusesForRejectsMissingArguments() {
        assertThrows(IllegalArgumentException.class, () -> service.statusesFor(null, DUE));
        assertThrows(IllegalArgumentException.class, () -> service.statusesFor(loanId, null));
    }
}