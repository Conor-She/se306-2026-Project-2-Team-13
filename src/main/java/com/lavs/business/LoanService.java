package com.lavs.business;

import com.lavs.data.MainframeGateway;
import com.lavs.model.Loan;
import com.lavs.model.Repayment;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Business logic for creating and editing loans and changing their status
// Depends only on the MainframeGateway interface, never on a concrete gateway.
public class LoanService {

    static final String NEW_LOAN_MESSAGE = "A new loan must start with status New";
    static final String STATUS_VIA_CHANGE_STATUS_MESSAGE =
            "A loan's status can only be changed through changeStatus, not by editing the loan";
    static final String LOAN_TYPE_REQUIRED_MESSAGE =
            "A loan type is required to calculate the repayment schedule";
    static final String TERM_REQUIRED_MESSAGE = "A loan term is required!";
    static final String CUSTOMER_CHANGE_MESSAGE =
            "A loan's customer can't be changed by editing the loan";

    // Stands in for the real ID during create's dry run, since the mainframe
    // hasn't assigned one yet
    private static final String DRY_RUN_LOAN_ID = "DRY-RUN";

    private final MainframeGateway gateway;
    private final LoanPrerequisiteChecker prerequisiteChecker;
    private final GuarantorRequirementValidator guarantorValidator;
    private final Map<Loan.Type, RepaymentCalculator> calculators;

    // Normal use: the real checkers and one calculator per loan type
    public LoanService(MainframeGateway gateway) {
        this(gateway, new LoanPrerequisiteChecker(), new GuarantorRequirementValidator(),
                defaultCalculators());
    }

    // Lets tests (or future code) supply their own collaborators
    public LoanService(MainframeGateway gateway, LoanPrerequisiteChecker prerequisiteChecker,
            GuarantorRequirementValidator guarantorValidator,
            Map<Loan.Type, RepaymentCalculator> calculators) {
        this.gateway = gateway;
        this.prerequisiteChecker = prerequisiteChecker;
        this.guarantorValidator = guarantorValidator;
        this.calculators = calculators;
    }

    // Creates a loan: prerequisites first, then a dry run of the schedule,
    // then the real creation, so nothing is created on the mainframe for a
    // loan that can't proceed
    public LoanWithSchedule create(Loan loan) {
        if (loan == null) {
            throw new IllegalArgumentException("loan must not be null");
        }
        if (loan.getStatus() != Loan.Status.NEW) {
            throw new IllegalArgumentException(NEW_LOAN_MESSAGE);
        }

        requireTerm(loan);
        checkPrerequisites(loan.getCustomerId());

        // The calculators reject some terms (e.g. "on renewal" payments), so
        // find that out now rather than after the loan exists
        calculatorFor(loan).calculateSchedule(withLoanId(loan, DRY_RUN_LOAN_ID));

        Loan created = gateway.createLoan(loan);
        List<Repayment> schedule = calculatorFor(created).calculateSchedule(created);
        gateway.saveRepaymentSchedule(created.getLoanId(), schedule);

        return new LoanWithSchedule(created, schedule);
    }

    // Edits an existing loan and regenerates its schedule. The status can't
    // be changed here, only through changeStatus.
    public LoanWithSchedule update(Loan loan) {
        if (loan == null || loan.getLoanId() == null) {
            throw new IllegalArgumentException("An existing loan with a loanId is required");
        }

        requireTerm(loan);

        Loan existing = gateway.getLoan(loan.getLoanId());
        if (existing.getStatus() != loan.getStatus()) {
            throw new InvalidStatusTransitionException(STATUS_VIA_CHANGE_STATUS_MESSAGE);
        }
        if (!existing.getCustomerId().equals(loan.getCustomerId())) {
            throw new IllegalArgumentException(CUSTOMER_CHANGE_MESSAGE);
        }
        
        // Check the new terms can be scheduled before changing anything
        calculatorFor(loan).calculateSchedule(loan);

        Loan updated = gateway.updateLoan(loan);
        List<Repayment> schedule = calculatorFor(updated).calculateSchedule(updated);
        gateway.saveRepaymentSchedule(updated.getLoanId(), schedule);

        return new LoanWithSchedule(updated, schedule);
    }

    // The only allowed status change is New to Pending
    public Loan changeStatus(String loanId, Loan.Status newStatus) {
        Loan current = gateway.getLoan(loanId);

        boolean isAllowed = current.getStatus() == Loan.Status.NEW
                && newStatus == Loan.Status.PENDING;
        if (!isAllowed) {
            throw new InvalidStatusTransitionException(
                    "A loan can only change status from New to Pending, not from "
                            + current.getStatus() + " to " + newStatus);
        }

        return gateway.updateLoanStatus(loanId, newStatus);
    }

    // Chosen order: mailing address, then phone or email, then a
    // guarantor if there is no employer. Each step fetches only what it
    // needs, so a failure stops further requests to the mainframe.
    private void checkPrerequisites(String customerId) {
        prerequisiteChecker.checkHasMailingAddress(gateway.getAddresses(customerId));
        prerequisiteChecker.checkHasAlternateContact(
                gateway.getPhones(customerId), gateway.getEmails(customerId));
        guarantorValidator.validate(
                customerId, gateway.getEmployers(customerId), gateway.getGuarantors(customerId));
    }

    private RepaymentCalculator calculatorFor(Loan loan) {
        RepaymentCalculator calculator = calculators.get(loan.getType());
        if (calculator == null) {
            throw new IllegalArgumentException(LOAN_TYPE_REQUIRED_MESSAGE);
        }
        return calculator;
    }

    // Loan is immutable, so "changing" the ID means building a copy
    private Loan withLoanId(Loan loan, String loanId) {
        return new Loan(loanId, loan.getCustomerId(), loan.getStatus(), loan.getPrincipal(),
                loan.getInterestRate(), loan.getType(), loan.isFloating(), loan.getStartDate(),
                loan.getRenewDate(), loan.getPaymentAmount(), loan.getPaymentFrequency(),
                loan.getRateFrequency(), loan.getTermMonths());
    }

    private static Map<Loan.Type, RepaymentCalculator> defaultCalculators() {
        Map<Loan.Type, RepaymentCalculator> calculators = new EnumMap<>(Loan.Type.class);
        calculators.put(Loan.Type.TABLE_LOAN, new TableLoanCalculator());
        calculators.put(Loan.Type.REDUCING_BALANCE, new ReducingBalanceCalculator());
        calculators.put(Loan.Type.INTEREST_ONLY, new InterestOnlyCalculator());
        return calculators;
    }

    // The mainframe doesn't store the term, so nothing downstream would
    // notice a missing one. A loan being created or edited must have it.
    private void requireTerm(Loan loan) {
        if (loan.getTermMonths() == null) {
            throw new IllegalArgumentException(TERM_REQUIRED_MESSAGE);
        }
    }
}