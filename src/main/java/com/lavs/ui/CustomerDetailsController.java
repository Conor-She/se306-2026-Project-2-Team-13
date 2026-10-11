package com.lavs.ui;

import javafx.fxml.FXML;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

/** Customer summary header and navigation for the customer details page. */
public class CustomerDetailsController {
    @FXML private Label customerHeading;
    @FXML private Label customerId;
    @FXML private Label sectionPlaceholder;
    @FXML private ToggleGroup customerTabs;
    @FXML private Button backButton;
    @FXML private String tabStyle, selectedTabStyle, focusStyle;
    private CustomersController.CustomerSummary customer;
    private Runnable onBack;

    @FXML private void initialize() {
        for (var toggle : customerTabs.getToggles()) {
            ToggleButton tab = (ToggleButton) toggle;
            tab.styleProperty().bind(Bindings.createStringBinding(() ->
                    tabStyle + (tab.isSelected() ? selectedTabStyle : "")
                            + (tab.isFocused() ? focusStyle : ""),
                    tab.selectedProperty(), tab.focusedProperty()));
        }
        String backStyle = backButton.getStyle();
        backButton.styleProperty().bind(Bindings.createStringBinding(() ->
                backStyle + (backButton.isFocused() ? focusStyle : ""),
                backButton.focusedProperty()));
        customerTabs.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null) {
                customerTabs.selectToggle(previous);
                return;
            }
            String section = ((ToggleButton) selected).getText();
            sectionPlaceholder.setText("Details".equals(section)
                    ? "Customer details are coming soon."
                    : section + " are coming soon.");
        });
    }

    public void setCustomer(CustomersController.CustomerSummary customer) {
        this.customer = customer;
        customerHeading.setText(customer.name());
        customerId.setText("Customer ID: " + customer.customerId());
        customerTabs.selectToggle(customerTabs.getToggles().get(0));
    }
    public CustomersController.CustomerSummary getCustomer() { return customer; }
    public void setOnBack(Runnable onBack) { this.onBack = onBack; }
    @FXML private void back() { onBack.run(); }
}
