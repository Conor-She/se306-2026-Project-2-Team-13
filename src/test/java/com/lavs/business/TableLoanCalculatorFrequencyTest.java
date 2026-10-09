package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableLoanCalculatorFrequencyTest {

    private final TableLoanCalculator calculator = new TableLoanCalculator();
    private final LocalDate startDate = LocalDate.of(2026, 1, 1);

    // Builds a valid table loan, so each test only has to vary the fields
    // it is actually testing
    private Loan buildLoan(String loanId, Loan.PaymentFrequency paymentFrequency,
            Loan.RateFrequency rateFrequency, Integer termMonths) {
        return new Loan(loanId, "C001", Loan.Status.NEW, 100000, 6.0, Loan.Type.TABLE_LOAN,
                false, startDate, startDate.plusYears(2), 0, paymentFrequency, rateFrequency,
                termMonths);
    }

    // A 5-year loan paid yearly should produce exactly 5 repayments, one
    // year apart, ending with a zero balance
    @Test
    void schedulesYearlyPayments() {
        List<Repayment> schedule = calculator.calculateSchedule(buildLoan("L001",
                Loan.PaymentFrequency.YEARLY, Loan.RateFrequency.YEARLY, 60));

        assertEquals(5, schedule.size());
        assertEquals(startDate.plusYears(1), schedule.get(0).getDueDate());
        assertEquals(startDate.plusYears(5), schedule.get(4).getDueDate());
        assertEquals(0.0, schedule.get(4).getRemainingBalance());
    }

    // Interest compounding daily but paid monthly is a valid combination,
    // and must still clear the balance exactly on the final payment
    @Test
    void schedulesMonthlyPaymentsWithDailyRateFrequency() {
        List<Repayment> schedule = calculator.calculateSchedule(buildLoan("L001",
                Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.DAILY, 12));

        assertEquals(12, schedule.size());
        assertEquals(0.0, schedule.get(11).getRemainingBalance());
    }

    // "On renewal" has no fixed number of payments per year, so it must be
    // rejected with a message that names the problem
    @Test
    void rejectsOnRenewalPaymentFrequency() {
        Loan loan = buildLoan("L001", Loan.PaymentFrequency.ON_RENEWAL,
                Loan.RateFrequency.MONTHLY, 12);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateSchedule(loan));

        assertTrue(exception.getMessage().contains("ON_RENEWAL"));
    }

    // A loan re-fetched from the mainframe has no term, so it can't be
    // scheduled and must fail clearly rather than with a NullPointerException
    @Test
    void rejectsUnknownTerm() {
        Loan loan = buildLoan("L001", Loan.PaymentFrequency.MONTHLY,
                Loan.RateFrequency.MONTHLY, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateSchedule(loan));

        assertTrue(exception.getMessage().contains("termMonths"));
    }

    // A loan the mainframe hasn't assigned an ID yet can't own repayments
    @Test
    void rejectsLoanWithoutAssignedId() {
        Loan loan = buildLoan(null, Loan.PaymentFrequency.MONTHLY,
                Loan.RateFrequency.MONTHLY, 12);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateSchedule(loan));

        assertTrue(exception.getMessage().contains("loanId"));
    }
}