package com.lavs.ui;

import java.time.LocalDate;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddLoanControllerTest {
    @BeforeAll static void startToolkit() throws Exception { JavaFxTestSupport.startToolkit(); }

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
            assertNotNull(controller.validateDetails());
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
            ((TextField) loader.getNamespace().get("principal")).setText("NaN");
            assertNotNull(controller.validateDetails());
            return null;
        });
    }
}
