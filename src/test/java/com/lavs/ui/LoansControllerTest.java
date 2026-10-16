package com.lavs.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.lavs.business.LoanBrowsingService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.CoBorrower;
import com.lavs.model.Customer;
import com.lavs.model.Loan;
import com.lavs.model.Payment;
import java.time.LocalDate;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;

/** The Loans page against an in-memory mainframe: real loans, their records, and empty and failure states. */
class LoansControllerTest {
    private FXMLLoader loader;
    private LoansController controller;
    private FakeMainframeGateway gateway;

    @BeforeAll static void startToolkit() throws Exception { JavaFxTestSupport.startToolkit(); }

    @BeforeEach void open() throws Exception {
        JavaFxTestSupport.run(() -> {
            loader = new FXMLLoader(LoansController.class.getResource("fxml/loans.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1100, 700));
            stage.show();
            gateway = new FakeMainframeGateway();
            gateway.createCustomer(new Customer("C1", "Ms", "Sarah", "Johnson", null, "NZ"));
            gateway.createCustomer(new Customer("C2", "Mr", "Alex", "Smith", null, "NZ"));
            return null;
        });
    }

    @AfterEach void close() throws Exception {
        JavaFxTestSupport.run(() -> {
            controller.dispose();
            List.copyOf(Window.getWindows()).forEach(Window::hide);
            return null;
        });
    }

    private void connect() {
        controller.setRequestExecutor(Runnable::run);
        controller.setLoanBrowsingService(new LoanBrowsingService(gateway));
    }

    private void loan(String id, String customerId, double principal, Loan.Type type) {
        gateway.createLoan(new Loan(id, customerId, Loan.Status.NEW, principal, 6.5, type, false,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 500,
                Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12));
    }

    private void search(String query) {
        TextField field = (TextField) loader.getNamespace().get("searchField");
        field.setText(query);
        field.fireEvent(new ActionEvent());
    }

    @SuppressWarnings("unchecked")
    private TableView<LoansController.LoanSummary> table() {
        return (TableView<LoansController.LoanSummary>) loader.getNamespace().get("loanTable");
    }

    private String heading() { return ((Label) loader.getNamespace().get("resultsHeading")).getText(); }
    private String placeholder() { return ((Label) table().getPlaceholder()).getText(); }

    /** Runs one UI step; results of mainframe tasks are delivered before the next step runs. */
    private static void step(Runnable action) throws Exception {
        JavaFxTestSupport.run(() -> { action.run(); return null; });
    }

    @Test void opensWithoutSampleLoansAndPromptsForASearch() throws Exception {
        step(() -> {
            assertTrue(table().getItems().isEmpty());
            assertEquals(LoansController.NOT_CONNECTED, placeholder());
            connect();
            assertTrue(table().getItems().isEmpty());
            assertEquals("Search loans", heading());
            assertEquals(LoansController.SEARCH_PROMPT, placeholder());
        });
    }

    @Test void searchingShowsTheMainframesLoans() throws Exception {
        step(() -> {
            loan("L2", "C1", 20000, null);
            loan("L1", "C1", 10000, Loan.Type.REDUCING_BALANCE);
            loan("L3", "C2", 5000, Loan.Type.TABLE_LOAN);
            connect();
            search("Johnson");
            assertEquals("Searching loans...", heading());
        });
        step(() -> {
            assertEquals("Search results (2)", heading());
            assertEquals(List.of(
                    new LoansController.LoanSummary("L1", "C1", "Sarah Johnson", "New", "$10,000.00", "Reducing Balance"),
                    new LoansController.LoanSummary("L2", "C1", "Sarah Johnson", "New", "$20,000.00", "Not recorded")),
                    table().getItems());
            search("L3");
        });
        step(() -> {
            assertEquals(1, table().getItems().size());
            assertEquals("L3", table().getItems().get(0).loanId());
            assertEquals("Alex Smith", table().getItems().get(0).customerName());
        });
    }

    @Test void customerWithNoLoansShowsAClearEmptyState() throws Exception {
        step(() -> {
            connect();
            search("Smith");
        });
        step(() -> {
            assertTrue(table().getItems().isEmpty());
            assertEquals("Search results (0)", heading());
            assertEquals("No loans found for \"Smith\"", placeholder());
        });
    }

    @Test void unavailableMainframeShowsARetryMessage() throws Exception {
        step(() -> {
            connect();
            gateway.simulateUnavailable(true);
            search("Johnson");
        });
        step(() -> {
            assertEquals("Could not load loans. Press Enter to retry.", heading());
            assertEquals("Loan search is unavailable", placeholder());
            gateway.simulateUnavailable(false);
            loan("L1", "C1", 10000, Loan.Type.TABLE_LOAN);
            search("Johnson");
        });
        step(() -> assertEquals(1, table().getItems().size()));
    }

    @Test void openingALoanShowsItsMainframePaymentsAndCoBorrowers() throws Exception {
        step(() -> {
            loan("L1", "C1", 10000, Loan.Type.TABLE_LOAN);
            gateway.addPayment(new Payment("PAY-7", "L1", null, LocalDate.of(2026, 3, 1), 812.5));
            gateway.addCoBorrower(new CoBorrower(null, "L1", "C2"));
            connect();
            search("Johnson");
        });
        step(() -> controller.openDetails(table().getItems().get(0)));
        step(() -> {
            Parent details = (Parent) loader.getNamespace().get("detailsView");
            assertTrue(details.isVisible());
            GridPane payments = (GridPane) details.lookup("#paymentRows");
            assertTrue(payments.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> "PAY-7".equals(label.getText())));
            GridPane coborrowers = (GridPane) details.lookup("#coborrowersTable");
            assertTrue(coborrowers.lookupAll(".button").stream().filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .anyMatch(b -> "Edit co-borrower for Alex Smith".equals(b.getAccessibleText())));
        });
    }

}
