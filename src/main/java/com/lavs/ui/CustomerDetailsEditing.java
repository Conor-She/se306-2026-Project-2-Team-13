package com.lavs.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import static com.lavs.ui.CustomerRecordEditor.Kind;

/** Keeps a separate editable preview for each customer during this controller's lifetime. */
final class CustomerDetailsEditing {
    private static final String CELL = "-fx-font-size: 12px; -fx-text-fill: #55558d;";
    private static final String ACTION = "-fx-background-color: transparent; -fx-text-fill: #747ba6; "
            + "-fx-font-size: 19px; -fx-padding: 4 10; -fx-min-width: 36; -fx-min-height: 36; -fx-cursor: hand;";
    private static final String BADGE = "-fx-background-color: #d9f3ec; -fx-background-radius: 12; "
            + "-fx-text-fill: #329e8a; -fx-font-size: 11px; -fx-padding: 2 16;";
    private final Node owner;
    private final Map<String, Label> labels;
    private final Map<Kind, GridPane> tables;
    private final VBox notes, additionalContacts, additionalEmployers;
    private final Map<String, Button> contactLinks;
    private final HBox primaryEmployerRow;
    private final Consumer<CustomersController.CustomerSummary> onChange;
    private final Map<String, State> sessions = new HashMap<>();
    private State state;

    private static final class State {
        CustomersController.CustomerSummary summary;
        final Map<Kind, Map<String, String>> details = new EnumMap<>(Kind.class);
        final Map<Kind, List<Map<String, String>>> records = new EnumMap<>(Kind.class);
        State(CustomersController.CustomerSummary customer) {
            summary = customer;
            details.put(Kind.PERSONAL, values("title", "Mrs", "firstName", firstName(customer.name()), "lastName", lastName(customer.name()), "birthDate", "14-03-1990", "nationality", "New Zealand"));
            details.put(Kind.CONTACT, values("email", customer.email(), "phone", customer.phone(), "address", "12 Koru Street, Whero, Auckland, 0945"));
            details.put(Kind.EMPLOYMENT, values("employer", "Smith Consulting", "role", "Consultant", "employmentType", "Full-time", "income", "85000"));
            records.put(Kind.CONTACT_RECORD, new ArrayList<>());
            records.put(Kind.EMPLOYMENT, new ArrayList<>());
            records.put(Kind.LOAN, new ArrayList<>(List.of(
                    values("id", "LN00123", "type", "Home Loan", "amount", "450000", "rate", "4.95", "status", "Active"),
                    values("id", "LN00456", "type", "Personal Loan", "amount", "15000", "rate", "12.50", "status", "Active"),
                    values("id", "LN00789", "type", "Car Loan", "amount", "30000", "rate", "6.75", "status", "Active"))));
            records.put(Kind.NOTE, new ArrayList<>(List.of(
                    values("title", "Loan discussion", "text", "Customer is looking to consolidate existing loans.", "date", "2025-09-12", "author", "Jack Slater"),
                    values("title", "Follow up", "text", "Called customer to confirm documents received.", "date", "2025-08-03", "author", "Sarah Lee"),
                    values("title", "General note", "text", "Customer is happy with the current service.", "date", "2025-06-21", "author", "Jack Slater"))));
            records.put(Kind.DOCUMENT, new ArrayList<>(List.of(
                    values("fileName", "ID - Passport.pdf", "type", "Identity", "date", "2025-09-12"),
                    values("fileName", "Proof of Address.pdf", "type", "Address", "date", "2025-08-03"),
                    values("fileName", "Payslip.pdf", "type", "Income", "date", "2025-06-21"),
                    values("fileName", "Bank Statement.pdf", "type", "Bank Statement", "date", "2025-05-10"))));
            records.put(Kind.GUARANTOR, new ArrayList<>(List.of(values("firstName", "Michael", "lastName", "Johnson", "relationship", "Brother", "phone", "021 987 6543", "email", "michael.johnson@email.com", "status", "Active"))));
        }
    }

    CustomerDetailsEditing(Node owner, Map<String, Label> labels, Map<Kind, GridPane> tables,
            VBox notes, VBox additionalContacts, VBox additionalEmployers, HBox primaryEmployerRow, Map<String, Button> contactLinks, Consumer<CustomersController.CustomerSummary> onChange) {
        this.owner = owner;
        this.labels = labels;
        this.tables = tables;
        this.notes = notes;
        this.additionalContacts = additionalContacts;
        this.additionalEmployers = additionalEmployers;
        this.contactLinks = contactLinks;
        this.primaryEmployerRow = primaryEmployerRow;
        this.onChange = onChange;
    }

    CustomersController.CustomerSummary select(CustomersController.CustomerSummary customer) {
        state = sessions.computeIfAbsent(customer.customerId(), id -> new State(customer));
        render();
        return state.summary;
    }

    void editDetails(Kind kind) {
        State target = state;
        CustomerRecordEditor.create(window(), kind, target.details.get(kind), false, saved -> {
            target.details.put(kind, saved);
            Map<String, String> personal = target.details.get(Kind.PERSONAL);
            Map<String, String> contact = target.details.get(Kind.CONTACT);
            target.summary = new CustomersController.CustomerSummary(target.summary.customerId(), fullName(personal), contact.get("phone"), contact.get("email"));
            if (state == target) { render(); onChange.accept(target.summary); }
        }).show();
    }

    private static String contactKey(String type) {
        return switch (type) { case "Email" -> "email"; case "Phone" -> "phone"; case "Address" -> "address"; default -> throw new IllegalArgumentException(type); };
    }

    void editContact(String type) { editContact(type, null, () -> {}); }

    private void editContact(String type, Map<String, String> record, Runnable afterSave) {
        State target = state;
        Map<String, String> original = record == null
                ? values("contactType", type, "value", target.details.get(Kind.CONTACT).get(contactKey(type)),
                        "primary", "true", "texts", target.details.get(Kind.CONTACT).getOrDefault("texts", "false"))
                : new LinkedHashMap<>(record);
        if (record != null) original.put("primary", "false");
        Dialog<Map<String, String>> dialog = CustomerRecordEditor.create(window(), Kind.CONTACT_RECORD, original, false, saved -> {
            if (record == null) {
                target.details.get(Kind.CONTACT).put(contactKey(type), saved.get("value"));
                if (type.equals("Phone")) target.details.get(Kind.CONTACT).put("texts", saved.get("texts"));
            } else {
                record.clear(); record.putAll(saved);
                if (Boolean.parseBoolean(saved.get("primary"))) promoteContact(target, type, record);
            }
            updateSummary(target);
            if (state == target) { render(); afterSave.run(); }
        });
        VBox form = (VBox) ((ScrollPane) dialog.getDialogPane().getContent()).getContent();
        Button delete = deleteButton("Delete contact", () -> confirmDelete("contact", () -> {
            deleteContact(target, type, record);
            dialog.close();
            if (state == target) { render(); afterSave.run(); }
        }));
        form.getChildren().add(delete);
        dialog.setHeaderText("Edit " + type.toLowerCase(Locale.ROOT));
        dialog.show();
    }

    private void updateSummary(State target) {
        Map<String, String> contact = target.details.get(Kind.CONTACT);
        target.summary = new CustomersController.CustomerSummary(target.summary.customerId(),
                fullName(target.details.get(Kind.PERSONAL)), contact.get("phone"), contact.get("email"));
        if (state == target) onChange.accept(target.summary);
    }

    private void promoteContact(State target, String type, Map<String, String> record) {
        Map<String, String> primary = target.details.get(Kind.CONTACT);
        String previous = primary.getOrDefault(contactKey(type), "");
        String previousTexts = primary.getOrDefault("texts", "false");
        String nextValue = record.get("value");
        String nextTexts = record.getOrDefault("texts", "false");
        primary.put(contactKey(type), nextValue);
        if (type.equals("Phone")) primary.put("texts", nextTexts);
        if (previous.isBlank()) target.records.get(Kind.CONTACT_RECORD).removeIf(entry -> entry == record);
        else {
            record.clear();
            record.putAll(values("contactType", type, "value", previous, "primary", "false", "texts", type.equals("Phone") ? previousTexts : "false"));
        }
    }

    private void deleteContact(State target, String type, Map<String, String> record) {
        if (record != null) target.records.get(Kind.CONTACT_RECORD).removeIf(entry -> entry == record);
        else {
            target.details.get(Kind.CONTACT).put(contactKey(type), "");
            if (type.equals("Phone")) target.details.get(Kind.CONTACT).put("texts", "false");
            target.records.get(Kind.CONTACT_RECORD).stream().filter(entry -> type.equals(entry.get("contactType")))
                    .findFirst().ifPresent(entry -> promoteContact(target, type, entry));
        }
        updateSummary(target);
    }

    private Button deleteButton(String accessibleText, Runnable action) {
        Button button = new Button(accessibleText.equals("Delete contact") ? "Delete contact" : "");
        button.setGraphic(icon("M 1 4 H 15 M 5 4 V 1 H 11 V 4 M 3 4 L 4 18 H 12 L 13 4 M 6 7 V 15 M 10 7 V 15", "#b42318"));
        button.setMinSize(28, 28);
        button.setTooltip(new Tooltip(accessibleText));
        button.setAccessibleText(accessibleText);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: #b42318; -fx-padding: 5; -fx-cursor: hand;");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void confirmDelete(String kind, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete this " + kind + "?", ButtonType.CANCEL, ButtonType.OK);
        if (window() != null) alert.initOwner(window());
        alert.setHeaderText("Delete " + kind);
        alert.setOnHidden(event -> { if (alert.getResult() == ButtonType.OK) action.run(); });
        alert.show();
    }

    void deleteEmployer() {
        State target = state;
        confirmDelete("employer", () -> {
            if (target.records.get(Kind.EMPLOYMENT).isEmpty()) target.details.remove(Kind.EMPLOYMENT);
            else target.details.put(Kind.EMPLOYMENT, target.records.get(Kind.EMPLOYMENT).remove(0));
            if (state == target) render();
        });
    }

    void viewContacts(String type) {
        Dialog<Void> dialog = new Dialog<>();
        if (window() != null) dialog.initOwner(window());
        dialog.setTitle("All " + type.toLowerCase(Locale.ROOT) + " entries");
        dialog.setHeaderText("Customer " + type.toLowerCase(Locale.ROOT) + " entries");
        dialog.getDialogPane().setId("customerContactList");
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-background-color: white;");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox list = new VBox(8);
        list.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true); scroll.setPrefViewportHeight(280);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");
        dialog.getDialogPane().setContent(scroll);
        populateContacts(type, list);
        dialog.show();
    }

    private void populateContacts(String type, VBox list) {
        list.getChildren().clear();
        String primary = state.details.get(Kind.CONTACT).getOrDefault(contactKey(type), "");
        if (!primary.isBlank()) list.getChildren().add(contactRow(type, primary, null, list));
        state.records.get(Kind.CONTACT_RECORD).stream().filter(record -> type.equals(record.get("contactType")))
                .forEach(record -> list.getChildren().add(contactRow(type, record.get("value"), record, list)));
        if (list.getChildren().isEmpty()) list.getChildren().add(label("No entries added."));
    }

    private HBox contactRow(String type, String value, Map<String, String> record, VBox list) {
        VBox text = new VBox(5, label(value));
        if (record == null) text.getChildren().add(label(type.equals("Address") ? "Mailing address" : "Primary"));
        text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
        Button edit = new Button(); edit.setStyle(ACTION);
        edit.setAccessibleText("Edit " + value);
        edit.setGraphic(icon("M 1 15 L 4 8 L 12 0 L 16 4 L 8 12 Z M 4 8 L 8 12 M 10 2 L 14 6", "#55558d"));
        edit.setOnAction(event -> editContact(type, record, () -> populateContacts(type, list)));
        VBox actions = new VBox(5, edit);
        if (record != null) {
            State target = state;
            Button primary = new Button("Make primary");
            primary.setAccessibleText("Make primary " + value);
            primary.setOnAction(event -> {
                promoteContact(target, type, record); updateSummary(target);
                if (state == target) { render(); populateContacts(type, list); }
            });
            actions.getChildren().add(primary);
        }
        if (type.equals("Phone")) {
            CheckBox texts = new CheckBox("Send TXT messages");
            texts.setAccessibleText("Send TXT messages to " + value);
            texts.setSelected(Boolean.parseBoolean(record == null ? state.details.get(Kind.CONTACT).getOrDefault("texts", "false") : record.getOrDefault("texts", "false")));
            texts.setOnAction(event -> {
                if (record == null) state.details.get(Kind.CONTACT).put("texts", Boolean.toString(texts.isSelected()));
                else record.put("texts", Boolean.toString(texts.isSelected()));
            });
            text.getChildren().add(texts);
        }
        State target = state;
        actions.getChildren().add(deleteButton("Delete " + value, () -> confirmDelete("contact", () -> {
            deleteContact(target, type, record);
            if (state == target) { render(); populateContacts(type, list); }
        })));
        HBox row = new HBox(12, text, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 12 14; -fx-background-color: #f5f6ff; -fx-background-radius: 8; -fx-border-color: #dce2ff; -fx-border-radius: 8;");
        return row;
    }

    void add(Kind kind) { editRecord(kind, null); }

    private void editRecord(Kind kind, Map<String, String> record) {
        State target = state;
        Map<String, String> draft = record == null ? new LinkedHashMap<>() : record;
        if (record == null && (kind == Kind.NOTE || kind == Kind.DOCUMENT)) draft.put("date", LocalDate.now().toString());
        CustomerRecordEditor.create(window(), kind, draft, record == null, saved -> {
            if (record == null && kind == Kind.EMPLOYMENT && !target.details.containsKey(kind)) target.details.put(kind, saved);
            else if (record == null) target.records.get(kind).add(saved);
            else { record.clear(); record.putAll(saved); }
            if (kind == Kind.CONTACT_RECORD) {
                String type = saved.get("contactType");
                if (Boolean.parseBoolean(saved.get("primary")) || target.details.get(Kind.CONTACT).getOrDefault(contactKey(type), "").isBlank())
                    promoteContact(target, type, saved);
                updateSummary(target);
            }
            if (state == target) render();
        }, saved -> kind == Kind.LOAN && target.records.get(kind).stream()
                .anyMatch(other -> other != record && other.get("id").equalsIgnoreCase(saved.get("id")))
                ? Map.of("id", "This loan ID already exists for the customer.") : Map.of()).show();
    }

    private javafx.stage.Window window() {
        return owner.getScene() == null ? null : owner.getScene().getWindow();
    }

    private void render() {
        Map<String, String> personal = state.details.get(Kind.PERSONAL);
        Map<String, String> contact = state.details.get(Kind.CONTACT);
        Map<String, String> employment = state.details.get(Kind.EMPLOYMENT);
        primaryEmployerRow.setVisible(employment != null);
        primaryEmployerRow.setManaged(employment != null);
        set("customerHeading", fullName(personal));
        set("firstNameValue", personal.get("firstName"));
        set("lastNameValue", personal.get("lastName"));
        set("titleValue", personal.get("title"));
        set("birthDateValue", CustomerRecordEditor.DISPLAY_DATE.format(LocalDate.parse(personal.get("birthDate"), CustomerRecordEditor.BIRTH_DATE)));
        set("nationalityValue", personal.get("nationality"));
        set("emailValue", contactDisplay(contact.get("email"), "No email address"));
        set("phoneValue", contactDisplay(contact.get("phone"), "No phone number"));
        set("addressValue", contactDisplay(contact.get("address"), "No mailing address"));
        set("employerNameValue", employment == null ? "" : employment.get("employer"));
        set("employmentSummaryValue", employment == null ? "" : employment.get("role") + "  \u2022  " + employment.get("employmentType") + "  \u2022  " + money(employment.get("income")) + " / year");
        tables.forEach(this::renderTable);
        renderNotes();
        additionalContacts.getChildren().clear();
        additionalContacts.setVisible(false);
        additionalContacts.setManaged(false);
        contactLinks.forEach((type, button) -> {
            long count = state.records.get(Kind.CONTACT_RECORD).stream().filter(record -> type.equals(record.get("contactType"))).count();
            if (!state.details.get(Kind.CONTACT).getOrDefault(contactKey(type), "").isBlank()) count++;
            button.setVisible(count > 1);
            button.setManaged(count > 1);
        });
        renderAdditional(Kind.EMPLOYMENT, additionalEmployers);
    }

    private static String contactDisplay(String value, String emptyMessage) {
        return value == null || value.isBlank() ? emptyMessage : value;
    }

    private static String firstName(String name) {
        String[] parts = name.strip().split("\\s+", 2);
        return parts[0];
    }
    private static String lastName(String name) {
        String[] parts = name.strip().split("\\s+", 2);
        return parts.length == 2 ? parts[1] : "";
    }
    private static String fullName(Map<String, String> record) {
        return (record.getOrDefault("firstName", "") + " " + record.getOrDefault("lastName", "")).strip();
    }
    private void renderAdditional(Kind kind, VBox container) {
        container.getChildren().clear();
        for (Map<String, String> record : state.records.get(kind)) {
            String title = kind == Kind.CONTACT_RECORD ? record.get("contactType") : record.get("employer");
            String description = kind == Kind.CONTACT_RECORD ? record.get("value")
                    : record.get("role") + "  \u2022  " + record.get("employmentType") + "  \u2022  " + money(record.get("income")) + " / year";
            Label heading = label(title);
            Label summary = label(description);
            if (kind == Kind.EMPLOYMENT) {
                heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #20205e;");
                summary.setStyle("-fx-font-size: 12px; -fx-text-fill: #626499;");
            }
            VBox text = new VBox(6, heading, summary);
            text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
            Button edit = new Button();
            edit.setAccessibleText("Edit " + title);
            edit.setStyle(((Button) primaryEmployerRow.getChildren().get(1)).getStyle());
            edit.setGraphic(icon("M 1 15 L 4 8 L 12 0 L 16 4 L 8 12 Z M 4 8 L 8 12 M 10 2 L 14 6", "#55558d"));
            ((SVGPath) edit.getGraphic()).setStrokeWidth(1.3);
            edit.setOnAction(event -> editRecord(kind, record));
            State target = state;
            Button delete = deleteButton("Delete " + title, () -> confirmDelete("employer", () -> {
                target.records.get(kind).removeIf(entry -> entry == record);
                if (state == target) render();
            }));
            delete.setStyle(((Button) primaryEmployerRow.getChildren().get(2)).getStyle());
            ((SVGPath) delete.getGraphic()).setStrokeWidth(1.5);
            HBox row = new HBox(primaryEmployerRow.getSpacing(), text, edit, delete);
            row.setAlignment(primaryEmployerRow.getAlignment());
            row.setStyle(primaryEmployerRow.getStyle());
            container.getChildren().add(row);
        }
        if (kind == Kind.EMPLOYMENT && state.details.get(kind) == null && container.getChildren().isEmpty())
            container.getChildren().add(label("No employers added."));
        container.setManaged(!container.getChildren().isEmpty());
        container.setVisible(!container.getChildren().isEmpty());
    }

    private void set(String id, String value) { labels.get(id).setText(value); }
    private static Map<String, String> values(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }
    private static String money(String value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);
        format.setMaximumFractionDigits(2);
        return "$" + format.format(new java.math.BigDecimal(value));
    }
    private static Label label(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setStyle(CELL);
        return label;
    }
    private static SVGPath icon(String path, String colour) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setFill(javafx.scene.paint.Color.TRANSPARENT);
        icon.setStroke(javafx.scene.paint.Color.web(colour));
        icon.setStrokeWidth(1.7);
        return icon;
    }
    private static Pane avatar() {
        Pane avatar = new Pane();
        avatar.setMinSize(36, 36); avatar.setPrefSize(36, 36); avatar.setMaxSize(36, 36);
        Circle background = new Circle(18, 18, 18, javafx.scene.paint.Color.web("#efedff"));
        Circle head = new Circle(18, 13, 6, javafx.scene.paint.Color.web("#393a7b"));
        SVGPath body = new SVGPath();
        body.setContent("M 8 28 C 8 16 28 16 28 28 Q 18 33 8 28 Z");
        body.setFill(javafx.scene.paint.Color.web("#393a7b"));
        avatar.getChildren().addAll(background, head, body);
        return avatar;
    }
    private void renderTable(Kind kind, GridPane table) {
        table.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) > 0);
        if (table.getRowConstraints().size() > 1) table.getRowConstraints().subList(1, table.getRowConstraints().size()).clear();
        List<Map<String, String>> records = state.records.get(kind);
        int columns = table.getColumnConstraints().size();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int row = index + 1;
            table.getRowConstraints().add(new RowConstraints(kind == Kind.GUARANTOR ? 60 : 52));
            Region divider = new Region();
            divider.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            divider.setStyle("-fx-border-color: transparent transparent #edf0ff transparent; -fx-border-width: 0 0 1 0;");
            GridPane.setMargin(divider, new Insets(0, 16, 0, 16));
            table.add(divider, 0, row, columns, 1);
            if (kind == Kind.LOAN) {
                Label id = label(record.get("id")); id.setPadding(new Insets(0, 0, 0, 16));
                table.add(id, 0, row); table.add(label(record.get("type")), 1, row);
                table.add(label(money(record.get("amount"))), 2, row);
                table.add(label(record.get("rate") + "%"), 3, row);
                table.add(status(record.get("status")), 4, row);
                table.add(actions(kind, record, "Actions for loan " + record.get("id")), 5, row);
            } else if (kind == Kind.DOCUMENT) {
                String colour = switch (record.get("type")) { case "Identity" -> "#7666db"; case "Address" -> "#42bda6"; default -> "#ed4d94"; };
                HBox name = new HBox(16, icon("M 1 0 H 10 L 15 5 V 20 H 1 Z M 10 0 V 5 H 15 M 5 9 H 11 M 5 12 H 11 M 5 15 H 9", colour), label(record.get("fileName")));
                name.setAlignment(Pos.CENTER_LEFT); name.setPadding(new Insets(0, 24, 0, 24)); name.setMinWidth(0);
                table.add(name, 0, row); table.add(label(record.get("type")), 1, row);
                table.add(label(CustomerRecordEditor.DISPLAY_DATE.format(LocalDate.parse(record.get("date")))), 2, row);
                Button download = new Button();
                download.setStyle(ACTION); download.setAccessibleText("Download " + record.get("fileName"));
                download.setGraphic(icon("M 7 0 V 10 M 3 6 L 7 10 L 11 6 M 1 10 V 13 H 13 V 10", "#747ba6"));
                download.setOnAction(event -> download(record));
                HBox actionBox = new HBox(8, download, actions(kind, record, "Actions for " + record.get("fileName")));
                actionBox.setAlignment(Pos.CENTER); table.add(actionBox, 3, row);
            } else {
                HBox name = new HBox(12, avatar(), label(fullName(record)));
                name.setAlignment(Pos.CENTER_LEFT); name.setPadding(new Insets(0, 20, 0, 20)); name.setMinWidth(0);
                table.add(name, 0, row); table.add(label(record.get("relationship")), 1, row);
                table.add(label(record.get("phone")), 2, row); table.add(label(record.get("email")), 3, row);
                table.add(status(record.get("status")), 4, row);
                table.add(actions(kind, record, "Actions for guarantor " + fullName(record)), 5, row);
            }
        }
        if (records.isEmpty()) {
            Label empty = label("No " + (kind == Kind.LOAN ? "loans" : kind == Kind.DOCUMENT ? "documents" : "guarantors") + " added.");
            empty.setPadding(new Insets(20)); table.add(empty, 0, 1, columns, 1);
        }
    }
    private static Label status(String text) {
        Label label = label(text);
        label.setStyle(text.equals("Active") ? BADGE : BADGE.replace("#d9f3ec", "#efedff").replace("#329e8a", "#6255cc"));
        return label;
    }
    private Button actions(Kind kind, Map<String, String> record, String accessibleText) {
        Button button = new Button("\u22EE"); button.setStyle(ACTION); button.setAccessibleText(accessibleText);
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem("Edit " + kind.title);
        edit.setOnAction(action -> editRecord(kind, record));
        MenuItem remove = new MenuItem("Remove " + kind.title);
        remove.setOnAction(action -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Remove this " + kind.title + " from the customer?", ButtonType.CANCEL, ButtonType.OK);
            if (window() != null) confirmation.initOwner(window());
            confirmation.setHeaderText("Remove " + kind.title);
            confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> { state.records.get(kind).remove(record); render(); });
        });
        menu.getItems().addAll(edit, remove);
        button.setContextMenu(menu);
        button.setOnAction(event -> menu.show(button, javafx.geometry.Side.BOTTOM, 0, 0));
        return button;
    }
    private void renderNotes() {
        if (notes.getChildren().size() > 1) notes.getChildren().subList(1, notes.getChildren().size()).clear();
        for (Map<String, String> record : state.records.get(Kind.NOTE)) {
            StackPane icon = new StackPane(icon("M 1 0 H 9 L 13 4 V 16 H 1 Z M 9 0 V 4 H 13 M 4 7 H 10 M 4 10 H 10 M 4 13 H 8", "#6255cc"));
            icon.setMinSize(36, 36); icon.setPrefSize(36, 36); icon.setMaxSize(36, 36);
            icon.setStyle("-fx-background-color: #efedff; -fx-background-radius: 18;");
            Label title = label(record.get("title")); title.setStyle(CELL + "-fx-font-weight: bold;");
            VBox text = new VBox(5, title, label(record.get("text"))); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
            VBox meta = new VBox(5, label(CustomerRecordEditor.DISPLAY_DATE.format(LocalDate.parse(record.get("date")))), label("By " + record.get("author")));
            meta.setMinWidth(145); meta.setPrefWidth(145);
            HBox row = new HBox(16, icon, text, meta, actions(Kind.NOTE, record, "Actions for " + record.get("title") + " note"));
            row.setAlignment(Pos.CENTER_LEFT); row.setMinHeight(74);
            row.setStyle("-fx-background-color: white; -fx-border-color: #e2e9fc; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14 18;");
            notes.getChildren().add(row);
        }
        if (state.records.get(Kind.NOTE).isEmpty()) notes.getChildren().add(label("No notes added."));
    }
    private void download(Map<String, String> document) {
        String path = document.getOrDefault("path", "");
        if (path.isBlank()) { information("Sample document", "This sample document has no local file. Use Edit document to choose one."); return; }
        FileChooser chooser = new FileChooser(); chooser.setTitle("Save customer document"); chooser.setInitialFileName(document.get("fileName"));
        java.io.File destination = chooser.showSaveDialog(window());
        if (destination != null) {
            try { Files.copy(Path.of(path), destination.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException error) { information("Unable to save document", "The local file could not be copied. Check that it still exists and try again."); }
        }
    }
    private void information(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        if (window() != null) alert.initOwner(window());
        alert.setHeaderText(title); alert.show();
    }
}
