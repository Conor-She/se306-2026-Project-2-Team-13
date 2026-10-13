package com.lavs.ui;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real FXML and user controls without a mainframe connection. */
class LoanDetailsControllerTest {
    private FXMLLoader loader;
    private LoanDetailsController controller;
    private Parent root;

    @BeforeAll static void startToolkit() throws Exception { JavaFxTestSupport.startToolkit(); }

    @BeforeEach void openLoan() throws Exception {
        JavaFxTestSupport.run(() -> {
            loader = new FXMLLoader(LoanDetailsController.class.getResource("fxml/loanDetails.fxml"));
            root = loader.load();
            controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1100, 700));
            stage.show();
            controller.setLoan(loan("LN-1", "Sarah Johnson", "Active", "$450,000", "Home Loan"));
            return null;
        });
    }

    @AfterEach void closeWindows() throws Exception {
        JavaFxTestSupport.run(() -> {
            List.copyOf(Window.getWindows()).forEach(Window::hide);
            return null;
        });
    }

    private static LoansController.LoanSummary loan(String id, String name, String status, String amount, String type) {
        return new LoansController.LoanSummary(id, "67670442", name, status, amount, type);
    }
    private <T> T node(String id, Class<T> type) { return type.cast(loader.getNamespace().get(id)); }
    private String text(String id) { return node(id, Label.class).getText(); }
    private Button button(String accessibleText) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> accessibleText.equals(b.getAccessibleText())).findFirst().orElseThrow();
    }
    private String paymentCell(int row, int column) {
        return node("paymentRows", GridPane.class).getChildren().stream().filter(Label.class::isInstance)
                .filter(n -> Integer.valueOf(row).equals(GridPane.getRowIndex(n))
                        && Integer.valueOf(column).equals(GridPane.getColumnIndex(n)))
                .map(Label.class::cast).map(Label::getText).findFirst().orElseThrow();
    }
    private static void click(DialogPane pane, String text) {
        ButtonType type = pane.getButtonTypes().stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
        ((Button) pane.lookupButton(type)).fire();
    }

    /** Interacts with showAndWait dialogs during their nested JavaFX event loop. */
    private void modal(Button trigger, Consumer<DialogPane> interaction) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean visited = new AtomicBoolean();
        Platform.runLater(() -> {
            DialogPane pane = null;
            try {
                pane = Window.getWindows().stream().filter(Window::isShowing)
                        .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                        .map(DialogPane.class::cast).findFirst().orElseThrow();
                visited.set(true);
                interaction.accept(pane);
            } catch (Throwable problem) {
                failure.set(problem);
            } finally {
                if (pane != null && pane.getScene() != null && pane.getScene().getWindow().isShowing()) {
                    click(pane, "Cancel");
                }
            }
        });
        trigger.fire();
        if (failure.get() != null) throw new AssertionError("Dialog interaction failed", failure.get());
        assertTrue(visited.get(), "Expected a payment dialog to open");
    }

    @Test void defaultsToLoanInformationAndUpdatesSelectedLoan() throws Exception {
        JavaFxTestSupport.run(() -> {
            assertTrue(node("loanDetailsView", Node.class).isVisible());
            assertEquals("Loan ID: LN-1", text("loanHeading"));
            assertEquals("Sarah Johnson | 67670442", text("customerHeading"));
            assertEquals("Home Loan", text("typeValue"));
            assertEquals("$450,000", text("amountValue"));
            assertEquals("Active", text("statusValue"));
            assertTrue(text("loanInfoBanner").contains("active"));
            controller.setLoan(loan("LN-2", "Alex Smith", "Pending", "$12,000", "Table Loan"));
            assertEquals("Loan ID: LN-2", text("loanHeading"));
            assertEquals("Alex Smith | 67670442", text("customerHeading"));
            assertEquals("Table Loan", text("typeValue"));
            assertEquals("$12,000", text("amountValue"));
            assertEquals("Pending", text("statusValue"));
            assertTrue(text("loanInfoBanner").contains("pending"));
            return null;
        });
    }

    @Test void eachTabShowsOnlyItsOwnContentAndSelectedIndicator() throws Exception {
        JavaFxTestSupport.run(() -> {
            String[] tabs = {"loanDetailsTab", "coborrowersTab", "repaymentScheduleTab", "paymentsTab"};
            String[] views = {"loanDetailsView", "coborrowersView", "repaymentScheduleView", "paymentsView"};
            for (int selected = 0; selected < tabs.length; selected++) {
                node(tabs[selected], Button.class).fire();
                for (int i = 0; i < tabs.length; i++) {
                    assertEquals(i == selected, node(views[i], Node.class).isVisible(), views[i]);
                    assertEquals(i == selected, node(views[i], Node.class).isManaged(), views[i]);
                    assertEquals(i == selected, node(tabs[i], Button.class).getParent()
                            .lookup(".loan-tab-indicator").isVisible(), tabs[i]);
                }
            }
            return null;
        });
    }

    @Test void repaymentScheduleContainsExactlyTwoCompleteStubRows() throws Exception {
        JavaFxTestSupport.run(() -> {
            node("repaymentScheduleTab", Button.class).fire();
            GridPane table = node("repaymentScheduleTable", GridPane.class);
            List<Label> rows = table.getChildren().stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).filter(n -> GridPane.getRowIndex(n) > 0).toList();
            assertEquals(12, rows.size());
            assertTrue(rows.stream().allMatch(n -> GridPane.getRowIndex(n) <= 2));
            assertTrue(rows.stream().anyMatch(n -> "15/03/2025".equals(n.getText())));
            assertTrue(rows.stream().anyMatch(n -> "01/04/2025".equals(n.getText())));
            assertTrue(rows.stream().anyMatch(n -> "$497,493.88".equals(n.getText())));
            return null;
        });
    }

    @Test void paymentsDisplayNewestFirstWithBadgesReferencesAndActions() throws Exception {
        JavaFxTestSupport.run(() -> {
            node("paymentsTab", Button.class).fire();
            assertEquals("Showing 1-3 of 3", text("paymentsCount"));
            assertEquals("01/09/2025", paymentCell(1, 0));
            assertEquals("15/08/2025", paymentCell(2, 0));
            assertEquals("01/08/2025", paymentCell(3, 0));
            assertEquals("$3,245.67", paymentCell(1, 1));
            assertEquals("Principal & Interest", paymentCell(1, 2));
            assertEquals("Paid", paymentCell(1, 3));
            assertEquals("FP-001234", paymentCell(1, 4));
            assertNotNull(button("Edit payment FP-001234"));
            assertNotNull(button("Delete payment FP-001234"));
            return null;
        });
    }

    @Test void addingPaymentValidatesAmountAndDateAndUpdatesTable() throws Exception {
        JavaFxTestSupport.run(() -> {
            node("paymentsTab", Button.class).fire();
            modal(button("Add payment"), pane -> {
                GridPane form = (GridPane) pane.getContent();
                TextField amount = form.getChildren().stream().filter(TextField.class::isInstance)
                        .map(TextField.class::cast).findFirst().orElseThrow();
                DatePicker date = (DatePicker) pane.lookup(".date-picker");
                Button save = (Button) pane.lookupButton(pane.getButtonTypes().stream()
                        .filter(b -> "Add payment".equals(b.getText())).findFirst().orElseThrow());
                for (String invalid : List.of("", "abc", "0", "-5")) {
                    amount.setText(invalid);
                    assertTrue(save.isDisabled(), invalid);
                }
                amount.setText("$1,250.50");
                date.setValue(null);
                assertTrue(save.isDisabled());
                date.setValue(LocalDate.of(2026, 1, 15));
                assertFalse(save.isDisabled());
                save.fire();
            });
            assertEquals("Showing 1-4 of 4", text("paymentsCount"));
            assertEquals("15/01/2026", paymentCell(1, 0));
            assertEquals("$1,250.50", paymentCell(1, 1));
            assertTrue(paymentCell(1, 4).startsWith("PAY-"));
            return null;
        });
    }

    @Test void cancellingAddAndEditPreservesPaymentsAndSavingEditReordersRows() throws Exception {
        JavaFxTestSupport.run(() -> {
            node("paymentsTab", Button.class).fire();
            modal(button("Add payment"), pane -> click(pane, "Cancel"));
            assertEquals("Showing 1-3 of 3", text("paymentsCount"));
            modal(button("Edit payment FP-001234"), pane -> {
                TextField amount = ((GridPane) pane.getContent()).getChildren().stream()
                        .filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElseThrow();
                assertEquals("3245.67", amount.getText());
                amount.setText("99");
                click(pane, "Cancel");
            });
            assertEquals("$3,245.67", paymentCell(1, 1));
            modal(button("Edit payment FP-001234"), pane -> {
                ((GridPane) pane.getContent()).getChildren().stream().filter(TextField.class::isInstance)
                        .map(TextField.class::cast).findFirst().orElseThrow().setText("600");
                ((DatePicker) pane.lookup(".date-picker")).setValue(LocalDate.of(2025, 7, 1));
                click(pane, "Save changes");
            });
            assertEquals("Showing 1-3 of 3", text("paymentsCount"));
            assertEquals("FP-001234", paymentCell(3, 4));
            assertEquals("$600.00", paymentCell(3, 1));
            assertEquals("01/07/2025", paymentCell(3, 0));
            return null;
        });
    }

    @Test void deletionRequiresConfirmationAndLastDeletionShowsEmptyState() throws Exception {
        JavaFxTestSupport.run(() -> {
            node("paymentsTab", Button.class).fire();
            modal(button("Delete payment FP-001234"), pane -> {
                assertEquals("Showing 1-3 of 3", text("paymentsCount"));
                assertTrue(((Label) pane.lookup(".content.label")).getText().contains("$3,245.67"));
                click(pane, "Cancel");
            });
            assertEquals("Showing 1-3 of 3", text("paymentsCount"));
            for (String id : List.of("FP-001234", "FP-001233", "FP-001232")) {
                modal(button("Delete payment " + id), pane -> click(pane, "OK"));
            }
            assertEquals("Showing 0 of 0", text("paymentsCount"));
            assertEquals("No payments recorded for this loan.", paymentCell(1, 0));
            return null;
        });
    }

    @Test void backButtonInvokesConfiguredNavigation() throws Exception {
        JavaFxTestSupport.run(() -> {
            AtomicBoolean returned = new AtomicBoolean();
            controller.setOnBack(() -> returned.set(true));
            button("Back to customer loans").fire();
            assertTrue(returned.get());
            return null;
        });
    }
}
