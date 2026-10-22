package com.lavs.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import com.lavs.business.LoanBrowsingService;
import com.lavs.business.LoanWithSchedule;
import com.lavs.model.Payment;
import com.lavs.model.Repayment;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
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
    @FXML private Label interestRateValue, paymentFrequencyValue, rateFrequencyValue;
    @FXML private Label paymentAmountValue, startDateValue, renewDateValue;
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
    @FXML private GridPane repaymentScheduleTable;
    @FXML private Label repaymentTotalValue, repaymentPrincipalValue;
    @FXML private Label repaymentInterestValue, repaymentTermValue, repaymentsCount;
    @FXML private String paymentCellStyle;
    @FXML private String paymentRowStyle;
    @FXML private GridPane coborrowersTable;
    private LoanCoborrowersEditing coborrowers;
    @FXML private String coborrowerRowStyle;
    @FXML private String coborrowerActionStyle;
    @FXML private String coborrowerIconStyle;
    @FXML private String coborrowerHoverStyle;
    @FXML private String coborrowerDeleteHoverStyle;

    private final ObservableList<Payment> payments = FXCollections.observableArrayList();
    private final AtomicLong recordsSequence = new AtomicLong();
    private LoansController.LoanSummary loan;
    private boolean recordsUnavailable;
    private LoanBrowsingService loanBrowsingService;
    private com.lavs.business.PaymentService paymentService;
    private Executor requestExecutor = ForkJoinPool.commonPool();
    private Runnable onBack;

    @FXML private void initialize() {
        coborrowers = new LoanCoborrowersEditing(coborrowersTable, coborrowerRowStyle,
                coborrowerActionStyle, coborrowerIconStyle, coborrowerHoverStyle,
                        coborrowerDeleteHoverStyle);
        showLoanDetailsTab();
        refreshPayments();
    }

    public void setLoan(LoansController.LoanSummary loan) {
        this.loan = loan;
        coborrowers.select(loan.loanId(), loan.customerId());
        loanHeading.setText("Loan ID: " + loan.loanId());
        customerHeading.setText(loan.customerName() + " | " + loan.customerId());
        loanInfoBanner.setText("This loan is currently " + loan.status().toLowerCase(Locale.ROOT)
                + ". You can view the loan details below.");
        statusValue.setText(loan.status());
        amountValue.setText(loan.amount());
        typeValue.setText(loan.type());
        boolean active = "Active".equals(loan.status());
        editLoanButton.setDisable(active);
        editLoanButton.setTooltip(new Tooltip(active ? "Active loans can't be edited."
                : "Edit loan"));
        interestRateValue.setText(loan.rate());
        paymentFrequencyValue.setText(loan.paymentFrequency());
        rateFrequencyValue.setText(loan.rateFrequency());
        paymentAmountValue.setText(loan.paymentAmount());
        startDateValue.setText(loan.startDate());
        renewDateValue.setText(loan.renewDate());

        payments.clear();
        recordsUnavailable = false;
        refreshPayments();
        showRepaymentLoading();
        loadLoanRecords(loan.loanId());
    }

    public void setLoanBrowsingService(LoanBrowsingService service) {
        loanBrowsingService = service;
    }

    /** Allows tests to run backend requests deterministically. */
    public void setRequestExecutor(Executor executor) {
        requestExecutor = executor == null ? ForkJoinPool.commonPool() : executor;
        configureCoborrowers();
    }

    private void loadLoanRecords(String loanId) {
        long request = recordsSequence.incrementAndGet();
        coborrowers.setRecords(loanId, List.of());
        if (loanBrowsingService == null) {
            showRepaymentUnavailable();
            return;
        }

        requestExecutor.execute(() -> {
            try {
                LoanBrowsingService.LoanRecords records = loanBrowsingService.recordsFor(loanId);
                javafx.application.Platform.runLater(() -> {
                    if (recordsSequence.get() != request || loan == null
                            || !loanId.equals(loan.loanId())) {
                                return;
                            }
                    payments.setAll(records.payments());
                    recordsUnavailable = false;
                    coborrowers.setRecords(loanId, records.coBorrowers());
                    refreshPayments();
                    refreshRepaymentSchedule(records.repaymentDetails());
                });
            } catch (RuntimeException failure) {
                javafx.application.Platform.runLater(() -> {
                    if (recordsSequence.get() != request || loan == null
                            || !loanId.equals(loan.loanId())) {
                                return;
                            }
                    payments.clear();
                    recordsUnavailable = true;
                    refreshPayments();
                    coborrowers.setUnavailable(loanId);
                    showRepaymentUnavailable();
                });
            }
        });
    }

    public void dispose() {
        recordsSequence.incrementAndGet();
    }

    public LoansController.LoanSummary getLoan() {
        return loan;
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    @FXML private void back() {
        if (onBack != null) {
            onBack.run();
        }
    }

    @FXML private void showLoanDetailsTab() {
        selectTab(loanDetailsTab, loanDetailsView);
    }

    @FXML private void showCoborrowersTab() {
        selectTab(coborrowersTab, coborrowersView);
    }

    @FXML private void addCoborrower() {
        if (loan != null) {
            coborrowers.add();
        }
    }

    @FXML private void showRepaymentScheduleTab() {
        selectTab(repaymentScheduleTab, repaymentScheduleView);
    }

    @FXML private void showPaymentsTab() {
        selectTab(paymentsTab, paymentsView);
    }

    @FXML private Button editLoanButton;
    private com.lavs.business.CustomerService customerService;
    private com.lavs.business.CoBorrowerService coBorrowerService;
    private java.util.function.Supplier<com.lavs.business.CustomerRegistrationService>
            registrationServiceFactory;

    public void setCustomerService(com.lavs.business.CustomerService service) {
        customerService = service;
        configureCoborrowers();
    }

    public void setCoBorrowerService(com.lavs.business.CoBorrowerService service) {
        coBorrowerService = service;
        configureCoborrowers();
    }

    public void setPaymentService(com.lavs.business.PaymentService service) {
        paymentService = service;
    }

    public void setRegistrationServiceFactory(
            java.util.function.Supplier<com.lavs.business.CustomerRegistrationService> factory) {
        registrationServiceFactory = factory;
        configureCoborrowers();
    }

    private void configureCoborrowers() {
        if (coborrowers == null) {
            return;
        }
        coborrowers.configure(customerService, coBorrowerService, registrationServiceFactory,
                requestExecutor, () -> {
                    if (loan != null) {
                        loadLoanRecords(loan.loanId());
                    }
                });
    }

    private void selectTab(Button selectedTab, VBox selectedView) {
        for (Button tab : List.of(loanDetailsTab, coborrowersTab, repaymentScheduleTab,
                paymentsTab)) {
            tab.setStyle(tab == selectedTab ? selectedTabStyle() : tabStyle());
            tab.getParent().lookup(".loan-tab-indicator").setVisible(tab == selectedTab);
        }
        for (VBox view : List.of(loanDetailsView, coborrowersView, repaymentScheduleView,
                paymentsView)) {
            boolean selected = view == selectedView;
            view.setVisible(selected);
            view.setManaged(selected);
        }
    }

    @FXML private void addPayment() {
        editPayment(null);
    }

    @FXML private void editLoan() {
        if (loan == null || customerService == null) {
            return;
        }
        var selected = loan;
        var service = customerService;
        BackgroundRequest.run(loanHeading.getScene().getWindow(), "Loading loan…", requestExecutor,
                () -> service.getLoan(selected.customerId(), selected.loanId()), existing -> {
                    if (loan != selected) {
                        return;
                    }
                    showLoanEditor(existing, selected);
                }, this::showEditError);
    }

    private void showLoanEditor(com.lavs.model.Loan existing,
            LoansController.LoanSummary selected) {
        var original = new java.util.LinkedHashMap<String, String>();
        original.put("id", existing.getLoanId());
        original.put("status", loan.status());
        original.put("type", existing.getType() == null ? "" : existing.getType().displayName());
        original.put("amount", java.math.BigDecimal.valueOf(existing.getPrincipal())
                .toPlainString());
        original.put("rate", java.math.BigDecimal.valueOf(existing.getInterestRate())
                .stripTrailingZeros().toPlainString());
        original.put("paymentAmount", Double.toString(existing.getPaymentAmount()));
        CustomerRecordEditor.create(loanHeading.getScene().getWindow(),
                CustomerRecordEditor.Kind.SAVED_LOAN,
                original, false, saved -> {
            var updated = new com.lavs.model.Loan(existing.getLoanId(), existing.getCustomerId(),
                        existing.getStatus(), Double.parseDouble(saved.get("amount")),
                        Double.parseDouble(saved.get("rate")),
                                com.lavs.model.Loan.Type.fromDisplayName(saved.get("type")),
                        existing.isFloating(), existing.getStartDate(), existing.getRenewDate(),
                        Double.parseDouble(saved.get("paymentAmount")),
                                existing.getPaymentFrequency(),
                        existing.getRateFrequency(), termFor(existing));
            var service = customerService;
            BackgroundRequest.run(loanHeading.getScene().getWindow(), "Saving loan…",
                    requestExecutor, () -> {
                service.updateLoan(updated);
                return service.getLoan(selected.customerId(), selected.loanId());
            }, refreshed -> {
                if (loan == selected) {
                    setLoan(LoansController.summaryFor(refreshed, selected.customerName()));
                }
            }, this::showEditError);
        }).show();
    }

    private void showEditError(RuntimeException failure) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                failure.getMessage() == null ? "The loan could not be saved."
                        : failure.getMessage());
        alert.setHeaderText("Could not edit loan");
        if (loanHeading.getScene() != null) {
            alert.initOwner(loanHeading.getScene().getWindow());
        }
        alert.showAndWait();
    }

    // The mainframe doesn't store the term, but the renew date is the start date plus the term,
    // so it can be recovered from those two dates.
    private static Integer termFor(com.lavs.model.Loan loan) {
        if (loan.getTermMonths() != null) {
            return loan.getTermMonths();
        }
        if (loan.getStartDate() == null || loan.getRenewDate() == null) {
            return null;
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(loan.getStartDate(),
                loan.getRenewDate());
        return months > 0 ? (int) months : null;
    }

    private void editPayment(Payment existingPayment) {
        if (loan == null) {
            return;
        }

        ButtonType saveType = new ButtonType(existingPayment == null ? "Add payment"
                : "Save changes",
                ButtonType.OK.getButtonData());
        Dialog<Payment> dialog = new Dialog<>();
        DialogView view = new DialogView(dialog, "paymentEditor.fxml");
        dialog.setTitle(existingPayment == null ? "Add payment" : "Edit payment");
        dialog.setHeaderText(existingPayment == null ? "Add payment" : "Edit payment");
        if (loanHeading.getScene() != null) {
            dialog.initOwner(loanHeading.getScene().getWindow());
        }
        var pane = dialog.getDialogPane();
        pane.setId("paymentEditor");
        pane.setPrefWidth(560);
        pane.getButtonTypes().setAll(ButtonType.CANCEL, saveType);
        dialog.setResizable(false);

        DatePicker paymentDate = view.get("paymentDate");
        paymentDate.setValue(existingPayment == null ? LocalDate.now()
                : existingPayment.getPaymentDate());
        TextField amount = view.get("paymentAmount");
        amount.setText(existingPayment == null ? ""
                : Double.toString(existingPayment.getAmountPaid()));
        Label context = view.get("paymentContext");
        context.setText((existingPayment == null
                ? "Record a payment for " : "Update the payment for ") + loan.loanId());

        Button saveButton = (Button) pane.lookupButton(saveType);
        Runnable validate = () -> saveButton.setDisable(
                paymentDate.getValue() == null || !isPositiveAmount(amount.getText()));
        paymentDate.valueProperty().addListener((observable, oldValue, newValue) -> validate.run());
        amount.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        validate.run();

        dialog.setResultConverter(button -> {
            if (button != saveType) {
                return null;
            }
            double amountPaid = parseAmount(amount.getText()).doubleValue();
            // A new payment has no ID yet: the mainframe assigns one when it is saved
            String paymentId = existingPayment == null ? null : existingPayment.getPaymentId();
            String repaymentId = existingPayment == null ? null : existingPayment.getRepaymentId();
            return new Payment(paymentId, loan.loanId(), repaymentId, paymentDate.getValue(),
                    amountPaid);
        });

        dialog.setOnShown(event -> {
            view.apply(pane);
            ButtonBar buttons = (ButtonBar) pane.lookup(".button-bar");
            if (buttons != null) {
                buttons.setButtonOrder("C+O");
            }
            amount.requestFocus();
        });

        dialog.showAndWait().ifPresent(updatedPayment -> {
            if (existingPayment == null) {
                changePayments("Could not add payment",
                        () -> paymentService.addPayment(updatedPayment));
            } else {
                changePayments("Could not edit payment",
                        () -> paymentService.updatePayment(updatedPayment));
            }
        });
    }

    // Runs a payment change on the mainframe off the screen thread, then reloads the loan's
    // records so the table shows exactly what the mainframe now holds (including real IDs).
    private void changePayments(String errorHeader, Runnable change) {
        if (paymentService == null) {
            showPaymentError(errorHeader,
                    new IllegalStateException("Payments are not available right now."));
            return;
        }
        String loanId = loan.loanId();
        requestExecutor.execute(() -> {
            try {
                change.run();
                javafx.application.Platform.runLater(() -> {
                    if (loan != null && loanId.equals(loan.loanId())) {
                        loadLoanRecords(loanId);
                    }
                });
            } catch (RuntimeException failure) {
                javafx.application.Platform.runLater(() -> showPaymentError(errorHeader, failure));
            }
        });
    }

    private void showPaymentError(String header, RuntimeException failure) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                failure.getMessage() == null ? "The mainframe could not save the payment."
                        : failure.getMessage());
        alert.setHeaderText(header);
        wrapAlertText(alert);
        if (loanHeading.getScene() != null) {
            alert.initOwner(loanHeading.getScene().getWindow());
        }
        alert.showAndWait();
    }

    // A long mainframe message is cut off with "..." by default; showing it in a wrapping label
    // lets the dialog grow tall enough to read all of it.
    private static void wrapAlertText(Alert alert) {
        Label message = new Label(alert.getContentText());
        message.setWrapText(true);
        message.setPrefWidth(420);
        message.setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setContent(message);
    }

    private void deletePayment(Payment payment) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the payment of " + formatAmount(payment.getAmountPaid()) + "?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setTitle("Delete payment");
        confirmation.setHeaderText("Delete payment");
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
            changePayments("Could not delete payment",
                    () -> paymentService.deletePayment(payment.getLoanId(),
                            payment.getPaymentId()));
        });
    }

    private void refreshPayments() {
        if (paymentRows == null) {
            return;
        }
        paymentRows.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null
                && GridPane.getRowIndex(node) > 0);
        List<Payment> sorted = payments.stream()
                .sorted(Comparator.comparing(Payment::getPaymentDate).reversed()).toList();
        int row = 1;
        for (Payment payment : sorted) {
            Region background = new Region();
            background.setStyle(paymentRowStyle);
            paymentRows.add(background, 0, row, 4, 1);
            String[] values = { payment.getPaymentDate().format(PAYMENT_DATE_FORMAT),
                    formatAmount(payment.getAmountPaid()), payment.getPaymentId() };
            javafx.scene.control.MenuItem edit = new javafx.scene.control.MenuItem("Edit payment");
            edit.setOnAction(event -> editPayment(payment));
            javafx.scene.control.MenuItem delete =
                    new javafx.scene.control.MenuItem("Delete payment");
            delete.setOnAction(event -> deletePayment(payment));
            javafx.scene.control.ContextMenu actions = new javafx.scene.control.ContextMenu(edit,
                    delete);
            for (int column = 0; column < values.length; column++) {
                Label cell = new Label(values[column]);
                cell.setStyle(paymentCellStyle);
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.setTooltip(new Tooltip("Right-click to edit or delete this payment"));
                cell.setOnContextMenuRequested(event -> actions.show(cell, event.getScreenX(),
                        event.getScreenY()));
                paymentRows.add(cell, column, row);
            }
            Button editButton = iconButton("edit", "Edit payment " + payment.getPaymentId());
            editButton.setOnAction(event -> editPayment(payment));
            Button deleteButton = iconButton("delete", "Delete payment " + payment.getPaymentId());
            deleteButton.setOnAction(event -> deletePayment(payment));
            HBox buttons = new HBox(4, editButton, deleteButton);
            buttons.setAlignment(Pos.CENTER);
            buttons.setMinWidth(Region.USE_PREF_SIZE);
            paymentRows.add(buttons, 3, row);
            row++;
        }
        if (sorted.isEmpty()) {
            Label empty = new Label(recordsUnavailable
                    ? "Payments could not be loaded."
                    : "No payments recorded for this loan.");
            empty.setStyle(paymentCellStyle);
            paymentRows.add(empty, 0, 1, 4, 1);
        }
        paymentsCount.setText(sorted.isEmpty() ? "Showing 0 of 0"
                : "Showing 1-" + sorted.size() + " of " + sorted.size());
    }

    private void showRepaymentLoading() {
        clearRepaymentRows();
        repaymentTotalValue.setText("Loading...");
        repaymentPrincipalValue.setText("Loading...");
        repaymentInterestValue.setText("Loading...");
        repaymentTermValue.setText("Loading...");
        repaymentsCount.setText("Loading...");
    }

    private void showRepaymentUnavailable() {
        clearRepaymentRows();
        repaymentTotalValue.setText("Unavailable");
        repaymentPrincipalValue.setText("Unavailable");
        repaymentInterestValue.setText("Unavailable");
        repaymentTermValue.setText("Unavailable");
        repaymentsCount.setText("Showing 0 of 0");
        addRepaymentMessage("Repayment schedule could not be loaded.");
    }

    private void refreshRepaymentSchedule(LoanWithSchedule details) {
        clearRepaymentRows();
        repaymentTotalValue.setText(formatAmount(details.getSummary().getTotalRepayable()));
        repaymentPrincipalValue.setText(formatAmount(details.getLoan().getPrincipal()));
        repaymentInterestValue.setText(formatAmount(details.getSummary().getTotalInterest()));
        repaymentTermValue.setText(formatTerm(termFor(details.getLoan())));

        List<Repayment> schedule = details.getSchedule();
        for (int index = 0; index < schedule.size(); index++) {
            Repayment repayment = schedule.get(index);
            int row = index + 1;
            Region divider = new Region();
            divider.getStyleClass().add("repayment-row-divider");
            divider.setMouseTransparent(true);
            divider.setStyle("-fx-border-color: #edf0fc transparent transparent transparent; "
                    + "-fx-border-width: 1 0 0 0;");
            repaymentScheduleTable.add(divider, 0, row, 6, 1);
            String[] values = {Integer.toString(row),
                    PAYMENT_DATE_FORMAT.format(repayment.getDueDate()),
                    formatAmount(repayment.getAmountDue()),
                            formatAmount(repayment.getPrincipalComponent()),
                    formatAmount(repayment.getInterestComponent()),
                            formatAmount(repayment.getRemainingBalance())};
            for (int column = 0; column < values.length; column++) {
                Label cell = new Label(values[column]);
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.setStyle("-fx-padding: 12 16; -fx-font-size: 11; -fx-text-fill: #55558d;");
                repaymentScheduleTable.add(cell, column, row);
            }
        }
        if (schedule.isEmpty()) {
            addRepaymentMessage("No repayment schedule recorded for this loan.");
        }
        repaymentsCount.setText(schedule.isEmpty() ? "Showing 0 of 0"
                : "Showing 1-" + schedule.size() + " of " + schedule.size());
    }

    private void clearRepaymentRows() {
        if (repaymentScheduleTable == null) {
            return;
        }
        repaymentScheduleTable.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null
                && GridPane.getRowIndex(node) > 0);
    }

    private void addRepaymentMessage(String message) {
        Label empty = new Label(message);
        empty.setStyle("-fx-padding: 18 16; -fx-font-size: 11; -fx-text-fill: #55558d;");
        repaymentScheduleTable.add(empty, 0, 1, 6, 1);
    }

    private String formatTerm(Integer termMonths) {
        if (termMonths == null) {
            return "Not recorded";
        }
        int years = termMonths / 12;
        int months = termMonths % 12;
        if (years == 0) {
            return months + (months == 1 ? " month" : " months");
        }
        if (months == 0) {
            return years + (years == 1 ? " year" : " years");
        }
        return years + (years == 1 ? " year " : " years ")
                + months + (months == 1 ? " month" : " months");
    }

    private Button iconButton(String iconType, String accessibleText) {
        SVGPath icon = new SVGPath();
        if ("edit".equals(iconType)) {
            icon.setContent("M 3 14 L 13 4 L 17 8 L 7 18 L 2 19 Z "
                    + "M 11 6 L 15 10 M 14 3 L 16 1 L 20 5 L 18 7");
        } else {
            icon.setContent("M 3 5 H 19 M 7 5 V 2 H 15 V 5 M 5 5 L 6 20 H 16 L 17 5 "
                    + "M 9 9 V 16 M 13 9 V 16");
        }
        icon.setFill(Color.TRANSPARENT);
        icon.setStyle(coborrowerIconStyle + ("delete".equals(iconType) ? " -fx-stroke: #c34f64;"
                : ""));

        Button button = new Button();
        button.setGraphic(icon);
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(accessibleText));
        button.setStyle(coborrowerActionStyle);
        return button;
    }

    private boolean isPositiveAmount(String value) {
        try {
            return com.lavs.business.PaymentService.isValidAmount(parseAmount(value));
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
                + " -fx-padding: 4 17 11 17; -fx-font-size: 14px; -fx-text-fill: #55558d;"
                + " -fx-cursor: hand;";
    }

    private String selectedTabStyle() {
        return tabStyle() + " -fx-text-fill: #20205e; -fx-font-weight: bold;";
    }
}
