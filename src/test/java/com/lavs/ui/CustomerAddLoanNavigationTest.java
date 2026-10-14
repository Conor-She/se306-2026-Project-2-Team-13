package com.lavs.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerAddLoanNavigationTest {
    @Test void customerLoanPrefillsBorrowerAndCancelReturnsToLoansTab() throws Exception {
        JavaFxTestSupport.startToolkit();
        JavaFxTestSupport.run(() -> {
            boolean[] discard = {false};
            AddLoanController[] editor = {null};
            var loader = new FXMLLoader(Main.class.getResource("fxml/dashboard.fxml"));
            loader.setControllerFactory(type -> {
                if (type == LoansController.class) return new LoansController() {
                    @Override protected AddLoanController createAddLoanController() {
                        editor[0] = new AddLoanController() {
                            @Override protected boolean confirmDiscardDetails() { return discard[0]; }
                        };
                        return editor[0];
                    }
                };
                try { return type.getDeclaredConstructor().newInstance(); }
                catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
            });
            Parent root = loader.load();
            new Scene(root, 1100, 800);
            ((Button) loader.getNamespace().get("customersNav")).fire();
            Parent customersView = (Parent) loader.getNamespace().get("customersView");
            // Select the same details controller that the customer list normally uses.
            // The dashboard include exposes its controller through the parent namespace.
            CustomersController customers = (CustomersController) loader.getNamespace().get("customersViewController");
            var details = getDetailsController(customers);
            var ethan = new CustomersController.CustomerSummary("C004", "Ethan Chen", "", "");
            details.setCustomer(ethan);
            customers.showCustomerLoans();
            root.applyCss();
            root.layout();
            Button add = customersView.lookupAll(".button").stream().filter(Button.class::isInstance)
                    .map(Button.class::cast).filter(button -> "+  Add Loan".equals(button.getText())).findFirst().orElseThrow();
            add.fire();
            root.applyCss();
            root.layout();
            Parent loansView = (Parent) loader.getNamespace().get("loansView");
            assertTrue(loansView.isVisible());
            assertFalse(customersView.isVisible());
            assertEquals("C004", editor[0].getDraft().primaryBorrower.customerId());
            assertEquals("Ethan Chen | Customer ID: C004", ((Label) loansView.lookup("#borrowerName")).getText());
            Button cancel = (Button) loansView.lookup("#cancel");
            cancel.fire();
            assertTrue(loansView.isVisible(), "Keep editing must stay in the wizard");
            discard[0] = true;
            cancel.fire();
            assertTrue(customersView.isVisible());
            assertFalse(loansView.isVisible());
            assertSame(ethan, details.getCustomer());
            assertTrue(customersView.lookup("#loansContent").isVisible());
            add.fire();
            root.applyCss();
            root.layout();
            for (String id : java.util.List.of("loanType", "paymentFrequency", "rateFrequency"))
                ((javafx.scene.control.ComboBox<?>) loansView.lookup("#" + id)).getSelectionModel().selectFirst();
            ((javafx.scene.control.TextField) loansView.lookup("#principal")).setText("12000");
            ((javafx.scene.control.TextField) loansView.lookup("#interestRate")).setText("6.25");
            ((javafx.scene.control.TextField) loansView.lookup("#term")).setText("12");
            ((javafx.scene.control.DatePicker) loansView.lookup("#startDate")).setValue(java.time.LocalDate.of(2026, 10, 14));
            ((javafx.scene.control.DatePicker) loansView.lookup("#renewDate")).setValue(java.time.LocalDate.of(2027, 10, 14));
            Button next = (Button) loansView.lookup("#next");
            next.fire();
            next.fire();
            assertEquals("Finish", next.getText());
            next.fire();
            next.fire(); // A second click must not create another loan.
            assertTrue(customersView.isVisible());
            assertTrue(customersView.lookup("#loansContent").isVisible());
            assertTrue(customersView.lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).anyMatch(label -> "SESSION-0001".equals(label.getText())));
            var table = (javafx.scene.control.TableView<?>) loansView.lookup("#loanTable");
            assertEquals(11, table.getItems().size());
            ((LoansController) loader.getNamespace().get("loansViewController")).onShown();
            assertEquals(11, table.getItems().size());
            details.setCustomer(new CustomersController.CustomerSummary("C001", "Amelia Adams", "", ""));
            assertFalse(customersView.lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).anyMatch(label -> "SESSION-0001".equals(label.getText())));
            details.setCustomer(ethan);
            details.showLoansTab();
            assertTrue(customersView.lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).anyMatch(label -> "SESSION-0001".equals(label.getText())));
            return null;
        });
    }

    private CustomerDetailsController getDetailsController(CustomersController controller) throws Exception {
        var field = CustomersController.class.getDeclaredField("detailsViewController");
        field.setAccessible(true);
        return (CustomerDetailsController) field.get(controller);
    }
}
