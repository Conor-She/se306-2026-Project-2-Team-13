package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomersServiceIntegrationTest {
    private FakeMainframeGateway gateway;
    private CustomersController controller;
    private Parent root;
    private TextField search;
    private TableView<CustomersController.CustomerSummary> table;

    @BeforeAll static void startToolkit() throws Exception { JavaFxTestSupport.startToolkit(); }

    @BeforeEach void load() throws Exception {
        gateway = spy(new FakeMainframeGateway());
        var service = new CustomerService(gateway);
        service.create(new Customer("C1", "Ms", "Jane", "Smith", LocalDate.of(1990, 5, 1), "NZ"),
                List.of(new Address(null, null, "5 Queen St", "CBD", "Auckland", "1010", "NZ", true)),
                List.of(new Phone(null, null, "021111111", true, true),
                        new Phone(null, null, "021222222", false, false)),
                List.of(new Email(null, null, "jane@example.com", true)),
                List.of(new Employer(null, null, "Acme", "Engineer", 90000, "Primary"),
                        new Employer(null, null, "Other company", "Consultant", 10000, "Secondary")));
        gateway.createCustomer(new Customer("C2", "Mr", "John", "Smith", null, "NZ"));
        JavaFxTestSupport.run(() -> {
            var loader = new FXMLLoader(Main.class.getResource("fxml/customers.fxml"));
            root = loader.load();
            new Scene(root, 1100, 800);
            controller = loader.getController();
            controller.setCustomerService(service);
            search = (TextField) loader.getNamespace().get("searchField");
            table = (TableView<CustomersController.CustomerSummary>) loader.getNamespace().get("customerTable");
            controller.onShown();
            return null;
        });
        searchFor("Smith");
        await(() -> table.getItems().size() == 2);
        clearInvocations(gateway);
    }

    @AfterEach void closeWindows() throws Exception {
        JavaFxTestSupport.run(() -> {
            for (javafx.stage.Window window : List.copyOf(javafx.stage.Window.getWindows())) window.hide();
            controller.dispose();
            return null;
        });
    }

    @Test void searchesBackendForNoSingleAndMultipleMatchesWithoutRetrievingDetails() throws Exception {
        searchFor("Nobody");
        await(() -> heading().equals("Search results (0)"));
        searchFor("Jane");
        await(() -> heading().equals("Search results (1)"));
        JavaFxTestSupport.run(() -> {
            assertEquals("C1", table.getItems().get(0).customerId());
            assertEquals("Jane Smith", table.getItems().get(0).name());
            assertEquals(3, table.getColumns().size());
            assertEquals("Date of Birth", table.getColumns().get(2).getText());
            assertEquals(LocalDate.of(1990, 5, 1), table.getColumns().get(2).getCellData(0));
            return null;
        });
        searchFor("Smith");
        await(() -> heading().equals("Search results (2)"));
        JavaFxTestSupport.run(() -> {
            assertNull(table.getItems().stream().filter(customer -> customer.customerId().equals("C2"))
                    .findFirst().orElseThrow().dateOfBirth());
            return null;
        });
        verify(gateway, never()).getCustomer(anyString());
        verify(gateway, never()).getAddresses(anyString());
        verify(gateway, never()).getPhones(anyString());
        verify(gateway, never()).getEmails(anyString());
        verify(gateway, never()).getEmployers(anyString());
    }

    @Test void openingResultLoadsRealDetailsAndAdditionalRecords() throws Exception {
        JavaFxTestSupport.run(() -> {
            controller.openCustomer(table.getItems().get(0));
            return null;
        });
        await(() -> root.lookup("#detailsView").isVisible());
        JavaFxTestSupport.run(() -> {
            root.applyCss();
            root.layout();
            assertEquals("Jane Smith", label("customerHeading"));
            assertEquals("Ms", label("titleValue"));
            assertEquals("1 May 1990", label("birthDateValue"));
            assertEquals("NZ", label("nationalityValue"));
            assertEquals("021111111", label("phoneValue"));
            assertEquals("jane@example.com", label("emailValue"));
            assertTrue(label("addressValue").contains("5 Queen St"));
            assertEquals("Acme", label("employerNameValue"));
            assertTrue(root.lookup("#viewAllPhone").isVisible());
            assertTrue(((Parent) root.lookup("#additionalEmployers")).getChildrenUnmodifiable().stream()
                    .anyMatch(node -> node instanceof Parent row && row.lookupAll(".label").stream()
                            .anyMatch(child -> child instanceof Label value && "Other company".equals(value.getText()))));
            var edit = root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> "Edit personal information".equals(button.getAccessibleText())).findFirst().orElseThrow();
            assertFalse(edit.isDisabled());
            return null;
        });
        verify(gateway).getCustomer("C1");
        verify(gateway).getAddresses("C1");
        verify(gateway).getPhones("C1");
        verify(gateway).getEmails("C1");
        verify(gateway).getEmployers("C1");
    }

    @Test void editingExistingCustomerPersistsAndIsVisibleAfterReopening() throws Exception {
        openJane();
        JavaFxTestSupport.run(() -> {
            editButton("Edit email").fire();
            DialogPane pane = editor();
            ((TextField) pane.lookup("#edit_value")).setText("updated@example.com");
            dialogButton(pane, "Save").fire();
            assertEquals("updated@example.com", label("emailValue"));
            return null;
        });

        assertEquals("updated@example.com", gateway.getEmails("C1").get(0).getEmailAddress());
        JavaFxTestSupport.run(() -> {
            controller.openCustomer(new CustomersController.CustomerSummary("C1", "Jane Smith", "", ""));
            return null;
        });
        await(() -> "updated@example.com".equals(label("emailValue")));
    }

    @Test void invalidContactAndCancelLeaveExistingCustomerUnchanged() throws Exception {
        openJane();
        JavaFxTestSupport.run(() -> {
            editButton("Edit phone").fire();
            DialogPane phone = editor();
            ((TextField) phone.lookup("#edit_value")).setText("invalid");
            dialogButton(phone, "Save").fire();
            assertTrue(phone.lookup("#error_value").isVisible());
            assertEquals("021111111", label("phoneValue"));
            dialogButton(phone, "Cancel").fire();

            editButton("Edit personal information").fire();
            DialogPane personal = editor();
            ((TextField) personal.lookup("#edit_lastName")).setText("Discarded");
            dialogButton(personal, "Cancel").fire();
            assertEquals("Jane Smith", label("customerHeading"));
            return null;
        });
        assertEquals("021111111", gateway.getPhones("C1").get(0).getNumber());
        assertEquals("Smith", gateway.getCustomer("C1").getLastName());
    }

    @Test void saveFailureIsReportedAndLeavesDisplayedDetailsUnchanged() throws Exception {
        openJane();
        JavaFxTestSupport.run(() -> {
            editButton("Edit email").fire();
            DialogPane pane = editor();
            ((TextField) pane.lookup("#edit_value")).setText("failed@example.com");
            gateway.simulateUnavailable(true);
            dialogButton(pane, "Save").fire();
            assertEquals("jane@example.com", label("emailValue"));
            assertTrue(javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                    .map(javafx.stage.Window::getScene).filter(java.util.Objects::nonNull)
                    .map(javafx.scene.Scene::getRoot).anyMatch(node -> "customerSaveError".equals(node.getId())));
            gateway.simulateUnavailable(false);
            return null;
        });
        assertEquals("jane@example.com", gateway.getEmails("C1").get(0).getEmailAddress());
    }

    @Test void clearingSearchPromptsForQueryWithoutRequestingUnsupportedBlankSearch() throws Exception {
        searchFor("");
        JavaFxTestSupport.run(() -> {
            assertEquals("Search customers", heading());
            assertTrue(table.getItems().isEmpty());
            assertEquals("Enter a customer name or ID to search", ((Label) table.getPlaceholder()).getText());
            return null;
        });
        verify(gateway, never()).searchCustomers("");
    }

    @Test void failedSearchClearsResultsAndEnterRetries() throws Exception {
        gateway.simulateUnavailable(true);
        searchFor("Jane");
        await(() -> heading().startsWith("Could not load"));
        JavaFxTestSupport.run(() -> { assertTrue(table.getItems().isEmpty()); return null; });
        gateway.simulateUnavailable(false);
        searchFor("Jane");
        await(() -> heading().equals("Search results (1)"));
    }

    @Test void failedRetrievalStaysOnListAndCanBeRetried() throws Exception {
        gateway.simulateUnavailable(true);
        JavaFxTestSupport.run(() -> { controller.openCustomer(table.getItems().get(0)); return null; });
        await(() -> heading().startsWith("Could not retrieve"));
        JavaFxTestSupport.run(() -> {
            assertTrue(root.lookup("#listView").isVisible());
            assertFalse(table.isDisabled());
            assertFalse(search.isDisabled());
            return null;
        });
        gateway.simulateUnavailable(false);
        JavaFxTestSupport.run(() -> { controller.openCustomer(table.getItems().get(0)); return null; });
        await(() -> root.lookup("#detailsView").isVisible());
    }

    @Test void repeatedSearchIncludesNewCustomers() throws Exception {
        gateway.createCustomer(new Customer("C3", "Ms", "Alice", "Smith", null, "NZ"));
        searchFor("Smith");
        await(() -> table.getItems().size() == 3);
    }

    private void searchFor(String query) throws Exception {
        JavaFxTestSupport.run(() -> { search.setText(query); search.fireEvent(new ActionEvent()); return null; });
    }
    private void openJane() throws Exception {
        JavaFxTestSupport.run(() -> {
            controller.openCustomer(table.getItems().stream().filter(customer -> customer.customerId().equals("C1"))
                    .findFirst().orElseThrow());
            return null;
        });
        await(() -> root.lookup("#detailsView").isVisible());
        JavaFxTestSupport.run(() -> { root.applyCss(); root.layout(); return null; });
    }
    private Button editButton(String accessibleText) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> accessibleText.equals(button.getAccessibleText())).findFirst().orElseThrow();
    }
    private static DialogPane editor() {
        return javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                .map(javafx.stage.Window::getScene).filter(java.util.Objects::nonNull)
                .map(javafx.scene.Scene::getRoot).filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> "customerRecordEditor".equals(pane.getId())).findFirst().orElseThrow();
    }
    private static Button dialogButton(DialogPane pane, String text) {
        return pane.getButtonTypes().stream().filter(type -> text.equals(type.getText()))
                .map(pane::lookupButton).map(Button.class::cast).findFirst().orElseThrow();
    }
    private String heading() { return label("resultsHeading"); }
    private String label(String id) { return ((Label) root.lookup("#" + id)).getText(); }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (JavaFxTestSupport.run(condition)) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for customer UI update");
    }
}
