package com.lavs.ui;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import com.lavs.model.LoanDraft;
import com.lavs.business.LoanPrerequisiteChecker;
import com.lavs.business.LoanPrerequisiteException;
import com.lavs.business.LoanSchedulePreviewService;
import com.lavs.business.LoanSummary;
import com.lavs.business.LoanSubmissionException;
import com.lavs.business.CustomerService;
import com.lavs.data.MainframeException;
import com.lavs.model.CustomerPreview;
import com.lavs.model.Repayment;
import java.util.concurrent.Executor;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

/** Three-step loan wizard. Finishing hands the draft to onSave, which saves it through the mainframe. */
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
    private java.util.function.Consumer<String> prerequisiteValidator;
    @FXML private Label prerequisiteNotice;
    private final LoanSchedulePreviewService previewService = new LoanSchedulePreviewService();
    @FXML private TableView<Repayment> repaymentTable;
    @FXML private TableColumn<Repayment, String> dueDateColumn, paymentColumn, principalColumn, interestColumn, balanceColumn;
    @FXML private Label repaymentCount, totalRepayable, totalInterest;
    private int step;
    private boolean editingReview;
    @FXML private Label reviewNotice;
    private String activeMarkerStyle, inactiveMarkerStyle, activeNameStyle, inactiveNameStyle;
    private List<CustomersController.CustomerSummary> customers = List.of();
    private CustomerService customerService;
    private Executor customerSearchExecutor = Runnable::run;
    private final PauseTransition primarySearchDelay = new PauseTransition(Duration.millis(300));
    private final PauseTransition coborrowerSearchDelay = new PauseTransition(Duration.millis(300));
    private long primarySearchVersion;
    private long coborrowerSearchVersion;
    private String pendingPrimaryQuery;
    private String pendingCoborrowerQuery;
    private long pendingPrimaryVersion;
    private long pendingCoborrowerVersion;
    private Runnable onCancel = () -> {};
    private java.util.function.Consumer<LoanDraft> onSave;
    private boolean saved;
    private boolean saving;
    private java.util.concurrent.Executor saveExecutor = Runnable::run;

    public void setSaveExecutor(java.util.concurrent.Executor saveExecutor) {
        this.saveExecutor = saveExecutor;
    }

    public void setOnSave(java.util.function.Consumer<LoanDraft> onSave) { this.onSave = onSave; }

    @FXML private void initialize() {
        var dateFormat = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var currency = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"));
        dueDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getDueDate().format(dateFormat)));
        paymentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(currency.format(cell.getValue().getAmountDue())));
        principalColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(currency.format(cell.getValue().getPrincipalComponent())));
        interestColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(currency.format(cell.getValue().getInterestComponent())));
        balanceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(currency.format(cell.getValue().getRemainingBalance())));
        ScrollPaneAppearance.install(repaymentTable, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        // Let the table scroll first, then stop boundary events reaching the review page.
        repaymentTable.addEventHandler(ScrollEvent.ANY, ScrollEvent::consume);
        ScrollPaneAppearance.install(loanScroll, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(customerResults, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(coborrowerResults, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        activeMarkerStyle = marker0.getStyle();
        inactiveMarkerStyle = marker1.getStyle();
        activeNameStyle = stepName0.getStyle();
        inactiveNameStyle = stepName1.getStyle();
        primarySearchDelay.setOnFinished(event -> loadPrimaryCustomers(pendingPrimaryQuery, pendingPrimaryVersion));
        coborrowerSearchDelay.setOnFinished(event -> loadCoborrowers(pendingCoborrowerQuery, pendingCoborrowerVersion));
        customerSearch.setOnAction(event -> {
            searchCustomers();
            primarySearchDelay.stop();
            if (customerService != null && !customerSearch.getText().isBlank())
                loadPrimaryCustomers(pendingPrimaryQuery, pendingPrimaryVersion);
        });
        coborrowerSearch.setOnAction(event -> {
            searchCoborrowers();
            coborrowerSearchDelay.stop();
            if (customerService != null && !coborrowerSearch.getText().isBlank() && draft.coborrowers.size() < 3)
                loadCoborrowers(pendingCoborrowerQuery, pendingCoborrowerVersion);
        });
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
            setPrimaryBorrower(value);
        });
    }

    public void setPrimaryBorrower(CustomersController.CustomerSummary customer) {
        draft.primaryBorrower = new LoanDraft.Borrower(customer.customerId(), customer.name());
        draft.coborrowers.removeIf(c -> c.customerId().equals(customer.customerId()));
        renderCoborrowers();
        borrowerName.setText(customer.name() + " | Customer ID: " + customer.customerId());
        selectedBorrower.setVisible(true);
        selectedBorrower.setManaged(true);
        customerSearch.clear();
        refreshPrerequisiteNotice();
        message.setVisible(false);
        message.setManaged(false);
    }

    public void focusLoanType() {
        javafx.application.Platform.runLater(loanType::requestFocus);
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

    public void setCustomerSearchService(CustomerService service, Executor executor) {
        customerService = service;
        customerSearchExecutor = executor == null ? Runnable::run : executor;
    }

    public void setPrerequisiteValidator(java.util.function.Consumer<String> validator) {
        prerequisiteValidator = java.util.Objects.requireNonNull(validator);
    }

    /** Cancels delayed or in-flight result updates when the wizard is closed. */
    public void dispose() {
        primarySearchDelay.stop();
        coborrowerSearchDelay.stop();
        primarySearchVersion++;
        coborrowerSearchVersion++;
    }

    public void focusBorrowerSearch() {
        javafx.application.Platform.runLater(() -> {
            if (customerSearch.getScene() != null && customerSearch.isVisible()) customerSearch.requestFocus();
        });
    }

    private void searchCustomers() {
        String query = customerSearch.getText() == null ? "" : customerSearch.getText().strip();
        long request = ++primarySearchVersion;
        primarySearchDelay.stop();
        if (query.isEmpty()) {
            hideResults(customerResults);
            return;
        }
        customerResults.setPlaceholder(new Label(customerService == null
                ? "No customers found" : "Searching customers..."));
        customerResults.setVisible(true);
        customerResults.setManaged(true);
        if (customerService == null) {
            customerResults.getItems().setAll(filterCustomers(customers, query));
            return;
        }
        pendingPrimaryQuery = query;
        pendingPrimaryVersion = request;
        customerResults.getItems().clear();
        primarySearchDelay.playFromStart();
    }

    @FXML private void clearBorrower() {
        draft.primaryBorrower = null;
        selectedBorrower.setVisible(false);
        selectedBorrower.setManaged(false);
        refreshPrerequisiteNotice();
        message.setVisible(false);
        message.setManaged(false);
        customerSearch.requestFocus();
    }

    @FXML private void cancel() {
        if (saving) return;
        if (confirmLeave()) onCancel.run();
    }

    public boolean confirmLeave() {
        boolean hasUncommittedDate = !startDate.getEditor().getText().isBlank()
                || !renewDate.getEditor().getText().isBlank();
        if (!draft.hasData() && !hasUncommittedDate) return true;
        return confirmDiscardDetails();
    }

    protected boolean confirmDiscardDetails() {
        ButtonType keepEditing = new ButtonType("Keep editing", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType discard = new ButtonType("Discard loan", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "The loan has not been saved. Discard the entered details?", keepEditing, discard);
        alert.setTitle("Discard loan details?");
        alert.setHeaderText("Cancel creating this loan?");
        if (loanScroll.getScene() != null && loanScroll.getScene().getWindow() != null)
            alert.initOwner(loanScroll.getScene().getWindow());
        return alert.showAndWait().orElse(keepEditing) == discard;
    }

    @FXML private void next() {
        if (saved || saving) return;
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
            if (onSave == null) {
                showMessage("Saving loans is not connected yet. No loan has been saved. Your details are kept here.");
                return;
            }
            save();
        } else {
            int target = editingReview ? 2 : step + 1;
            editingReview = false;
            showStep(target);
        }
    }

        // Saves on the save executor. Next and Back stay disabled until it finishes, so the loan
    // can't be saved twice, and a failure re-enables the form with a clear message.
    private void save() {
        LoanDraft snapshot = draft.copy();
        saving = true;
        next.setDisable(true);
        back.setDisable(true);
        showMessage("Saving the loan to the mainframe...");
        saveExecutor.execute(() -> {
            try {
                onSave.accept(snapshot);
                runOnFxThread(() -> {
                    saved = true;
                    saving = false;
                    back.setDisable(false);
                    message.setVisible(false);
                    message.setManaged(false);
                });
            } catch (RuntimeException failure) {
                runOnFxThread(() -> {
                    saving = false;
                    next.setDisable(false);
                    back.setDisable(false);
                    showMessage(describeSaveFailure(failure, snapshot.primaryBorrower.name()));
                });
            }
        });
    }

    /** What the person sees when a save fails. Every message says no loan was saved. */
    static String describeSaveFailure(RuntimeException failure, String borrowerName) {
        if (failure instanceof LoanPrerequisiteException prerequisite) {
            return prerequisiteMessage(borrowerName, prerequisite) + " No loan has been saved.";
        }
        if (failure instanceof MainframeException mainframe) {
            return mainframe.getType() == MainframeException.Type.UNAVAILABLE
                    ? "The mainframe could not be reached, so no loan has been saved. "
                            + "Check your connection and try again."
                    : "The mainframe rejected the loan, so no loan has been saved: "
                            + mainframe.getMessage();
        }
        if (failure instanceof LoanSubmissionException || failure instanceof IllegalArgumentException) {
            return failure.getMessage();
        }
        return "The loan could not be saved: "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
    }

    /** Runs the action now if already on the screen thread, otherwise queues it there. */
    static void runOnFxThread(Runnable action) {
        if (javafx.application.Platform.isFxApplicationThread()) action.run();
        else javafx.application.Platform.runLater(action);
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

    @FXML private void back() {
        if (saving) return;
        showStep(Math.max(0, step - 1));
    }

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

    // Choosing a result clears the search while the list is still handling that click, and
    // emptying the list then makes JavaFX throw IndexOutOfBoundsException. Hide it now and
    // empty it once the click is done, unless a new search has shown it again.
    private void hideResults(ListView<CustomersController.CustomerSummary> results) {
        results.setVisible(false);
        results.setManaged(false);
        javafx.application.Platform.runLater(() -> {
            if (!results.isVisible()) results.getItems().clear();
        });
    }

    private void searchCoborrowers() {
        String query = coborrowerSearch.getText() == null ? "" : coborrowerSearch.getText().strip();
        long request = ++coborrowerSearchVersion;
        coborrowerSearchDelay.stop();
        if (query.isEmpty() || draft.coborrowers.size() >= 3) {
            hideResults(coborrowerResults);
            return;
        }
        coborrowerResults.setPlaceholder(new Label(customerService == null
                ? "No customers found" : "Searching customers..."));
        coborrowerResults.setVisible(true);
        coborrowerResults.setManaged(true);
        if (customerService == null) {
            coborrowerResults.getItems().setAll(filterCustomers(customers, query).stream()
                    .filter(this::eligibleCoborrower).toList());
            return;
        }
        pendingCoborrowerQuery = query;
        pendingCoborrowerVersion = request;
        coborrowerResults.getItems().clear();
        coborrowerSearchDelay.playFromStart();
    }

    private void loadPrimaryCustomers(String query, long request) {
        customerSearchExecutor.execute(() -> {
            try {
                List<CustomersController.CustomerSummary> found = searchBackendCustomers(query);
                javafx.application.Platform.runLater(() -> {
                    if (request != primarySearchVersion || !query.equals(text(customerSearch.getText()).strip())) return;
                    customerResults.getItems().setAll(found);
                    customerResults.setPlaceholder(new Label("No customers found"));
                });
            } catch (RuntimeException failure) {
                javafx.application.Platform.runLater(() -> {
                    if (request != primarySearchVersion || !query.equals(text(customerSearch.getText()).strip())) return;
                    customerResults.getItems().clear();
                    customerResults.setPlaceholder(new Label("Customer search is unavailable. Press Enter to retry."));
                });
            }
        });
    }

    private void loadCoborrowers(String query, long request) {
        customerSearchExecutor.execute(() -> {
            try {
                List<CustomersController.CustomerSummary> found = searchBackendCustomers(query);
                javafx.application.Platform.runLater(() -> {
                    if (request != coborrowerSearchVersion
                            || !query.equals(text(coborrowerSearch.getText()).strip())) return;
                    coborrowerResults.getItems().setAll(found.stream().filter(this::eligibleCoborrower).toList());
                    coborrowerResults.setPlaceholder(new Label("No eligible customers found"));
                });
            } catch (RuntimeException failure) {
                javafx.application.Platform.runLater(() -> {
                    if (request != coborrowerSearchVersion
                            || !query.equals(text(coborrowerSearch.getText()).strip())) return;
                    coborrowerResults.getItems().clear();
                    coborrowerResults.setPlaceholder(new Label("Customer search is unavailable. Press Enter to retry."));
                });
            }
        });
    }

    private List<CustomersController.CustomerSummary> searchBackendCustomers(String query) {
        return customerService.search(query).stream().map(AddLoanController::summaryOf).toList();
    }

    private static CustomersController.CustomerSummary summaryOf(CustomerPreview preview) {
        return new CustomersController.CustomerSummary(preview.customerId().value(),
                (text(preview.firstName()) + " " + text(preview.lastName())).strip(), "", "",
                preview.dateOfBirth());
    }

    private static List<CustomersController.CustomerSummary> filterCustomers(
            List<CustomersController.CustomerSummary> customers, String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        return customers.stream().filter(customer -> customer.name().toLowerCase(Locale.ROOT).contains(normalized)
                || customer.customerId().toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }

    private static String text(String value) { return value == null ? "" : value; }

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
        String prerequisiteError = prerequisiteError();
        if (prerequisiteError != null) return prerequisiteError;
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

    private String prerequisiteError() {
        if (draft.primaryBorrower == null) return null;
        if (prerequisiteValidator == null) {
            return "Could not check " + draft.primaryBorrower.name()
                    + "'s loan requirements. Connect to the mainframe and try again.";
        }
        try {
            prerequisiteValidator.accept(draft.primaryBorrower.customerId());
            return null;
        } catch (LoanPrerequisiteException exception) {
            return prerequisiteMessage(draft.primaryBorrower.name(), exception);
        } catch (MainframeException exception) {
            return "Could not check " + draft.primaryBorrower.name()
                    + "'s loan requirements. Check the mainframe connection and try again.";
        }
    }

    private static String prerequisiteMessage(String borrowerName, LoanPrerequisiteException exception) {
        if (LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE.equals(exception.getMessage()))
            return borrowerName + " has no mailing address recorded. "
                    + "Add an address marked as mailing to this customer's record before continuing with the loan.";
        if (LoanPrerequisiteChecker.CONTACT_MESSAGE.equals(exception.getMessage()))
            return borrowerName + " has no phone number or email recorded. "
                    + "Add at least one alternate contact to this customer's record before continuing with the loan.";
        return borrowerName + " has no employer recorded and needs a guarantor. "
                + "Add a guarantor to this customer's record before continuing with the loan.";
    }

    private void refreshPrerequisiteNotice() {
        String error = prerequisiteError();
        prerequisiteNotice.setText(error == null ? "" : error);
        prerequisiteNotice.setVisible(error != null);
        prerequisiteNotice.setManaged(error != null);
    }

    private static boolean validNumber(String text, boolean allowZero) {
        try {
            int sign = new BigDecimal(text.strip()).signum();
            return allowZero ? sign >= 0 : sign > 0;
        } catch (NumberFormatException exception) { return false; }
    }
}
