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
            controller.configure(List.of(new CustomersController.CustomerSummary("C001", "Amelia Adams", "", "")), () -> {});
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
            ((TextField) loader.getNamespace().get("principal")).setText("NaN");
            assertNotNull(controller.validateDetails());
            return null;
        });
    }
}
