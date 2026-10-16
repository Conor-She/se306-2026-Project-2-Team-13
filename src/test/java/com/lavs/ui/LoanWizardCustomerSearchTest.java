package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Customer;
import com.lavs.model.CustomerId;
import com.lavs.model.CustomerPreview;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanWizardCustomerSearchTest {
    private Parent root;
    private DashboardController dashboard;
    private AddLoanController wizard;

    @BeforeAll static void toolkit() throws Exception { JavaFxTestSupport.startToolkit(); }
    @AfterEach void dispose() throws Exception {
        JavaFxTestSupport.run(() -> {
            if (dashboard != null) dashboard.dispose();
            if (wizard != null) wizard.dispose();
            return null;
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void searchesAllCustomersForBothBorrowerRolesRegardlessOfEarlierSearches(boolean earlierSearch) throws Exception {
        var gateway = spy(new FakeMainframeGateway());
        gateway.createCustomer(new Customer("C001", "Ms", "Amelia", "Adams", null, "NZ"));
        gateway.createCustomer(new Customer("C002", "Mr", "Benjamin", "Baker", null, "NZ"));
        gateway.createCustomer(new Customer("C004", "Mr", "Ethan", "Chen", null, "NZ"));
        var loader = new FXMLLoader(Main.class.getResource("fxml/dashboard.fxml"));
        JavaFxTestSupport.run(() -> {
            root = loader.load();
            new Scene(root, 1100, 800);
            dashboard = loader.getController();
            dashboard.setCustomerService(new CustomerService(gateway));
            if (earlierSearch) {
                Parent customers = (Parent) loader.getNamespace().get("customersView");
                TextField search = (TextField) customers.lookup("#searchField");
                search.setText("Benjamin");
                search.fireEvent(new ActionEvent());
            }
            return null;
        });
        if (earlierSearch) await(() -> ((CustomersController) loader.getNamespace().get("customersViewController"))
                .customerPreviews().size() == 1);
        JavaFxTestSupport.run(() -> {
            ((Button) loader.getNamespace().get("loansNav")).fire();
            ((LoansController) loader.getNamespace().get("loansViewController")).openAddLoan(null, () -> {});
            root.applyCss(); root.layout();
            search("customerSearch", "Amelia");
            return null;
        });
        await(() -> results("customerResults").getItems().size() == 1);
        JavaFxTestSupport.run(() -> {
            assertEquals("C001", results("customerResults").getItems().get(0).customerId());
            results("customerResults").getSelectionModel().select(0);
            for (String id : List.of("loanType", "paymentFrequency", "rateFrequency"))
                ((ComboBox<?>) root.lookup("#" + id)).getSelectionModel().selectFirst();
            ((TextField) root.lookup("#principal")).setText("12000");
            ((TextField) root.lookup("#interestRate")).setText("6.25");
            ((TextField) root.lookup("#term")).setText("12");
            ((DatePicker) root.lookup("#startDate")).setValue(LocalDate.of(2026, 10, 16));
            ((DatePicker) root.lookup("#renewDate")).setValue(LocalDate.of(2027, 10, 16));
            ((Button) root.lookup("#next")).fire();
            assertTrue(root.lookup("#coborrowersPane").isVisible());
            search("coborrowerSearch", "Ethan");
            return null;
        });
        await(() -> results("coborrowerResults").getItems().size() == 1);
        JavaFxTestSupport.run(() -> {
            assertEquals("C004", results("coborrowerResults").getItems().get(0).customerId());
            results("coborrowerResults").getSelectionModel().select(0);
            search("coborrowerSearch", "Ethan");
            return null;
        });
        await(() -> placeholder("coborrowerResults").equals("No eligible customers found"));
        JavaFxTestSupport.run(() -> { assertTrue(results("coborrowerResults").getItems().isEmpty()); search("coborrowerSearch", "Amelia"); return null; });
        await(() -> placeholder("coborrowerResults").equals("No eligible customers found"));
        JavaFxTestSupport.run(() -> { assertTrue(results("coborrowerResults").getItems().isEmpty()); search("customerSearch", "Nobody"); return null; });
        await(() -> placeholder("customerResults").equals("No customers found"));
        JavaFxTestSupport.run(() -> { assertTrue(results("customerResults").getItems().isEmpty()); return null; });
        verify(gateway).searchCustomers("Amelia");
        verify(gateway, atLeastOnce()).searchCustomers("Ethan");
        verify(gateway, never()).searchCustomers("");
    }

    @Test void lateRepliesCannotOverwriteNewQueriesAndFailuresCanBeRetried() throws Exception {
        var service = mock(CustomerService.class);
        var entered = new CountDownLatch(1);
        var release = new CompletableFuture<Void>();
        var oldFinished = new CountDownLatch(1);
        var old = new CustomerPreview(new CustomerId("C001"), "Ms", "Old", "Result", null);
        var current = new CustomerPreview(new CustomerId("C004"), "Mr", "Current", "Result", null);
        when(service.search("old")).thenAnswer(call -> {
            entered.countDown();
            release.join();
            oldFinished.countDown();
            return List.of(old);
        });
        when(service.search("current")).thenReturn(List.of(current));
        when(service.search("retry")).thenThrow(new IllegalStateException("Offline")).thenReturn(List.of(current));
        try {
            JavaFxTestSupport.run(() -> {
                var loader = new FXMLLoader(Main.class.getResource("fxml/addLoan.fxml"));
                root = loader.load();
                wizard = loader.getController();
                wizard.configure(List.of(new CustomersController.CustomerSummary("CACHED", "Cached", "", "")), () -> {});
                wizard.setCustomerService(service);
                search("customerSearch", "old");
                return null;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            JavaFxTestSupport.run(() -> { search("customerSearch", "current"); return null; });
            await(() -> results("customerResults").getItems().size() == 1);
            release.complete(null);
            assertTrue(oldFinished.await(5, TimeUnit.SECONDS));
            JavaFxTestSupport.run(() -> {
                assertEquals("C004", results("customerResults").getItems().get(0).customerId());
                search("customerSearch", "retry");
                return null;
            });
            await(() -> placeholder("customerResults").contains("Press Enter to retry"));
            JavaFxTestSupport.run(() -> {
                assertTrue(results("customerResults").getItems().isEmpty());
                ((TextField) root.lookup("#customerSearch")).fireEvent(new ActionEvent());
                return null;
            });
            await(() -> results("customerResults").getItems().size() == 1);
            JavaFxTestSupport.run(() -> { search("customerSearch", ""); assertFalse(results("customerResults").isVisible()); return null; });
            verify(service, never()).search("");
        } finally { release.complete(null); }
    }

    private void search(String id, String query) {
        var field = (TextField) root.lookup("#" + id);
        field.setText(query);
        field.fireEvent(new ActionEvent());
    }
    @SuppressWarnings("unchecked") private ListView<CustomersController.CustomerSummary> results(String id) {
        return (ListView<CustomersController.CustomerSummary>) root.lookup("#" + id);
    }
    private String placeholder(String id) { return ((Label) results(id).getPlaceholder()).getText(); }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (JavaFxTestSupport.run(condition)) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for wizard customer search");
    }
}
