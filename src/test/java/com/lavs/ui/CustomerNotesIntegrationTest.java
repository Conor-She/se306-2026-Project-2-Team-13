package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.business.MainframeCustomerRegistrationService;
import com.lavs.data.FakeMainframeGateway;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerNotesIntegrationTest {
    private final FakeMainframeGateway gateway = new FakeMainframeGateway();
    private Parent root;
    private CustomerDetailsController details;

    @BeforeAll static void toolkit() throws Exception { JavaFxTestSupport.startToolkit(); }
    @AfterEach void close() throws Exception {
        JavaFxTestSupport.run(() -> { List.copyOf(Window.getWindows()).forEach(Window::hide); return null; });
    }

    private void show(Parent view) {
        root = view;
        var stage = new Stage();
        stage.setScene(new Scene(root, 1100, 800));
        stage.show();
        root.applyCss(); root.layout();
    }

    private Button button(String text) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText()) || text.equals(button.getAccessibleText()))
                .findFirst().orElseThrow();
    }

    private void openCustomer(String id) throws Exception {
        JavaFxTestSupport.run(() -> {
            var loader = new FXMLLoader(Main.class.getResource("fxml/customerDetails.fxml"));
            Parent view = loader.load();
            details = loader.getController();
            details.setCustomerService(new CustomerService(gateway));
            show(view);
            details.setCustomerDetails(new CustomerService(gateway).getById(new com.lavs.model.CustomerId(id)));
            ((ToggleButton) loader.getNamespace().get("notesTab")).fire();
            return null;
        });
    }

    @Test void wizardSupportsDraftCrudAndSavesNotesForTheCustomerTab() throws Exception {
        var savedId = new AtomicReference<String>();
        JavaFxTestSupport.run(() -> {
            var loader = new FXMLLoader(Main.class.getResource("fxml/addCustomer.fxml"));
            var controller = new AddCustomerController(new MainframeCustomerRegistrationService(gateway),
                    () -> {}, (id, draft) -> savedId.set(id));
            loader.setControllerFactory(type -> controller);
            show(loader.load());
            ((ComboBox<?>) loader.getNamespace().get("title")).getSelectionModel().selectFirst();
            ((TextField) loader.getNamespace().get("firstName")).setText("Ada");
            ((TextField) loader.getNamespace().get("lastName")).setText("Lovelace");
            ((TextField) loader.getNamespace().get("dateOfBirth")).setText("10-12-1990");
            ((TextField) loader.getNamespace().get("nationality")).setText("New Zealand");
            Button next = (Button) loader.getNamespace().get("next");
            next.fire(); next.fire(); next.fire();
            button("+ Add note").fire();
            ((TextArea) root.lookup("#text")).setText("Original note");
            button("Done").fire();
            button("Edit").fire();
            ((TextArea) root.lookup("#text")).setText("Edited wizard note\nSecond line");
            button("Done").fire();
            button("+ Add note").fire();
            ((TextArea) root.lookup("#text")).setText("Remove this draft note");
            var rows = (VBox) loader.getNamespace().get("noteRows");
            ((Button) rows.getChildren().get(1).lookup("#remove")).fire();
            assertEquals(1, rows.getChildren().size());
            next.fire(); next.fire(); next.fire();
            return null;
        });
        await(() -> savedId.get() != null);
        String id = savedId.get();
        assertEquals(1, gateway.getNotes(id).size());
        assertEquals("Edited wizard note\nSecond line", gateway.getNotes(id).get(0).getNoteText());
        openCustomer(id);
        await(() -> hasText("Edited wizard note\nSecond line"));
        JavaFxTestSupport.run(() -> {
            assertFalse(button("+  Add Note").isDisabled());
            Platform.runLater(() -> {
                DialogPane pane = dialog();
                ((TextArea) pane.lookup("#noteText")).setText(" ");
                click(pane, "Save");
                assertTrue(pane.getScene().getWindow().isShowing(), "Blank notes must stay in the editor");
                ((TextArea) pane.lookup("#noteText")).setText("Added from the customer tab");
                click(pane, "Save");
            });
            button("+  Add Note").fire();
            return null;
        });
        await(() -> hasText("Added from the customer tab"));
        String noteId = gateway.getNotes(id).get(0).getNoteId();
        JavaFxTestSupport.run(() -> {
            Platform.runLater(() -> {
                DialogPane pane = dialog();
                ((TextField) pane.lookup("#noteTitle")).setText("Follow up");
                ((TextArea) pane.lookup("#noteText")).setText("Edited customer note");
                click(pane, "Save");
            });
            button("Edit customer note " + noteId).fire();
            return null;
        });
        await(() -> hasText("Edited customer note"));
        JavaFxTestSupport.run(() -> {
            Platform.runLater(() -> click(dialog(), "Cancel"));
            button("Delete customer note " + noteId).fire();
            return null;
        });
        assertEquals(2, gateway.getNotes(id).size());
        JavaFxTestSupport.run(() -> {
            Platform.runLater(() -> click(dialog(), "OK"));
            button("Delete customer note " + noteId).fire();
            return null;
        });
        await(() -> !hasText("Edited customer note") && hasText("Added from the customer tab"));
        assertEquals(1, gateway.getNotes(id).size());
        openCustomer(id); // Fresh controller and service read the persisted record.
        await(() -> hasText("Added from the customer tab"));
        JavaFxTestSupport.run(() -> {
            details.setCustomer(new CustomersController.CustomerSummary("OTHER", "Other Customer", "", ""));
            return null;
        });
        await(() -> hasText("No notes added."));
        assertEquals(1, gateway.getNotes(id).size());
    }

    @Test void failedLoadShowsRetryAndRecoversWithoutChangingNotes() throws Exception {
        var service = new MainframeCustomerRegistrationService(gateway);
        var draft = new com.lavs.model.CustomerDraft();
        draft.personal.putAll(java.util.Map.of("title", "Ms", "firstName", "Ada", "lastName", "Lovelace",
                "dateOfBirth", "10-12-1990", "nationality", "NZ"));
        draft.notes.add(java.util.Map.of("text", "Keep this note"));
        String id = service.save(draft);
        openCustomer(id);
        await(() -> hasText("Keep this note"));
        gateway.simulateUnavailable(true);
        JavaFxTestSupport.run(() -> {
            ((ToggleButton) root.lookup("#detailsTab")).fire();
            ((ToggleButton) root.lookup("#notesTab")).fire();
            return null;
        });
        await(() -> hasText("Reload notes"));
        gateway.simulateUnavailable(false);
        JavaFxTestSupport.run(() -> { button("Reload notes").fire(); return null; });
        await(() -> hasText("Keep this note"));
        assertEquals(1, gateway.getNotes(id).size());
    }

    private boolean hasText(String text) {
        return root.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> text.equals(label.getText()))
                || root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(button -> text.equals(button.getText()));
    }

    private static DialogPane dialog() {
        return Window.getWindows().stream().filter(Window::isShowing).map(w -> w.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
    }

    private static void click(DialogPane pane, String text) {
        var type = pane.getButtonTypes().stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
        ((Button) pane.lookupButton(type)).fire();
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (JavaFxTestSupport.run(condition)) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for customer notes UI");
    }
}
