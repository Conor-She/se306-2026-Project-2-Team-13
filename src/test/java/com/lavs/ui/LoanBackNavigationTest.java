package com.lavs.ui;

import com.lavs.business.CustomerService;
import com.lavs.business.LoanBrowsingService;
import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.Customer;
import com.lavs.model.CustomerId;
import com.lavs.model.Loan;
import java.time.LocalDate;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoanBackNavigationTest {
    // AI-assisted: Covered returning to customer loans and restoring search-origin navigation.
    @Test void backReturnsToCustomerLoansAndResetsForSearchOrigin() throws Exception {
        JavaFxTestSupport.startToolkit();
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("fxml/dashboard.fxml"));
        JavaFxTestSupport.run(() -> {
            Parent root = loader.load();
            new Scene(root, 1100, 800);
            var gateway = new FakeMainframeGateway();
            gateway.createCustomer(new Customer("C1", "Ms", "Jane", "Smith", null, "NZ"));
            gateway.createLoan(new Loan("LN-1", "C1", Loan.Status.NEW, 12000, 6,
                    Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                    1100, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12));
            DashboardController dashboard = loader.getController();
            var service = new CustomerService(gateway);
            dashboard.setCustomerService(service);
            dashboard.setLoanBrowsingService(new LoanBrowsingService(gateway));
            LoansController loans = (LoansController) loader.getNamespace().get("loansViewController");
            loans.setRequestExecutor(Runnable::run);
            ((Button) loader.getNamespace().get("customersNav")).fire();
            CustomersController customers = (CustomersController) loader.getNamespace().get("customersViewController");
            var field = CustomersController.class.getDeclaredField("detailsViewController");
            field.setAccessible(true);
            ((CustomerDetailsController) field.get(customers)).setCustomerDetails(service.getById(new CustomerId("C1")));
            customers.showCustomerLoans();
            root.applyCss();
            root.layout();
            Parent customerView = (Parent) loader.getNamespace().get("customersView");
            customerView.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> "Review loan LN-1".equals(button.getAccessibleText()))
                    .findFirst().orElseThrow().fire();
            return null;
        });
        try {
            JavaFxTestSupport.run(() -> {
                Parent loansView = (Parent) loader.getNamespace().get("loansView");
                assertTrue(loansView.isVisible());
                back(loansView).fire();
                Parent customerView = (Parent) loader.getNamespace().get("customersView");
                assertTrue(customerView.isVisible());
                assertTrue(customerView.lookup("#loansContent").isVisible());
                assertFalse(loansView.isVisible());
                assertTrue(((Button) loader.getNamespace().get("customersNav")).getStyleClass().contains("selected"));

                // A subsequent loan opened from search must use the search back action.
                ((Button) loader.getNamespace().get("loansNav")).fire();
                LoansController loans = (LoansController) loader.getNamespace().get("loansViewController");
                loans.openDetails(new LoansController.LoanSummary("LN-1", "C1", "Jane Smith", "New", "$12,000", "Table Loan"));
                back(loansView).fire();
                assertTrue(loansView.isVisible());
                assertFalse(customerView.isVisible());
                assertTrue(loansView.lookup("#listView").isVisible());
                return null;
            });
        } finally {
            JavaFxTestSupport.run(() -> { ((DashboardController) loader.getController()).dispose(); return null; });
        }
    }

    private Button back(Parent view) {
        return view.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "Back to customer loans".equals(button.getAccessibleText()))
                .findFirst().orElseThrow();
    }
}
