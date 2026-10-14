package com.lavs.ui;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import com.lavs.model.LoanDraft;
import com.lavs.business.StubLoanGuarantorService;
import com.lavs.business.LoanPrerequisiteException;
import com.lavs.business.LoanSchedulePreviewService;
import com.lavs.business.LoanSummary;
import com.lavs.model.Repayment;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;

/** Three-step loan wizard. Persistence is not yet connected to the sample loan list. */
public class AddLoanController {
    @FXML private TextField customerSearch, principal, interestRate, term;
    @FXML private ListView<CustomersController.CustomerSummary> customerResults;
    @FXML private Label borrowerName, message;
    @FXML private HBox selectedBorrower;
    @FXML private ComboBox<String> loanType, paymentFrequency, rateFrequency, status;
    @FXML private DatePicker startDate, renewDate;
    @FXML private ScrollPane loanScroll;
    @FXML private String scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle;
    @FXML private VBox detailsPane, coborrowersPane, coborrowerRows;
    @FXML private TextField coborrowerSearch;
    @FXML private ListView<CustomersController.CustomerSummary> coborrowerResults;
    @FXML private Label coborrowerCount, emptyCoborrowers, marker0, marker1, stepName0, stepName1;
    @FXML private Button back, next;
    @FXML private VBox reviewPane, reviewCoborrowers;
    @FXML private Label marker2, stepName2, reviewBorrower, reviewType, reviewPrincipal,
            reviewInterest, reviewPaymentFrequency, reviewRateFrequency, reviewTerm,
            reviewStartDate, reviewRenewDate, reviewStatus, reviewCoborrowerCount;
    @FXML private String coborrowerRowStyle, removeButtonStyle;
    private final LoanDraft draft = new LoanDraft();
    private final StubLoanGuarantorService guarantorService = new StubLoanGuarantorService();
    @FXML private Label guarantorNotice;
    private final LoanSchedulePreviewService previewService = new LoanSchedulePreviewService();
    @FXML private TableView<Repayment> repaymentTable;
    @FXML private TableColumn<Repayment, String> dueDateColumn, paymentColumn, principalColumn, interestColumn, balanceColumn;
    @FXML private Label repaymentCount, totalRepayable, totalInterest;
    private int step;
    private boolean editingReview;
    @FXML private Label reviewNotice;
    private String activeMarkerStyle, inactiveMarkerStyle, activeNameStyle, inactiveNameStyle;
    private List<CustomersController.CustomerSummary> customers = List.of();
    private Runnable onCancel = () -> {};

    @FXML private void initialize() {
        var dateFormat = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var currency = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"));
        dueDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getDueDate().format(dateFormat)));
        paymentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(currency.format(cell.getValue().getAmountDue())));
        principalColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(currency.format(cell.getValue().getPrincipalComponent())));
        interestColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(currency.format(cell.getValue().getInterestComponent())));
        balanceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(currency.format(cell.getValue().getRemainingBalance())));
        ScrollPaneAppearance.install(repaymentTable, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(loanScroll, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(customerResults, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(coborrowerResults, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        activeMarkerStyle = marker0.getStyle();
        inactiveMarkerStyle = marker1.getStyle();
        activeNameStyle = stepName0.getStyle();
        inactiveNameStyle = stepName1.getStyle();
        coborrowerSearch.textProperty().addListener((obs, old, value) -> searchCoborrowers());
        coborrowerResults.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value == null || draft.coborrowers.size() >= 3 || !eligibleCoborrower(value)) return;
            draft.coborrowers.add(new LoanDraft.Borrower(value.customerId(), value.name()));
            coborrowerSearch.clear();
            renderCoborrowers();
        });
        loanType.getItems().setAll("Table Loan", "Reducing Balance", "Interest Only");
        paymentFrequency.getItems().setAll("Weekly", "Fortnightly", "Monthly", "Yearly", "On Renewal");
        rateFrequency.getItems().setAll("Daily", "Weekly", "Fortnightly", "Monthly", "Yearly");
        status.getItems().setAll("New", "Pending", "Active", "Defaulted", "Cancelled");
        bindText("principal", principal);
        bindText("interestRate", interestRate);
        bindText("term", term);
        bindChoice("loanType", loanType);
        bindChoice("paymentFrequency", paymentFrequency);
        bindChoice("rateFrequency", rateFrequency);
        bindChoice("status", status);
        startDate.valueProperty().addListener((obs, old, value) -> draft.startDate = value);
        renewDate.valueProperty().addListener((obs, old, value) -> draft.renewDate = value);
        status.setValue("New");
        customerResults.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(CustomersController.CustomerSummary customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null ? null : customer.name() + " | Customer ID: " + customer.customerId());
            }
        });
        coborrowerResults.setCellFactory(customerResults.getCellFactory());
        customerSearch.textProperty().addListener((obs, old, value) -> searchCustomers());
        customerResults.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value == null) return;
            draft.primaryBorrower = new LoanDraft.Borrower(value.customerId(), value.name());
            draft.coborrowers.removeIf(c -> c.customerId().equals(value.customerId()));
            renderCoborrowers();
            borrowerName.setText(value.name() + " | Customer ID: " + value.customerId());
            selectedBorrower.setVisible(true);
            selectedBorrower.setManaged(true);
            customerSearch.clear();
            refreshGuarantorNotice();
            message.setVisible(false);
            message.setManaged(false);
        });
    }

    private void bindText(String key, TextField field) {
        draft.details.put(key, field.getText());
        field.textProperty().addListener((obs, old, value) -> draft.details.put(key, value));
    }

    private void bindChoice(String key, ComboBox<String> field) {
        field.valueProperty().addListener((obs, old, value) -> draft.details.put(key, value == null ? "" : value));
    }

    /** Returns an independent snapshot, including incomplete edits. */
    public LoanDraft getDraft() { return draft.copy(); }

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
        draft.primaryBorrower = null;
        selectedBorrower.setVisible(false);
        selectedBorrower.setManaged(false);
        refreshGuarantorNotice();
        message.setVisible(false);
        message.setManaged(false);
        customerSearch.requestFocus();
    }

    @FXML private void cancel() { onCancel.run(); }

    @FXML private void next() {
        String error = validateDetails();
        if (error != null) { showMessage(error); return; }
        if (step == 2 || step == 1 || editingReview) {
            try {
                updateSchedule();
            } catch (IllegalArgumentException | ArithmeticException exception) {
                repaymentTable.getItems().clear();
                showMessage("Unable to generate repayment schedule: " + exception.getMessage()
                        + ". Please edit the loan details and try again.");
                return;
            }
        }
        if (step == 2) {
            showMessage("Saving loans is not connected yet. No loan has been saved. Your details are kept here.");
        } else {
            int target = editingReview ? 2 : step + 1;
            editingReview = false;
            showStep(target);
        }
    }

    @FXML private void editBorrower() { editStep(0); }

    @FXML private void editLoanDetails() {
        editStep(0);
        javafx.application.Platform.runLater(principal::requestFocus);
    }

    @FXML private void editCoborrowers() { editStep(1); }

    private void editStep(int target) {
        editingReview = true;
        showStep(target);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisible(true);
        message.setManaged(true);
    }

    @FXML private void back() { showStep(Math.max(0, step - 1)); }

    private void showStep(int selectedStep) {
        step = selectedStep;
        VBox[] panes = {detailsPane, coborrowersPane, reviewPane};
        Label[] markers = {marker0, marker1, marker2};
        Label[] names = {stepName0, stepName1, stepName2};
        for (int index = 0; index < panes.length; index++) {
            panes[index].setVisible(index == step);
            panes[index].setManaged(index == step);
            markers[index].setStyle(index == step ? activeMarkerStyle : inactiveMarkerStyle);
            names[index].setStyle(index == step ? activeNameStyle : inactiveNameStyle);
        }
        back.setVisible(step > 0 && !editingReview);
        back.setManaged(back.isVisible());
        next.setText(step == 2 ? "Finish" : editingReview ? "Update and return to Review" : "Next");
        reviewNotice.setVisible(editingReview);
        reviewNotice.setManaged(editingReview);
        message.setVisible(false);
        message.setManaged(false);
        loanScroll.setVvalue(0);
        if (step == 1) {
            renderCoborrowers();
            focusCoborrowerSearch();
        } else if (step == 2) {
            renderReview();
            javafx.application.Platform.runLater(next::requestFocus);
        } else focusBorrowerSearch();
    }

    private void renderReview() {
        reviewBorrower.setText(draft.primaryBorrower.name() + " | Customer ID: " + draft.primaryBorrower.customerId());
        reviewType.setText(draft.details.getOrDefault("loanType", ""));
        java.text.NumberFormat currency = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"));
        reviewPrincipal.setText(currency.format(new BigDecimal(draft.details.getOrDefault("principal", "").strip())));
        reviewInterest.setText(new BigDecimal(draft.details.getOrDefault("interestRate", "").strip()).stripTrailingZeros().toPlainString() + "%");
        reviewPaymentFrequency.setText(draft.details.getOrDefault("paymentFrequency", ""));
        reviewRateFrequency.setText(draft.details.getOrDefault("rateFrequency", ""));
        reviewTerm.setText(Integer.parseInt(draft.details.getOrDefault("term", "").strip()) + " months");
        var dateFormat = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        reviewStartDate.setText(draft.startDate.format(dateFormat));
        reviewRenewDate.setText(draft.renewDate.format(dateFormat));
        reviewStatus.setText(draft.details.getOrDefault("status", ""));
        reviewCoborrowerCount.setText("Co-borrowers (" + draft.coborrowers.size() + "/3)");
        reviewCoborrowers.getChildren().clear();
        if (draft.coborrowers.isEmpty()) {
            reviewCoborrowers.getChildren().add(new Label("No co-borrowers added."));
        } else {
            for (var customer : draft.coborrowers) {
                Label name = new Label(customer.name() + " | Customer ID: " + customer.customerId());
                name.setWrapText(true);
                name.setMinWidth(0);
                reviewCoborrowers.getChildren().add(name);
            }
        }
    }

    private void updateSchedule() {
        List<Repayment> schedule = previewService.generate(draft.copy());
        repaymentTable.getItems().setAll(schedule);
        LoanSummary summary = LoanSummary.fromSchedule(schedule);
        var currency = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"));
        repaymentCount.setText(schedule.size() + " repayments");
        totalRepayable.setText(currency.format(summary.getTotalRepayable()));
        totalInterest.setText(currency.format(summary.getTotalInterest()));
    }

    private boolean eligibleCoborrower(CustomersController.CustomerSummary customer) {
        return (draft.primaryBorrower == null || !draft.primaryBorrower.customerId().equals(customer.customerId()))
                && draft.coborrowers.stream().noneMatch(c -> c.customerId().equals(customer.customerId()));
    }

    private void searchCoborrowers() {
        String query = coborrowerSearch.getText().strip().toLowerCase(Locale.ROOT);
        coborrowerResults.getItems().setAll(query.isEmpty() || draft.coborrowers.size() >= 3 ? List.of()
                : customers.stream().filter(this::eligibleCoborrower)
                .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(query)
                        || c.customerId().toLowerCase(Locale.ROOT).contains(query)).toList());
        coborrowerResults.setVisible(!query.isEmpty() && draft.coborrowers.size() < 3);
        coborrowerResults.setManaged(coborrowerResults.isVisible());
    }

    @FXML private void focusCoborrowerSearch() {
        javafx.application.Platform.runLater(() -> {
            if (step == 1 && !coborrowerSearch.isDisabled()) coborrowerSearch.requestFocus();
        });
    }

    private void renderCoborrowers() {
        coborrowerRows.getChildren().clear();
        for (var customer : draft.coborrowers) {
            Label name = new Label(customer.name() + " | Customer ID: " + customer.customerId());
            name.setWrapText(true);
            name.setMinWidth(0);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button remove = new Button("Remove");
            remove.setStyle(removeButtonStyle);
            remove.setMinWidth(Region.USE_PREF_SIZE);
            remove.setAccessibleText("Remove " + customer.name() + " as co-borrower");
            remove.setOnAction(event -> {
                draft.coborrowers.remove(customer);
                renderCoborrowers();
                searchCoborrowers();
                focusCoborrowerSearch();
            });
            HBox row = new HBox(12, name, spacer, remove);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setStyle(coborrowerRowStyle);
            coborrowerRows.getChildren().add(row);
        }
        coborrowerCount.setText("Added co-borrowers (" + draft.coborrowers.size() + "/3)");
        emptyCoborrowers.setVisible(draft.coborrowers.isEmpty());
        emptyCoborrowers.setManaged(draft.coborrowers.isEmpty());
        coborrowerSearch.setDisable(draft.coborrowers.size() >= 3);
    }

    String validateDetails() {
        if (draft.primaryBorrower == null) return "Select a primary borrower from the customer search results.";
        String guarantorError = guarantorError();
        if (guarantorError != null) return guarantorError;
        if (draft.details.getOrDefault("loanType", "").isBlank()) return "Select a loan type.";
        if (!validNumber(draft.details.getOrDefault("principal", ""), false)) return "Enter a principal greater than zero.";
        if (!validNumber(draft.details.getOrDefault("interestRate", ""), true)) return "Enter an interest rate of zero or greater.";
        if (draft.details.getOrDefault("paymentFrequency", "").isBlank()) return "Select a payment frequency.";
        if (draft.details.getOrDefault("rateFrequency", "").isBlank()) return "Select a rate frequency.";
        try {
            if (Integer.parseInt(draft.details.getOrDefault("term", "").strip()) <= 0) return "Enter a term greater than zero in months.";
        } catch (NumberFormatException exception) { return "Enter the term as a whole number of months."; }
        try {
            startDate.commitValue();
            renewDate.commitValue();
        } catch (RuntimeException exception) { return "Enter valid start and renewal dates."; }
        if (draft.startDate == null || draft.renewDate == null) return "Choose start and renewal dates.";
        if (!draft.renewDate.isAfter(draft.startDate)) return "Renewal date must be after the start date.";
        if (draft.details.getOrDefault("status", "").isBlank()) return "Select a status.";
        return null;
    }

    private String guarantorError() {
        if (draft.primaryBorrower == null) return null;
        try {
            guarantorService.validate(draft.primaryBorrower.customerId());
            return null;
        } catch (LoanPrerequisiteException exception) {
            return draft.primaryBorrower.name() + " has no employer recorded and needs a guarantor. "
                    + "Add a guarantor to this customer's record before continuing with the loan.";
        }
    }

    private void refreshGuarantorNotice() {
        String error = guarantorError();
        guarantorNotice.setText(error == null ? "" : error);
        guarantorNotice.setVisible(error != null);
        guarantorNotice.setManaged(error != null);
    }

    private static boolean validNumber(String text, boolean allowZero) {
        try {
            int sign = new BigDecimal(text.strip()).signum();
            return allowZero ? sign >= 0 : sign > 0;
        } catch (NumberFormatException exception) { return false; }
    }
}
