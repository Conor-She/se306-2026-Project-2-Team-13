package com.lavs.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.lavs.model.Payment;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** Displays loan information and the loan's related tabs and payments. */
public class LoanDetailsController {
    private static final DateTimeFormatter PAYMENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ENGLISH);

    @FXML private Label loanHeading;
    @FXML private Label customerHeading;
    @FXML private Label loanInfoBanner;
    @FXML private Label statusValue;
    @FXML private Label amountValue;
    @FXML private Label typeValue;
    @FXML private Button loanDetailsTab;
    @FXML private Button coborrowersTab;
    @FXML private Button repaymentScheduleTab;
    @FXML private Button paymentsTab;
    @FXML private VBox loanDetailsView;
    @FXML private VBox coborrowersView;
    @FXML private VBox repaymentScheduleView;
    @FXML private VBox paymentsView;
    @FXML private GridPane paymentRows;
    @FXML private Label paymentsCount;
    @FXML private String paymentCellStyle;
    @FXML private String paymentBadgeStyle;
    @FXML private String paymentRowStyle;
    @FXML private GridPane coborrowersTable;
    private LoanCoborrowersEditing coborrowers;
    @FXML private String coborrowerRowStyle;
    @FXML private String coborrowerActionStyle;
    @FXML private String coborrowerIconStyle;
    @FXML private String coborrowerHoverStyle;
    @FXML private String coborrowerDeleteHoverStyle;

    private final ObservableList<Payment> payments = FXCollections.observableArrayList();
    private LoansController.LoanSummary loan;
    private String loadedLoanId;
    private Runnable onBack;

    @FXML private void initialize() {
        coborrowers = new LoanCoborrowersEditing(coborrowersTable, coborrowerRowStyle,
                coborrowerActionStyle, coborrowerIconStyle, coborrowerHoverStyle, coborrowerDeleteHoverStyle);
        showLoanDetailsTab();
        refreshPayments();
    }

    public void setLoan(LoansController.LoanSummary loan) {
        this.loan = loan;
        coborrowers.select(loan.loanId());
        loanHeading.setText("Loan ID: " + loan.loanId());
        customerHeading.setText(loan.customerName() + " | " + loan.customerId());
        loanInfoBanner.setText("This loan is currently " + loan.status().toLowerCase(Locale.ROOT)
                + ". You can view the loan details below.");
        statusValue.setText(loan.status());
        amountValue.setText(loan.amount());
        typeValue.setText(loan.type());

        if (!loan.loanId().equals(loadedLoanId)) {
            loadedLoanId = loan.loanId();
            payments.setAll(samplePayments(loan.loanId()));
            refreshPayments();
        }
    }

    public LoansController.LoanSummary getLoan() {
        return loan;
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    @FXML private void back() {
        if (onBack != null) onBack.run();
    }

    @FXML private void showLoanDetailsTab() {
        selectTab(loanDetailsTab, loanDetailsView);
    }

    @FXML private void showCoborrowersTab() {
        selectTab(coborrowersTab, coborrowersView);
    }

    @FXML private void addCoborrower() {
        if (loan != null) coborrowers.add();
    }

    @FXML private void showRepaymentScheduleTab() {
        selectTab(repaymentScheduleTab, repaymentScheduleView);
    }

    @FXML private void showPaymentsTab() {
        selectTab(paymentsTab, paymentsView);
    }

    private void selectTab(Button selectedTab, VBox selectedView) {
        for (Button tab : List.of(loanDetailsTab, coborrowersTab, repaymentScheduleTab, paymentsTab)) {
            tab.setStyle(tab == selectedTab ? selectedTabStyle() : tabStyle());
            tab.getParent().lookup(".loan-tab-indicator").setVisible(tab == selectedTab);
        }
        for (VBox view : List.of(loanDetailsView, coborrowersView, repaymentScheduleView, paymentsView)) {
            boolean selected = view == selectedView;
            view.setVisible(selected);
            view.setManaged(selected);
        }
    }

    @FXML private void addPayment() {
        editPayment(null);
    }

    private void editPayment(Payment existingPayment) {
        if (loan == null) return;

        ButtonType saveType = new ButtonType(existingPayment == null ? "Add payment" : "Save changes",
                ButtonType.OK.getButtonData());
        Dialog<Payment> dialog = new Dialog<>();
        dialog.setTitle(existingPayment == null ? "Add payment" : "Edit payment");
        dialog.setHeaderText(existingPayment == null
                ? "Record a payment for " + loan.loanId()
                : "Update this payment for " + loan.loanId());
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveType);

        DatePicker paymentDate = new DatePicker(existingPayment == null
                ? LocalDate.now() : existingPayment.getPaymentDate());
        TextField amount = new TextField(existingPayment == null
                ? "" : Double.toString(existingPayment.getAmountPaid()));
        amount.setPromptText("e.g. 500");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new Insets(12, 0, 0, 0));
        form.addRow(0, new Label("Date"), paymentDate);
        form.addRow(1, new Label("Amount paid"), amount);
        dialog.getDialogPane().setContent(form);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        Runnable validate = () -> saveButton.setDisable(
                paymentDate.getValue() == null || !isPositiveAmount(amount.getText()));
        paymentDate.valueProperty().addListener((observable, oldValue, newValue) -> validate.run());
        amount.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        validate.run();

        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            double amountPaid = parseAmount(amount.getText()).doubleValue();
            String paymentId = existingPayment == null
                    ? "PAY-" + UUID.randomUUID() : existingPayment.getPaymentId();
            String repaymentId = existingPayment == null ? null : existingPayment.getRepaymentId();
            return new Payment(paymentId, loan.loanId(), repaymentId, paymentDate.getValue(), amountPaid);
        });

        dialog.showAndWait().ifPresent(updatedPayment -> {
            if (existingPayment == null) {
                payments.add(updatedPayment);
            } else {
                int index = payments.indexOf(existingPayment);
                if (index >= 0) payments.set(index, updatedPayment);
            }
            refreshPayments();
        });
    }

    private void deletePayment(Payment payment) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the payment of " + formatAmount(payment.getAmountPaid()) + "?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setTitle("Delete payment");
        confirmation.setHeaderText("Delete payment");
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
            payments.remove(payment);
            refreshPayments();
        });
    }

    private void refreshPayments() {
        if (paymentRows == null) return;
        paymentRows.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null
                && GridPane.getRowIndex(node) > 0);
        List<Payment> sorted = payments.stream()
                .sorted(Comparator.comparing(Payment::getPaymentDate).reversed()).toList();
        int row = 1;
        for (Payment payment : sorted) {
            Region background = new Region();
            background.setStyle(paymentRowStyle);
            paymentRows.add(background, 0, row, 6, 1);
            String[] values = { payment.getPaymentDate().format(PAYMENT_DATE_FORMAT),
                    formatAmount(payment.getAmountPaid()), "Principal & Interest", "Paid", payment.getPaymentId() };
            javafx.scene.control.MenuItem edit = new javafx.scene.control.MenuItem("Edit payment");
            edit.setOnAction(event -> editPayment(payment));
            javafx.scene.control.MenuItem delete = new javafx.scene.control.MenuItem("Delete payment");
            delete.setOnAction(event -> deletePayment(payment));
            javafx.scene.control.ContextMenu actions = new javafx.scene.control.ContextMenu(edit, delete);
            for (int column = 0; column < values.length; column++) {
                Label cell = new Label(values[column]);
                cell.setStyle(column == 3 ? paymentBadgeStyle : paymentCellStyle);
                cell.setMaxWidth(column == 3 ? Region.USE_PREF_SIZE : Double.MAX_VALUE);
                if (column == 3) cell.setTranslateX(18);
                cell.setTooltip(new Tooltip("Right-click to edit or delete this payment"));
                cell.setOnContextMenuRequested(event -> actions.show(cell, event.getScreenX(), event.getScreenY()));
                paymentRows.add(cell, column, row);
            }
            Button editButton = iconButton("edit", "Edit payment " + payment.getPaymentId());
            editButton.setOnAction(event -> editPayment(payment));
            Button deleteButton = iconButton("delete", "Delete payment " + payment.getPaymentId());
            deleteButton.setOnAction(event -> deletePayment(payment));
            HBox buttons = new HBox(4, editButton, deleteButton);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            buttons.setMinWidth(Region.USE_PREF_SIZE);
            buttons.setPadding(new Insets(0, 12, 0, 0));
            paymentRows.add(buttons, 5, row);
            row++;
        }
        if (sorted.isEmpty()) {
            Label empty = new Label("No payments recorded for this loan.");
            empty.setStyle(paymentCellStyle);
            paymentRows.add(empty, 0, 1, 6, 1);
        }
        paymentsCount.setText(sorted.isEmpty() ? "Showing 0 of 0"
                : "Showing 1-" + sorted.size() + " of " + sorted.size());
    }

    private Button iconButton(String iconType, String accessibleText) {
        SVGPath icon = new SVGPath();
        if ("edit".equals(iconType)) {
            icon.setContent("M 3 14 L 13 4 L 17 8 L 7 18 L 2 19 Z M 11 6 L 15 10 M 14 3 L 16 1 L 20 5 L 18 7");
        } else {
            icon.setContent("M 3 5 H 19 M 7 5 V 2 H 15 V 5 M 5 5 L 6 20 H 16 L 17 5 M 9 9 V 16 M 13 9 V 16");
        }
        icon.setFill(Color.TRANSPARENT);
        icon.setStyle(coborrowerIconStyle + ("delete".equals(iconType) ? " -fx-stroke: #c34f64;" : ""));

        Button button = new Button();
        button.setGraphic(icon);
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(accessibleText));
        button.setStyle(coborrowerActionStyle);
        return button;
    }

    private List<Payment> samplePayments(String loanId) {
        return List.of(
                new Payment("FP-001232", loanId, null,
                        LocalDate.of(2025, 8, 1), 3245.67),
                new Payment("FP-001233", loanId, null,
                        LocalDate.of(2025, 8, 15), 3245.67),
                new Payment("FP-001234", loanId, null,
                        LocalDate.of(2025, 9, 1), 3245.67));
    }

    private boolean isPositiveAmount(String value) {
        try {
            return parseAmount(value).signum() > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private BigDecimal parseAmount(String value) {
        return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    }

    private String formatAmount(double amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.US);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(amount);
    }

    private String tabStyle() {
        return "-fx-background-color: transparent; -fx-border-color: transparent;"
                + " -fx-border-width: 0 0 5 0; -fx-border-radius: 0;"
                + " -fx-padding: 4 17 11 17; -fx-font-size: 14px; -fx-text-fill: #55558d; -fx-cursor: hand;";
    }

    private String selectedTabStyle() {
        return tabStyle() + " -fx-text-fill: #20205e; -fx-font-weight: bold;";
    }
}
