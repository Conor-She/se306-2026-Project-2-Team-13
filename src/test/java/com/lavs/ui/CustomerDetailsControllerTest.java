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
            CustomerUiFixtures.seed(controller, SARAH);
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
            assertEquals("Sarah", label("firstNameValue").getText());
            assertEquals("Johnson", label("lastNameValue").getText());
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
            assertEquals("Amelia", label("firstNameValue").getText());
            assertEquals("Adams", label("lastNameValue").getText());
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
            assertRow(table, 0, "Document Name", "Type", "Actions");
            String[][] documents = {
                {"ID - Passport.pdf", "Identity"},
                {"Proof of Address.pdf", "Address"},
                {"Payslip.pdf", "Income"},
                {"Bank Statement.pdf", "Bank Statement"}
            };
            for (int index = 0; index < documents.length; index++) {
                String[] document = documents[index];
                assertRow(table, index + 1, document);
                List<Button> actions = rowLabelsAndButtons(table, index + 1).stream()
                        .filter(Button.class::isInstance).map(Button.class::cast).toList();
                assertEquals(List.of("Actions for " + document[0]),
                        actions.stream().map(Button::getAccessibleText).toList());
                actions.forEach(button -> assertNotNull(button.getOnAction()));
            }
            assertEquals(4, table.getChildren().stream()
                    .mapToInt(CustomerDetailsControllerTest::rowIndex).max().orElseThrow());
            assertNotNull(buttonWithText(content("documents"), "+  Add Document").getOnAction());
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

    @Test void overviewCardsUseThreeColumnsAndStackAtNarrowWidths() throws Exception {
        JavaFxTestSupport.run(() -> {
            var personal = (javafx.scene.layout.VBox) loader.getNamespace().get("personalCard");
            var contact = (javafx.scene.layout.VBox) loader.getNamespace().get("contactCard");
            var employment = (javafx.scene.layout.VBox) loader.getNamespace().get("employmentCard");
            for (int width : new int[] {1008, 420}) {
                root.resize(width, 900);
                for (int pass = 0; pass < 4; pass++) { root.applyCss(); root.requestLayout(); root.layout(); }
                assertEquals(personal.getWidth(), contact.getWidth(), 1);
                assertEquals(contact.getWidth(), employment.getWidth(), 1);
                if (width == 1008) {
                    assertEquals(personal.getLayoutY(), contact.getLayoutY(), 1);
                    assertEquals(contact.getLayoutY(), employment.getLayoutY(), 1);
                    assertTrue(contact.getLayoutX() > personal.getLayoutX());
                    assertTrue(employment.getLayoutX() > contact.getLayoutX());
                } else {
                    assertTrue(contact.getLayoutY() > personal.getLayoutY());
                    assertTrue(employment.getLayoutY() > contact.getLayoutY());
                }
            }
            return null;
        });
    }

    @Test void overviewDoesNotKeepResizingAcrossRenderedFrames() throws Exception {
        var completed = new java.util.concurrent.CompletableFuture<Void>();
        JavaFxTestSupport.run(() -> {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(root.getScene());
            stage.setWidth(760);
            stage.setHeight(560);
            stage.show();
            var cards = List.of((javafx.scene.layout.VBox) loader.getNamespace().get("personalCard"),
                    (javafx.scene.layout.VBox) loader.getNamespace().get("contactCard"),
                    (javafx.scene.layout.VBox) loader.getNamespace().get("employmentCard"));
            new javafx.animation.AnimationTimer() {
                int frames;
                List<Double> previous;
                @Override public void handle(long now) {
                    try {
                        frames++;
                        var geometry = cards.stream().flatMap(card -> java.util.stream.Stream.of(
                                card.getWidth(), card.getHeight(), card.getLayoutX(), card.getLayoutY())).toList();
                        if (frames > 10 && previous != null) {
                            for (int index = 0; index < geometry.size(); index++) {
                                assertEquals(previous.get(index), geometry.get(index), 0.5,
                                        "Overview geometry changed on frame " + frames);
                            }
                        }
                        previous = geometry;
                        if (frames >= 35) { stop(); stage.close(); completed.complete(null); }
                    } catch (Throwable failure) {
                        stop(); stage.close(); completed.completeExceptionally(failure);
                    }
                }
            }.start();
            return null;
        });
        completed.get(10, java.util.concurrent.TimeUnit.SECONDS);
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
