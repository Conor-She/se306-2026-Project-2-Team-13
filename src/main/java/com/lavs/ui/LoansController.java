package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.business.LoanBrowsingService;
import com.lavs.business.LoanSubmissionService;
import com.lavs.model.Loan;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
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
    private static final String NOT_RECORDED = "Not recorded";

    public record LoanSummary(String loanId, String customerId, String customerName,
            String status, String amount, String type,
            String rate, String paymentFrequency, String rateFrequency,
            String paymentAmount, String startDate, String renewDate) {
        public LoanSummary(String loanId, String customerId, String customerName,
                String status, String amount, String type) {
            this(loanId, customerId, customerName, status, amount, type,
                    NOT_RECORDED, NOT_RECORDED, NOT_RECORDED, NOT_RECORDED, NOT_RECORDED, NOT_RECORDED);
        }
    }

    @FXML private TextField searchField;
    @FXML private String loanStyles;
    @FXML private Label resultsFooter;
    @FXML private TableColumn<LoanSummary, LoanSummary> actionsColumn;
    @FXML private Label resultsHeading;
    @FXML private TableView<LoanSummary> loanTable;
    @FXML private TableColumn<LoanSummary, String> idColumn, customerIdColumn, statusColumn, amountColumn, typeColumn;
    @FXML private VBox listView, detailsView;
    @FXML private LoanDetailsController detailsViewController;

    private VBox addLoanView;
    private AddLoanController activeAddLoanController;
    private final ObservableList<LoanSummary> loans = FXCollections.observableArrayList();
    private final ExecutorService defaultRequestExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "lavs-loan-search");
        thread.setDaemon(true);
        return thread;
    });
    private Executor requestExecutor = defaultRequestExecutor;
    private final AtomicLong searchSequence = new AtomicLong();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(300));
    private LoanBrowsingService loanBrowsingService;
    private CustomerService customerService;
    private LoanSubmissionService submission;
    private java.util.function.Consumer<LoanSubmissionService.SavedLoan> onSaved = loan -> {};

    // Saving talks to the mainframe, so in the app it runs on a background thread.
    private Executor saveExecutor = task -> {
        Thread thread = new Thread(task, "lavs-save-loan");
        thread.setDaemon(true);
        thread.start();
    };

    public void setOnSaved(java.util.function.Consumer<LoanSubmissionService.SavedLoan> onSaved) {
        this.onSaved = onSaved;
    }

    public void setCustomerService(CustomerService service) {
        customerService = service;
        detailsViewController.setCustomerService(service);
        if (activeAddLoanController != null && service != null) {
            activeAddLoanController.setCustomerSearchService(service, requestExecutor);
        }
    }

    public void setLoanSubmissionService(LoanSubmissionService submission) {
        this.submission = submission;
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
        if (activeAddLoanController != null && customerService != null) {
            activeAddLoanController.setCustomerSearchService(customerService, requestExecutor);
        }
    }

    public void setSaveExecutor(Executor saveExecutor) {
        this.saveExecutor = saveExecutor;
    }

    @FXML private void openAddLoan() throws java.io.IOException {
        openAddLoan(null, this::showListAndFocusSearch);
    }

    public void openAddLoan(CustomersController.CustomerSummary customer, Runnable onCancel) throws java.io.IOException {
        var loader = new javafx.fxml.FXMLLoader(getClass().getResource("fxml/addLoan.fxml"));
        loader.setControllerFactory(type -> createAddLoanController());
        VBox editor = loader.load();
        AddLoanController controller = loader.getController();
        controller.setSaveExecutor(saveExecutor);
        controller.configure(List.of(), () -> {
            showList(true);
            onCancel.run();
        });
        if (customerService != null) {
            controller.setCustomerSearchService(customerService, requestExecutor);
        }
        if (submission != null) {
            controller.setPrerequisiteValidator(submission::validatePrerequisites);
        }
        if (customer != null) controller.setPrimaryBorrower(customer);
        controller.setOnSave(draft -> {
            if (submission == null) {
                throw new IllegalStateException("Saving loans is not connected to the mainframe.");
            }
            // The slow part runs off the screen thread, and throws if the loan wasn't created.
            var saved = submission.submit(draft);
            // Only a loan that really exists on the mainframe reaches the screen.
            AddLoanController.runOnFxThread(() -> {
                onSaved.accept(saved);
                showList(true);
                if (customer != null) {
                    onCancel.run();
                } else {
                    searchField.setText(saved.loanId());
                    searchLoans();
                }
            });
        });
        showList(true);
        listView.setVisible(false);
        listView.setManaged(false);
        addLoanView = editor;
        activeAddLoanController = controller;
        VBox.setVgrow(editor, javafx.scene.layout.Priority.ALWAYS);
        ((VBox) listView.getParent()).getChildren().add(editor);
        if (customer == null) controller.focusBorrowerSearch();
        else controller.focusLoanType();
    }

    protected AddLoanController createAddLoanController() { return new AddLoanController(); }

    @FXML private void initialize() {
        listView.getParent().getStylesheets().add("data:text/css;base64," + java.util.Base64.getEncoder()
                .encodeToString(loanStyles.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        resultsFooter.textProperty().bind(javafx.beans.binding.Bindings.size(loans).asString("Showing %d loans"));
        actionsColumn.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue()));
        actionsColumn.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            private final javafx.scene.control.Hyperlink view = new javafx.scene.control.Hyperlink("View loan  →");
            {
                view.getStyleClass().add("loan-profile-link");
                view.setOnAction(event -> { if (getItem() != null) openDetails(getItem()); });
                view.setOnMouseClicked(javafx.event.Event::consume);
            }
            @Override protected void updateItem(LoanSummary value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : view);
                if (!empty && value != null) view.setAccessibleText("View loan " + value.loanId());
            }
        });
        idColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().loanId()));
        customerIdColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().customerId()));
        statusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().status()));
        amountColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().amount()));
        typeColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().type()));
        loanTable.setItems(loans);
        loanTable.getSortOrder().add(idColumn);
        resultsHeading.setText("Search loans");
        updateEmptyState();
        searchDelay.setOnFinished(event -> searchLoans());
        searchField.textProperty().addListener((observable, oldValue, value) -> {
            searchSequence.incrementAndGet();
            loans.clear();
            loanTable.setTooltip(null);
            if (value == null || value.isBlank()) {
                searchLoans();
            } else {
                resultsHeading.setText("Searching loans...");
                loanTable.setPlaceholder(new Label("Searching loans..."));
                searchDelay.playFromStart();
            }
        });
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
        searchDelay.stop();
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
        return summaryFor(listing.loan(), listing.customerName());
    }

    static LoanSummary summaryFor(Loan loan, String customerName) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        String status = loan.getStatus() == null ? "Not recorded" : titleCase(loan.getStatus().name());
        String type = loan.getType() == null ? "Not recorded" : titleCase(loan.getType().name());
        return new LoanSummary(loan.getLoanId(), loan.getCustomerId(), customerName, status,
                currency.format(loan.getPrincipal()), type,
                java.math.BigDecimal.valueOf(loan.getInterestRate()).stripTrailingZeros().toPlainString() + "%",
                enumText(loan.getPaymentFrequency()), enumText(loan.getRateFrequency()),
                currency.format(loan.getPaymentAmount()), dateText(loan.getStartDate()), dateText(loan.getRenewDate()));
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

    private static final java.time.format.DateTimeFormatter DATE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ENGLISH);

    private static String enumText(Enum<?> value) {
        return value == null ? NOT_RECORDED : titleCase(value.name());
    }

    private static String dateText(java.time.LocalDate date) {
        return date == null ? NOT_RECORDED : date.format(DATE_FORMAT);
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
        searchDelay.stop();
        searchSequence.incrementAndGet();
        if (activeAddLoanController != null) activeAddLoanController.dispose();
        defaultRequestExecutor.shutdownNow();
        detailsViewController.dispose();
    }

    void openDetails(LoanSummary loan) {
        detailsViewController.setLoan(loan);
        showList(false);
    }

    /** Opens a loan's details page by ID, e.g. from a customer's Loans tab. */
    public void openLoan(String loanId, Runnable onNotFound) {
        if (loanBrowsingService == null) { onNotFound.run(); return; }
        requestExecutor.execute(() -> {
            try {
                var match = loanBrowsingService.search(loanId).loans().stream()
                        .filter(listing -> loanId.equals(listing.loan().getLoanId())).findFirst();
                Platform.runLater(() -> {
                    if (match.isPresent()) openDetails(summaryOf(match.get()));
                    else onNotFound.run();
                });
            } catch (RuntimeException failure) {
                Platform.runLater(onNotFound);
            }
        });
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
            if (activeAddLoanController != null) {
                activeAddLoanController.dispose();
                activeAddLoanController = null;
            }
        }
        listView.setVisible(visible);
        listView.setManaged(visible);
        detailsView.setVisible(!visible);
        detailsView.setManaged(!visible);
    }
}
