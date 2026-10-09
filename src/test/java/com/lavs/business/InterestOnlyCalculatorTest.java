package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;

class InterestOnlyCalculatorTest {
    private static final double MONEY_TOLERANCE = 0.001;

    private final RepaymentCalculator calculator = new InterestOnlyCalculator();

    @Test
    void createsKnownMonthlySchedule() {
        Loan loan = loan("IO001", 12_000.00, 12.0, false);

        List<Repayment> schedule = calculator.calculateSchedule(loan);

        assertEquals(12, schedule.size());
        assertRepayment(
                schedule.get(0),
                LocalDate.of(2026, 2, 1),
                120.00,
                0.00,
                120.00,
                12_000.00);
        assertRepayment(
                schedule.get(10),
                LocalDate.of(2026, 12, 1),
                120.00,
                0.00,
                120.00,
                12_000.00);
        assertRepayment(
                schedule.get(11),
                LocalDate.of(2027, 1, 1),
                12_120.00,
                12_000.00,
                120.00,
                0.00);
    }

    @Test
    void supportsZeroInterest() {
        Loan loan = loan("IO002", 12_000.00, 0.0, false);

        List<Repayment> schedule = calculator.calculateSchedule(loan);

        assertAll(
                () -> assertEquals(0.00, schedule.get(0).getAmountDue()),
                () -> assertEquals(12_000.00, schedule.get(0).getRemainingBalance()),
                () -> assertEquals(12_000.00, schedule.get(11).getAmountDue()),
                () -> assertEquals(0.00, schedule.get(11).getRemainingBalance()));
    }

    @Test
    void acceptsFloatingAndFixedInterestOnlyLoans() {
        List<Repayment> fixedSchedule = calculator.calculateSchedule(
                loan("IO003", 12_000.00, 12.0, false));
        List<Repayment> floatingSchedule = calculator.calculateSchedule(
                loan("IO004", 12_000.00, 12.0, true));

        assertAll(
                () -> assertEquals(fixedSchedule.size(), floatingSchedule.size()),
                () -> assertEquals(
                        fixedSchedule.get(0).getAmountDue(),
                        floatingSchedule.get(0).getAmountDue()),
                () -> assertEquals(
                        fixedSchedule.get(11).getPrincipalComponent(),
                        floatingSchedule.get(11).getPrincipalComponent()));
    }

    @Test
    void rejectsOtherLoanTypesAndUnusableRates() {
        Loan reducingLoan = new Loan(
                "IO005", "C001", Loan.Status.NEW, 12_000.00, 12.0,
                Loan.Type.REDUCING_BALANCE, false, LocalDate.of(2026, 1, 1), null, 0.0,
                Loan.Frequency.MONTHLY, Loan.Frequency.MONTHLY, 12);
        Loan unusableRateLoan = new Loan(
                "IO006", "C001", Loan.Status.NEW, 12_000.00, Double.MAX_VALUE,
                Loan.Type.INTEREST_ONLY, false, LocalDate.of(2026, 1, 1), null, 0.0,
                Loan.Frequency.MONTHLY, Loan.Frequency.WEEKLY, 12);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(reducingLoan)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(unusableRateLoan)));
    }

    private Loan loan(String loanId, double principal, double interestRate, boolean isFloating) {
        return new Loan(
                loanId,
                "C001",
                Loan.Status.NEW,
                principal,
                interestRate,
                Loan.Type.INTEREST_ONLY,
                isFloating,
                LocalDate.of(2026, 1, 1),
                null,
                0.0,
                Loan.Frequency.MONTHLY,
                Loan.Frequency.MONTHLY,
                12);
    }

    private void assertRepayment(
            Repayment repayment,
            LocalDate expectedDate,
            double expectedAmount,
            double expectedPrincipal,
            double expectedInterest,
            double expectedBalance) {
        assertAll(
                () -> assertEquals(expectedDate, repayment.getDueDate()),
                () -> assertEquals(
                        expectedAmount, repayment.getAmountDue(), MONEY_TOLERANCE),
                () -> assertEquals(
                        expectedPrincipal,
                        repayment.getPrincipalComponent(),
                        MONEY_TOLERANCE),
                () -> assertEquals(
                        expectedInterest,
                        repayment.getInterestComponent(),
                        MONEY_TOLERANCE),
                () -> assertEquals(
                        expectedBalance,
                        repayment.getRemainingBalance(),
                        MONEY_TOLERANCE));
    }
}
