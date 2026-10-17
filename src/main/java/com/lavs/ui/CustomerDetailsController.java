package com.lavs.ui;

import javafx.fxml.FXML;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.ColumnConstraints;
import java.util.Map;
import java.util.function.Consumer;

import com.lavs.ui.CustomerRecordEditor.Kind;

import static com.lavs.ui.CustomerRecordEditor.Kind;

/** Customer summary header and navigation for the customer details page. */
public class CustomerDetailsController {
    @FXML private Label customerHeading;
    @FXML private Label customerId;
    @FXML private Label firstNameValue, lastNameValue, emailValue, phoneValue;
    @FXML private Label titleValue, birthDateValue, nationalityValue, addressValue;
    @FXML private Label employerNameValue, employmentSummaryValue;
    @FXML private GridPane loansTable, documentsTable, guarantorsTable;
    @FXML private VBox notesList, additionalContacts, additionalEmployers;
    @FXML private VBox personalCard, contactCard, employmentCard;
    @FXML private GridPane overviewCards;
    @FXML private javafx.scene.layout.HBox primaryEmployerRow;
    private CustomerDetailsEditing editing;
    private CustomerNotesEditing noteEditing;

    public void setCustomerService(com.lavs.business.CustomerService service) {
        editing.setCustomerService(service);
        noteEditing = new CustomerNotesEditing(notesList, service);
        if (customer != null) noteEditing.select(customer.customerId());
    }
    private Consumer<CustomersController.CustomerSummary> onCustomerChanged = updated -> {};
    @FXML private ScrollPane detailsContent;
    @FXML private ScrollPane loansContent;
    @FXML private ScrollPane notesContent;
    @FXML private ScrollPane documentsContent;
    @FXML private ScrollPane guarantorsContent;
    @FXML private ToggleGroup customerTabs;
    @FXML private Button backButton, viewAllEmail, viewAllPhone, viewAllAddress;
    @FXML private String tabStyle, selectedTabStyle, focusStyle;
    @FXML private String scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle;
    private CustomersController.CustomerSummary customer;
    private Runnable onBack;
    private Consumer<CustomersController.CustomerSummary> onAddLoan = customer -> {};

    @FXML private void initialize() {
        int[] currentColumns = {0};
        Runnable arrangeCards = () -> {
            // Use the outer scroll pane width, which does not change when its scrollbar appears.
            double available = Math.max(0, detailsContent.getWidth() - 24);
            int columns = available >= 720 ? 3 : available >= 480 ? 2 : 1;
            if (columns == currentColumns[0]) return;
            currentColumns[0] = columns;
            overviewCards.getColumnConstraints().clear();
            for (int column = 0; column < columns; column++) {
                ColumnConstraints constraint = new ColumnConstraints();
                constraint.setPercentWidth(100.0 / columns);
                overviewCards.getColumnConstraints().add(constraint);
            }
            VBox[] cards = {personalCard, contactCard, employmentCard};
            for (int index = 0; index < cards.length; index++) {
                GridPane.setColumnIndex(cards[index], index % columns);
                GridPane.setRowIndex(cards[index], index / columns);
            }
        };
        detailsContent.widthProperty().addListener((observable, previous, width) -> arrangeCards.run());
        arrangeCards.run();
        editing = new CustomerDetailsEditing(customerHeading, Map.ofEntries(
                Map.entry("customerHeading", customerHeading), Map.entry("firstNameValue", firstNameValue), Map.entry("lastNameValue", lastNameValue),
                Map.entry("titleValue", titleValue), Map.entry("birthDateValue", birthDateValue),
                Map.entry("nationalityValue", nationalityValue), Map.entry("emailValue", emailValue),
                Map.entry("phoneValue", phoneValue), Map.entry("addressValue", addressValue),
                Map.entry("employerNameValue", employerNameValue), Map.entry("employmentSummaryValue", employmentSummaryValue)),
                Map.of(Kind.LOAN, loansTable, Kind.DOCUMENT, documentsTable, Kind.GUARANTOR, guarantorsTable),
                notesList, additionalContacts, additionalEmployers, primaryEmployerRow, Map.of("Email", viewAllEmail, "Phone", viewAllPhone, "Address", viewAllAddress), updated -> { customer = updated; onCustomerChanged.accept(updated); });
        ScrollPaneAppearance.install(detailsContent, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(loansContent, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(notesContent, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(documentsContent, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        ScrollPaneAppearance.install(guarantorsContent, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
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
            boolean documents = "Documents".equals(section);
            boolean guarantors = "Guarantors".equals(section);
            detailsContent.setVisible(details);
            detailsContent.setManaged(details);
            loansContent.setVisible(loans);
            loansContent.setManaged(loans);
            notesContent.setVisible(notes);
            notesContent.setManaged(notes);
            if (notes && noteEditing != null) noteEditing.refresh();
            documentsContent.setVisible(documents);
            documentsContent.setManaged(documents);
            guarantorsContent.setVisible(guarantors);
            guarantorsContent.setManaged(guarantors);
        });
    }

    public void setCustomer(CustomersController.CustomerSummary customer) {
        this.customer = editing.select(customer);
        resetView();
        if (noteEditing != null) noteEditing.select(customer.customerId());
    }

    public void setCustomerDetails(com.lavs.model.CustomerDetails details) {
        this.customer = editing.select(details);
        setReadOnly(backButton.getParent());
        resetView();
        if (noteEditing != null) noteEditing.select(customer.customerId());
    }

    private void setReadOnly(javafx.scene.Node node) {
        if (node == notesList && noteEditing != null) return;
        if (node instanceof Button button && button != backButton
                && button != viewAllEmail && button != viewAllPhone && button != viewAllAddress
                && !"+  Add Loan".equals(button.getText())
                && !"+  Add Document".equals(button.getText())
                && !"+  Add Guarantor".equals(button.getText())
                && !isCustomerEditButton(button)) {
            button.setDisable(true);
            button.setTooltip(new javafx.scene.control.Tooltip("This record type is not editable here yet."));
        }
        if (node instanceof ScrollPane scroll && scroll.getContent() != null) setReadOnly(scroll.getContent());
        if (node instanceof javafx.scene.Parent parent) parent.getChildrenUnmodifiable().forEach(this::setReadOnly);
    }

    private boolean isCustomerEditButton(Button button) {
        String action = button.getAccessibleText();
        return action != null && (action.startsWith("Actions for ") || action.startsWith("Edit ") || action.startsWith("Delete ")
                || action.equals("Add contact information") || action.equals("Add employer"));
    }

    private void resetView() {
        customerId.setText("Customer ID: " + this.customer.customerId());
        detailsContent.setVvalue(0);
        loansContent.setVvalue(0);
        notesContent.setVvalue(0);
        documentsContent.setVvalue(0);
        guarantorsContent.setVvalue(0);
        customerTabs.selectToggle(customerTabs.getToggles().get(0));
    }
    public CustomersController.CustomerSummary getCustomer() { return customer; }
    public void addSessionLoan(CustomersController.CustomerSummary customer, com.lavs.business.LoanSubmissionService.SavedLoan loan) {
        editing.addSessionLoan(customer, loan);
    }
    public void setOnBack(Runnable onBack) { this.onBack = onBack; }
    public void setOnAddLoan(Consumer<CustomersController.CustomerSummary> onAddLoan) {
        this.onAddLoan = onAddLoan;
    }
    public void showLoansTab() {
        customerTabs.getToggles().stream().filter(toggle -> "Loans".equals(((ToggleButton) toggle).getText()))
                .findFirst().ifPresent(customerTabs::selectToggle);
    }
    public void setOnCustomerChanged(Consumer<CustomersController.CustomerSummary> onCustomerChanged) {
        this.onCustomerChanged = onCustomerChanged;
    }
    @FXML private void editPersonal() { editing.editDetails(Kind.PERSONAL); }
    @FXML private void editEmail() { editing.editContact("Email"); }
    @FXML private void editPhone() { editing.editContact("Phone"); }
    @FXML private void editAddress() { editing.editContact("Address"); }
    @FXML private void viewEmail() { editing.viewContacts("Email"); }
    @FXML private void viewPhone() { editing.viewContacts("Phone"); }
    @FXML private void viewAddress() { editing.viewContacts("Address"); }
    @FXML private void addContact() { editing.add(Kind.CONTACT_RECORD); }
    @FXML private void deleteEmployer() { editing.deleteEmployer(); }
    @FXML private void addEmployer() { editing.add(Kind.EMPLOYMENT); }
    @FXML private void editEmployment() { editing.editDetails(Kind.EMPLOYMENT); }
    @FXML private void addLoan() { if (customer != null) onAddLoan.accept(customer); }
    @FXML private void addNote() {
        if (noteEditing != null) noteEditing.edit(null);
        else editing.add(Kind.NOTE);
    }
    @FXML private void addDocument() { editing.add(Kind.DOCUMENT); }
    @FXML private void addGuarantor() { editing.add(Kind.GUARANTOR); }
    @FXML private void back() { onBack.run(); }
    @FXML private void showStubAction() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(customerHeading.getScene().getWindow());
        alert.setTitle("Customer details preview");
        alert.setHeaderText("Co-borrowers");
        alert.setContentText("Co-borrower records are not available in this preview.");
        alert.showAndWait();
    }
}
