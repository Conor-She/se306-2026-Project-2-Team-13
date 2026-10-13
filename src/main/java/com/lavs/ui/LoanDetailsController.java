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
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

    @FXML private Label loanHeading;
    @FXML private Label customerHeading;
    @FXML private Label loanIdValue;
    @FXML private Label customerIdValue;
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
    @FXML private VBox paymentRows;
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
        loanIdValue.setText(loan.loanId());
        customerIdValue.setText(loan.customerId());
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
        List<Node> rows = payments.stream()
                .sorted(Comparator.comparing(Payment::getPaymentDate))
                .map(this::createPaymentRow)
                .map(Node.class::cast)
                .toList();
        if (rows.isEmpty()) {
            Label emptyMessage = new Label("No payments recorded for this loan.");
            emptyMessage.setStyle("-fx-font-size: 17px; -fx-text-fill: #666666; -fx-padding: 14 4;");
            paymentRows.getChildren().setAll(emptyMessage);
        } else {
            paymentRows.getChildren().setAll(rows);
        }
    }

    private HBox createPaymentRow(Payment payment) {
        Label date = new Label(payment.getPaymentDate().format(PAYMENT_DATE_FORMAT));
        date.setStyle("-fx-font-size: 18px;");
        date.setPrefWidth(220);

        Label amount = new Label(formatAmount(payment.getAmountPaid()));
        amount.setStyle("-fx-font-size: 18px;");
        amount.setPrefWidth(190);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editButton = iconButton("edit", "Edit payment dated " + date.getText());
        editButton.setOnAction(event -> editPayment(payment));
        Button deleteButton = iconButton("delete", "Delete payment dated " + date.getText());
        deleteButton.setOnAction(event -> deletePayment(payment));

        HBox row = new HBox(14, date, amount, spacer, editButton, deleteButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 18, 10, 18));
        row.setMinHeight(58);
        row.setStyle("-fx-background-color: white; -fx-border-color: #2f2f2f;"
                + " -fx-border-width: 1; -fx-border-radius: 20; -fx-background-radius: 20;");
        return row;
    }

    private Button iconButton(String iconType, String accessibleText) {
        SVGPath icon = new SVGPath();
        if ("edit".equals(iconType)) {
            icon.setContent("M3 17.25 L3 21 L6.75 21 L17.81 9.94 L14.06 6.19 Z "
                    + "M20.71 7.04 C21.1 6.65 21.1 6.02 20.71 5.63 L18.37 3.29 "
                    + "C17.98 2.9 17.35 2.9 16.96 3.29 L15.13 5.12 L18.88 8.87 Z");
        } else {
            icon.setContent("M4 6 L20 6 M9 6 L9 4 L15 4 L15 6 M6 6 L7 21 L17 21 L18 6 "
                    + "M10 10 L10 18 M14 10 L14 18");
        }
        icon.setFill(Color.TRANSPARENT);
        icon.setStroke(Color.BLACK);
        icon.setStrokeWidth(1.4);
        icon.setScaleX(1.25);
        icon.setScaleY(1.25);

        Button button = new Button();
        button.setGraphic(icon);
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(accessibleText));
        button.setStyle("-fx-background-color: transparent; -fx-padding: 4 6; -fx-cursor: hand;");
        return button;
    }

    private List<Payment> samplePayments(String loanId) {
        return List.of(
                new Payment(loanId + "-PAY-1", loanId, null,
                        LocalDate.of(2026, 9, 10), 500.00),
                new Payment(loanId + "-PAY-2", loanId, null,
                        LocalDate.of(2026, 10, 12), 500.00),
                new Payment(loanId + "-PAY-3", loanId, null,
                        LocalDate.of(2026, 11, 9), 500.00));
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
        formatter.setMinimumFractionDigits(0);
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
