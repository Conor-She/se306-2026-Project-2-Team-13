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

/** Editable sample co-borrowers, retained per loan for this controller's lifetime. */
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
        records = sessions.computeIfAbsent(loanId, id -> new ArrayList<>(List.of(
                sample("Michael", "Johnson", "Brother", "021 987 6543", "michael.johnson@email.com"),
                sample("Sophie", "Johnson", "Sister", "027 456 7890", "sophie.j@email.com"),
                sample("Chris", "Johnson", "Parent", "021 123 4567", "chris.j@email.com"))));
        render();
    }

    void add() { edit(null); }

    private void edit(Map<String, String> existing) {
        List<Map<String, String>> target = records;
        Dialog<Map<String, String>> dialog = CustomerRecordEditor.create(
                table.getScene() == null ? null : table.getScene().getWindow(),
                CustomerRecordEditor.Kind.GUARANTOR, existing == null ? Map.of() : existing,
                existing == null, saved -> {
                    if (existing == null) target.add(saved);
                    else { existing.clear(); existing.putAll(saved); }
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
            table.add(empty, 0, 1, 6, 1);
        }
        int row = 1;
        for (Map<String, String> record : records) {
            Region divider = new Region();
            divider.getStyleClass().add("coborrowers-row");
            divider.setStyle(rowStyle);
            divider.setMouseTransparent(true);
            table.add(divider, 0, row, 6, 1);
            Label initials = cell(record.get("firstName").substring(0, 1) + record.get("lastName").substring(0, 1));
            initials.setAlignment(Pos.CENTER);
            initials.setMinSize(32, 32);
            initials.setStyle("-fx-background-color: linear-gradient(to bottom right, #e8e7ff, #ceccff); -fx-background-radius: 20; -fx-text-fill: #4947a5; -fx-font-size: 11px;");
            HBox person = new HBox(10, initials, cell(name(record)));
            person.setAlignment(Pos.CENTER_LEFT);
            person.setTranslateX(16);
            table.add(person, 0, row);
            table.add(cell(record.get("relationship")), 1, row);
            table.add(cell(record.get("phone")), 2, row);
            table.add(cell(record.get("email")), 3, row);
            Label status = cell(record.get("status"));
            status.setMaxWidth(Region.USE_PREF_SIZE);
            status.setWrapText(false);
            GridPane.setFillWidth(status, false);
            status.setStyle("-fx-background-color: " + ("Active".equals(record.get("status")) ? "#d9f3ec" : "#eeecff")
                    + "; -fx-background-radius: 12; -fx-text-fill: #329e8a; -fx-padding: 3 14; -fx-font-size: 11px;");
            table.add(status, 4, row);
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
            table.add(actions, 5, row++);
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
    private static String name(Map<String, String> record) { return record.get("firstName") + " " + record.get("lastName"); }
    private static Map<String, String> sample(String first, String last, String relationship, String phone, String email) {
        return new LinkedHashMap<>(Map.of("firstName", first, "lastName", last, "relationship", relationship,
                "phone", phone, "email", email, "status", "Active"));
    }
}
