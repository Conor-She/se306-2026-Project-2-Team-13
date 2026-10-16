package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Address;
import com.lavs.model.Customer;
import com.lavs.model.Employer;
import com.lavs.model.LoanDraft;
import com.lavs.model.Phone;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LoanCreationServiceTest {

    @Test
    void createsLoanScheduleAndCoBorrowersThatAreSearchableByBorrowerName() {
        FakeMainframeGateway gateway = new FakeMainframeGateway();
        gateway.createCustomer(new Customer("C1", "Ms", "Baba", "Lala", null, "NZ"));
        gateway.addAddress(new Address(null, "C1", "5 Queen Street", "CBD", "Auckland", "1010", "NZ", true));
        gateway.addPhone(new Phone(null, "C1", "0211234567", true, true));
        gateway.addEmployer(new Employer(null, "C1", "Acme Limited", "Engineer", 90000, "Primary"));
        gateway.createCustomer(new Customer("C2", "Mr", "Sam", "Taylor", null, "NZ"));

        LoanDraft draft = new LoanDraft();
        draft.primaryBorrower = new LoanDraft.Borrower("C1", "Baba Lala");
        draft.details.put("loanType", "Table Loan");
        draft.details.put("principal", "12000");
        draft.details.put("interestRate", "6.25");
        draft.details.put("term", "12");
        draft.details.put("paymentFrequency", "Monthly");
        draft.details.put("rateFrequency", "Monthly");
        draft.details.put("status", "New");
        draft.startDate = LocalDate.of(2026, 11, 1);
        draft.renewDate = LocalDate.of(2027, 11, 1);
        draft.coborrowers.add(new LoanDraft.Borrower("C2", "Sam Taylor"));

        LoanWithSchedule saved = new LoanCreationService(gateway).create(draft);

        assertNotNull(saved.getLoan().getLoanId());
        assertFalse(saved.getSchedule().isEmpty());
        assertEquals(saved.getSchedule(), gateway.getRepaymentSchedule(saved.getLoan().getLoanId()));
        assertEquals(1, gateway.getCoBorrowers(saved.getLoan().getLoanId()).size());
        LoanBrowsingService.LoanSearchResult result = new LoanBrowsingService(gateway).search("Baba Lala");
        assertEquals(1, result.loans().size());
        assertEquals(saved.getLoan().getLoanId(), result.loans().get(0).loan().getLoanId());
        assertEquals("Baba Lala", result.loans().get(0).customerName());
    }
}
