package com.lavs.ui;

import javafx.fxml.FXML;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

/** Customer summary header and navigation for the customer details page. */
public class CustomerDetailsController {
    @FXML private Label customerHeading;
    @FXML private Label customerId;
    @FXML private Label sectionPlaceholder;
    @FXML private Label fullNameValue, emailValue, phoneValue;
    @FXML private ScrollPane detailsContent;
    @FXML private ScrollPane loansContent;
    @FXML private ScrollPane notesContent;
    @FXML private ToggleGroup customerTabs;
    @FXML private Button backButton;
    @FXML private String tabStyle, selectedTabStyle, focusStyle;
    @FXML private String scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle;
    private CustomersController.CustomerSummary customer;
    private Runnable onBack;

    @FXML private void initialize() {
        ScrollPaneAppearance.install(detailsContent, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(loansContent, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(notesContent, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
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
            boolean details = "Details".equals(section);
            boolean loans = "Loans".equals(section);
            boolean notes = "Notes".equals(section);
            detailsContent.setVisible(details);
            detailsContent.setManaged(details);
            loansContent.setVisible(loans);
            loansContent.setManaged(loans);
            notesContent.setVisible(notes);
            notesContent.setManaged(notes);
            sectionPlaceholder.setVisible(!details && !loans && !notes);
            sectionPlaceholder.setManaged(!details && !loans && !notes);
            sectionPlaceholder.setText(section + " are coming soon.");
        });
    }

    public void setCustomer(CustomersController.CustomerSummary customer) {
        this.customer = customer;
        customerHeading.setText(customer.name());
        customerId.setText("Customer ID: " + customer.customerId());
        fullNameValue.setText(customer.name());
        emailValue.setText(customer.email());
        phoneValue.setText(customer.phone());
        detailsContent.setVvalue(0);
        loansContent.setVvalue(0);
        notesContent.setVvalue(0);
        customerTabs.selectToggle(customerTabs.getToggles().get(0));
    }
    public CustomersController.CustomerSummary getCustomer() { return customer; }
    public void setOnBack(Runnable onBack) { this.onBack = onBack; }
    @FXML private void back() { onBack.run(); }
    @FXML private void showStubAction() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(customerHeading.getScene().getWindow());
        alert.setTitle("Customer details preview");
        alert.setHeaderText("Sample information");
        alert.setContentText("This page currently displays stub information. Editing and additional records are coming soon.");
        alert.showAndWait();
    }
}
