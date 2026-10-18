package com.lavs.ui;

import java.time.LocalDate;
import java.util.List;
import com.lavs.business.LoanSubmissionService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Address;
import com.lavs.model.Customer;
import com.lavs.model.Employer;
import com.lavs.model.Phone;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddLoanControllerTest {
    @BeforeAll static void startToolkit() throws Exception { JavaFxTestSupport.startToolkit(); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void cancelRequiresConfirmationForEnteredDetails(boolean discard) throws Exception {
        JavaFxTestSupport.run(() -> {
            int[] confirmations = {0};
            int[] cancellations = {0};
            AddLoanController controller = new AddLoanController() {
                @Override protected boolean confirmDiscardDetails() {
                    confirmations[0]++;
                    return discard;
                }
            };
            var loader = new FXMLLoader(Main.class.getResource("fxml/addLoan.fxml"));
            loader.setControllerFactory(type -> controller);
            loader.load();
            controller.configure(List.of(), () -> cancellations[0]++);
            Button cancel = (Button) loader.getNamespace().get("cancel");
            cancel.fire();
            assertEquals(0, confirmations[0], "Empty forms can cancel immediately");
            assertEquals(1, cancellations[0]);
            ((TextField) loader.getNamespace().get("principal")).setText("500000");
            cancel.fire();
            assertEquals(1, confirmations[0]);
            assertEquals(discard ? 2 : 1, cancellations[0]);
            assertEquals("500000", controller.getDraft().details.get("principal"));
            return null;
        });
    }

    @Test void searchesBorrowerAndValidatesDetailsWithoutSaving() throws Exception {
        JavaFxTestSupport.run(() -> {
            var loader = new FXMLLoader(Main.class.getResource("fxml/addLoan.fxml"));
            javafx.scene.Parent root = loader.load();
            new javafx.scene.Scene(root, 1008, 662);
            root.applyCss();
            root.layout();
            AddLoanController controller = loader.getController();
            controller.configure(List.of(
                    new CustomersController.CustomerSummary("C001", "Amelia Adams", "", ""),
                    new CustomersController.CustomerSummary("C002", "Benjamin Baker", "", ""),
                    new CustomersController.CustomerSummary("C003", "Charlotte Brown", "", ""),
                    new CustomersController.CustomerSummary("C004", "Ethan Chen", "", ""),
                    new CustomersController.CustomerSummary("C005", "Isla Jones", "", "")), () -> {});
            var gateway = new FakeMainframeGateway();
            for (String customerId : List.of("C001", "C003", "C004", "C006", "C007")) {
                gateway.createCustomer(
                        new Customer(customerId, "", "Test", "Customer", null, "NZ"));
            }
            gateway.addAddress(address(1L, "C006", false));
            gateway.addPhone(phone(1L, "C006"));
            gateway.addEmployer(employer(1L, "C006"));
            gateway.addAddress(address(2L, "C007", true));
            gateway.addEmployer(employer(2L, "C007"));
            gateway.addAddress(address(3L, "C003", true));
            gateway.addPhone(phone(3L, "C003"));
            gateway.addAddress(address(4L, "C004", true));
            gateway.addPhone(phone(4L, "C004"));
            gateway.addEmployer(employer(4L, "C004"));
            gateway.addAddress(address(5L, "C001", true));
            gateway.addPhone(phone(5L, "C001"));
            gateway.addEmployer(employer(5L, "C001"));
            controller.setPrerequisiteValidator(
                    new LoanSubmissionService(gateway)::validatePrerequisites);
            assertNotNull(controller.validateDetails());
            controller.setPrimaryBorrower(new CustomersController.CustomerSummary("C006", "Liam Patel", "", ""));
            assertTrue(controller.validateDetails().contains("mailing address"));
            Label prerequisite = (Label) loader.getNamespace().get("prerequisiteNotice");
            assertTrue(prerequisite.isVisible());
            assertTrue(prerequisite.getText().contains("Liam Patel"));
            ((Button) loader.getNamespace().get("next")).fire();
            assertFalse(((javafx.scene.layout.VBox) loader.getNamespace().get("coborrowersPane")).isVisible());
            controller.setPrimaryBorrower(new CustomersController.CustomerSummary("C007", "Oliver Smith", "", ""));
            assertTrue(controller.validateDetails().contains("phone number or email"));
            assertTrue(prerequisite.getText().contains("Oliver Smith"));
            ((Button) loader.getNamespace().get("next")).fire();
            assertFalse(((javafx.scene.layout.VBox) loader.getNamespace().get("coborrowersPane")).isVisible());
            TextField primarySearch = (TextField) loader.getNamespace().get("customerSearch");
            ListView<?> primaryResults = (ListView<?>) loader.getNamespace().get("customerResults");
            primarySearch.setText("C003");
            primaryResults.getSelectionModel().select(0);
            Label prerequisiteNotice = (Label) loader.getNamespace().get("prerequisiteNotice");
            assertTrue(prerequisiteNotice.isVisible());
            assertTrue(prerequisiteNotice.getText().contains("Charlotte Brown"));
            assertTrue(controller.validateDetails().contains("guarantor"));
            ((Button) loader.getNamespace().get("next")).fire();
            assertFalse(((javafx.scene.layout.VBox) loader.getNamespace().get("coborrowersPane")).isVisible());
            primarySearch.setText("C004");
            primaryResults.getSelectionModel().select(0);
            assertFalse(prerequisiteNotice.isVisible());
            assertEquals("Select a loan type.", controller.validateDetails());
            ((TextField) loader.getNamespace().get("customerSearch")).setText("c001");
            ListView<?> results = (ListView<?>) loader.getNamespace().get("customerResults");
            assertEquals(1, results.getItems().size());
            results.getSelectionModel().select(0);
            for (String id : List.of("loanType", "paymentFrequency", "rateFrequency"))
                ((ComboBox<?>) loader.getNamespace().get(id)).getSelectionModel().selectFirst();
            ((TextField) loader.getNamespace().get("principal")).setText("500000");
            ((TextField) loader.getNamespace().get("interestRate")).setText("6.25");
            ((TextField) loader.getNamespace().get("term")).setText("360");
            ((DatePicker) loader.getNamespace().get("startDate")).setValue(LocalDate.of(2026, 10, 14));
            ((DatePicker) loader.getNamespace().get("renewDate")).setValue(LocalDate.of(2031, 10, 14));
            assertNull(controller.validateDetails());
            var snapshot = controller.getDraft();
            assertEquals("C001", snapshot.primaryBorrower.customerId());
            assertEquals("500000", snapshot.details.get("principal"));
            assertEquals(LocalDate.of(2026, 10, 14), snapshot.startDate);
            snapshot.details.put("principal", "1");
            assertEquals("500000", controller.getDraft().details.get("principal"));
            ((Button) loader.getNamespace().get("next")).fire();
            var coPane = (javafx.scene.layout.VBox) loader.getNamespace().get("coborrowersPane");
            assertTrue(coPane.isVisible());
            TextField coSearch = (TextField) loader.getNamespace().get("coborrowerSearch");
            ListView<?> coResults = (ListView<?>) loader.getNamespace().get("coborrowerResults");
            coSearch.setText("C001");
            assertTrue(coResults.getItems().isEmpty(), "Primary borrower must be excluded");
            coSearch.setText("C002");
            coResults.getSelectionModel().select(0);
            coSearch.setText("C002");
            assertTrue(coResults.getItems().isEmpty(), "Existing co-borrower must be excluded");
            for (String id : List.of("C003", "C004")) {
                coSearch.setText(id);
                coResults.getSelectionModel().select(0);
            }
            assertTrue(coSearch.isDisabled());
            var rows = (javafx.scene.layout.VBox) loader.getNamespace().get("coborrowerRows");
            assertEquals(3, rows.getChildren().size());
            assertEquals(3, controller.getDraft().coborrowers.size());
            ((Button) loader.getNamespace().get("back")).fire();
            assertFalse(coPane.isVisible());
            assertEquals("500000", ((TextField) loader.getNamespace().get("principal")).getText());
            ((Button) loader.getNamespace().get("next")).fire();
            assertEquals(3, rows.getChildren().size());
            var row = (javafx.scene.layout.HBox) rows.getChildren().get(0);
            ((Button) row.getChildren().get(2)).fire();
            assertFalse(coSearch.isDisabled());
            assertEquals(2, rows.getChildren().size());
            Button next = (Button) loader.getNamespace().get("next");
            Button back = (Button) loader.getNamespace().get("back");
            next.fire();
            assertTrue(((javafx.scene.layout.VBox) loader.getNamespace().get("reviewPane")).isVisible());
            assertFalse(coPane.isVisible());
            assertEquals("Finish", next.getText());
            TableView<?> repaymentTable = (TableView<?>) loader.getNamespace().get("repaymentTable");
            assertEquals(1560, repaymentTable.getItems().size());
            var firstRepayment = (com.lavs.model.Repayment) repaymentTable.getItems().get(0);
            assertEquals(LocalDate.of(2026, 10, 21), firstRepayment.getDueDate());
            assertEquals("Amelia Adams | Customer ID: C001", ((Label) loader.getNamespace().get("reviewBorrower")).getText());
            assertEquals("$500,000.00", ((Label) loader.getNamespace().get("reviewPrincipal")).getText());
            assertEquals("6.25%", ((Label) loader.getNamespace().get("reviewInterest")).getText());
            assertEquals("360 months", ((Label) loader.getNamespace().get("reviewTerm")).getText());
            assertEquals("14/10/2026", ((Label) loader.getNamespace().get("reviewStartDate")).getText());
            assertEquals("Co-borrowers (2/3)", ((Label) loader.getNamespace().get("reviewCoborrowerCount")).getText());
            ((Button) loader.getNamespace().get("editLoanDetails")).fire();
            assertEquals("Update and return to Review", next.getText());
            assertFalse(back.isVisible());
            ((TextField) loader.getNamespace().get("principal")).setText("invalid");
            next.fire();
            assertTrue(((Label) loader.getNamespace().get("reviewNotice")).isVisible());
            assertFalse(((javafx.scene.layout.VBox) loader.getNamespace().get("reviewPane")).isVisible());
            ((TextField) loader.getNamespace().get("principal")).setText("550000");
            next.fire();
            assertEquals("$550,000.00", ((Label) loader.getNamespace().get("reviewPrincipal")).getText());
            assertTrue(((com.lavs.model.Repayment) repaymentTable.getItems().get(0)).getAmountDue() > firstRepayment.getAmountDue());
            assertEquals("Finish", next.getText());
            ((Button) loader.getNamespace().get("editCoborrowers")).fire();
            assertTrue(coPane.isVisible());
            assertEquals(2, rows.getChildren().size());
            next.fire();
            ((Button) loader.getNamespace().get("editBorrower")).fire();
            assertTrue(((javafx.scene.layout.VBox) loader.getNamespace().get("detailsPane")).isVisible());
            next.fire();
            assertEquals("Amelia Adams | Customer ID: C001", ((Label) loader.getNamespace().get("reviewBorrower")).getText());
            back.fire();
            assertTrue(coPane.isVisible());
            assertEquals("Next", next.getText());
            back.fire();
            ((TextField) loader.getNamespace().get("principal")).setText("600000");
            next.fire();
            next.fire();
            assertEquals("$600,000.00", ((Label) loader.getNamespace().get("reviewPrincipal")).getText());
            back.fire();
            while (!rows.getChildren().isEmpty()) {
                row = (javafx.scene.layout.HBox) rows.getChildren().get(0);
                ((Button) row.getChildren().get(2)).fire();
            }
            ((Button) loader.getNamespace().get("next")).fire();
            assertEquals("Co-borrowers (0/3)", ((Label) loader.getNamespace().get("reviewCoborrowerCount")).getText());
            next.fire();
            assertTrue(((Label) loader.getNamespace().get("message")).getText().contains("No loan has been saved"));
            ((Button) loader.getNamespace().get("editLoanDetails")).fire();
            ((ComboBox<?>) loader.getNamespace().get("paymentFrequency")).getSelectionModel().select(4);
            next.fire();
            assertFalse(((javafx.scene.layout.VBox) loader.getNamespace().get("reviewPane")).isVisible());
            assertTrue(((Label) loader.getNamespace().get("message")).getText().contains("Unable to generate repayment schedule"));
            assertTrue(repaymentTable.getItems().isEmpty());
            ((TextField) loader.getNamespace().get("principal")).setText("NaN");
            assertEquals("NaN", controller.getDraft().details.get("principal"));
            assertNotNull(controller.validateDetails());
            return null;
        });
    }

    @Test void blocksContinuingWhenLiveEligibilityChecksAreUnavailable() throws Exception {
        JavaFxTestSupport.run(() -> {
            var loader = new FXMLLoader(Main.class.getResource("fxml/addLoan.fxml"));
            javafx.scene.Parent root = loader.load();
            new javafx.scene.Scene(root, 1008, 662);
            root.applyCss();
            root.layout();
            AddLoanController controller = loader.getController();

            controller.setPrimaryBorrower(
                    new CustomersController.CustomerSummary("C001", "Amelia Adams", "", ""));

            assertTrue(controller.validateDetails().contains("Connect to the mainframe"));
            assertTrue(((Label) loader.getNamespace().get("prerequisiteNotice")).isVisible());
            ((Button) loader.getNamespace().get("next")).fire();
            assertFalse(((javafx.scene.layout.VBox) loader.getNamespace().get("coborrowersPane")).isVisible());
            return null;
        });
    }

    // Clicking a result clears the search, which used to empty the list mid-click. JavaFX
    // reports that IndexOutOfBoundsException to the FX thread's handler, so capture it there.
    @Test void clickingASearchResultDoesNotThrow() throws Exception {
        List<Throwable> uncaught = new java.util.concurrent.CopyOnWriteArrayList<>();
        JavaFxTestSupport.run(() -> {
            var fxThread = Thread.currentThread();
            var previous = fxThread.getUncaughtExceptionHandler();
            fxThread.setUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
            var stage = new javafx.stage.Stage();
            try {
                var loader = new FXMLLoader(Main.class.getResource("fxml/addLoan.fxml"));
                stage.setScene(new javafx.scene.Scene(loader.load(), 1008, 662));
                stage.show();
                AddLoanController controller = loader.getController();
                controller.configure(List.of(
                        new CustomersController.CustomerSummary("C001", "Amelia Adams", "", ""),
                        new CustomersController.CustomerSummary("C002", "Amelia Baker", "", "")), () -> {});
                controller.setPrerequisiteValidator(customerId -> {});

                ((TextField) loader.getNamespace().get("customerSearch")).setText("Amelia");
                ListView<?> primaryResults = (ListView<?>) loader.getNamespace().get("customerResults");
                pressCell(primaryResults, 1);
                assertEquals("C002", controller.getDraft().primaryBorrower.customerId());
                assertFalse(primaryResults.isVisible());

                ((TextField) loader.getNamespace().get("coborrowerSearch")).setText("Amelia");
                ListView<?> coResults = (ListView<?>) loader.getNamespace().get("coborrowerResults");
                pressCell(coResults, 0);
                assertEquals("C001", controller.getDraft().coborrowers.get(0).customerId());
                assertFalse(coResults.isVisible());
            } finally {
                stage.close();
                fxThread.setUncaughtExceptionHandler(previous);
            }
            return null;
        });
        assertTrue(uncaught.isEmpty(), () -> "FX thread threw: " + uncaught);
    }

    @Test void primaryAndCoborrowerPickersSearchAllBackendCustomersForEachQuery() throws Exception {
        FXMLLoader[] loader = new FXMLLoader[1];
        AddLoanController[] controller = new AddLoanController[1];
        JavaFxTestSupport.run(() -> {
            loader[0] = new FXMLLoader(Main.class.getResource("fxml/addLoan.fxml"));
            loader[0].load();
            controller[0] = loader[0].getController();
            var gateway = new com.lavs.data.FakeMainframeGateway();
            gateway.createCustomer(new com.lavs.model.Customer("C101", "", "Baba", "Lala", null, "NZ"));
            gateway.createCustomer(new com.lavs.model.Customer("C102", "", "a", "a", null, "NZ"));
            controller[0].configure(List.of(), () -> {});
            controller[0].setCustomerSearchService(new com.lavs.business.CustomerService(gateway), Runnable::run);
            ((TextField) loader[0].getNamespace().get("customerSearch")).setText("Baba");
            return null;
        });

        awaitCustomerSearch();
        JavaFxTestSupport.run(() -> {
            ListView<?> results = (ListView<?>) loader[0].getNamespace().get("customerResults");
            assertEquals("Baba Lala", ((CustomersController.CustomerSummary) results.getItems().get(0)).name());
            ((TextField) loader[0].getNamespace().get("customerSearch")).setText("a a");
            return null;
        });
        awaitCustomerSearch();
        JavaFxTestSupport.run(() -> {
            ListView<?> results = (ListView<?>) loader[0].getNamespace().get("customerResults");
            assertEquals(1, results.getItems().size());
            assertEquals("a a", ((CustomersController.CustomerSummary) results.getItems().get(0)).name());
            ((TextField) loader[0].getNamespace().get("customerSearch")).setText("Baba");
            return null;
        });
        awaitCustomerSearch();
        JavaFxTestSupport.run(() -> {
            ListView<?> results = (ListView<?>) loader[0].getNamespace().get("customerResults");
            assertEquals("Baba Lala", ((CustomersController.CustomerSummary) results.getItems().get(0)).name());
            controller[0].setPrimaryBorrower((CustomersController.CustomerSummary) results.getItems().get(0));
            ((TextField) loader[0].getNamespace().get("coborrowerSearch")).setText("a a");
            return null;
        });
        awaitCustomerSearch();
        JavaFxTestSupport.run(() -> {
            ListView<?> results = (ListView<?>) loader[0].getNamespace().get("coborrowerResults");
            assertEquals(1, results.getItems().size());
            assertEquals("a a", ((CustomersController.CustomerSummary) results.getItems().get(0)).name());
            ((TextField) loader[0].getNamespace().get("coborrowerSearch")).setText("Baba");
            return null;
        });
        awaitCustomerSearch();
        JavaFxTestSupport.run(() -> {
            ListView<?> results = (ListView<?>) loader[0].getNamespace().get("coborrowerResults");
            assertTrue(results.getItems().isEmpty(), "The primary borrower cannot be selected as a co-borrower");
            controller[0].dispose();
            return null;
        });
    }

    private static void awaitCustomerSearch() throws InterruptedException {
        Thread.sleep(500); // Let the 300 ms type-ahead debounce and FX result update complete.
    }

    // A real mouse press, which goes through ListView's own click handling.
    private static void pressCell(ListView<?> list, int index) {
        list.getScene().getRoot().applyCss();
        list.getScene().getRoot().layout();
        var cell = list.lookupAll(".list-cell").stream()
                .map(node -> (ListCell<?>) node)
                .filter(c -> c.isVisible() && c.getIndex() == index && !c.isEmpty())
                .findFirst().orElseThrow();
        var centre = cell.localToScene(cell.getWidth() / 2, cell.getHeight() / 2);
        javafx.event.Event.fireEvent(cell, new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_PRESSED, centre.getX(), centre.getY(),
                centre.getX(), centre.getY(),
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null));
    }

    private static Address address(Long id, String customerId, boolean mailing) {
        return new Address(id, customerId, "10 Main St", "CBD", "Auckland", "1010", "NZ", mailing);
    }

    private static Phone phone(Long id, String customerId) {
        return new Phone(id, customerId, "0211234567", true, true);
    }

    private static Employer employer(Long id, String customerId) {
        return new Employer(id, customerId, "Acme Ltd", "Engineer", 90000, "Full-time");
    }

    /**
     * A failed save shows a clear message, leaves the form usable, and a retry can succeed
     * without saving twice
     */
    @Test void failedSaveReportsTheErrorAndCanBeRetried() throws Exception {
        JavaFxTestSupport.run(() -> {
            var loader = new FXMLLoader(Main.class.getResource("fxml/addLoan.fxml"));
            javafx.scene.Parent root = loader.load();
            new javafx.scene.Scene(root, 1008, 662);
            root.applyCss();
            root.layout();
            AddLoanController controller = loader.getController();
            int[] attempts = {0};
            controller.setSaveExecutor(Runnable::run);
            controller.setPrerequisiteValidator(customerId -> {});
            controller.setOnSave(draft -> {
                attempts[0]++;
                if (attempts[0] == 1) {
                    throw new com.lavs.data.MainframeException(
                            com.lavs.data.MainframeException.Type.UNAVAILABLE, 10, "down");
                }
            });
            controller.configure(List.of(), () -> {});
            controller.setPrimaryBorrower(
                    new CustomersController.CustomerSummary("C001", "Amelia Adams", "", ""));
            for (String id : List.of("loanType", "paymentFrequency", "rateFrequency"))
                ((ComboBox<?>) loader.getNamespace().get(id)).getSelectionModel().selectFirst();
            ((TextField) loader.getNamespace().get("principal")).setText("12000");
            ((TextField) loader.getNamespace().get("interestRate")).setText("6.25");
            ((TextField) loader.getNamespace().get("term")).setText("12");
            ((DatePicker) loader.getNamespace().get("startDate")).setValue(LocalDate.of(2026, 10, 14));
            ((DatePicker) loader.getNamespace().get("renewDate")).setValue(LocalDate.of(2027, 10, 14));
            Button next = (Button) loader.getNamespace().get("next");
            next.fire();
            next.fire();
            assertEquals("Finish", next.getText());

            next.fire();
            Label message = (Label) loader.getNamespace().get("message");
            assertEquals(1, attempts[0]);
            assertTrue(message.isVisible());
            assertTrue(message.getText().contains("could not be reached"));
            assertTrue(message.getText().contains("no loan has been saved"));
            assertFalse(next.isDisabled());

            next.fire();
            assertEquals(2, attempts[0]);
            next.fire();
            assertEquals(2, attempts[0], "A saved loan must not be saved again");
            return null;
        });
    }
}
