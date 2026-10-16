package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.model.CustomerNote;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Loads and edits persisted customer notes without blocking the JavaFX thread. */
final class CustomerNotesEditing {
    private static final String EDIT = "-fx-cursor: hand; -fx-padding: 9 16; -fx-background-radius: 7; -fx-border-radius: 7; -fx-background-color: white; -fx-border-color: #9693ca; -fx-text-fill: #393a7b;";
    private final VBox list;
    private final CustomerService service;
    private String customerId;
    private long version;

    CustomerNotesEditing(VBox list, CustomerService service) {
        this.list = list;
        this.service = service;
    }

    void select(String customerId) {
        this.customerId = customerId;
        refresh();
    }

    void refresh() {
        if (customerId == null) return;
        long request = ++version;
        String selectedCustomer = customerId;
        replaceRows(new Label("Loading customer notes..."));
        var task = new Task<List<CustomerNote>>() {
            @Override protected List<CustomerNote> call() { return service.getNotes(selectedCustomer); }
        };
        task.setOnSucceeded(event -> { if (request == version) render(task.getValue()); });
        task.setOnFailed(event -> {
            if (request == version) {
                replaceRows();
                showError("Unable to load customer notes. ", task.getException());
            }
        });
        CompletableFuture.runAsync(task);
    }

    private void replaceRows(javafx.scene.Node... rows) {
        if (list.getChildren().size() > 1) list.getChildren().subList(1, list.getChildren().size()).clear();
        list.getChildren().addAll(rows);
    }

    private void render(List<CustomerNote> notes) {
        replaceRows();
        if (notes.isEmpty()) list.getChildren().add(new Label("No notes added."));
        for (CustomerNote note : notes) {
            Label title = new Label(note.getType());
            title.setStyle("-fx-font-weight: bold; -fx-text-fill: #393a7b;");
            Label text = new Label(note.getNoteText());
            text.setWrapText(true);
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            Label meta = new Label(java.util.stream.Stream.of(note.getDateAdded() == null ? null
                            : CustomerRecordEditor.DISPLAY_DATE.format(note.getDateAdded()), note.getAuthor())
                    .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" · ")));
            meta.setStyle("-fx-text-fill: #747ba6; -fx-font-size: 12;");
            VBox body = new VBox(8, title, text, meta);
            body.setMinWidth(0);
            HBox.setHgrow(body, Priority.ALWAYS);
            Button edit = new Button("Edit");
            edit.setAccessibleText("Edit customer note " + note.getNoteId());
            edit.setStyle(EDIT);
            edit.setOnAction(event -> edit(note));
            Button delete = new Button("Delete");
            delete.setAccessibleText("Delete customer note " + note.getNoteId());
            delete.setStyle(EDIT.replace("#9693ca", "#D95733").replace("#393a7b", "#D95733"));
            delete.setOnAction(event -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this customer note?", ButtonType.CANCEL, ButtonType.OK);
                if (list.getScene() != null) confirm.initOwner(list.getScene().getWindow());
                confirm.showAndWait().filter(ButtonType.OK::equals)
                        .ifPresent(result -> change(() -> service.deleteNote(note.getCustomerId(), note.getNoteId())));
            });
            HBox row = new HBox(16, body, edit, delete);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 16; -fx-background-color: white; -fx-border-color: #dce2ff; -fx-border-radius: 8; -fx-background-radius: 8;");
            list.getChildren().add(row);
        }
    }

    void edit(CustomerNote note) {
        if (customerId == null) return;
        String selectedCustomer = customerId;
        var dialog = new Dialog<String>();
        dialog.setTitle(note == null ? "Add customer note" : "Edit customer note");
        if (list.getScene() != null) dialog.initOwner(list.getScene().getWindow());
        dialog.getDialogPane().setId("customerNoteEditor");
        var title = new TextField(note == null ? "General" : note.getType());
        title.setId("noteTitle");
        title.setAccessibleText("Note title");
        var text = new TextArea(note == null ? "" : note.getNoteText());
        text.setId("noteText");
        text.setAccessibleText("Customer note");
        text.setWrapText(true);
        text.setPrefRowCount(5);
        var error = new Label();
        error.setWrapText(true);
        error.setStyle("-fx-text-fill: #b42318;");
        var form = new VBox(8, new Label("Title"), title, new Label("Note"), text, error);
        form.setPrefWidth(460);
        dialog.getDialogPane().setContent(form);
        var save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, save);
        var saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.setStyle(EDIT);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (title.getText().isBlank() || text.getText().isBlank()) {
                error.setText("Enter a title and note text.");
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == save ? text.getText().strip() : null);
        dialog.showAndWait().ifPresent(value -> change(() -> {
            if (note == null) service.addNote(selectedCustomer, value, title.getText().strip());
            else service.updateNote(new CustomerNote(note.getNoteId(), note.getCustomerId(), value,
                    note.getDateAdded(), title.getText().strip(), note.getAuthor()));
        }));
    }

    private void change(Runnable mutation) {
        long request = ++version;
        list.setDisable(true);
        var task = new Task<Void>() {
            @Override protected Void call() { mutation.run(); return null; }
        };
        task.setOnSucceeded(event -> { list.setDisable(false); refresh(); });
        task.setOnFailed(event -> {
            list.setDisable(false);
            if (request == version) showError("Unable to save the note change. ", task.getException());
        });
        CompletableFuture.runAsync(task);
    }

    private void showError(String prefix, Throwable failure) {
        Label error = new Label(prefix + Objects.toString(failure.getMessage(), "Please try again."));
        error.setWrapText(true);
        error.setStyle("-fx-text-fill: #b42318;");
        Button retry = new Button("Reload notes");
        retry.setStyle(EDIT);
        retry.setOnAction(event -> refresh());
        list.getChildren().addAll(error, retry);
    }
}
