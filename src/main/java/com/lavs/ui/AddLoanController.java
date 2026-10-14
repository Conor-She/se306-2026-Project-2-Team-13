package com.lavs.ui;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
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
    private final List<CustomersController.CustomerSummary> coborrowers = new ArrayList<>();
    private int step;
    private boolean editingReview;
    @FXML private Label reviewNotice;
    private String activeMarkerStyle, inactiveMarkerStyle, activeNameStyle, inactiveNameStyle;
    private CustomersController.CustomerSummary borrower;
    private List<CustomersController.CustomerSummary> customers = List.of();
    private Runnable onCancel = () -> {};

    @FXML private void initialize() {
        ScrollPaneAppearance.install(loanScroll, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(customerResults, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(coborrowerResults, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        activeMarkerStyle = marker0.getStyle();
        inactiveMarkerStyle = marker1.getStyle();
        activeNameStyle = stepName0.getStyle();
        inactiveNameStyle = stepName1.getStyle();
        coborrowerSearch.textProperty().addListener((obs, old, value) -> searchCoborrowers());
        coborrowerResults.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value == null || coborrowers.size() >= 3 || !eligibleCoborrower(value)) return;
            coborrowers.add(value);
            coborrowerSearch.clear();
            renderCoborrowers();
        });
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
        coborrowerResults.setCellFactory(customerResults.getCellFactory());
        customerSearch.textProperty().addListener((obs, old, value) -> searchCustomers());
        customerResults.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value == null) return;
            borrower = value;
            coborrowers.removeIf(c -> c.customerId().equals(value.customerId()));
            renderCoborrowers();
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
        if (error != null) { showMessage(error); return; }
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
        reviewBorrower.setText(borrower.name() + " | Customer ID: " + borrower.customerId());
        reviewType.setText(loanType.getValue());
        java.text.NumberFormat currency = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"));
        reviewPrincipal.setText(currency.format(new BigDecimal(principal.getText().strip())));
        reviewInterest.setText(new BigDecimal(interestRate.getText().strip()).stripTrailingZeros().toPlainString() + "%");
        reviewPaymentFrequency.setText(paymentFrequency.getValue());
        reviewRateFrequency.setText(rateFrequency.getValue());
        reviewTerm.setText(Integer.parseInt(term.getText().strip()) + " months");
        var dateFormat = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        reviewStartDate.setText(startDate.getValue().format(dateFormat));
        reviewRenewDate.setText(renewDate.getValue().format(dateFormat));
        reviewStatus.setText(status.getValue());
        reviewCoborrowerCount.setText("Co-borrowers (" + coborrowers.size() + "/3)");
        reviewCoborrowers.getChildren().clear();
        if (coborrowers.isEmpty()) {
            reviewCoborrowers.getChildren().add(new Label("No co-borrowers added."));
        } else {
            for (var customer : coborrowers) {
                Label name = new Label(customer.name() + " | Customer ID: " + customer.customerId());
                name.setWrapText(true);
                name.setMinWidth(0);
                reviewCoborrowers.getChildren().add(name);
            }
        }
    }

    private boolean eligibleCoborrower(CustomersController.CustomerSummary customer) {
        return (borrower == null || !borrower.customerId().equals(customer.customerId()))
                && coborrowers.stream().noneMatch(c -> c.customerId().equals(customer.customerId()));
    }

    private void searchCoborrowers() {
        String query = coborrowerSearch.getText().strip().toLowerCase(Locale.ROOT);
        coborrowerResults.getItems().setAll(query.isEmpty() || coborrowers.size() >= 3 ? List.of()
                : customers.stream().filter(this::eligibleCoborrower)
                .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(query)
                        || c.customerId().toLowerCase(Locale.ROOT).contains(query)).toList());
        coborrowerResults.setVisible(!query.isEmpty() && coborrowers.size() < 3);
        coborrowerResults.setManaged(coborrowerResults.isVisible());
    }

    @FXML private void focusCoborrowerSearch() {
        javafx.application.Platform.runLater(() -> {
            if (step == 1 && !coborrowerSearch.isDisabled()) coborrowerSearch.requestFocus();
        });
    }

    private void renderCoborrowers() {
        coborrowerRows.getChildren().clear();
        for (var customer : coborrowers) {
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
                coborrowers.remove(customer);
                renderCoborrowers();
                searchCoborrowers();
                focusCoborrowerSearch();
            });
            HBox row = new HBox(12, name, spacer, remove);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setStyle(coborrowerRowStyle);
            coborrowerRows.getChildren().add(row);
        }
        coborrowerCount.setText("Added co-borrowers (" + coborrowers.size() + "/3)");
        emptyCoborrowers.setVisible(coborrowers.isEmpty());
        emptyCoborrowers.setManaged(coborrowers.isEmpty());
        coborrowerSearch.setDisable(coborrowers.size() >= 3);
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
