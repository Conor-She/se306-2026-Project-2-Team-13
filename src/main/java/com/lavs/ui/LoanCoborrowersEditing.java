package com.lavs.ui;

import com.lavs.business.CoBorrowerService;
import com.lavs.business.CustomerRegistrationService;
import com.lavs.business.CustomerService;
import com.lavs.business.LoanBrowsingService.CoBorrowerDetails;
import com.lavs.model.CustomerPreview;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Displays co-borrowers and persists additions and removals through the mainframe. */
final class LoanCoborrowersEditing {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ENGLISH);

    private final GridPane table;
    private final String rowStyle, actionStyle, iconStyle, deleteHoverStyle;
    private List<CoBorrowerDetails> records = List.of();
    private String loanId;
    private String primaryCustomerId;
    private CustomerService customerService;
    private CoBorrowerService coBorrowerService;
    private Supplier<CustomerRegistrationService> registrationServiceFactory;
    private Executor requestExecutor = ForkJoinPool.commonPool();
    private Runnable onRecordsChanged = () -> {};
    private long searchSequence;

    LoanCoborrowersEditing(GridPane table, String rowStyle, String actionStyle,
            String iconStyle, String hoverStyle, String deleteHoverStyle) {
        this.table = table;
        this.rowStyle = rowStyle;
        this.actionStyle = actionStyle;
        this.iconStyle = iconStyle;
        this.deleteHoverStyle = deleteHoverStyle;
    }

    void configure(CustomerService customerService, CoBorrowerService coBorrowerService,
            Supplier<CustomerRegistrationService> registrationServiceFactory,
            Executor requestExecutor, Runnable onRecordsChanged) {
        this.customerService = customerService;
        this.coBorrowerService = coBorrowerService;
        this.registrationServiceFactory = registrationServiceFactory;
        this.requestExecutor = requestExecutor == null ? ForkJoinPool.commonPool() : requestExecutor;
        this.onRecordsChanged = onRecordsChanged == null ? () -> {} : onRecordsChanged;
    }

    void select(String loanId, String primaryCustomerId) {
        this.loanId = loanId;
        this.primaryCustomerId = primaryCustomerId;
        records = List.of();
        render();
    }

    void setRecords(String loanId, List<CoBorrowerDetails> coBorrowers) {
        if (!loanId.equals(this.loanId)) return;
        records = List.copyOf(coBorrowers);
        render();
    }

    void setUnavailable(String loanId) {
        if (!loanId.equals(this.loanId)) return;
        records = List.of();
        render();
        table.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> Integer.valueOf(1).equals(GridPane.getRowIndex(label)))
                .findFirst().ifPresent(label -> label.setText("Co-borrowers could not be loaded."));
    }

    void add() {
        if (customerService == null || coBorrowerService == null) {
            showError("Co-borrower management is not connected. Sign in again.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        DialogView view = new DialogView(dialog, "coborrowerChoice.fxml");
        dialog.setTitle("Add co-borrower");
        dialog.setHeaderText("How would you like to add the co-borrower?");
        dialog.getDialogPane().setId("addCoborrowerChoice");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(500, 340);
        dialog.setResizable(false);

        Button existing = view.get("chooseExistingCustomer");
        existing.setOnAction(event -> {
            dialog.close();
            showExistingCustomerPicker();
        });

        Button create = view.get("createNewCustomer");
        create.setOnAction(event -> {
            dialog.close();
            showNewCustomerEditor();
        });

        dialog.setOnShown(event -> view.apply(dialog.getDialogPane()));
        initOwner(dialog);
        dialog.show();
    }

    private void showExistingCustomerPicker() {
        ButtonType addType = new ButtonType("Add selected", ButtonBar.ButtonData.OK_DONE);
        Dialog<String> dialog = new Dialog<>();
        DialogView view = new DialogView(dialog, "coborrowerPicker.fxml");
        dialog.setTitle("Add existing customer");
        dialog.setHeaderText("Search for an existing customer");
        DialogPane pane = dialog.getDialogPane();
        pane.setId("existingCoborrowerPicker");
        pane.getButtonTypes().setAll(ButtonType.CANCEL, addType);
        pane.setPrefSize(680, 430);
        dialog.setResizable(false);

        TextField search = view.get("coborrowerCustomerSearch");
        ListView<CustomerPreview> results = view.get("coborrowerCustomerResults");
        results.setCellFactory(list -> new ListCell<>() {
            { DialogView.styleCustomerCell(this); }
            @Override protected void updateItem(CustomerPreview customer, boolean empty) {
                super.updateItem(customer, empty);
                if (empty || customer == null) {
                    setText(null);
                    return;
                }
                String name = (value(customer.firstName()) + " " + value(customer.lastName())).strip();
                String birthDate = customer.dateOfBirth() == null
                        ? "Date of birth not recorded" : DATE_FORMAT.format(customer.dateOfBirth());
                setText(name + "\nCustomer ID: " + customer.customerId().value() + "  •  " + birthDate);
            }
        });
        Label status = view.get("coborrowerSearchStatus");
        dialog.setOnShown(event -> view.apply(pane));

        Button add = (Button) pane.lookupButton(addType);
        add.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());
        PauseTransition delay = new PauseTransition(Duration.millis(300));
        Runnable runSearch = () -> searchCustomers(search.getText(), results, status);
        delay.setOnFinished(event -> runSearch.run());
        search.textProperty().addListener((observable, oldValue, newValue) -> {
            searchSequence++;
            delay.playFromStart();
        });
        search.setOnAction(event -> {
            delay.stop();
            runSearch.run();
        });
        results.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && results.getSelectionModel().getSelectedItem() != null) {
                add.fire();
            }
        });
        dialog.setResultConverter(button -> button == addType
                ? results.getSelectionModel().getSelectedItem().customerId().value() : null);
        dialog.setOnHidden(event -> {
            delay.stop();
            if (dialog.getResult() != null) persistAdd(dialog.getResult());
        });
        initOwner(dialog);
        dialog.show();
        Platform.runLater(search::requestFocus);
    }

    private void searchCustomers(String rawQuery, ListView<CustomerPreview> results, Label status) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        long request = ++searchSequence;
        results.getItems().clear();
        if (query.isEmpty()) {
            status.setText("Enter a customer name or ID.");
            return;
        }
        status.setText("Searching customers...");
        requestExecutor.execute(() -> {
            try {
                List<CustomerPreview> matches = customerService.search(query).stream()
                        .filter(customer -> eligible(customer.customerId().value()))
                        .toList();
                Platform.runLater(() -> {
                    if (request != searchSequence) return;
                    results.getItems().setAll(matches);
                    status.setText(matches.isEmpty()
                            ? "No eligible customers found."
                            : "Select the customer to add.");
                });
            } catch (RuntimeException failure) {
                Platform.runLater(() -> {
                    if (request != searchSequence) return;
                    status.setText("Customer search is unavailable. Press Enter to retry.");
                });
            }
        });
    }

    private boolean eligible(String customerId) {
        return !customerId.equals(primaryCustomerId)
                && records.stream().noneMatch(record -> customerId.equals(record.customerId()));
    }

    private void showNewCustomerEditor() {
        if (registrationServiceFactory == null) {
            showError("Customer registration is not connected. Sign in again.");
            return;
        }
        try {
            Stage[] stage = new Stage[1];
            AddCustomerController[] controller = new AddCustomerController[1];
            controller[0] = new AddCustomerController(registrationServiceFactory.get(),
                    () -> stage[0].close(),
                    (customerId, draft) -> {
                        stage[0].close();
                        persistAdd(customerId);
                    });
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/addCustomer.fxml"));
            loader.setControllerFactory(type -> controller[0]);
            Parent root = loader.load();
            root.setId("newCoborrowerCustomerEditor");

            stage[0] = new Stage();
            Window owner = owner();
            if (owner != null) {
                stage[0].initOwner(owner);
                stage[0].initModality(Modality.WINDOW_MODAL);
            }
            stage[0].setTitle("Create customer for co-borrower");
            stage[0].setScene(new Scene(root, 1100, 760));
            stage[0].setMinWidth(900);
            stage[0].setMinHeight(650);
            stage[0].setOnCloseRequest(event -> {
                if (!controller[0].confirmLeave()) event.consume();
            });
            stage[0].show();
            controller[0].focusTitle();
        } catch (IOException | RuntimeException failure) {
            showError("The new customer form could not be opened. Please try again.");
        }
    }

    private void persistAdd(String customerId) {
        table.setDisable(true);
        requestExecutor.execute(() -> {
            try {
                coBorrowerService.addCoBorrower(loanId, customerId);
                Platform.runLater(() -> {
                    table.setDisable(false);
                    onRecordsChanged.run();
                });
            } catch (RuntimeException failure) {
                Platform.runLater(() -> {
                    table.setDisable(false);
                    showError(message(failure, "The co-borrower could not be added."));
                });
            }
        });
    }

    private void delete(CoBorrowerDetails record) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + name(record) + " from this loan?", ButtonType.CANCEL, ButtonType.OK);
        initOwner(confirmation);
        confirmation.setTitle("Remove co-borrower");
        confirmation.setHeaderText("Remove co-borrower");
        confirmation.setOnHidden(event -> {
            if (confirmation.getResult() != ButtonType.OK) return;
            table.setDisable(true);
            requestExecutor.execute(() -> {
                try {
                    coBorrowerService.removeCoBorrower(loanId, record.coBorrowerId());
                    Platform.runLater(() -> {
                        table.setDisable(false);
                        onRecordsChanged.run();
                    });
                } catch (RuntimeException failure) {
                    Platform.runLater(() -> {
                        table.setDisable(false);
                        showError(message(failure, "The co-borrower could not be removed."));
                    });
                }
            });
        });
        confirmation.show();
    }

    private void render() {
        table.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null
                && GridPane.getRowIndex(node) > 0);
        if (records.isEmpty()) {
            table.add(cell("No co-borrowers recorded for this loan."), 0, 1, 5, 1);
        }
        int row = 1;
        for (CoBorrowerDetails record : records) {
            Region divider = new Region();
            divider.getStyleClass().add("coborrowers-row");
            divider.setStyle(rowStyle);
            divider.setMouseTransparent(true);
            table.add(divider, 0, row, 5, 1);
            Label initials = cell(initials(record));
            initials.setAlignment(Pos.CENTER);
            initials.setMinSize(32, 32);
            initials.setStyle("-fx-background-color: linear-gradient(to bottom right, #e8e7ff, #ceccff); "
                    + "-fx-background-radius: 20; -fx-text-fill: #4947a5; -fx-font-size: 11px;");
            HBox person = new HBox(10, initials, cell(name(record)));
            person.setAlignment(Pos.CENTER_LEFT);
            person.setTranslateX(16);
            table.add(person, 0, row);
            table.add(cell(value(record.customerId())), 1, row);
            table.add(cell(value(record.phone())), 2, row);
            table.add(cell(value(record.email())), 3, row);
            Button delete = iconButton("Remove co-borrower", name(record),
                    "M 3 5 H 19 M 7 5 V 2 H 15 V 5 M 5 5 L 6 20 H 16 L 17 5 M 9 9 V 16 M 13 9 V 16");
            delete.getStyleClass().add("coborrower-delete");
            delete.getGraphic().setStyle(iconStyle + " -fx-stroke: #c34f64;");
            delete.setOnAction(event -> delete(record));
            HBox actions = new HBox(4, delete);
            actions.getStyleClass().add("coborrower-action-buttons");
            actions.setAlignment(Pos.CENTER_LEFT);
            actions.setMinWidth(Region.USE_PREF_SIZE);
            table.add(actions, 4, row++);
        }
        table.getChildren().stream().filter(Label.class::isInstance)
                .forEach(label -> label.setTranslateX(16));
    }

    private Button iconButton(String action, String name, String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("coborrower-action-icon");
        icon.setStyle(iconStyle);
        Button button = new Button(null, icon);
        button.setAccessibleText(action + " for " + name);
        button.setTooltip(new Tooltip(action));
        button.getStyleClass().add("coborrower-actions");
        button.setStyle(actionStyle);
        Runnable refreshStyle = () -> button.setStyle(actionStyle
                + (button.isHover() || button.isFocused() ? deleteHoverStyle : ""));
        button.hoverProperty().addListener((observable, oldValue, newValue) -> refreshStyle.run());
        button.focusedProperty().addListener((observable, oldValue, newValue) -> refreshStyle.run());
        return button;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Co-borrower could not be updated");
        alert.setHeaderText("Co-borrower could not be updated");
        initOwner(alert);
        alert.show();
    }

    private void initOwner(Dialog<?> dialog) {
        Window owner = owner();
        if (owner != null) dialog.initOwner(owner);
    }

    private Window owner() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }

    private static Label cell(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #55558d; -fx-padding: 10 0;");
        return label;
    }

    private static String name(CoBorrowerDetails record) {
        return (value(record.firstName()) + " " + value(record.lastName())).strip();
    }

    private static String initials(CoBorrowerDetails record) {
        String first = value(record.firstName());
        String last = value(record.lastName());
        return (first.isEmpty() ? "" : first.substring(0, 1))
                + (last.isEmpty() ? "" : last.substring(0, 1));
    }

    private static String message(RuntimeException failure, String fallback) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? fallback : failure.getMessage();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
