package com.lavs.ui;

import com.lavs.business.CoBorrowerService;
import com.lavs.business.CustomerService;
import com.lavs.business.LoanBrowsingService;
import com.lavs.business.MainframeCustomerRegistrationService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.CoBorrower;
import com.lavs.model.Customer;
import com.lavs.model.CustomerPreview;
import com.lavs.model.Loan;
import java.time.LocalDate;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// AI-assisted: Helped write the co-borrower display tests.
class LoanCoborrowersTest {
    private FXMLLoader loader;
    private Stage stage;
    private GridPane table;
    private FakeMainframeGateway gateway;

    @BeforeAll static void start() throws Exception {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach void setup() throws Exception {
        JavaFxTestSupport.run(() -> {
            loader = new FXMLLoader(LoanDetailsController.class.getResource("fxml/loanDetails.fxml"));
            Parent root = loader.load();
            LoanDetailsController controller = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root, 1000, 650));
            stage.show();

            gateway = new FakeMainframeGateway();
            gateway.createCustomer(new Customer("67670442", "Ms", "Sarah", "Johnson", null, "NZ"));
            gateway.createCustomer(new Customer("C2", "Mr", "Alex", "Smith", null, "NZ"));
            gateway.createCustomer(new Customer("C3", "Ms", "Casey", "Reed",
                    LocalDate.of(1994, 7, 16), "NZ"));
            gateway.createLoan(new Loan("LN-1", "67670442", Loan.Status.ACTIVE, 450000, 6.5,
                    Loan.Type.TABLE_LOAN, false, LocalDate.of(2025, 1, 1), LocalDate.of(2055, 1, 1),
                    3245.67, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 360));
            gateway.addCoBorrower(new CoBorrower("CB-1", "LN-1", "C2"));

            controller.setLoanBrowsingService(new LoanBrowsingService(gateway));
            controller.setCustomerService(new CustomerService(gateway));
            controller.setCoBorrowerService(new CoBorrowerService(gateway));
            controller.setRegistrationServiceFactory(
                    () -> new MainframeCustomerRegistrationService(gateway));
            controller.setRequestExecutor(Runnable::run);
            controller.setLoan(loan("LN-1"));
            ((Button) loader.getNamespace().get("coborrowersTab")).fire();
            table = (GridPane) loader.getNamespace().get("coborrowersTable");
            return null;
        });
    }

    @AfterEach void close() throws Exception {
        JavaFxTestSupport.run(() -> {
            for (Window window : List.copyOf(Window.getWindows())) window.hide();
            return null;
        });
    }

    private static LoansController.LoanSummary loan(String id) {
        return new LoansController.LoanSummary(id, "67670442", "Sarah Johnson",
                "Active", "$450,000", "Home Loan");
    }

    private Button addButton() {
        return stage.getScene().getRoot().lookupAll(".button").stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "Add co-borrower".equals(button.getAccessibleText()))
                .findFirst().orElseThrow();
    }

    private Button removeButton(String name) {
        return table.lookupAll(".coborrower-actions").stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> ("Remove co-borrower for " + name)
                        .equals(button.getAccessibleText()))
                .findFirst().orElseThrow();
    }

    private long rowCount() {
        return table.getChildren().stream()
                .filter(node -> node.getStyleClass().contains("coborrower-action-buttons"))
                .count();
    }

    private static DialogPane dialog(String id) {
        return Window.getWindows().stream().filter(Window::isShowing)
                .map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> id.equals(pane.getId())).findFirst().orElseThrow();
    }

    private static DialogPane confirmation() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> "Remove co-borrower".equals(pane.getHeaderText()))
                .findFirst().orElseThrow();
    }

    private static void flushFx() throws Exception {
        JavaFxTestSupport.run(() -> null);
        JavaFxTestSupport.run(() -> null);
    }

    @Test void existingCustomerIsPersistedWithMainframeIds() throws Exception {
        JavaFxTestSupport.run(() -> {
            assertEquals(1, rowCount());
            addButton().fire();
            ((Button) dialog("addCoborrowerChoice").lookup("#chooseExistingCustomer")).fire();
            DialogPane picker = dialog("existingCoborrowerPicker");
            TextField search = (TextField) picker.lookup("#coborrowerCustomerSearch");
            search.setText("Casey");
            search.fireEvent(new ActionEvent());
            return null;
        });

        JavaFxTestSupport.run(() -> {
            DialogPane picker = dialog("existingCoborrowerPicker");
            @SuppressWarnings("unchecked")
            ListView<CustomerPreview> results =
                    (ListView<CustomerPreview>) picker.lookup("#coborrowerCustomerResults");
            assertEquals(1, results.getItems().size());
            assertEquals("C3", results.getItems().get(0).customerId().value());
            results.getSelectionModel().select(0);
            ButtonType add = picker.getButtonTypes().stream()
                    .filter(type -> "Add selected".equals(type.getText())).findFirst().orElseThrow();
            ((Button) picker.lookupButton(add)).fire();
            return null;
        });
        flushFx();

        List<CoBorrower> saved = gateway.getCoBorrowers("LN-1");
        assertEquals(2, saved.size());
        CoBorrower added = saved.stream().filter(item -> "C3".equals(item.getCustomerId()))
                .findFirst().orElseThrow();
        assertNotNull(added.getCoBorrowerId());
        JavaFxTestSupport.run(() -> {
            assertEquals(2, rowCount());
            assertTrue(table.getChildren().stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).anyMatch(label -> "C3".equals(label.getText())));
            return null;
        });
    }

    @Test void newCustomerIsRegisteredThenLinkedWithAssignedIds() throws Exception {
        JavaFxTestSupport.run(() -> {
            addButton().fire();
            DialogPane choice = dialog("addCoborrowerChoice");
            assertNotNull(choice.lookup("#chooseExistingCustomer"));
            assertNotNull(choice.lookup("#createNewCustomer"));
            ((Button) choice.lookup("#createNewCustomer")).fire();

            Window editor = Window.getWindows().stream().filter(Window::isShowing)
                    .filter(window -> "newCoborrowerCustomerEditor"
                            .equals(window.getScene().getRoot().getId()))
                    .findFirst().orElseThrow();
            assertNotNull(editor.getScene().lookup("#firstName"));
            assertNotNull(editor.getScene().lookup("#dateOfBirth"));
            @SuppressWarnings("unchecked")
            ComboBox<String> title = (ComboBox<String>) editor.getScene().lookup("#title");
            title.setValue("Ms");
            ((TextField) editor.getScene().lookup("#firstName")).setText("Morgan");
            ((TextField) editor.getScene().lookup("#lastName")).setText("Lee");
            ((TextField) editor.getScene().lookup("#dateOfBirth")).setText("12-08-1993");
            ((TextField) editor.getScene().lookup("#nationality")).setText("NZ");
            ((Button) editor.getScene().lookup("#next")).fire();
            for (int step = 0; step < 4; step++) {
                ((Button) editor.getScene().lookup("#skip")).fire();
            }
            ((Button) editor.getScene().lookup("#next")).fire();
            return null;
        });

        for (int attempt = 0; attempt < 100 && gateway.getCoBorrowerCount("LN-1") < 2; attempt++) {
            Thread.sleep(25);
            JavaFxTestSupport.run(() -> null);
        }
        flushFx();

        CoBorrower added = gateway.getCoBorrowers("LN-1").stream()
                .filter(item -> item.getCustomerId().startsWith("FAKE-CUSTOMER-"))
                .findFirst().orElseThrow();
        assertNotNull(added.getCoBorrowerId());
        assertNotNull(gateway.getCustomer(added.getCustomerId()));
        assertFalse(Window.getWindows().stream().anyMatch(window -> window.isShowing()
                && "newCoborrowerCustomerEditor".equals(window.getScene().getRoot().getId())));
    }

    @Test void removalIsPersistedAndCancellationLeavesTheLinkUntouched() throws Exception {
        JavaFxTestSupport.run(() -> {
            removeButton("Alex Smith").fire();
            ((Button) confirmation().lookupButton(ButtonType.CANCEL)).fire();
            assertEquals(1, gateway.getCoBorrowerCount("LN-1"));

            removeButton("Alex Smith").fire();
            ((Button) confirmation().lookupButton(ButtonType.OK)).fire();
            return null;
        });
        flushFx();

        assertEquals(0, gateway.getCoBorrowerCount("LN-1"));
        JavaFxTestSupport.run(() -> {
            assertEquals(0, rowCount());
            assertTrue(table.getChildren().stream().filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .anyMatch(label -> "No co-borrowers recorded for this loan."
                            .equals(label.getText())));
            return null;
        });
    }
}
