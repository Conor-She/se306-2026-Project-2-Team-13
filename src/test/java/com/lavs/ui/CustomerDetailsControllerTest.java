package com.lavs.ui;

import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDetailsControllerTest {
    private static final List<String> SECTIONS =
            List.of("details", "loans", "notes", "documents", "guarantors");
    private static final CustomersController.CustomerSummary SARAH =
            new CustomersController.CustomerSummary("67670442", "Sarah Johnson",
                    "021 135 6767", "sjohn67@gmail.com");
    private FXMLLoader loader;
    private Parent root;
    private CustomerDetailsController controller;

    @BeforeAll static void startJavaFx() throws Exception {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach void loadPage() throws Exception {
        JavaFxTestSupport.run(() -> {
            loader = new FXMLLoader(Main.class.getResource("fxml/customerDetails.fxml"));
            root = loader.load();
            new Scene(root, 1008, 662);
            controller = loader.getController();
            controller.setCustomer(SARAH);
            root.applyCss();
            root.layout();
            return null;
        });
    }

    @Test void displaysSelectedCustomerAndDefaultsToDetails() throws Exception {
        JavaFxTestSupport.run(() -> {
            assertSame(SARAH, controller.getCustomer());
            assertEquals("Sarah Johnson", label("customerHeading").getText());
            assertEquals("Customer ID: 67670442", label("customerId").getText());
            assertEquals("Sarah Johnson", label("fullNameValue").getText());
            assertEquals("sjohn67@gmail.com", label("emailValue").getText());
            assertEquals("021 135 6767", label("phoneValue").getText());
            assertOnlySectionVisible("details");
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"details", "loans", "notes", "documents", "guarantors"})
    void switchingTabsDisplaysOnlySelectedSection(String section) throws Exception {
        JavaFxTestSupport.run(() -> {
            // Select another tab first so Details is tested as a transition too.
            tab("notes").fire();
            tab(section).fire();
            assertOnlySectionVisible(section);
            ToggleGroup group = (ToggleGroup) loader.getNamespace().get("customerTabs");
            assertSame(tab(section), group.getSelectedToggle());
            assertEquals(1, group.getToggles().stream().filter(t -> t.isSelected()).count());
            assertTrue(tab(section).getStyle().contains("-fx-font-weight: bold"));
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"details", "loans", "notes", "documents", "guarantors"})
    void clickingSelectedTabKeepsSectionOpen(String section) throws Exception {
        JavaFxTestSupport.run(() -> {
            tab(section).fire();
            tab(section).fire();
            assertOnlySectionVisible(section);
            return null;
        });
    }

    @Test void changingCustomerUpdatesFieldsAndResetsTabsAndScrollPositions() throws Exception {
        JavaFxTestSupport.run(() -> {
            for (String section : SECTIONS) content(section).setVvalue(0.75);
            tab("guarantors").fire();
            var next = new CustomersController.CustomerSummary("C001", "Amelia Adams",
                    "021 555 0101", "amelia@example.com");
            controller.setCustomer(next);
            assertSame(next, controller.getCustomer());
            assertEquals("Amelia Adams", label("customerHeading").getText());
            assertEquals("Customer ID: C001", label("customerId").getText());
            assertEquals("Amelia Adams", label("fullNameValue").getText());
            assertEquals("amelia@example.com", label("emailValue").getText());
            assertEquals("021 555 0101", label("phoneValue").getText());
            assertOnlySectionVisible("details");
            for (String section : SECTIONS) {
                assertEquals(0, content(section).getVvalue(), 0.001, section);
            }
            return null;
        });
    }

    @Test void backButtonInvokesConfiguredNavigation() throws Exception {
        JavaFxTestSupport.run(() -> {
            int[] calls = {0};
            controller.setOnBack(() -> calls[0]++);
            tab("documents").fire();
            ((Button) loader.getNamespace().get("backButton")).fire();
            assertEquals(1, calls[0]);
            return null;
        });
    }

    @Test void documentsTableShowsMatchingMetadataAndAccessibleRowActions() throws Exception {
        JavaFxTestSupport.run(() -> {
            tab("documents").fire();
            GridPane table = table("documents");
            assertRow(table, 0, "Document Name", "Type", "Uploaded Date", "Actions");
            String[][] documents = {
                {"ID - Passport.pdf", "Identity", "12 Sep 2025"},
                {"Proof of Address.pdf", "Address", "3 Aug 2025"},
                {"Payslip.pdf", "Income", "21 Jun 2025"},
                {"Bank Statement.pdf", "Bank Statement", "10 May 2025"}
            };
            for (int index = 0; index < documents.length; index++) {
                String[] document = documents[index];
                assertRow(table, index + 1, document);
                List<Button> actions = rowLabelsAndButtons(table, index + 1).stream()
                        .filter(Button.class::isInstance).map(Button.class::cast).toList();
                assertEquals(List.of("Download " + document[0], "Actions for " + document[0]),
                        actions.stream().map(Button::getAccessibleText).toList());
                actions.forEach(button -> assertNotNull(button.getOnAction()));
            }
            assertEquals(4, table.getChildren().stream()
                    .mapToInt(CustomerDetailsControllerTest::rowIndex).max().orElseThrow());
            assertNotNull(buttonWithText(content("documents"), "+  Upload Document").getOnAction());
            return null;
        });
    }

    @Test void guarantorsTableShowsRelationshipContactDetailsAndStatus() throws Exception {
        JavaFxTestSupport.run(() -> {
            tab("guarantors").fire();
            GridPane table = table("guarantors");
            assertRow(table, 0, "Name", "Relationship", "Phone", "Email", "Status", "Actions");
            assertRow(table, 1, "Michael Johnson", "Brother", "021 987 6543",
                    "michael.johnson@email.com", "Active");
            List<Button> actions = rowLabelsAndButtons(table, 1).stream()
                    .filter(Button.class::isInstance).map(Button.class::cast).toList();
            assertEquals(1, actions.size());
            assertEquals("Actions for guarantor Michael Johnson", actions.get(0).getAccessibleText());
            assertNotNull(actions.get(0).getOnAction());
            assertNotNull(buttonWithText(content("guarantors"), "+  Add Guarantor").getOnAction());
            return null;
        });
    }

    private Label label(String id) { return (Label) loader.getNamespace().get(id); }
    private ToggleButton tab(String section) {
        return (ToggleButton) loader.getNamespace().get(section + "Tab");
    }
    private ScrollPane content(String section) {
        return (ScrollPane) loader.getNamespace().get(section + "Content");
    }
    private void assertOnlySectionVisible(String selected) {
        for (String section : SECTIONS) {
            assertEquals(section.equals(selected), content(section).isVisible(), section + " visibility");
            assertEquals(section.equals(selected), content(section).isManaged(), section + " layout");
            assertEquals(section.equals(selected), tab(section).isSelected(), section + " selection");
        }
    }
    private GridPane table(String section) {
        return ((Parent) content(section).getContent()).getChildrenUnmodifiable().stream()
                .filter(GridPane.class::isInstance).map(GridPane.class::cast).findFirst().orElseThrow();
    }
    private static int rowIndex(Node node) {
        Integer row = GridPane.getRowIndex(node);
        return row == null ? 0 : row;
    }
    private static List<Node> rowLabelsAndButtons(GridPane table, int row) {
        return table.getChildren().stream().filter(node -> rowIndex(node) == row)
                .flatMap(node -> descendants(node).stream())
                .filter(node -> node instanceof Label || node instanceof Button).toList();
    }
    private static List<Node> descendants(Node node) {
        var nodes = new java.util.ArrayList<Node>();
        nodes.add(node);
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> nodes.addAll(descendants(child)));
        }
        return nodes;
    }
    private static void assertRow(GridPane table, int row, String... expected) {
        assertEquals(List.of(expected), rowLabelsAndButtons(table, row).stream()
                .filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList(),
                "Table row " + row);
    }
    private static Button buttonWithText(ScrollPane pane, String text) {
        return descendants(pane.getContent()).stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }
}
