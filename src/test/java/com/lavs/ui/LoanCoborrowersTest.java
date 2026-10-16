package com.lavs.ui;

import com.lavs.business.LoanBrowsingService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.CoBorrower;
import com.lavs.model.Customer;
import com.lavs.model.Loan;
import java.time.LocalDate;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LoanCoborrowersTest {
    private FXMLLoader loader;
    private LoanDetailsController controller;
    private Stage stage;
    private GridPane table;
    private FakeMainframeGateway gateway;

    @BeforeAll static void start() throws Exception { JavaFxTestSupport.startToolkit(); }
    @BeforeEach void setup() throws Exception {
        JavaFxTestSupport.run(() -> {
            loader = new FXMLLoader(LoanDetailsController.class.getResource("fxml/loanDetails.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            stage = new Stage(); stage.setScene(new Scene(root, 1000, 650)); stage.show();
            gateway = new FakeMainframeGateway();
            gateway.createCustomer(new Customer("67670442", "Ms", "Sarah", "Johnson", null, "NZ"));
            gateway.createCustomer(new Customer("C2", "Mr", "Alex", "Smith", null, "NZ"));
            gateway.createLoan(new Loan("LN-1", "67670442", Loan.Status.ACTIVE, 450000, 6.5,
                    Loan.Type.TABLE_LOAN, false, LocalDate.of(2025, 1, 1), LocalDate.of(2055, 1, 1),
                    3245.67, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 360));
            gateway.addCoBorrower(new CoBorrower("CB-1", "LN-1", "C2"));
            controller.setLoanBrowsingService(new LoanBrowsingService(gateway));
            controller.setRequestExecutor(Runnable::run);
            controller.setLoan(loan("LN-1"));
            ((Button) loader.getNamespace().get("coborrowersTab")).fire();
            table = (GridPane) loader.getNamespace().get("coborrowersTable");
            return null;
        });
    }
    @AfterEach void close() throws Exception {
        JavaFxTestSupport.run(() -> {
            for (Window window : java.util.List.copyOf(Window.getWindows())) window.hide();
            return null;
        });
    }
    private static LoansController.LoanSummary loan(String id) {
        return new LoansController.LoanSummary(id, "67670442", "Sarah Johnson", "Active", "$450,000", "Home Loan");
    }
    private Button action(String action, String name) {
        return table.lookupAll(".coborrower-actions").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> (action + " co-borrower for " + name).equals(button.getAccessibleText())).findFirst().orElseThrow();
    }
    private long count() { return table.getChildren().stream()
            .filter(node -> node.getStyleClass().contains("coborrower-action-buttons")).count(); }
    private static DialogPane editor() {
        return Window.getWindows().stream().filter(Window::isShowing).map(w -> w.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(p -> "loanCoborrowerEditor".equals(p.getId())).findFirst().orElseThrow();
    }
    private static void field(DialogPane pane, String key, String value) {
        ((TextField) pane.lookup("#edit_" + key)).setText(value);
    }
    private static void save(DialogPane pane) {
        ButtonType save = pane.getButtonTypes().stream().filter(type -> "Save".equals(type.getText())).findFirst().orElseThrow();
        ((Button) pane.lookupButton(save)).fire();
    }
    @Test void backendCoBorrowerLoadsAndCanBeEditedInTheCurrentView() throws Exception {
        JavaFxTestSupport.run(() -> {
            assertTrue(((Parent) loader.getNamespace().get("coborrowersView")).isVisible());
            assertEquals(1, count());
            assertNotNull(action("Edit", "Alex Smith"));
            ((Button) stage.getScene().getRoot().lookupAll(".button").stream()
                    .filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> "Add co-borrower".equals(b.getAccessibleText())).findFirst().orElseThrow()).fire();
            DialogPane pane = editor();
            field(pane, "firstName", "Casey"); field(pane, "lastName", "Reed");
            field(pane, "relationship", "Friend"); field(pane, "phone", "021 555 1234");
            field(pane, "email", "invalid");
            save(pane);
            assertEquals(1, count()); assertTrue(pane.getScene().getWindow().isShowing());
            field(pane, "email", "alex@example.com"); save(pane);
            assertEquals(2, count());
            action("Edit", "Casey Reed").fire();
            pane = editor();
            assertEquals("alex@example.com", ((TextField) pane.lookup("#edit_email")).getText());
            field(pane, "firstName", "Casey"); field(pane, "lastName", "Reed"); save(pane);
            assertNotNull(action("Edit", "Casey Reed"));
            return null;
        });
    }
    @Test void cancellingAndDeletingLeaveCorrectRowsAndEmptyState() throws Exception {
        JavaFxTestSupport.run(() -> {
            action("Edit", "Alex Smith").fire();
            DialogPane pane = editor(); field(pane, "firstName", "Changed");
            ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
            assertNotNull(action("Edit", "Alex Smith"));
            action("Delete", "Alex Smith").fire();
            DialogPane cancelledDelete = Window.getWindows().stream().filter(Window::isShowing)
                    .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                    .map(DialogPane.class::cast).filter(p -> p.getButtonTypes().contains(ButtonType.OK))
                    .findFirst().orElseThrow();
            assertEquals(1, count());
            ((Button) cancelledDelete.lookupButton(ButtonType.CANCEL)).fire();
            assertEquals(1, count());
            action("Delete", "Alex Smith").fire();
            DialogPane confirmation = Window.getWindows().stream().filter(Window::isShowing)
                    .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                    .map(DialogPane.class::cast).filter(p -> p.getButtonTypes().contains(ButtonType.OK))
                    .findFirst().orElseThrow();
            ((Button) confirmation.lookupButton(ButtonType.OK)).fire();
            assertEquals(0, count());
            assertTrue(table.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> "No co-borrowers recorded for this loan.".equals(label.getText())));
            return null;
        });
    }
}
