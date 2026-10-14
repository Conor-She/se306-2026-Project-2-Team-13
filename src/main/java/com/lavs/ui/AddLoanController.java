package com.lavs.ui;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

/** First step of the loan wizard. No loan is saved until the later review step exists. */
public class AddLoanController {
    @FXML private TextField customerSearch, principal, interestRate, term;
    @FXML private ListView<CustomersController.CustomerSummary> customerResults;
    @FXML private Label borrowerName, message;
    @FXML private HBox selectedBorrower;
    @FXML private ComboBox<String> loanType, paymentFrequency, rateFrequency, status;
    @FXML private DatePicker startDate, renewDate;
    @FXML private ScrollPane loanScroll;
    @FXML private String scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle;
    private CustomersController.CustomerSummary borrower;
    private List<CustomersController.CustomerSummary> customers = List.of();
    private Runnable onCancel = () -> {};

    @FXML private void initialize() {
        ScrollPaneAppearance.install(loanScroll, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        loanType.getItems().setAll("Table Loan", "Reducing Balance", "Interest Only");
        paymentFrequency.getItems().setAll("Weekly", "Fortnightly", "Monthly", "Yearly", "On Renewal");
        rateFrequency.getItems().setAll("Daily", "Weekly", "Fortnightly", "Monthly", "Yearly");
        status.getItems().setAll("New", "Pending", "Active", "Defaulted", "Cancelled");
        status.setValue("New");
        customerResults.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(CustomersController.CustomerSummary customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null ? null : customer.name() + " | Customer ID: " + customer.customerId());
            }
        });
        customerSearch.textProperty().addListener((obs, old, value) -> searchCustomers());
        customerResults.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value == null) return;
            borrower = value;
            borrowerName.setText(value.name() + " | Customer ID: " + value.customerId());
            selectedBorrower.setVisible(true);
            selectedBorrower.setManaged(true);
            customerSearch.clear();
        });
    }

    public void configure(List<CustomersController.CustomerSummary> customers, Runnable onCancel) {
        this.customers = List.copyOf(customers);
        this.onCancel = onCancel;
    }

    public void focusBorrowerSearch() {
        javafx.application.Platform.runLater(() -> {
            if (customerSearch.getScene() != null && customerSearch.isVisible()) customerSearch.requestFocus();
        });
    }

    private void searchCustomers() {
        String query = customerSearch.getText().strip().toLowerCase(Locale.ROOT);
        customerResults.getItems().setAll(query.isEmpty() ? List.of() : customers.stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(query)
                        || c.customerId().toLowerCase(Locale.ROOT).contains(query)).toList());
        customerResults.setPlaceholder(new Label("No customers found"));
        customerResults.setVisible(!query.isEmpty());
        customerResults.setManaged(!query.isEmpty());
    }

    @FXML private void clearBorrower() {
        borrower = null;
        selectedBorrower.setVisible(false);
        selectedBorrower.setManaged(false);
        customerSearch.requestFocus();
    }

    @FXML private void cancel() { onCancel.run(); }

    @FXML private void next() {
        String error = validateDetails();
        message.setText(error == null
                ? "Loan details are ready. Co-borrowers and Review will be available in the next update. No loan has been saved."
                : error);
        message.setVisible(true);
        message.setManaged(true);
    }

    String validateDetails() {
        if (borrower == null) return "Select a primary borrower from the customer search results.";
        if (loanType.getValue() == null) return "Select a loan type.";
        if (!validNumber(principal.getText(), false)) return "Enter a principal greater than zero.";
        if (!validNumber(interestRate.getText(), true)) return "Enter an interest rate of zero or greater.";
        if (paymentFrequency.getValue() == null) return "Select a payment frequency.";
        if (rateFrequency.getValue() == null) return "Select a rate frequency.";
        try {
            if (Integer.parseInt(term.getText().strip()) <= 0) return "Enter a term greater than zero in months.";
        } catch (NumberFormatException exception) { return "Enter the term as a whole number of months."; }
        try {
            startDate.commitValue();
            renewDate.commitValue();
        } catch (RuntimeException exception) { return "Enter valid start and renewal dates."; }
        if (startDate.getValue() == null || renewDate.getValue() == null) return "Choose start and renewal dates.";
        if (!renewDate.getValue().isAfter(startDate.getValue())) return "Renewal date must be after the start date.";
        if (status.getValue() == null) return "Select a status.";
        return null;
    }

    private static boolean validNumber(String text, boolean allowZero) {
        try {
            int sign = new BigDecimal(text.strip()).signum();
            return allowZero ? sign >= 0 : sign > 0;
        } catch (NumberFormatException exception) { return false; }
    }
}
