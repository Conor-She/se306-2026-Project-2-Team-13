package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Customer;
import com.lavs.model.CustomerPreview;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class GuarantorCustomerPickerTest {
    private Dialog<Map<String, String>> dialog;
    private final AtomicReference<Map<String, String>> saved = new AtomicReference<>();

    @BeforeAll static void toolkit() throws Exception { JavaFxTestSupport.startToolkit(); }
    @AfterEach void close() throws Exception {
        JavaFxTestSupport.run(() -> { if (dialog != null) dialog.close(); return null; });
    }

    private FakeMainframeGateway gateway() {
        var gateway = new FakeMainframeGateway();
        gateway.createCustomer(new Customer("C1", "Ms", "Jane", "Smith", null, "NZ"));
        gateway.createCustomer(new Customer("C2", "Mr", "John", "Smith", null, "NZ"));
        gateway.createCustomer(new Customer("C3", "Ms", "Alice", "Smith", null, "NZ"));
        return gateway;
    }

    private void open(CustomerService service, String existing) throws Exception {
        JavaFxTestSupport.run(() -> {
            var original = existing == null ? Map.<String, String>of()
                    : Map.of("customerId", existing, "relationship", "Parent");
            dialog = CustomerRecordEditor.create(null, CustomerRecordEditor.Kind.SAVED_GUARANTOR,
                    original, existing == null, saved::set);
            GuarantorCustomerPicker.install(dialog, service, Set.of("C1", "C3"), existing);
            dialog.show();
            return null;
        });
    }

    @Test void searchesByNameFiltersIneligibleCustomersAndRequiresSelection() throws Exception {
        open(new CustomerService(gateway()), null);
        JavaFxTestSupport.run(() -> {
            input().setText("Smith"); input().fireEvent(new ActionEvent());
            ((TextField) dialog.getDialogPane().lookup("#edit_relationship")).setText("Parent");
            save();
            assertTrue(dialog.isShowing());
            assertNull(saved.get());
            return null;
        });
        awaitResults();
        JavaFxTestSupport.run(() -> {
            assertEquals(List.of("C2"), results().getItems().stream().map(item -> item.customerId().value()).toList());
            results().getSelectionModel().select(0);
            assertEquals("C2", input().getText());
            assertTrue(((Label) dialog.getDialogPane().lookup("#guarantorSelectedCustomer")).getText().contains("John Smith"));
            save();
            assertEquals("C2", saved.get().get("customerId"));
            return null;
        });
    }

    @Test void changingSelectedTextRequiresSelectingAgainAndIdSearchWorks() throws Exception {
        open(new CustomerService(gateway()), "C2");
        JavaFxTestSupport.run(() -> {
            input().setText("C2");
            input().setText("John");
            save();
            assertTrue(dialog.isShowing());
            input().setText("C2"); input().fireEvent(new ActionEvent());
            return null;
        });
        awaitResults();
        JavaFxTestSupport.run(() -> {
            results().getSelectionModel().select(0); save();
            assertEquals("C2", saved.get().get("customerId"));
            return null;
        });
    }

    @Test void searchFailureCanBeRetried() throws Exception {
        var gateway = gateway();
        gateway.simulateUnavailable(true);
        open(new CustomerService(gateway), null);
        JavaFxTestSupport.run(() -> { input().setText("John"); input().fireEvent(new ActionEvent()); return null; });
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!JavaFxTestSupport.run(() -> results().getPlaceholder() instanceof Label label
                && label.getText().contains("unavailable"))) {
            if (System.nanoTime() > deadline) fail("Expected failed search message");
            Thread.sleep(20);
        }
        gateway.simulateUnavailable(false);
        JavaFxTestSupport.run(() -> { input().fireEvent(new ActionEvent()); return null; });
        awaitResults();
    }

    private TextField input() { return (TextField) dialog.getDialogPane().lookup("#edit_customerId"); }
    @SuppressWarnings("unchecked") private ListView<CustomerPreview> results() {
        return (ListView<CustomerPreview>) dialog.getDialogPane().lookup("#guarantorCustomerResults");
    }
    private void save() {
        var pane = dialog.getDialogPane();
        pane.getButtonTypes().stream().filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .map(pane::lookupButton).map(Button.class::cast).findFirst().orElseThrow().fire();
    }
    private void awaitResults() throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!JavaFxTestSupport.run(() -> !results().getItems().isEmpty())) {
            if (System.nanoTime() > deadline) fail("Expected matching customer");
            Thread.sleep(20);
        }
    }
}
