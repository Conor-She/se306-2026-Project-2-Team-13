package com.lavs.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Placeholder for the future customer details form. */
public class CustomerDetailsController {
    @FXML private Label customerHeading;
    private CustomersController.CustomerSummary customer;
    private Runnable onBack;

    public void setCustomer(CustomersController.CustomerSummary customer) {
        this.customer = customer;
        customerHeading.setText(customer.name() + " (" + customer.customerId() + ")");
    }
    public CustomersController.CustomerSummary getCustomer() { return customer; }
    public void setOnBack(Runnable onBack) { this.onBack = onBack; }
    @FXML private void back() { onBack.run(); }
}
