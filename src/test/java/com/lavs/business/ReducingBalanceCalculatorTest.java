package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;

class ReducingBalanceCalculatorTest {
    private static final double MONEY_TOLERANCE = 0.001;

    private final RepaymentCalculator calculator = new ReducingBalanceCalculator();

    @Test
    void createsKnownMonthlySchedule() {
        Loan loan = loan("RB001", 12_000.00, 12.0, false);

        List<Repayment> schedule = calculator.calculateSchedule(loan);

        assertEquals(12, schedule.size());
        assertRepayment(
                schedule.get(0),
                LocalDate.of(2026, 2, 1),
                1_120.00,
                1_000.00,
                120.00,
                11_000.00);
        assertRepayment(
                schedule.get(1),
                LocalDate.of(2026, 3, 1),
                1_110.00,
                1_000.00,
                110.00,
                10_000.00);
        assertRepayment(
                schedule.get(11),
                LocalDate.of(2027, 1, 1),
                1_010.00,
                1_000.00,
                10.00,
                0.00);
    }

    @Test
    void adjustsFinalPrincipalToClearRounding() {
        Loan loan = loan("RB002", 1_000.00, 0.0, false);

        List<Repayment> schedule = calculator.calculateSchedule(loan);

        assertAll(
                () -> assertEquals(83.33, schedule.get(0).getPrincipalComponent()),
                () -> assertEquals(83.37, schedule.get(11).getPrincipalComponent()),
                () -> assertEquals(0.00, schedule.get(11).getRemainingBalance()));
    }

    @Test
    void acceptsFloatingAndFixedReducingBalanceLoans() {
        List<Repayment> fixedSchedule = calculator.calculateSchedule(
                loan("RB003", 12_000.00, 12.0, false));
        List<Repayment> floatingSchedule = calculator.calculateSchedule(
                loan("RB004", 12_000.00, 12.0, true));

        assertAll(
                () -> assertEquals(fixedSchedule.size(), floatingSchedule.size()),
                () -> assertEquals(
                        fixedSchedule.get(0).getAmountDue(),
                        floatingSchedule.get(0).getAmountDue()),
                () -> assertEquals(
                        fixedSchedule.get(11).getRemainingBalance(),
                        floatingSchedule.get(11).getRemainingBalance()));
    }

    @Test
    void rejectsOtherLoanTypes() {
        Loan tableLoan = new Loan(
                "RB005", "C001", Loan.Status.NEW, 12_000.00, 12.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), null, 0.0,
                Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12);

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateSchedule(tableLoan));
    }

    private Loan loan(String loanId, double principal, double interestRate, boolean isFloating) {
        return new Loan(
                loanId,
                "C001",
                Loan.Status.NEW,
                principal,
                interestRate,
                Loan.Type.REDUCING_BALANCE,
                isFloating,
                LocalDate.of(2026, 1, 1),
                null,
                0.0,
                Loan.PaymentFrequency.MONTHLY,
                Loan.RateFrequency.MONTHLY,
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
