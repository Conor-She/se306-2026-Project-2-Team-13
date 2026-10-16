package com.lavs.ui;

import com.lavs.business.LoanBrowsingService;
import com.lavs.business.LoanCreationService;
import com.lavs.business.LoanWithSchedule;
import com.lavs.model.Loan;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

/** Searchable loan list and navigation to the selected loan's details. */
public class LoansController {
    public static final String NOT_CONNECTED = "Connect to the mainframe to search loans";
    public static final String SEARCH_PROMPT = "Enter a loan ID or customer name or ID";

    public record LoanSummary(String loanId, String customerId, String customerName,
            String status, String amount, String type) {
    }

    @FXML private TextField searchField;
    @FXML private Label resultsHeading;
    @FXML private TableView<LoanSummary> loanTable;
    @FXML private TableColumn<LoanSummary, String> idColumn, customerIdColumn, statusColumn, amountColumn, typeColumn;
    @FXML private VBox listView, detailsView;
    @FXML private LoanDetailsController detailsViewController;

    private VBox addLoanView;
    private final ObservableList<LoanSummary> loans = FXCollections.observableArrayList();
    private final ExecutorService defaultRequestExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "lavs-loan-search");
        thread.setDaemon(true);
        return thread;
    });
    private Executor requestExecutor = defaultRequestExecutor;
    private final AtomicLong searchSequence = new AtomicLong();
    private LoanBrowsingService loanBrowsingService;
    private LoanCreationService loanCreationService;
    private java.util.function.BiConsumer<com.lavs.model.LoanDraft, LoanWithSchedule> onSaved = (draft, loan) -> {};
    private java.util.function.Supplier<List<CustomersController.CustomerSummary>> customerPreviews = List::of;

    public void setCustomerPreviews(java.util.function.Supplier<List<CustomersController.CustomerSummary>> supplier) {
        customerPreviews = supplier;
    }

    public void setOnSaved(
            java.util.function.BiConsumer<com.lavs.model.LoanDraft, LoanWithSchedule> onSaved) {
        this.onSaved = onSaved;
    }

    public void setLoanCreationService(LoanCreationService service) {
        loanCreationService = service;
    }

    public void setLoanBrowsingService(LoanBrowsingService service) {
        loanBrowsingService = service;
        detailsViewController.setLoanBrowsingService(service);
        updateEmptyState();
    }

    /** Allows tests to run backend requests deterministically. */
    public void setRequestExecutor(Executor executor) {
        requestExecutor = executor == null ? defaultRequestExecutor : executor;
        detailsViewController.setRequestExecutor(requestExecutor);
    }

    @FXML private void openAddLoan() throws java.io.IOException {
        openAddLoan(null, this::showListAndFocusSearch);
    }

    public void openAddLoan(CustomersController.CustomerSummary customer, Runnable onCancel) throws java.io.IOException {
        var loader = new javafx.fxml.FXMLLoader(getClass().getResource("fxml/addLoan.fxml"));
        loader.setControllerFactory(type -> createAddLoanController());
        VBox editor = loader.load();
        AddLoanController controller = loader.getController();
        controller.configure(customerPreviews.get(), () -> {
            showList(true);
            onCancel.run();
        });
        if (loanCreationService != null) {
            controller.setPrerequisiteValidator(loanCreationService::validatePrerequisites);
        }
        if (customer != null) controller.setPrimaryBorrower(customer);
        controller.setOnSave(draft -> {
            if (loanCreationService == null) {
                throw new IllegalStateException("Loan creation is not connected to the mainframe.");
            }
            LoanWithSchedule saved = loanCreationService.create(draft);
            onSaved.accept(draft, saved);
            if (customer != null) {
                onCancel.run();
            } else {
                showList(true);
                searchField.setText(saved.getLoan().getLoanId());
                searchLoans();
            }
        });
        showList(true);
        listView.setVisible(false);
        listView.setManaged(false);
        addLoanView = editor;
        VBox.setVgrow(editor, javafx.scene.layout.Priority.ALWAYS);
        ((VBox) listView.getParent()).getChildren().add(editor);
        if (customer == null) controller.focusBorrowerSearch();
        else controller.focusLoanType();
    }

    protected AddLoanController createAddLoanController() { return new AddLoanController(); }

    @FXML private void initialize() {
        idColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().loanId()));
        customerIdColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().customerId()));
        statusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().status()));
        amountColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().amount()));
        typeColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().type()));
        loanTable.setItems(loans);
        loanTable.getSortOrder().add(idColumn);
        resultsHeading.setText("Search loans");
        updateEmptyState();
        searchField.setOnAction(event -> searchLoans());
        loanTable.setRowFactory(table -> {
            TableRow<LoanSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) openDetails(row.getItem());
            });
            return row;
        });
        detailsViewController.setOnBack(this::showListAndFocusSearch);
    }

    @FXML private void searchLoans() {
        String query = searchField.getText() == null ? "" : searchField.getText().strip();
        long request = searchSequence.incrementAndGet();
        loans.clear();

        if (loanBrowsingService == null) {
            resultsHeading.setText("Search loans");
            loanTable.setPlaceholder(new Label(NOT_CONNECTED));
            return;
        }
        if (query.isEmpty()) {
            resultsHeading.setText("Search loans");
            loanTable.setPlaceholder(new Label(SEARCH_PROMPT));
            return;
        }

        resultsHeading.setText("Searching loans...");
        loanTable.setPlaceholder(new Label("Searching loans..."));
        requestExecutor.execute(() -> {
            try {
                LoanBrowsingService.LoanSearchResult result = loanBrowsingService.search(query);
                Platform.runLater(() -> {
                    if (searchSequence.get() != request) return;
                    loans.setAll(result.loans().stream().map(LoansController::summaryOf).toList());
                    resultsHeading.setText("Search results (" + loans.size() + ")");
                    updateEmptyState();
                    if (result.truncated()) {
                        loanTable.setTooltip(new javafx.scene.control.Tooltip(
                                "More customers matched than can be shown. Narrow your search."));
                    } else {
                        loanTable.setTooltip(null);
                    }
                    sortLoans();
                });
            } catch (RuntimeException failure) {
                Platform.runLater(() -> {
                    if (searchSequence.get() != request) return;
                    loans.clear();
                    resultsHeading.setText("Could not load loans. Press Enter to retry.");
                    loanTable.setPlaceholder(new Label("Loan search is unavailable"));
                });
            }
        });
    }

    private static LoanSummary summaryOf(LoanBrowsingService.LoanListing listing) {
        Loan loan = listing.loan();
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        String status = loan.getStatus() == null ? "Not recorded" : titleCase(loan.getStatus().name());
        String type = loan.getType() == null ? "Not recorded" : titleCase(loan.getType().name());
        return new LoanSummary(loan.getLoanId(), loan.getCustomerId(), listing.customerName(), status,
                currency.format(loan.getPrincipal()), type);
    }

    private static String titleCase(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void updateEmptyState() {
        if (resultsHeading == null || loanTable == null) return;
        if (loans.isEmpty()) {
            if (loanBrowsingService == null) loanTable.setPlaceholder(new Label(NOT_CONNECTED));
            else if (searchField == null || searchField.getText().isBlank())
                loanTable.setPlaceholder(new Label(SEARCH_PROMPT));
            else loanTable.setPlaceholder(new Label("No loans found for \"" + searchField.getText().strip() + "\""));
        }
    }

    private void sortLoans() {
        idColumn.setSortType(TableColumn.SortType.ASCENDING);
        loanTable.getSortOrder().setAll(List.of(idColumn));
        loanTable.sort();
    }

    public void onShown() {
        showList(true);
        searchSequence.incrementAndGet();
        searchField.clear();
        loans.clear();
        resultsHeading.setText("Search loans");
        updateEmptyState();
        sortLoans();
        focusSearch();
    }

    public void dispose() {
        searchSequence.incrementAndGet();
        defaultRequestExecutor.shutdownNow();
        detailsViewController.dispose();
    }

    void openDetails(LoanSummary loan) {
        detailsViewController.setLoan(loan);
        showList(false);
    }

    private void showListAndFocusSearch() {
        showList(true);
        focusSearch();
    }

    private void focusSearch() {
        Platform.runLater(() -> { if (listView.isVisible()) searchField.requestFocus(); });
    }

    private void showList(boolean visible) {
        if (addLoanView != null) {
            ((VBox) listView.getParent()).getChildren().remove(addLoanView);
            addLoanView = null;
        }
        listView.setVisible(visible);
        listView.setManaged(visible);
        detailsView.setVisible(!visible);
        detailsView.setManaged(!visible);
    }
}
