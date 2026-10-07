package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;

class TableLoanCalculatorTest {
    private static final double MONEY_TOLERANCE = 0.001;

    private final RepaymentCalculator calculator = new TableLoanCalculator();

    @Test
    void createsThirtyYearMonthlySchedule() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        Loan loan = tableLoan(
                "L001", 500_000.00, 5.5, 360,
                Loan.Frequency.MONTHLY, Loan.Frequency.MONTHLY, startDate);

        List<Repayment> schedule = calculator.calculateSchedule(loan);

        assertEquals(360, schedule.size());
        assertRepayment(
                schedule.get(0),
                LocalDate.of(2026, 2, 1),
                2_838.95,
                547.28,
                2_291.67,
                499_452.72);
        assertRepayment(
                schedule.get(359),
                LocalDate.of(2056, 1, 1),
                2_834.28,
                2_821.35,
                12.93,
                0.00);
        assertScheduleIntegrity(loan, schedule);
    }

    @Test
    void createsTwelveMonthSchedule() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        Loan loan = tableLoan(
                "L002", 12_000.00, 12.0, 12,
                Loan.Frequency.MONTHLY, Loan.Frequency.MONTHLY, startDate);

        List<Repayment> schedule = calculator.calculateSchedule(loan);

        assertEquals(12, schedule.size());
        assertRepayment(
                schedule.get(0),
                LocalDate.of(2026, 2, 1),
                1_066.19,
                946.19,
                120.00,
                11_053.81);
        assertRepayment(
                schedule.get(11),
                LocalDate.of(2027, 1, 1),
                1_066.14,
                1_055.58,
                10.56,
                0.00);
        assertScheduleIntegrity(loan, schedule);
    }

    @Test
    void supportsZeroInterest() {
        Loan loan = tableLoan(
                "L003", 12_000.00, 0.0, 12,
                Loan.Frequency.MONTHLY,
                Loan.Frequency.MONTHLY,
                LocalDate.of(2026, 1, 31));

        List<Repayment> schedule = calculator.calculateSchedule(loan);

        assertEquals(12, schedule.size());
        assertEquals(LocalDate.of(2026, 2, 28), schedule.get(0).getDueDate());
        assertEquals(LocalDate.of(2026, 3, 31), schedule.get(1).getDueDate());
        for (Repayment repayment : schedule) {
            assertAll(
                    () -> assertEquals(1_000.00, repayment.getAmountDue(), MONEY_TOLERANCE),
                    () -> assertEquals(1_000.00, repayment.getPrincipalComponent(), MONEY_TOLERANCE),
                    () -> assertEquals(0.00, repayment.getInterestComponent(), MONEY_TOLERANCE));
        }
        assertScheduleIntegrity(loan, schedule);
    }

    @Test
    void supportsWeeklyAndFortnightlySchedules() {
        Loan weeklyLoan = tableLoan(
                "L004", 5_200.00, 4.0, 12,
                Loan.Frequency.WEEKLY,
                Loan.Frequency.WEEKLY,
                LocalDate.of(2026, 1, 1));
        Loan fortnightlyLoan = tableLoan(
                "L005", 5_200.00, 4.0, 12,
                Loan.Frequency.FORTNIGHTLY,
                Loan.Frequency.MONTHLY,
                LocalDate.of(2026, 1, 1));

        List<Repayment> weeklySchedule = calculator.calculateSchedule(weeklyLoan);
        List<Repayment> fortnightlySchedule = calculator.calculateSchedule(fortnightlyLoan);

        assertAll(
                () -> assertEquals(52, weeklySchedule.size()),
                () -> assertEquals(LocalDate.of(2026, 1, 8), weeklySchedule.get(0).getDueDate()),
                () -> assertEquals(26, fortnightlySchedule.size()),
                () -> assertEquals(
                        LocalDate.of(2026, 1, 15),
                        fortnightlySchedule.get(0).getDueDate()));
        assertScheduleIntegrity(weeklyLoan, weeklySchedule);
        assertScheduleIntegrity(fortnightlyLoan, fortnightlySchedule);
    }

    @Test
    void rejectsInvalidLoanInputs() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(new Loan(
                                "L006", "C001", Loan.Status.NEW, 10_000.00, 5.0,
                                Loan.Type.INTEREST_ONLY, false, startDate, null, 0.0,
                                Loan.Frequency.MONTHLY, Loan.Frequency.MONTHLY, 12))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(tableLoan(
                                "L007", 10_000.00, -1.0, 12,
                                Loan.Frequency.MONTHLY, Loan.Frequency.MONTHLY, startDate))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(tableLoan(
                                "L008", 10_000.00, Double.NaN, 12,
                                Loan.Frequency.MONTHLY, Loan.Frequency.MONTHLY, startDate))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(tableLoan(
                                "L009", 10_000.00, 5.0, 12,
                                Loan.Frequency.MONTHLY, Loan.Frequency.MONTHLY, null))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(tableLoan(
                                "L010", 10_000.00, 5.0, 12,
                                null, Loan.Frequency.MONTHLY, startDate))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(tableLoan(
                                "L011", 10_000.00, 5.0, 12,
                                Loan.Frequency.MONTHLY, null, startDate))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateSchedule(tableLoan(
                                "L012", 10_000.00, 5.0, 1,
                                Loan.Frequency.WEEKLY, Loan.Frequency.WEEKLY, startDate))));
    }

    private Loan tableLoan(
            String loanId,
            double principal,
            double annualInterestRate,
            int termMonths,
            Loan.Frequency paymentFrequency,
            Loan.Frequency rateFrequency,
            LocalDate startDate) {
        return new Loan(
                loanId,
                "C001",
                Loan.Status.NEW,
                principal,
                annualInterestRate,
                Loan.Type.TABLE_LOAN,
                false,
                startDate,
                null,
                0.0,
                paymentFrequency,
                rateFrequency,
                termMonths);
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
                () -> assertEquals(expectedAmount, repayment.getAmountDue(), MONEY_TOLERANCE),
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

    private void assertScheduleIntegrity(Loan loan, List<Repayment> schedule) {
        double previousBalance = roundedMoney(loan.getPrincipal());
        double totalPrincipal = 0.0;
        Set<String> repaymentIds = new HashSet<>();

        for (Repayment repayment : schedule) {
            assertAll(
                    () -> assertEquals(loan.getLoanId(), repayment.getLoanId()),
                    () -> assertFalse(repayment.getRepaymentId().isBlank()),
                    () -> assertEquals(
                            repayment.getAmountDue(),
                            roundedMoney(repayment.getPrincipalComponent()
                                    + repayment.getInterestComponent()),
                            MONEY_TOLERANCE));
            assertTrue(repaymentIds.add(repayment.getRepaymentId()));
            assertEquals(
                    repayment.getRemainingBalance(),
                    roundedMoney(previousBalance - repayment.getPrincipalComponent()),
                    MONEY_TOLERANCE);
            assertTrue(repayment.getRemainingBalance() >= 0.0);
            totalPrincipal += repayment.getPrincipalComponent();
            previousBalance = repayment.getRemainingBalance();
        }

        assertEquals(0.00, schedule.get(schedule.size() - 1).getRemainingBalance());
        assertEquals(
                roundedMoney(loan.getPrincipal()),
                roundedMoney(totalPrincipal),
                MONEY_TOLERANCE);
    }

    private double roundedMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
