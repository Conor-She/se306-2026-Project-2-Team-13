package com.lavs.ui;

import java.time.LocalDate;
import java.util.List;
import com.lavs.business.LoanBrowsingService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Loan;
import com.lavs.model.Payment;
import com.lavs.model.Repayment;
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
    private FakeMainframeGateway gateway;

    @BeforeAll static void startToolkit() throws Exception { JavaFxTestSupport.startToolkit(); }

    @BeforeEach void openLoan() throws Exception {
        JavaFxTestSupport.run(() -> {
            loader = new FXMLLoader(LoanDetailsController.class.getResource("fxml/loanDetails.fxml"));
            root = loader.load();
            controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1100, 700));
            stage.show();
            gateway = new FakeMainframeGateway();
            gateway.createCustomer(new com.lavs.model.Customer("67670442", "Ms", "Sarah", "Johnson", null, "NZ"));
            gateway.createLoan(new Loan("LN-1", "67670442", Loan.Status.ACTIVE, 450000, 6.5,
                    Loan.Type.TABLE_LOAN, false, LocalDate.of(2025, 1, 1), LocalDate.of(2055, 1, 1),
                    3245.67, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 360));
            gateway.addPayment(new Payment("P1", "LN-1", "R1", LocalDate.of(2025, 8, 1), 3245.67));
            gateway.addPayment(new Payment("P2", "LN-1", null, LocalDate.of(2025, 8, 15), 3245.67));
            gateway.addPayment(new Payment("P3", "LN-1", "R2", LocalDate.of(2025, 9, 1), 3245.67));
            gateway.saveRepaymentSchedule("LN-1", List.of(
                    new Repayment("R2", "LN-1", LocalDate.of(2025, 3, 1), 3245.67, 1245.67, 2000, 448754.33),
                    new Repayment("R1", "LN-1", LocalDate.of(2025, 2, 1), 3000, 1000, 2000, 449000),
                    new Repayment("R3", "LN-1", LocalDate.of(2025, 4, 1), 3500, 1500, 2000, 447254.33)));
            controller.setLoanBrowsingService(new LoanBrowsingService(gateway));
            controller.setPaymentService(new com.lavs.business.PaymentService(gateway));
            controller.setRequestExecutor(Runnable::run);
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
    // Runs an empty task so any screen updates already queued (like a table reload) finish first
    private void flushScreenThread() throws Exception {
        JavaFxTestSupport.run(() -> null);
    }

    private String paymentCell(int row, int column) {
        return node("paymentRows", GridPane.class).getChildren().stream().filter(Label.class::isInstance)
                .filter(n -> Integer.valueOf(row).equals(GridPane.getRowIndex(n))
                        && Integer.valueOf(column).equals(GridPane.getColumnIndex(n)))
                .map(Label.class::cast).map(Label::getText).findFirst().orElseThrow();
    }
    private String repaymentCell(int row, int column) {
        return node("repaymentScheduleTable", GridPane.class).getChildren().stream().filter(Label.class::isInstance)
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

    @Test void repaymentScheduleUsesTheSelectedLoansRealRowsAndSummary() throws Exception {
        JavaFxTestSupport.run(() -> {
            node("repaymentScheduleTab", Button.class).fire();
            GridPane table = node("repaymentScheduleTable", GridPane.class);
            List<Label> rows = table.getChildren().stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).filter(n -> GridPane.getRowIndex(n) > 0).toList();
            assertEquals(18, rows.size());
            assertTrue(rows.stream().allMatch(n -> GridPane.getRowIndex(n) <= 3));
            assertEquals("01/02/2025", repaymentCell(1, 1));
            assertEquals("$3,000.00", repaymentCell(1, 2));
            assertEquals("$447,254.33", repaymentCell(3, 5));
            assertEquals("$9,745.67", text("repaymentTotalValue"));
            assertEquals("$450,000.00", text("repaymentPrincipalValue"));
            assertEquals("$6,000.00", text("repaymentInterestValue"));
            assertEquals("30 years", text("repaymentTermValue"));
            assertEquals("Showing 1-3 of 3", text("repaymentsCount"));
            return null;
        });
    }

    @Test void repaymentTermIsRecoveredWhenMainframeDoesNotReturnIt() throws Exception {
        JavaFxTestSupport.run(() -> {
            gateway.createLoan(new Loan("LN-RELOADED", "67670442", Loan.Status.NEW, 12000, 5,
                    Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2029, 4, 1),
                    1000, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, null));
            controller.setLoan(loan("LN-RELOADED", "Sarah Johnson", "New", "$12,000", "Table Loan"));
            return null;
        });
        JavaFxTestSupport.run(() -> {
            assertEquals("3 years 3 months", text("repaymentTermValue"));
            return null;
        });
    }

    @Test void loanWithoutARepaymentScheduleShowsAnHonestEmptyState() throws Exception {
        JavaFxTestSupport.run(() -> {
            gateway.createLoan(new Loan("LN-EMPTY", "67670442", Loan.Status.NEW, 12000, 5,
                    Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                    1000, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12));
            controller.setLoan(loan("LN-EMPTY", "Sarah Johnson", "New", "$12,000", "Table Loan"));
            return null;
        });
        JavaFxTestSupport.run(() -> {
            assertEquals("$0.00", text("repaymentTotalValue"));
            assertEquals("$12,000.00", text("repaymentPrincipalValue"));
            assertEquals("Showing 0 of 0", text("repaymentsCount"));
            assertEquals("No repayment schedule recorded for this loan.", repaymentCell(1, 0));
            return null;
        });
    }

    @Test void unavailableRepaymentScheduleShowsAnErrorInsteadOfOldLoanData() throws Exception {
        JavaFxTestSupport.run(() -> {
            gateway.simulateUnavailable(true);
            controller.setLoan(loan("LN-1", "Sarah Johnson", "Active", "$450,000", "Home Loan"));
            return null;
        });
        JavaFxTestSupport.run(() -> {
            assertEquals("Unavailable", text("repaymentTotalValue"));
            assertEquals("Showing 0 of 0", text("repaymentsCount"));
            assertEquals("Repayment schedule could not be loaded.", repaymentCell(1, 0));
            gateway.simulateUnavailable(false);
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
            assertEquals("P3", paymentCell(1, 2));
            assertNotNull(button("Edit payment P3"));
            assertNotNull(button("Delete payment P3"));
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
            return null;
        });
        // The table reloads from the mainframe after the save; let that reload finish first
        flushScreenThread();
        JavaFxTestSupport.run(() -> {
            assertEquals(4, gateway.getPayments("LN-1").size());
            assertEquals("Showing 1-4 of 4", text("paymentsCount"));
            assertEquals("15/01/2026", paymentCell(1, 0));
            assertEquals("$1,250.50", paymentCell(1, 1));
            // The ID comes from the mainframe, not from the screen
            assertTrue(paymentCell(1, 2).startsWith("FAKE-PAYMENT"));
            return null;
        });
    }

    @Test void cancellingAddAndEditPreservesPaymentsAndSavingEditReordersRows() throws Exception {
        JavaFxTestSupport.run(() -> {
            node("paymentsTab", Button.class).fire();
            modal(button("Add payment"), pane -> click(pane, "Cancel"));
            assertEquals("Showing 1-3 of 3", text("paymentsCount"));
            modal(button("Edit payment P3"), pane -> {
                TextField amount = ((GridPane) pane.getContent()).getChildren().stream()
                        .filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElseThrow();
                assertEquals("3245.67", amount.getText());
                amount.setText("99");
                click(pane, "Cancel");
            });
            assertEquals("$3,245.67", paymentCell(1, 1));
            modal(button("Edit payment P3"), pane -> {
                ((GridPane) pane.getContent()).getChildren().stream().filter(TextField.class::isInstance)
                        .map(TextField.class::cast).findFirst().orElseThrow().setText("600");
                ((DatePicker) pane.lookup(".date-picker")).setValue(LocalDate.of(2025, 7, 1));
                click(pane, "Save changes");
            });
            return null;
        });
        flushScreenThread();
        JavaFxTestSupport.run(() -> {
            assertEquals(600, gateway.getPayments("LN-1").stream()
                    .filter(p -> "P3".equals(p.getPaymentId())).findFirst().orElseThrow().getAmountPaid());
            assertEquals("Showing 1-3 of 3", text("paymentsCount"));
            assertEquals("P3", paymentCell(3, 2));
            assertEquals("$600.00", paymentCell(3, 1));
            assertEquals("01/07/2025", paymentCell(3, 0));
            return null;
        });
    }

    @Test void deletionRequiresConfirmationAndLastDeletionShowsEmptyState() throws Exception {
        JavaFxTestSupport.run(() -> {
            node("paymentsTab", Button.class).fire();
            modal(button("Delete payment P3"), pane -> {
                assertEquals("Showing 1-3 of 3", text("paymentsCount"));
                assertTrue(((Label) pane.lookup(".content.label")).getText().contains("$3,245.67"));
                click(pane, "Cancel");
            });
            assertEquals("Showing 1-3 of 3", text("paymentsCount"));
            return null;
        });
        for (String id : List.of("P3", "P2", "P1")) {
            JavaFxTestSupport.run(() -> {
                modal(button("Delete payment " + id), pane -> click(pane, "OK"));
                return null;
            });
        }
        flushScreenThread();
        JavaFxTestSupport.run(() -> {
            assertTrue(gateway.getPayments("LN-1").isEmpty());
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

        private static DialogPane editorDialog() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).filter(p -> "customerRecordEditor".equals(p.getId()))
                .findFirst().orElseThrow();
    }

    @Test void editLoanIsDisabledForActiveLoansOnly() throws Exception {
        JavaFxTestSupport.run(() -> {
            assertTrue(button("Edit loan").isDisabled(), "LN-1 is Active, so it cannot be edited");
            controller.setLoan(loan("LN-2", "Sarah Johnson", "New", "$12,000", "Table Loan"));
            assertFalse(button("Edit loan").isDisabled());
            return null;
        });
    }

    @Test void editingALoanSavesItToTheMainframeAndRefreshesThePage() throws Exception {
        JavaFxTestSupport.run(() -> {
            gateway.createLoan(new Loan("LN-2", "67670442", Loan.Status.NEW, 12000, 5,
                    Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                    1066.19, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, null));
            controller.setCustomerService(new com.lavs.business.CustomerService(gateway));
            controller.setLoan(loan("LN-2", "Sarah Johnson", "New", "$12,000", "Table Loan"));
            button("Edit loan").fire();
            DialogPane pane = editorDialog();
            ((TextField) pane.lookup("#edit_amount")).setText("15000");
            ((TextField) pane.lookup("#edit_rate")).setText("4.5");
            click(pane, "Save");
            assertEquals(15000, gateway.getLoan("LN-2").getPrincipal());
            assertEquals("$15,000.00", text("amountValue"));
            assertEquals("4.5%", text("interestRateValue"));
            assertEquals("$1,066.19", text("paymentAmountValue"));
            return null;
        });
    }

    @Test @SuppressWarnings("unchecked")
    void theEditLoanDialogShowsReadableLoanTypesAndSavesTheChosenOne() throws Exception {
        JavaFxTestSupport.run(() -> {
            gateway.createLoan(new Loan("LN-3", "67670442", Loan.Status.NEW, 12000, 5,
                    Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                    1066.19, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, null));
            controller.setCustomerService(new com.lavs.business.CustomerService(gateway));
            controller.setLoan(loan("LN-3", "Sarah Johnson", "New", "$12,000", "Table Loan"));
            button("Edit loan").fire();
            DialogPane pane = editorDialog();
            ComboBox<String> type = (ComboBox<String>) pane.lookup("#edit_type");
            assertEquals(List.of("Table Loan", "Reducing Balance", "Interest Only"), type.getItems());
            assertEquals("Table Loan", type.getValue());
            type.setValue("Interest Only");
            click(pane, "Save");
            assertEquals(Loan.Type.INTEREST_ONLY, gateway.getLoan("LN-3").getType());
            return null;
        });
    }
}
