package com.lavs.business;

import com.lavs.data.MainframeException;
import com.lavs.data.MainframeGateway;
import com.lavs.data.PartialLoanCreationException;
import com.lavs.model.Employer;
import com.lavs.model.Guarantor;
import com.lavs.model.Loan;
import com.lavs.model.Repayment;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

// Business logic for creating, editing, finding loans and changing their status
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
    static final String PAYMENT_AMOUNT_MESSAGE = "The payment amount must be greater than zero";

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
        requirePaymentAmount(loan);
        checkPrerequisites(loan.getCustomerId());

        // The calculators reject some terms (e.g. "on renewal" payments), so
        // find that out now rather than after the loan exists
        calculatorFor(loan).calculateSchedule(withLoanId(loan, DRY_RUN_LOAN_ID));

        Loan created;
        try {
            created = gateway.createLoan(loan);
        } catch (PartialLoanCreationException failure) {
            throw new LoanSubmissionException(failure.getMessage(), failure);
        }
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
        requirePaymentAmount(loan);

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

    // Finds the loans a customer is the primary borrower on, for the
    // "Customer's Loans" screen (DFD process 10.0)
    public List<Loan> findByCustomer(String customerId) {
        requireNotBlank(customerId, "customerId");
        return List.copyOf(gateway.findLoansByCustomer(customerId));
    }

    // A loan's full details: the loan, every row of its saved schedule, and the
    // summary of that schedule. The schedule is read back as it was saved, not
    // recalculated, because the mainframe doesn't keep the type or term needed
    // to calculate it again.
    public LoanWithSchedule getFullDetails(String loanId) {
        requireNotBlank(loanId, "loanId");

        Loan loan = gateway.getLoan(loanId);
        // Sorted here, so the schedule reads in order whatever order the
        // mainframe returns it in
        List<Repayment> schedule = gateway.getRepaymentSchedule(loanId).stream()
                .sorted(Comparator.comparing(Repayment::getDueDate))
                .toList();

        return new LoanWithSchedule(loan, schedule);
    }

    // Chosen order: mailing address, then phone or email, then a
    // guarantor if there is no employer. Each step fetches only what it
    // needs, so a failure stops further requests to the mainframe.
    private void checkPrerequisites(String customerId) {
        prerequisiteChecker.checkHasMailingAddress(gateway.getAddresses(customerId));
        prerequisiteChecker.checkHasAlternateContact(
                gateway.getPhones(customerId), gateway.getEmails(customerId));

        List<Employer> employers = gateway.getEmployers(customerId);
        boolean hasEmployer = employers != null && employers.stream()
                .anyMatch(employer -> customerId.equals(employer.getCustomerId()));
        if (hasEmployer) {
            guarantorValidator.validate(customerId, employers, List.of());
            return;
        }

        // A saved relationship only counts if its linked guarantor is still a customer.
        List<Guarantor> guarantors = gateway.getGuarantors(customerId);
        List<Guarantor> existingGuarantors = guarantors == null ? List.of() : guarantors.stream()
                .filter(guarantor -> customerId.equals(guarantor.getCustomerId()))
                .filter(guarantor -> customerExists(guarantor.getGuarantorCustomerId()))
                .toList();
        guarantorValidator.validate(customerId, employers, existingGuarantors);
    }

    // The mainframe rejects a lookup for a customer that doesn't exist, so that
    // business error means "not a customer"; an outage still reaches the caller.
    // AI-assisted: Helped implement the customer and guarantor prerequisite checks.
    private boolean customerExists(String customerId) {
        if (customerId == null || customerId.isBlank()) return false;
        try {
            return gateway.getCustomer(customerId) != null;
        } catch (MainframeException exception) {
            if (exception.getType() == MainframeException.Type.UNAVAILABLE) throw exception;
            return false;
        }
    }

    /** Checks whether the backend customer has the records required to create a loan. */
    public void validatePrerequisites(String customerId) {
        requireNotBlank(customerId, "customerId");
        checkPrerequisites(customerId);
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

    // The product owner suggested a payment amount above zero as a reasonable
    // rule. Written so that a missing number (NaN) is refused too.
    private void requirePaymentAmount(Loan loan) {
        if (!(loan.getPaymentAmount() > 0)) {
            throw new IllegalArgumentException(PAYMENT_AMOUNT_MESSAGE);
        }
    }

    private void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }
}
