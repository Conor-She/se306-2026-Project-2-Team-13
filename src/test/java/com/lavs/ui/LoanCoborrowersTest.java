package com.lavs.ui;

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

    @BeforeAll static void start() throws Exception { JavaFxTestSupport.startToolkit(); }
    @BeforeEach void setup() throws Exception {
        JavaFxTestSupport.run(() -> {
            loader = new FXMLLoader(LoanDetailsController.class.getResource("fxml/loanDetails.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            stage = new Stage(); stage.setScene(new Scene(root, 1000, 650)); stage.show();
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
    @Test void addValidatesContactAndKeepsEditsPerLoan() throws Exception {
        JavaFxTestSupport.run(() -> {
            assertTrue(((Parent) loader.getNamespace().get("coborrowersView")).isVisible());
            assertEquals(3, count());
            ((Button) stage.getScene().getRoot().lookupAll(".button").stream()
                    .filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> "Add co-borrower".equals(b.getAccessibleText())).findFirst().orElseThrow()).fire();
            DialogPane pane = editor();
            field(pane, "firstName", "Alex"); field(pane, "lastName", "Smith");
            field(pane, "relationship", "Friend"); field(pane, "phone", "021 555 1234");
            field(pane, "email", "invalid");
            save(pane);
            assertEquals(3, count()); assertTrue(pane.getScene().getWindow().isShowing());
            field(pane, "email", "alex@example.com"); save(pane);
            assertEquals(4, count());
            action("Edit", "Alex Smith").fire();
            pane = editor();
            assertEquals("alex@example.com", ((TextField) pane.lookup("#edit_email")).getText());
            field(pane, "firstName", "Alexa"); save(pane);
            assertNotNull(action("Edit", "Alexa Smith"));
            controller.setLoan(loan("LN-2")); assertEquals(3, count());
            controller.setLoan(loan("LN-1")); assertEquals(4, count());
            assertNotNull(action("Edit", "Alexa Smith"));
            return null;
        });
    }
    @Test void cancellingAndDeletingLeaveCorrectRowsAndEmptyState() throws Exception {
        JavaFxTestSupport.run(() -> {
            action("Edit", "Michael Johnson").fire();
            DialogPane pane = editor(); field(pane, "firstName", "Changed");
            ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
            assertNotNull(action("Edit", "Michael Johnson"));
            action("Delete", "Michael Johnson").fire();
            DialogPane cancelledDelete = Window.getWindows().stream().filter(Window::isShowing)
                    .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                    .map(DialogPane.class::cast).filter(p -> p.getButtonTypes().contains(ButtonType.OK))
                    .findFirst().orElseThrow();
            assertEquals(3, count());
            ((Button) cancelledDelete.lookupButton(ButtonType.CANCEL)).fire();
            assertEquals(3, count());
            for (String name : java.util.List.of("Michael Johnson", "Sophie Johnson", "Chris Johnson")) {
                action("Delete", name).fire();
                DialogPane confirmation = Window.getWindows().stream().filter(Window::isShowing)
                        .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                        .map(DialogPane.class::cast).filter(p -> p.getButtonTypes().contains(ButtonType.OK))
                        .findFirst().orElseThrow();
                ((Button) confirmation.lookupButton(ButtonType.OK)).fire();
            }
            assertEquals(0, count());
            assertTrue(table.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> "No co-borrowers recorded for this loan.".equals(label.getText())));
            return null;
        });
    }
}
