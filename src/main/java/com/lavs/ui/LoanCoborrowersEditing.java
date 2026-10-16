package com.lavs.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

/** Displays backend co-borrowers and supports temporary edits in the current view. */
final class LoanCoborrowersEditing {
    private final GridPane table;
    private final Map<String, List<Map<String, String>>> sessions = new HashMap<>();
    private List<Map<String, String>> records = List.of();

    private final String rowStyle, actionStyle, iconStyle, hoverStyle, deleteHoverStyle;

    LoanCoborrowersEditing(GridPane table, String rowStyle, String actionStyle,
            String iconStyle, String hoverStyle, String deleteHoverStyle) {
        this.table = table;
        this.rowStyle = rowStyle;
        this.actionStyle = actionStyle;
        this.iconStyle = iconStyle;
        this.hoverStyle = hoverStyle;
        this.deleteHoverStyle = deleteHoverStyle;
    }

    void select(String loanId) {
        records = sessions.computeIfAbsent(loanId, id -> new ArrayList<>());
        render();
    }

    void setRecords(String loanId, List<com.lavs.business.LoanBrowsingService.CoBorrowerDetails> coBorrowers) {
        List<Map<String, String>> loaded = new ArrayList<>();
        for (var coBorrower : coBorrowers) {
            Map<String, String> record = new LinkedHashMap<>();
            record.put("coBorrowerId", value(coBorrower.coBorrowerId()));
            record.put("customerId", value(coBorrower.customerId()));
            record.put("firstName", value(coBorrower.firstName()));
            record.put("lastName", value(coBorrower.lastName()));
            record.put("phone", value(coBorrower.phone()));
            record.put("email", value(coBorrower.email()));
            loaded.add(record);
        }
        sessions.put(loanId, loaded);
        records = loaded;
        render();
    }

    void setUnavailable(String loanId) {
        sessions.put(loanId, new ArrayList<>());
        records = sessions.get(loanId);
        render();
        table.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> Integer.valueOf(1).equals(GridPane.getRowIndex(label)))
                .findFirst().ifPresent(label -> label.setText("Co-borrowers could not be loaded."));
    }

    void add() { edit(null); }

    private void edit(Map<String, String> existing) {
        List<Map<String, String>> target = records;
        Dialog<Map<String, String>> dialog = CustomerRecordEditor.create(
                table.getScene() == null ? null : table.getScene().getWindow(),
                CustomerRecordEditor.Kind.GUARANTOR, existing == null ? Map.of() : existing,
                existing == null, saved -> {
                    if (existing == null) target.add(saved);
                    else {
                        String customerId = existing.get("customerId");
                        String coBorrowerId = existing.get("coBorrowerId");
                        existing.clear();
                        existing.putAll(saved);
                        if (customerId != null) existing.put("customerId", customerId);
                        if (coBorrowerId != null) existing.put("coBorrowerId", coBorrowerId);
                    }
                    if (records == target) render();
                });
        String title = existing == null ? "Add co-borrower" : "Edit co-borrower";
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.getDialogPane().setId("loanCoborrowerEditor");
        dialog.show();
    }

    private void delete(Map<String, String> record) {
        List<Map<String, String>> target = records;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + name(record) + " from this loan?", ButtonType.CANCEL, ButtonType.OK);
        if (table.getScene() != null) confirmation.initOwner(table.getScene().getWindow());
        confirmation.setTitle("Delete co-borrower");
        confirmation.setHeaderText("Delete co-borrower");
        confirmation.setOnHidden(event -> {
            if (confirmation.getResult() == ButtonType.OK) {
                target.remove(record);
                if (records == target) render();
            }
        });
        confirmation.show();
    }

    private void render() {
        table.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) > 0);
        if (records.isEmpty()) {
            Label empty = cell("No co-borrowers recorded for this loan.");
            table.add(empty, 0, 1, 5, 1);
        }
        int row = 1;
        for (Map<String, String> record : records) {
            Region divider = new Region();
            divider.getStyleClass().add("coborrowers-row");
            divider.setStyle(rowStyle);
            divider.setMouseTransparent(true);
            table.add(divider, 0, row, 5, 1);
            Label initials = cell(initials(record));
            initials.setAlignment(Pos.CENTER);
            initials.setMinSize(32, 32);
            initials.setStyle("-fx-background-color: linear-gradient(to bottom right, #e8e7ff, #ceccff); -fx-background-radius: 20; -fx-text-fill: #4947a5; -fx-font-size: 11px;");
            HBox person = new HBox(10, initials, cell(name(record)));
            person.setAlignment(Pos.CENTER_LEFT);
            person.setTranslateX(16);
            table.add(person, 0, row);
            table.add(cell(record.getOrDefault("customerId", "")), 1, row);
            table.add(cell(record.getOrDefault("phone", "")), 2, row);
            table.add(cell(record.getOrDefault("email", "")), 3, row);
            Button edit = iconButton("Edit co-borrower", name(record),
                    "M 3 14 L 13 4 L 17 8 L 7 18 L 2 19 Z M 11 6 L 15 10 M 14 3 L 16 1 L 20 5 L 18 7");
            edit.setOnAction(event -> edit(record));
            Button delete = iconButton("Delete co-borrower", name(record),
                    "M 3 5 H 19 M 7 5 V 2 H 15 V 5 M 5 5 L 6 20 H 16 L 17 5 M 9 9 V 16 M 13 9 V 16");
            delete.getStyleClass().add("coborrower-delete");
            delete.getGraphic().setStyle(iconStyle + " -fx-stroke: #c34f64;");
            delete.setOnAction(event -> delete(record));
            HBox actions = new HBox(4, edit, delete);
            actions.getStyleClass().add("coborrower-action-buttons");
            actions.setAlignment(Pos.CENTER_LEFT);
            actions.setMinWidth(Region.USE_PREF_SIZE);
            table.add(actions, 4, row++);
        }
        table.getChildren().stream().filter(Label.class::isInstance)
                .forEach(label -> label.setTranslateX(16));
    }

    private Button iconButton(String action, String name, String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("coborrower-action-icon");
        icon.setStyle(iconStyle);
        Button button = new Button(null, icon);
        button.setAccessibleText(action + " for " + name);
        button.setTooltip(new Tooltip(action));
        button.getStyleClass().add("coborrower-actions");
        button.setStyle(actionStyle);
        Runnable refreshStyle = () -> button.setStyle(actionStyle
                + (button.isHover() || button.isFocused()
                ? (action.startsWith("Delete") ? deleteHoverStyle : hoverStyle) : ""));
        button.hoverProperty().addListener((observable, oldValue, newValue) -> refreshStyle.run());
        button.focusedProperty().addListener((observable, oldValue, newValue) -> refreshStyle.run());
        return button;
    }

    private static Label cell(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #55558d; -fx-padding: 10 0;");
        return label;
    }
    private static String name(Map<String, String> record) {
        return (value(record.get("firstName")) + " " + value(record.get("lastName"))).strip();
    }
    private static String initials(Map<String, String> record) {
        String first = value(record.get("firstName"));
        String last = value(record.get("lastName"));
        return (first.isEmpty() ? "" : first.substring(0, 1)) + (last.isEmpty() ? "" : last.substring(0, 1));
    }
    private static String value(String value) { return value == null ? "" : value; }
}
