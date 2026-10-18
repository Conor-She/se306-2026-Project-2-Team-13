package com.lavs.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.concurrent.Task;
import com.lavs.business.CustomerService;
import com.lavs.model.CustomerId;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Searches and retrieves customers through the signed-in service. */
public class CustomersController {
    private java.util.function.Supplier<com.lavs.business.CustomerRegistrationService> registrationServiceFactory;
    private final javafx.collections.ObservableList<CustomerSummary> customers = FXCollections.observableArrayList();
    private VBox addCustomerView;
    private AddCustomerController addCustomerController;
    private VBox root;
    private CustomerService customerService;
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(300));
    private long searchVersion;
    private final java.util.concurrent.ExecutorService requests =
            java.util.concurrent.Executors.newSingleThreadExecutor(action -> {
                Thread thread = new Thread(action, "lavs-customers");
                thread.setDaemon(true);
                return thread;
            });

    public void setCustomerService(CustomerService service) {
        customerService = java.util.Objects.requireNonNull(service);
        detailsViewController.setCustomerService(service);
    }

    public void dispose() {
        searchDelay.stop();
        searchVersion++;
        requests.shutdownNow();
    }

    public CustomersController() {
        registrationServiceFactory = () -> {
            throw new IllegalStateException("Customer registration is not connected");
        };
    }

    public CustomersController(com.lavs.business.CustomerRegistrationService registrationService) {
        java.util.Objects.requireNonNull(registrationService);
        this.registrationServiceFactory = () -> registrationService;
    }

    public void setRegistrationServiceFactory(java.util.function.Supplier<com.lavs.business.CustomerRegistrationService> factory) {
        this.registrationServiceFactory = java.util.Objects.requireNonNull(factory);
    }
    public record CustomerSummary(String customerId, String name, String phone, String email,
            LocalDate dateOfBirth) {
        public CustomerSummary(String customerId, String name, String phone, String email) {
            this(customerId, name, phone, email, null);
        }
        boolean matches(String query) {
            return List.of(customerId, name, phone, email).stream()
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query));
        }
    }
    @FXML private TextField searchField;
    @FXML private Label resultsHeading;
    @FXML private Label resultsFooter;
    @FXML private TableView<CustomerSummary> customerTable;
    @FXML private TableColumn<CustomerSummary, String> idColumn, nameColumn;
    @FXML private TableColumn<CustomerSummary, CustomerSummary> actionsColumn;
    @FXML private TableColumn<CustomerSummary, LocalDate> dateOfBirthColumn;
    @FXML private VBox listView, detailsView;
    @FXML private CustomerDetailsController detailsViewController;

    @FXML private void initialize() {
        root = (VBox) listView.getParent();
        idColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().customerId()));
        nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        nameColumn.setCellFactory(column -> new TableCell<>() {
            private final Label initials = new Label();
            private final Label name = new Label();
            private final javafx.scene.layout.HBox content = new javafx.scene.layout.HBox(12, initials, name);
            {
                initials.getStyleClass().add("customer-initials");
                initials.setMinSize(32, 32);
                initials.setPrefSize(32, 32);
                initials.setMaxSize(32, 32);
                name.setStyle("-fx-text-fill: #393a7b;");
                content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : content);
                if (!empty && value != null) {
                    String[] parts = value.strip().split("\\s+");
                    String letters = value.isBlank() ? "?" : parts[0].substring(0, 1)
                            + (parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "");
                    initials.setText(letters.toUpperCase(Locale.ROOT));
                    name.setText(value);
                }
            }
        });
        actionsColumn.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue()));
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Hyperlink profile = new Hyperlink("View profile  →");
            {
                profile.getStyleClass().add("customer-profile-link");
                profile.setOnAction(event -> {
                    if (getItem() != null) openCustomer(getItem());
                });
                profile.setOnMouseClicked(javafx.event.Event::consume);
            }
            @Override protected void updateItem(CustomerSummary value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : profile);
                if (!empty && value != null) profile.setAccessibleText("View profile for " + value.name());
            }
        });
        resultsFooter.textProperty().bind(javafx.beans.binding.Bindings.size(customers)
                .asString("Showing %d customers"));
        dateOfBirthColumn.setCellValueFactory(c ->
                new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue().dateOfBirth()));
        dateOfBirthColumn.setCellFactory(column -> new TableCell<>() {
            private final DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/uuuu");
            @Override protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty ? null : date == null ? "Not provided" : format.format(date));
            }
        });
        var sorted = new SortedList<>(customers);
        sorted.comparatorProperty().bind(customerTable.comparatorProperty());
        customerTable.setItems(sorted);
        customerTable.getSortOrder().add(nameColumn);
        customerTable.setPlaceholder(new Label("Search Customers"));
        resultsHeading.setText("Enter a customer name or ID to search");
        searchDelay.setOnFinished(event -> refreshCustomers());
        searchField.textProperty().addListener((obs, oldValue, value) -> {
            searchVersion++;
            customers.clear();
            if (value.isBlank()) {
                searchDelay.stop();
                refreshCustomers();
            } else {
                resultsHeading.setText("Searching customers...");
                searchDelay.playFromStart();
            }
        });
        searchField.setOnAction(event -> { searchDelay.stop(); refreshCustomers(); });
        customerTable.setRowFactory(table -> {
            TableRow<CustomerSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && !row.isEmpty()) {
                    openCustomer(row.getItem());
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

    private static String text(String value) { return value == null ? "" : value; }

    private void refreshCustomers() {
        long version = ++searchVersion;
        String query = searchField.getText();
        if (query.isBlank()) {
            customers.clear();
            resultsHeading.setText("Search customers");
            customerTable.setPlaceholder(new Label("Enter a customer name or ID to search"));
            return;
        }
        if (customerService == null) {
            resultsHeading.setText("Customer search is not connected. Sign in again.");
            return;
        }
        resultsHeading.setText("Searching customers...");
        customers.clear();
        Task<List<CustomerSummary>> task = new Task<>() {
            @Override protected List<CustomerSummary> call() {
                return customerService.search(query).stream().map(preview -> new CustomerSummary(
                        preview.customerId().value(),
                        (text(preview.firstName()) + " " + text(preview.lastName())).strip(), "", "", preview.dateOfBirth()))
                        .toList();
            }
        };
        task.setOnSucceeded(event -> {
            if (version != searchVersion) return;
            customers.setAll(task.getValue());
            resultsHeading.setText("Search results (" + customers.size() + ")");
            customerTable.setPlaceholder(new Label("No customers found"));
        });
        task.setOnFailed(event -> {
            if (version != searchVersion) return;
            resultsHeading.setText("Could not load customers. Press Enter to retry.");
            customerTable.setPlaceholder(new Label("Customer search is unavailable"));
        });
        requests.execute(task);
    }

    void openCustomer(CustomerSummary summary) {
        if (customerService == null || customerTable.isDisabled()) return;
        customerTable.setDisable(true);
        searchField.setDisable(true);
        searchDelay.stop();
        long version = ++searchVersion;
        resultsHeading.setText("Loading customer details...");
        Task<com.lavs.model.CustomerDetails> task = new Task<>() {
            @Override protected com.lavs.model.CustomerDetails call() {
                return customerService.getById(new CustomerId(summary.customerId()));
            }
        };
        task.setOnSucceeded(event -> {
            customerTable.setDisable(false);
            searchField.setDisable(false);
            if (version != searchVersion) return;
            resultsHeading.setText("Search results (" + customers.size() + ")");
            detailsViewController.setCustomerDetails(task.getValue());
            showList(false);
        });
        task.setOnFailed(event -> {
            customerTable.setDisable(false);
            searchField.setDisable(false);
            if (version != searchVersion) return;
            resultsHeading.setText("Could not retrieve customer details. Select the customer to retry.");
            java.util.logging.Logger.getLogger(CustomersController.class.getName()).log(
                    java.util.logging.Level.WARNING, "Could not retrieve customer details", task.getException());
        });
        requests.execute(task);
    }

    public void onShown() {
        showList(true);
        searchDelay.stop();
        searchField.clear();
        searchDelay.stop();
        refreshCustomers();
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

    public void addSessionLoan(com.lavs.business.LoanSubmissionService.SavedLoan loan) {
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
        searchDelay.stop();
        searchVersion++;
        closeEditor();
        listView.setVisible(false);
        listView.setManaged(false);
        detailsView.setVisible(false);
        detailsView.setManaged(false);
        addCustomerController = new AddCustomerController(registrationServiceFactory.get(), this::onShown, (id, draft) -> {
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
