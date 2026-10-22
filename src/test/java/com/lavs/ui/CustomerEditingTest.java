package com.lavs.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.lavs.ui.CustomerRecordEditor.Kind;

import static org.junit.jupiter.api.Assertions.*;
import static com.lavs.ui.CustomerRecordEditor.Kind;

class CustomerEditingTest {
    private FXMLLoader loader;
    private Parent root;
    private Stage stage;
    private CustomerDetailsController controller;
    private final CustomersController.CustomerSummary sarah =
            new CustomersController.CustomerSummary("67670442", "Sarah Johnson", "021 135 6767", "sjohn67@gmail.com");

    @BeforeAll static void startToolkit() throws Exception { JavaFxTestSupport.startToolkit(); }

    @BeforeEach void loadPage() throws Exception {
        JavaFxTestSupport.run(() -> {
            loader = new FXMLLoader(Main.class.getResource("fxml/customerDetails.fxml"));
            root = loader.load();
            controller = loader.getController();
            CustomerUiFixtures.seed(controller, sarah);
            stage = new Stage();
            stage.setScene(new Scene(root, 1008, 662));
            stage.show();
            root.applyCss(); root.layout();
            return null;
        });
    }

    @AfterEach void closeWindows() throws Exception {
        JavaFxTestSupport.run(() -> {
            for (Window window : List.copyOf(Window.getWindows())) window.hide();
            if (stage != null) stage.close();
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({
        "details,Edit personal information,lastName,Wilson",
        "details,Edit email,value,sarah@example.com",
        "details,Edit employment,employer,New Company",
        "notes,Actions for Loan discussion note,title,Updated discussion",
        "documents,Actions for ID - Passport.pdf,fileName,New Passport.pdf",
        "guarantors,Actions for guarantor Michael Johnson,lastName,Wilson"
    })
    void editingEachSectionUpdatesPage(String tab, String action, String field, String value) throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor(tab, action);
            DialogPane pane = editor();
            assertFalse(((TextField) pane.lookup("#edit_" + field)).getText().isBlank(), "Edit fields should be prefilled");
            ((TextField) pane.lookup("#edit_" + field)).setText(value);
            dialogButton(pane, "Save").fire();
            assertNull(findEditor(), "Valid save should close the editor");
            assertTrue(nodes(root).stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals(field.equals("amount") ? "$123,456" : field.equals("lastName") ? (tab.equals("guarantors") ? "Michael " : "Sarah ") + value : value)),
                    "Saved value was not displayed");
            assertTrue(((ToggleButton) loader.getNamespace().get(tab + "Tab")).isSelected());
            return null;
        });
    }

    @Test void cancelDoesNotChangeCustomer() throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("details", "Edit personal information");
            DialogPane pane = editor();
            ((TextField) pane.lookup("#edit_firstName")).setText("Discard this change");
            dialogButton(pane, "Cancel").fire();
            assertSame(sarah, controller.getCustomer());
            assertEquals("Sarah Johnson", ((Label) loader.getNamespace().get("customerHeading")).getText());
            return null;
        });
    }

    @Test void invalidEmailKeepsEditorOpenAndLeavesOriginalContactUnchanged() throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("details", "Edit email");
            DialogPane pane = editor();
            ((TextField) pane.lookup("#edit_value")).setText("invalid");
            dialogButton(pane, "Save").fire();
            assertSame(pane, editor());
            assertTrue(pane.lookup("#error_value").isVisible());
            assertEquals("sjohn67@gmail.com", controller.getCustomer().email());
            ((TextField) pane.lookup("#edit_value")).setText("sarah@example.com");
            dialogButton(pane, "Save").fire();
            assertNull(findEditor());
            assertEquals("sarah@example.com", controller.getCustomer().email());
            return null;
        });
    }

    @Test void editsPersistForSameCustomerAndDoNotLeakToAnotherCustomer() throws Exception {
        JavaFxTestSupport.run(() -> {
            AtomicReference<CustomersController.CustomerSummary> changed = new AtomicReference<>();
            controller.setOnCustomerChanged(changed::set);
            openEditor("details", "Edit personal information");
            DialogPane pane = editor();
            ((TextField) pane.lookup("#edit_lastName")).setText("Wilson");
            dialogButton(pane, "Save").fire();
            assertEquals("Sarah Wilson", changed.get().name());
            var amelia = new CustomersController.CustomerSummary("C001", "Amelia Adams", "021 555 0101", "amelia@example.com");
            controller.setCustomer(amelia);
            assertEquals("Amelia Adams", controller.getCustomer().name());
            controller.setCustomer(sarah);
            assertEquals("Sarah Wilson", controller.getCustomer().name());
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({"notes,+  Add Note,title,New note", "guarantors,+  Add Guarantor,firstName,Jane"})
    void addingRecordsAppendsToSelectedTab(String tab, String action, String key, String expected) throws Exception {
        JavaFxTestSupport.run(() -> {
            ((ToggleButton) loader.getNamespace().get(tab + "Tab")).fire();
            Button add = nodes(root).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().equals(action)).findFirst().orElseThrow();
            add.fire();
            DialogPane pane = editor();
            Map<String, String> values = tab.equals("loans")
                    ? Map.of("id", expected, "amount", "5000", "rate", "3.25")
                    : tab.equals("notes") ? Map.of("title", expected, "text", "New customer note", "author", "Jack Slater")
                    : Map.of("firstName", expected, "lastName", "Johnson", "relationship", "Sister", "phone", "021 555 0101", "email", "jane@example.com");
            values.forEach((field, value) -> ((TextInputControl) pane.lookup("#edit_" + field)).setText(value));
            dialogButton(pane, "Save").fire();
            assertNull(findEditor());
            assertTrue(nodes(root).stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> (tab.equals("guarantors") ? expected + " Johnson" : expected).equals(label.getText())));
            return null;
        });
    }

    @Test void addsDocumentUsingMetadataWithoutSelectingAFile() throws Exception {
        JavaFxTestSupport.run(() -> {
            ((ToggleButton) loader.getNamespace().get("documentsTab")).fire();
            nodes(root).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().equals("+  Add Document")).findFirst().orElseThrow().fire();
            DialogPane pane = editor();
            ((TextField) pane.lookup("#edit_fileName")).setText("insurance.pdf");
            ((ComboBox<String>) pane.lookup("#edit_type")).setValue("Other");
            ((DatePicker) pane.lookup("#edit_date")).setValue(LocalDate.of(2026, 10, 14));
            assertNull(pane.lookup("#edit_path"));
            dialogButton(pane, "Save").fire();
            assertNull(findEditor());
            assertTrue(nodes(root).stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals("insurance.pdf")));
            assertTrue(nodes(root).stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals("14 Oct 2026")));
            return null;
        });
    }

    @Test void editsDocumentTypeAndUploadDate() throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("documents", "Actions for ID - Passport.pdf");
            DialogPane pane = editor();
            ((ComboBox<String>) pane.lookup("#edit_type")).setValue("Other");
            ((DatePicker) pane.lookup("#edit_date")).setValue(LocalDate.of(2026, 9, 1));
            dialogButton(pane, "Save").fire();

            assertNull(findEditor());
            assertTrue(nodes(root).stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals("1 Sep 2026")));
            return null;
        });
    }

    @Test void deletesDocumentFromCustomer() throws Exception {
        JavaFxTestSupport.run(() -> {
            ((ToggleButton) loader.getNamespace().get("documentsTab")).fire();
            Button actions = nodes(root).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> "Actions for Payslip.pdf".equals(button.getAccessibleText()))
                    .findFirst().orElseThrow();
            javafx.application.Platform.runLater(CustomerEditingTest::confirmDeletion);
            actions.getContextMenu().getItems().get(1).fire();

            assertFalse(nodes(root).stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals("Payslip.pdf")));
            return null;
        });
    }

    @Test void validationRejectsImpossibleDatesAndInvalidNumbers() {
        assertTrue(CustomerRecordEditor.validate(Kind.PERSONAL,
                Map.of("title", "Ms", "firstName", "Sarah", "lastName", "Johnson", "birthDate", "31-02-1990", "nationality", "New Zealand"), false).containsKey("birthDate"));
        Map<String, String> invalid = CustomerRecordEditor.validate(Kind.LOAN,
                Map.of("id", "LN1", "type", "Home Loan", "amount", "-20", "rate", "101", "status", "Active"), true);
        assertTrue(invalid.containsKey("amount"));
        assertTrue(invalid.containsKey("rate"));
    }

    @ParameterizedTest
    @CsvSource({"Add contact information,additionalContacts", "Add employer,additionalEmployers"})
    void addCreatesSeparateRecordAndPreservesOriginal(String action, String container) throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("details", action);
            DialogPane pane = editor();
            if (container.equals("additionalContacts")) {
                ((TextField) pane.lookup("#edit_value")).setText("second@example.com");
            } else {
                ((TextField) pane.lookup("#edit_employer")).setText("Second Company");
                ((TextField) pane.lookup("#edit_role")).setText("Developer");
                ((TextField) pane.lookup("#edit_income")).setText("45000");
            }
            dialogButton(pane, "Save").fire();
            assertNull(findEditor());
            assertEquals("sjohn67@gmail.com", controller.getCustomer().email());
            assertEquals("Smith Consulting", ((Label) loader.getNamespace().get("employerNameValue")).getText());
            if (container.equals("additionalContacts")) {
                Button view = (Button) loader.getNamespace().get("viewAllEmail");
                assertTrue(view.isVisible());
                assertFalse(((Button) loader.getNamespace().get("viewAllPhone")).isVisible());
                view.fire();
                DialogPane contacts = contactList();
                Button edit = nodes(contacts).stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> "Edit second@example.com".equals(button.getAccessibleText())).findFirst().orElseThrow();
                edit.fire();
                pane = editor();
                assertEquals("second@example.com", ((TextField) pane.lookup("#edit_value")).getText());
                ((TextField) pane.lookup("#edit_value")).setText("updated@example.com");
                dialogButton(pane, "Save").fire();
                assertTrue(nodes(contacts).stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> "updated@example.com".equals(label.getText())));
                assertEquals("sjohn67@gmail.com", controller.getCustomer().email());
                dialogButton(contacts, "Close").fire();
                controller.setCustomer(new CustomersController.CustomerSummary("C001", "Amelia Adams", "021 555 0101", "amelia@example.com"));
                assertFalse(view.isVisible());
                controller.setCustomer(sarah);
                assertTrue(view.isVisible());
            } else {
                Parent list = (Parent) loader.getNamespace().get(container);
                assertEquals(1, list.getChildrenUnmodifiable().size());
                Button edit = nodes(list).stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
                assertNotNull(edit.getGraphic());
                edit.fire(); pane = editor();
                assertEquals("Second Company", ((TextField) pane.lookup("#edit_employer")).getText());
                dialogButton(pane, "Cancel").fire();
                controller.setCustomer(new CustomersController.CustomerSummary("C001", "Amelia Adams", "021 555 0101", "amelia@example.com"));
                assertEquals(1, list.getChildrenUnmodifiable().size());
                assertEquals("No employers added.", ((Label) list.getChildrenUnmodifiable().get(0)).getText());
                controller.setCustomer(sarah);
                assertEquals(1, list.getChildrenUnmodifiable().size());
            }
            return null;
        });
    }

    private void openEditor(String tab, String accessibleText) {
        ((ToggleButton) loader.getNamespace().get(tab + "Tab")).fire();
        Button button = nodes(root).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> accessibleText.equals(candidate.getAccessibleText())).findFirst().orElseThrow();
        if (button.getContextMenu() == null) button.fire();
        else button.getContextMenu().getItems().get(0).fire();
    }
    private static DialogPane contactList() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window.getScene() != null).map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> "customerContactList".equals(pane.getId())).findFirst().orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({"Address,34 Queen Street,viewAllAddress"})
    void contactsAreGroupedByTypeAndPrimaryCanBeEditedFromList(String type, String value, String link) throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("details", "Add contact information");
            DialogPane pane = editor();
            fillContact(pane, type, value);
            dialogButton(pane, "Save").fire();
            Button view = (Button) loader.getNamespace().get(link);
            assertTrue(view.isVisible()); view.fire();
            DialogPane contacts = contactList();
            List<Button> edits = nodes(contacts).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getAccessibleText() != null && button.getAccessibleText().startsWith("Edit ")).toList();
            assertEquals(2, edits.size());
            edits.get(0).fire(); pane = editor();
            assertTrue(pane.lookup("#edit_contactType").isDisabled());
            fillContact(pane, type, value);
            dialogButton(pane, "Save").fire();
            assertEquals(shown(type, value), ((Label) loader.getNamespace().get(type.equals("Phone") ? "phoneValue" : "addressValue")).getText());
            return null;
        });
    }

    private static void confirmDeletion() {
        DialogPane confirmation = Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window.getScene() != null).map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.getButtonTypes().contains(ButtonType.OK)).findFirst().orElseThrow();
        ((Button) confirmation.lookupButton(ButtonType.OK)).fire();
    }

    @Test void choosingPrimaryAddressPreservesOtherAddressAndAllowsDeletingIt() throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("details", "Add contact information");
            DialogPane pane = editor();
            fillContact(pane, "Address", "34 Queen Street");
            dialogButton(pane, "Save").fire();
            ((Button) loader.getNamespace().get("viewAllAddress")).fire();
            DialogPane contacts = contactList();
            nodes(contacts).stream().filter(CheckBox.class::isInstance).map(CheckBox.class::cast)
                    .filter(button -> "Make primary 34 Queen Street, Auckland, NZ".equals(button.getAccessibleText())).findFirst().orElseThrow().fire();
            assertEquals("34 Queen Street, Auckland, NZ", ((Label) loader.getNamespace().get("addressValue")).getText());
            assertTrue(nodes(contacts).stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals("12 Koru Street, Whero, Auckland, 0945")));
            nodes(contacts).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> "Delete 12 Koru Street, Whero, Auckland, 0945".equals(button.getAccessibleText())).findFirst().orElseThrow().fire();
            confirmDeletion();
            assertEquals("34 Queen Street, Auckland, NZ", ((Label) loader.getNamespace().get("addressValue")).getText());
            assertFalse(((Button) loader.getNamespace().get("viewAllAddress")).isVisible());
            return null;
        });
    }

    @Test void phoneTxtPreferenceIsSavedAndRestoredInEditor() throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("details", "Edit phone");
            DialogPane pane = editor();
            CheckBox texts = (CheckBox) pane.lookup("#edit_texts");
            assertTrue(texts.isVisible()); texts.setSelected(true);
            dialogButton(pane, "Save").fire();
            openEditor("details", "Edit phone");
            assertTrue(((CheckBox) editor().lookup("#edit_texts")).isSelected());
            dialogButton(editor(), "Cancel").fire();
            assertNull(findEditor(), "Cancel should close the phone editor");
            openEditor("details", "Edit email");
            assertFalse(editor().lookup("#edit_texts").isVisible());
            return null;
        });
    }

    @Test void deletingPrimaryContactPromotesRemainingEntryAndUpdatesSummary() throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("details", "Add contact information");
            DialogPane pane = editor();
            ((TextField) pane.lookup("#edit_value")).setText("second@example.com");
            dialogButton(pane, "Save").fire();
            openEditor("details", "Edit email");
            pane = editor();
            nodes(pane).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> "Delete contact".equals(button.getAccessibleText())).findFirst().orElseThrow().fire();
            confirmDeletion();
            assertNull(findEditor());
            assertEquals("second@example.com", controller.getCustomer().email());
            assertEquals("second@example.com", ((Label) loader.getNamespace().get("emailValue")).getText());
            return null;
        });
    }

    @Test void missingContactsShowMessagesWithoutChangingStoredValues() throws Exception {
        JavaFxTestSupport.run(() -> {
            String[][] contacts = {
                {"Edit email", "emailValue", "No email address"},
                {"Edit phone", "phoneValue", "No phone number"},
                {"Edit address", "addressValue", "No mailing address"}
            };
            for (String[] contact : contacts) {
                openEditor("details", contact[0]);
                DialogPane pane = editor();
                nodes(pane).stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> "Delete contact".equals(button.getAccessibleText())).findFirst().orElseThrow().fire();
                confirmDeletion();
                assertEquals(contact[2], ((Label) loader.getNamespace().get(contact[1])).getText());
                openEditor("details", contact[0]);
                assertEquals("", ((TextField) editor().lookup("#edit_value")).getText());
                dialogButton(editor(), "Cancel").fire();
            }
            assertEquals("", controller.getCustomer().email());
            assertEquals("", controller.getCustomer().phone());
            return null;
        });
    }

    @Test void deletingSpecificEmployerAndLastEmployerKeepsAddWorking() throws Exception {
        JavaFxTestSupport.run(() -> {
            openEditor("details", "Add employer");
            DialogPane pane = editor();
            ((TextField) pane.lookup("#edit_employer")).setText("Second Company");
            ((TextField) pane.lookup("#edit_role")).setText("Developer");
            ((TextField) pane.lookup("#edit_income")).setText("45000");
            dialogButton(pane, "Save").fire();
            nodes(root).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> "Delete Second Company".equals(button.getAccessibleText())).findFirst().orElseThrow().fire();
            confirmDeletion();
            assertEquals("Smith Consulting", ((Label) loader.getNamespace().get("employerNameValue")).getText());
            openEditor("details", "Delete employment");
            confirmDeletion();
            assertFalse(((Node) loader.getNamespace().get("primaryEmployerRow")).isManaged());
            openEditor("details", "Add employer");
            pane = editor();
            ((TextField) pane.lookup("#edit_employer")).setText("Replacement Company");
            ((TextField) pane.lookup("#edit_role")).setText("Consultant");
            ((TextField) pane.lookup("#edit_income")).setText("50000");
            dialogButton(pane, "Save").fire();
            assertEquals("Replacement Company", ((Label) loader.getNamespace().get("employerNameValue")).getText());
            assertTrue(((Node) loader.getNamespace().get("primaryEmployerRow")).isManaged());
            return null;
        });
    }

    @Test void addingEmployersOnlyIncreasesEmploymentCardHeight() throws Exception {
        JavaFxTestSupport.run(() -> {
            var personal = (javafx.scene.layout.VBox) loader.getNamespace().get("personalCard");
            var contact = (javafx.scene.layout.VBox) loader.getNamespace().get("contactCard");
            var employment = (javafx.scene.layout.VBox) loader.getNamespace().get("employmentCard");
            for (int pass = 0; pass < 4; pass++) { root.applyCss(); root.requestLayout(); root.layout(); }
            double personalHeight = personal.getHeight();
            double contactHeight = contact.getHeight();
            double employmentHeight = employment.getHeight();
            for (int index = 0; index < 3; index++) {
                openEditor("details", "Add employer");
                DialogPane pane = editor();
                ((TextField) pane.lookup("#edit_employer")).setText("New Company " + index);
                ((TextField) pane.lookup("#edit_role")).setText("Consultant");
                ((TextField) pane.lookup("#edit_income")).setText("45000");
                dialogButton(pane, "Save").fire();
                for (int pass = 0; pass < 4; pass++) { root.applyCss(); root.requestLayout(); root.layout(); }
                assertEquals(personalHeight, personal.getHeight(), 1, "Personal card grew with employment");
                assertEquals(contactHeight, contact.getHeight(), 1, "Contact card grew with employment");
                assertTrue(employment.getHeight() > employmentHeight);
                employmentHeight = employment.getHeight();
                assertEquals(personal.getLayoutY(), employment.getLayoutY(), 1, "Cards should remain top aligned");
            }
            return null;
        });
    }

    private static DialogPane findEditor() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window.getScene() != null).map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> "customerRecordEditor".equals(pane.getId())).findFirst().orElse(null);
    }
    private static DialogPane editor() {
        DialogPane pane = findEditor(); assertNotNull(pane); pane.applyCss(); pane.layout(); return pane;
    }
    private static Button dialogButton(DialogPane pane, String text) {
        ButtonType type = pane.getButtonTypes().stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
        return (Button) pane.lookupButton(type);
    }
    private static List<Node> nodes(Node root) {
        List<Node> nodes = new ArrayList<>(); nodes.add(root);
        if (root instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> nodes.addAll(nodes(child)));
        return nodes;
    }

    private static void fillContact(DialogPane pane, String type, String value) {
        ((ComboBox<String>) pane.lookup("#edit_contactType")).setValue(type);
        if (type.equals("Address")) {
            ((TextField) pane.lookup("#edit_street")).setText(value);
            ((TextField) pane.lookup("#edit_suburb")).setText("");
            ((TextField) pane.lookup("#edit_city")).setText("Auckland");
            ((TextField) pane.lookup("#edit_postCode")).setText("");
            ((TextField) pane.lookup("#edit_country")).setText("NZ");
        } else {
            ((TextField) pane.lookup("#edit_value")).setText(value);
        }
    }

    // What the screen shows for a contact entered with fillContact
    private static String shown(String type, String value) {
        return type.equals("Address") ? value + ", Auckland, NZ" : value;
    }
}
