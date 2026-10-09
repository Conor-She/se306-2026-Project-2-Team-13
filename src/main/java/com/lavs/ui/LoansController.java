package com.lavs.ui;

import java.util.List;
import java.util.Locale;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
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
    public record LoanSummary(String loanId, String customerId, String customerName,
            String status, String amount, String type) {
        boolean matches(String query) {
            return List.of(loanId, customerId, customerName, status, amount, type).stream()
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query));
        }
    }
    @FXML private TextField searchField;
    @FXML private Label resultsHeading;
    @FXML private TableView<LoanSummary> loanTable;
    @FXML private TableColumn<LoanSummary, String> idColumn, customerIdColumn, statusColumn, amountColumn, typeColumn;
    @FXML private VBox listView, detailsView;
    @FXML private LoanDetailsController detailsViewController;

    @FXML private void initialize() {
        idColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().loanId()));
        customerIdColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().customerId()));
        statusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().status()));
        amountColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().amount()));
        typeColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().type()));
        var filtered = new FilteredList<>(FXCollections.observableArrayList(loadLoans()));
        var sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(loanTable.comparatorProperty());
        loanTable.setItems(sorted);
        loanTable.getSortOrder().add(idColumn);
        loanTable.setPlaceholder(new Label("No loans found"));
        resultsHeading.setText("All loans (" + filtered.size() + ")");
        searchField.textProperty().addListener((obs, oldValue, value) -> {
            String query = value.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(loan -> loan.matches(query));
            resultsHeading.setText((query.isEmpty() ? "All loans (" : "Search results (") + filtered.size() + ")");
        });
        loanTable.setRowFactory(table -> {
            TableRow<LoanSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) openDetails(row.getItem());
            });
            return row;
        });
        detailsViewController.setOnBack(this::showListAndFocusSearch);
    }

    /** Replace this method with the backend loan-summary call when available. */
    protected List<LoanSummary> loadLoans() {
        return List.of(
            new LoanSummary("L008", "C008", "Sophie Wilson", "Approved", "$10,000", "Table Loan"),
            new LoanSummary("L003", "C003", "Charlotte Brown", "Pending", "$5,000", "Interest Only"),
            new LoanSummary("L001", "C001", "Amelia Adams", "Approved", "$15,000", "Table Loan"),
            new LoanSummary("L006", "C006", "Liam Patel", "Rejected", "$8,000", "Reducing Balance"),
            new LoanSummary("L004", "C004", "Ethan Chen", "Pending", "$12,000", "Interest Only"),
            new LoanSummary("L010", "C010", "Zoe Williams", "Approved", "$20,000", "Reducing Balance"),
            new LoanSummary("L002", "C002", "Benjamin Baker", "Rejected", "$6,000", "Interest Only"),
            new LoanSummary("L009", "C009", "William Wong", "Approved", "$18,000", "Reducing Balance"),
            new LoanSummary("L005", "C005", "Isla Jones", "Pending", "$9,000", "Table Loan"),
            new LoanSummary("L007", "C007", "Oliver Smith", "Approved", "$14,000", "Table Loan"));
    }

    public void onShown() {
        showList(true);
        searchField.clear();
        idColumn.setSortType(TableColumn.SortType.ASCENDING);
        loanTable.getSortOrder().setAll(List.of(idColumn));
        loanTable.sort();
        focusSearch();
    }

    private void openDetails(LoanSummary loan) {
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
        listView.setVisible(visible);
        listView.setManaged(visible);
        detailsView.setVisible(!visible);
        detailsView.setManaged(!visible);
    }
}
