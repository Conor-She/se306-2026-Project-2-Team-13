package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.model.CustomerPreview;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Selects an existing customer, discarding stale asynchronous search results. */
final class GuarantorCustomerPicker {
    private final TextField input;
    private final ListView<CustomerPreview> results = new ListView<>();
    private final Label selected = new Label();
    private final PauseTransition delay = new PauseTransition(Duration.millis(250));
    private final CustomerService service;
    private final Set<String> excluded;
    private String selectedId;
    private long version;
    private boolean choosing;
    private boolean closed;

    static void install(Dialog<?> dialog, CustomerService service, Set<String> excluded, String existingId) {
        new GuarantorCustomerPicker(dialog, service, excluded, existingId);
    }

    private GuarantorCustomerPicker(Dialog<?> dialog, CustomerService service, Set<String> excluded, String existingId) {
        this.service = service;
        this.excluded = Set.copyOf(excluded);
        DialogPane pane = dialog.getDialogPane();
        VBox form = (VBox) ((ScrollPane) pane.getContent()).getContent();
        input = (TextField) form.lookup("#edit_customerId");
        input.setPromptText("Search by customer name or ID");
        input.setAccessibleText("Search for a guarantor by customer name or ID");
        results.setId("guarantorCustomerResults");
        results.setAccessibleText("Matching customers for guarantor");
        results.setPrefHeight(140);
        results.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(CustomerPreview customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null ? null : name(customer) + " | " + customer.customerId().value());
            }
        });
        selected.setId("guarantorSelectedCustomer");
        selected.setWrapText(true);
        selectedId = existingId;
        selected.setText(existingId == null ? "Select an existing customer from the results."
                : "Selected customer: " + existingId);
        int position = form.getChildren().indexOf(input);
        form.getChildren().addAll(position + 1, java.util.List.of(results, selected));
        showResults(false);
        input.textProperty().addListener((observable, old, value) -> {
            if (choosing) return;
            selectedId = null;
            selected.setText("Select an existing customer from the results.");
            ++version;
            delay.stop();
            results.getItems().clear();
            showResults(!value.isBlank());
            results.setPlaceholder(new Label("Searching customers..."));
            if (!value.isBlank()) delay.playFromStart();
        });
        delay.setOnFinished(event -> search());
        input.setOnAction(event -> { delay.stop(); search(); });
        results.getSelectionModel().selectedItemProperty().addListener((observable, old, customer) -> {
            if (customer == null) return;
            choosing = true;
            selectedId = customer.customerId().value();
            input.setText(selectedId);
            choosing = false;
            ++version;
            delay.stop();
            selected.setText("Selected: " + name(customer) + " | " + selectedId);
            showResults(false);
        });
        pane.getButtonTypes().stream().filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .map(pane::lookupButton).forEach(button -> button.addEventFilter(ActionEvent.ACTION, event -> {
                    if (selectedId == null || !selectedId.equals(input.getText().strip())) {
                        event.consume();
                        Label error = (Label) pane.lookup("#error_customerId");
                        error.setText("Search and select an existing customer.");
                        error.setVisible(true); error.setManaged(true);
                    }
                }));
        dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN, event -> { closed = true; ++version; delay.stop(); });
    }

    private void search() {
        String query = input.getText().strip();
        if (query.isEmpty() || closed) return;
        long request = ++version;
        showResults(true);
        results.setPlaceholder(new Label("Searching customers..."));
        CompletableFuture.supplyAsync(() -> service.search(query)).whenComplete((customers, failure) ->
                Platform.runLater(() -> {
                    if (closed || version != request) return;
                    results.getItems().clear();
                    if (failure != null) {
                        results.setPlaceholder(new Label("Customer search is unavailable. Press Enter to retry."));
                    } else {
                        results.getItems().setAll(customers.stream()
                                .filter(customer -> !excluded.contains(customer.customerId().value())).toList());
                        results.setPlaceholder(new Label("No eligible customers found"));
                    }
                }));
    }

    private void showResults(boolean visible) { results.setVisible(visible); results.setManaged(visible); }
    private static String name(CustomerPreview customer) {
        return (java.util.Objects.toString(customer.firstName(), "") + " "
                + java.util.Objects.toString(customer.lastName(), "")).strip();
    }
}
