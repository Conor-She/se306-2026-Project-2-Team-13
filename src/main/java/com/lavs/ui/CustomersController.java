package com.lavs.ui;

import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Searchable customer list with a details placeholder. */
public class CustomersController {
    private final com.lavs.business.CustomerRegistrationService registrationService;
    private final javafx.collections.ObservableList<CustomerSummary> customers = FXCollections.observableArrayList();
    private VBox addCustomerView;
    private AddCustomerController addCustomerController;
    private VBox root;
    public CustomersController() {
        this(new com.lavs.business.StubCustomerRegistrationService());
    }

    public CustomersController(com.lavs.business.CustomerRegistrationService registrationService) {
        this.registrationService = java.util.Objects.requireNonNull(registrationService);
    }
    public record CustomerSummary(String customerId, String name, String phone, String email) {
        boolean matches(String query) {
            return List.of(customerId, name, phone, email).stream()
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query));
        }
    }
    @FXML private TextField searchField;
    @FXML private Label resultsHeading;
    @FXML private TableView<CustomerSummary> customerTable;
    @FXML private TableColumn<CustomerSummary, String> idColumn, nameColumn, phoneColumn, emailColumn;
    @FXML private VBox listView, detailsView;
    @FXML private CustomerDetailsController detailsViewController;

    @FXML private void initialize() {
        root = (VBox) listView.getParent();
        idColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().customerId()));
        nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        phoneColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().phone()));
        emailColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().email()));
        customers.setAll(loadCustomers());
        var filtered = new FilteredList<>(customers);
        var sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(customerTable.comparatorProperty());
        customerTable.setItems(sorted);
        customerTable.getSortOrder().add(nameColumn);
        customerTable.setPlaceholder(new Label("No customers found"));
        resultsHeading.setText("All customers (" + filtered.size() + ")");
        filtered.addListener((javafx.collections.ListChangeListener<CustomerSummary>) change ->
            resultsHeading.setText((searchField.getText().isBlank() ? "All customers (" : "Search results (") + filtered.size() + ")"));
        searchField.textProperty().addListener((obs, oldValue, value) -> {
            String query = value.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(customer -> customer.matches(query));
            resultsHeading.setText((query.isEmpty() ? "All customers (" : "Search results (") + filtered.size() + ")");
        });
        customerTable.setRowFactory(table -> {
            TableRow<CustomerSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && !row.isEmpty()) {
                    detailsViewController.setCustomer(row.getItem());
                    showList(false);
                }
            });
            return row;
        });
        detailsViewController.setOnCustomerChanged(updated -> {
            for (int index = 0; index < customers.size(); index++) {
                if (customers.get(index).customerId().equals(updated.customerId())) {
                    customers.set(index, updated);
                    break;
                }
            }
        });
        detailsViewController.setOnBack(() -> { showList(true); focusSearch(); });
    }

    /** Replace this method with the backend customer-summary call when available. */
    protected List<CustomerSummary> loadCustomers() {
        return List.of(
            new CustomerSummary("C008", "Sophie Wilson", "021 555 0108", "sophie.wilson@example.com"),
            new CustomerSummary("C003", "Charlotte Brown", "021 555 0103", "charlotte.brown@example.com"),
            new CustomerSummary("C001", "Amelia Adams", "021 555 0101", "amelia.adams@example.com"),
            new CustomerSummary("C006", "Liam Patel", "021 555 0106", "liam.patel@example.com"),
            new CustomerSummary("C004", "Ethan Chen", "021 555 0104", "ethan.chen@example.com"),
            new CustomerSummary("C010", "Zoe Williams", "021 555 0110", "zoe.williams@example.com"),
            new CustomerSummary("C002", "Benjamin Baker", "021 555 0102", "benjamin.baker@example.com"),
            new CustomerSummary("C009", "William Wong", "021 555 0109", "william.wong@example.com"),
            new CustomerSummary("C005", "Isla Jones", "021 555 0105", "isla.jones@example.com"),
            new CustomerSummary("C007", "Oliver Smith", "021 555 0107", "oliver.smith@example.com"));
    }

    public void onShown() {
        showList(true);
        searchField.clear();
        nameColumn.setSortType(TableColumn.SortType.ASCENDING);
        customerTable.getSortOrder().setAll(List.of(nameColumn));
        customerTable.sort();
        focusSearch();
    }
    public boolean confirmLeave() {
        return addCustomerController == null || addCustomerController.confirmLeave();
    }

    public void setOnAddLoan(java.util.function.Consumer<CustomerSummary> action) {
        detailsViewController.setOnAddLoan(action);
    }

    public void addSessionLoan(com.lavs.business.SessionLoanStore.SavedLoan loan) {
        var borrower = loan.draft().primaryBorrower;
        CustomerSummary customer = customers.stream().filter(c -> c.customerId().equals(borrower.customerId()))
                .findFirst().orElse(new CustomerSummary(borrower.customerId(), borrower.name(), "", ""));
        detailsViewController.addSessionLoan(customer, loan);
    }

    public void showCustomerLoans() {
        showList(false);
        detailsViewController.showLoansTab();
    }

    @FXML public void openAddCustomer() {
        if (addCustomerView != null && !confirmLeave()) return;
        closeEditor();
        listView.setVisible(false);
        listView.setManaged(false);
        detailsView.setVisible(false);
        detailsView.setManaged(false);
        addCustomerController = new AddCustomerController(registrationService, this::onShown, (id, draft) -> {
            customers.add(new CustomerSummary(id,
                draft.personal.getOrDefault("firstName", "") + " " + draft.personal.getOrDefault("lastName", ""),
                primaryContact(draft.phones, "number"),
                primaryContact(draft.emails, "email")));
            onShown();
            Alert confirmation = new Alert(Alert.AlertType.INFORMATION, "Customer " + id + " has been saved.", ButtonType.OK);
            confirmation.setHeaderText("Customer added successfully");
            if (root.getScene() != null) confirmation.initOwner(root.getScene().getWindow());
            confirmation.showAndWait();
        });
        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("fxml/addCustomer.fxml"));
        loader.setControllerFactory(type -> addCustomerController);
        try {
            addCustomerView = loader.load();
        } catch (java.io.IOException exception) {
            onShown();
            Alert error = new Alert(Alert.AlertType.ERROR, "Unable to open Add Customer. Please try again.", ButtonType.OK);
            error.setHeaderText("Customer form could not be opened");
            if (root.getScene() != null) error.initOwner(root.getScene().getWindow());
            error.showAndWait();
            return;
        }
        VBox.setVgrow(addCustomerView, javafx.scene.layout.Priority.ALWAYS);
        root.getChildren().add(addCustomerView);
        addCustomerController.focusTitle();
    }

    private static String primaryContact(List<java.util.Map<String, String>> entries, String key) {
        return entries.stream().filter(entry -> "true".equals(entry.get("primary"))).findFirst()
            .or(() -> entries.stream().findFirst()).map(entry -> entry.getOrDefault(key, "")).orElse("");
    }

    private void closeEditor() {
        if (addCustomerView != null) root.getChildren().remove(addCustomerView);
        addCustomerView = null;
        addCustomerController = null;
    }
    private void focusSearch() {
        Platform.runLater(() -> { if (listView.isVisible()) searchField.requestFocus(); });
    }
    private void showList(boolean visible) {
        closeEditor();
        listView.setVisible(visible);
        listView.setManaged(visible);
        detailsView.setVisible(!visible);
        detailsView.setManaged(!visible);
    }
}
