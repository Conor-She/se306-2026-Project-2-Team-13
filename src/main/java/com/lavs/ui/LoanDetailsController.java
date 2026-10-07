package com.lavs.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Displays the selected loan summary and returns to the loan list. */
public class LoanDetailsController {
    @FXML private Label loanHeading;
    @FXML private Label loanIdValue;
    @FXML private Label customerIdValue;
    @FXML private Label statusValue;
    @FXML private Label amountValue;
    private LoansController.LoanSummary loan;
    private Runnable onBack;

    public void setLoan(LoansController.LoanSummary loan) {
        this.loan = loan;
        loanHeading.setText(loan.loanId() + " (" + loan.customerId() + ")");
        loanIdValue.setText(loan.loanId());
        customerIdValue.setText(loan.customerId());
        statusValue.setText(loan.status());
        amountValue.setText(loan.amount());
    }
    public LoansController.LoanSummary getLoan() { return loan; }
    public void setOnBack(Runnable onBack) { this.onBack = onBack; }
    @FXML private void back() {
        if (onBack != null) onBack.run();
    }
}
