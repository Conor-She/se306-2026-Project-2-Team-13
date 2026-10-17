package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.*;
import java.time.LocalDate;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises backend retrieval through the actual customer tab views. */
class CustomerRelatedTabsIntegrationTest {
    @BeforeAll static void toolkit() throws Exception { JavaFxTestSupport.startToolkit(); }

    @org.junit.jupiter.api.AfterEach void closeWindows() throws Exception {
        JavaFxTestSupport.run(() -> {
            List.copyOf(javafx.stage.Window.getWindows()).forEach(javafx.stage.Window::hide);
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({"documents", "guarantors", "loans"})
    void editsPersistedRecordsAndDeletesSupportedRecords(String tab) throws Exception {
        var gateway = new FakeMainframeGateway();
        gateway.createCustomer(new Customer("C1", "Ms", "Jane", "Smith", null, "NZ"));
        gateway.createCustomer(new Customer("C3", "Mr", "John", "Jones", null, "NZ"));
        gateway.addDocument(new CustomerDocument("DOC-1", "C1", "passport.pdf", "Identity", LocalDate.of(2026, 1, 2)));
        gateway.addGuarantor(new Guarantor("G1", "C1", "C3", "Parent"));
        gateway.createLoan(new Loan("LOAN-101", "C1", Loan.Status.NEW, 12000, 12,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                1066.19, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12));
        var service = new CustomerService(gateway);
        var loader = new FXMLLoader(Main.class.getResource("fxml/customerDetails.fxml"));
        JavaFxTestSupport.run(() -> {
            Parent root = loader.load();
            var stage = new javafx.stage.Stage();
            stage.setScene(new Scene(root, 1100, 800)); stage.show();
            CustomerDetailsController controller = loader.getController();
            controller.setCustomerService(service);
            controller.setCustomerDetails(service.getById(new CustomerId("C1")));
            ((ToggleButton) loader.getNamespace().get(tab + "Tab")).fire();
            root.applyCss(); root.layout();
            actionButton(loader, tab).getContextMenu().getItems().get(0).fire();
            DialogPane pane = dialog("customerRecordEditor");
            String field = tab.equals("documents") ? "fileName" : tab.equals("guarantors") ? "relationship" : "amount";
            ((TextField) pane.lookup("#edit_" + field)).setText(tab.equals("loans") ? "10000" : "Updated");
            pane.getButtonTypes().stream().filter(type -> type.getText().equals("Save"))
                    .map(pane::lookupButton).map(Button.class::cast).findFirst().orElseThrow().fire();
            return null;
        });
        if (tab.equals("documents")) assertEquals("Updated", gateway.getDocuments("C1").get(0).getFileName());
        else if (tab.equals("guarantors")) assertEquals("Updated", gateway.getGuarantors("C1").get(0).getRelationshipType());
        else {
            assertEquals(10000, gateway.getLoan("LOAN-101").getPrincipal());
            assertFalse(gateway.getRepaymentSchedule("LOAN-101").isEmpty());
        }
        JavaFxTestSupport.run(() -> {
            ContextMenu menu = actionButton(loader, tab).getContextMenu();
            if (tab.equals("loans")) { assertTrue(menu.getItems().get(1).isDisable()); return null; }
            javafx.application.Platform.runLater(() -> {
                DialogPane confirmation = dialog(null);
                ((Button) confirmation.lookupButton(ButtonType.OK)).fire();
            });
            menu.getItems().get(1).fire();
            return null;
        });
        if (tab.equals("documents")) assertTrue(gateway.getDocuments("C1").isEmpty());
        if (tab.equals("guarantors")) assertTrue(gateway.getGuarantors("C1").isEmpty());
    }

    private static Button actionButton(FXMLLoader loader, String tab) {
        Parent table = (Parent) loader.getNamespace().get(tab + "Table");
        return table.lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> button.getContextMenu() != null).findFirst().orElseThrow();
    }

    private static DialogPane dialog(String id) {
        return javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast)
                .filter(pane -> id == null ? pane.getButtonTypes().contains(ButtonType.OK) : id.equals(pane.getId()))
                .findFirst().orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({"loans, LOAN-101", "notes, Existing backend note",
            "documents, passport.pdf", "guarantors, Alice Jones"})
    void listsExistingRecordsAndClearsThemWhenSelectingAnEmptyCustomer(String tab, String expected) throws Exception {
        var gateway = new FakeMainframeGateway();
        gateway.createCustomer(new Customer("C1", "Ms", "Jane", "Smith", null, "NZ"));
        gateway.createCustomer(new Customer("C2", "Mr", "John", "Smith", null, "NZ"));
        gateway.createCustomer(new Customer("C3", "Ms", "Alice", "Jones", null, "NZ"));
        gateway.addDocument(new CustomerDocument("DOC-1", "C1", "passport.pdf", "Identity", LocalDate.of(2026, 1, 2)));
        gateway.addDocument(new CustomerDocument("DOC-2", "C1", "licence", "Identity", null));
        gateway.addGuarantor(new Guarantor("G1", "C1", "C3", "Parent"));
        gateway.addNote(new CustomerNote("N1", "C1", "Existing backend note", LocalDate.of(2026, 1, 2), "General", "Staff"));
        gateway.createLoan(new Loan("LOAN-101", "C1", Loan.Status.NEW, 12000, 12,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                1066.19, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12));
        var service = new CustomerService(gateway);
        var populated = service.getById(new CustomerId("C1"));
        var empty = service.getById(new CustomerId("C2"));
        var loader = new FXMLLoader(Main.class.getResource("fxml/customerDetails.fxml"));
        Parent root = JavaFxTestSupport.run(() -> {
            Parent view = loader.load();
            new Scene(view, 1100, 800);
            CustomerDetailsController controller = loader.getController();
            controller.setCustomerService(service);
            controller.setCustomerDetails(populated);
            ((ToggleButton) loader.getNamespace().get(tab + "Tab")).fire();
            view.applyCss(); view.layout();
            return view;
        });
        awaitText(loader, tab, expected);
        JavaFxTestSupport.run(() -> {
            assertTrue(root.lookup("#" + tab + "Content").isVisible());
            if (tab.equals("documents")) {
                assertTrue(hasText(loader, tab, "Identity"));
                assertTrue(hasText(loader, tab, "2 Jan 2026"));
                assertTrue(hasText(loader, tab, "Not provided"));
            }
            if (tab.equals("guarantors")) assertTrue(hasText(loader, tab, "Parent"));
            // Persisted records expose backend actions. Loan deletion is unsupported.
            if (!tab.equals("notes")) {
                Parent content = (Parent) loader.getNamespace().get(tab + "Table");
                assertTrue(content.lookupAll(".button").stream().noneMatch(javafx.scene.Node::isDisabled));
                for (var node : content.lookupAll(".button")) {
                    Button button = (Button) node;
                    assertFalse(button.getContextMenu().getItems().get(0).isDisable());
                    assertEquals(tab.equals("loans"), button.getContextMenu().getItems().get(1).isDisable());
                }
            }
            CustomerDetailsController controller = loader.getController();
            controller.setCustomerDetails(empty);
            ((ToggleButton) loader.getNamespace().get(tab + "Tab")).fire();
            return null;
        });
        awaitText(loader, tab, "No " + tab + " added.");
        JavaFxTestSupport.run(() -> { assertFalse(hasText(loader, tab, expected)); return null; });
    }

    private static boolean hasText(FXMLLoader loader, String tab, String text) {
        Parent content = (Parent) loader.getNamespace().get(tab.equals("notes") ? "notesList" : tab + "Table");
        return content.lookupAll(".label").stream().filter(Label.class::isInstance)
                .map(Label.class::cast).anyMatch(label -> text.equals(label.getText()));
    }

    private static void awaitText(FXMLLoader loader, String tab, String text) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (JavaFxTestSupport.run(() -> hasText(loader, tab, text))) return;
            Thread.sleep(20);
        }
        fail("Expected " + tab + " tab to contain: " + text);
    }
}
