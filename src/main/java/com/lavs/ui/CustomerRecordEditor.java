package com.lavs.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.ArrayList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Shared session-only editor for the records displayed on a customer page. */
final class CustomerRecordEditor {
    enum Kind {
        PERSONAL("personal information", List.of(
                field("title", "Title", "Mr", "Mrs", "Ms", "Miss", "Mx"),
                field("firstName", "First name"), field("lastName", "Last name"), field("birthDate", "Date of birth (dd-MM-yyyy)"),
                field("nationality", "Nationality"))),
        CONTACT("contact information", List.of(field("email", "Primary email"),
                field("phone", "Primary phone"), field("address", "Mailing address"))),
        CONTACT_RECORD("contact", List.of(field("contactType", "Contact type", "Email", "Phone", "Address"),
                field("value", "Contact details"))),
        EMPLOYMENT("employment", List.of(field("employer", "Company name"),
                field("role", "Job title"), field("employmentType", "Employment type", "Full-time", "Part-time", "Contract", "Casual"),
                field("income", "Annual income (NZD)"))),
        LOAN("loan", List.of(field("id", "Loan ID"),
                field("type", "Loan type", "Home Loan", "Personal Loan", "Car Loan", "Other"),
                field("amount", "Amount (NZD)"), field("rate", "Interest rate (%)"),
                field("status", "Status", "Active", "Pending", "Closed"))),
        SAVED_LOAN("loan", List.of(
                field("type", "Loan type", "TABLE_LOAN", "REDUCING_BALANCE", "INTEREST_ONLY"),
                field("amount", "Amount (NZD)"), field("rate", "Interest rate (%)"),
                field("termMonths", "Term (months)"), field("paymentAmount", "Payment amount (NZD)"))),
        SAVED_GUARANTOR("guarantor", List.of(field("customerId", "Guarantor customer ID"),
                field("relationship", "Relationship"))),
        NOTE("note", List.of(field("title", "Title"), field("text", "Note"), field("author", "Author"))),
        DOCUMENT("document", List.of(field("fileName", "Document name"),
                field("type", "Document type", "Identity", "Address", "Income", "Bank Statement", "Other"),
                field("date", "Upload date"))),
        SAVED_DOCUMENT("document", List.of(field("fileName", "Document name"),
                field("type", "Document type"),
                field("date", "Upload date"))),
        GUARANTOR("guarantor", List.of(field("firstName", "First name"), field("lastName", "Last name"), field("relationship", "Relationship"),
                field("phone", "Phone"), field("email", "Email"), field("status", "Status", "Active", "Inactive")));

        final String title;
        final List<Field> fields;
        Kind(String title, List<Field> fields) { this.title = title; this.fields = fields; }
    }

    record Field(String key, String label, List<String> options) {}
    private static Field field(String key, String label, String... options) {
        return new Field(key, label, List.of(options));
    }
    static final DateTimeFormatter BIRTH_DATE = DateTimeFormatter.ofPattern("dd-MM-uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.ENGLISH);
    private static final String INPUT_STYLE = "-fx-background-color: white; -fx-border-color: #b9b7cd; "
            + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 9;";
    private static final List<String> ADDRESS_KEYS = List.of("street", "suburb", "city", "postCode", "country");

    /** One-line form of an address, used for display. */
    static String addressText(Map<String, String> values) {
        return ADDRESS_KEYS.stream().map(key -> values.getOrDefault(key, "").strip())
                .filter(part -> !part.isBlank()).collect(java.util.stream.Collectors.joining(", "));
    }
    private CustomerRecordEditor() {}

    static Dialog<Map<String, String>> create(Window owner, Kind kind, Map<String, String> original,
            boolean adding, Consumer<Map<String, String>> onSave) {
        return create(owner, kind, original, adding, onSave, values -> Map.of());
    }

    static Dialog<Map<String, String>> create(Window owner, Kind kind, Map<String, String> original,
            boolean adding, Consumer<Map<String, String>> onSave,
            java.util.function.Function<Map<String, String>, Map<String, String>> extraValidation) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle((adding ? "Add " : "Edit ") + kind.title);
        dialog.setHeaderText((adding ? "Add " : "Edit ") + kind.title);
        DialogPane pane = dialog.getDialogPane();
        pane.setId("customerRecordEditor");
        pane.setStyle("-fx-font-family: 'Segoe UI'; -fx-background-color: white;");
        pane.setPrefWidth(500);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, save);
        VBox form = new VBox(7);
        form.setStyle("-fx-padding: 6 16 12 16;");
        Map<String, Control> inputs = new LinkedHashMap<>();
        Map<String, Label> errors = new LinkedHashMap<>();
        Map<String, String> draft = new LinkedHashMap<>(original);
        List<Node> valueNodes = new ArrayList<>();
        List<Node> addressNodes = new ArrayList<>();
        for (Field field : kind.fields) {
            Label label = requiredLabel(field.label);
            Control input;
            String value = original.getOrDefault(field.key, "");
            if (!field.options.isEmpty()) {
                ComboBox<String> choices = new ComboBox<>();
                choices.getItems().setAll(field.options);
                choices.setValue(value.isBlank() ? field.options.get(0) : value);
                choices.setMaxWidth(Double.MAX_VALUE);
                input = choices;
            } else if (field.key.equals("date")) {
                DatePicker date = new DatePicker(value.isBlank() ? null : LocalDate.parse(value));
                date.setMaxWidth(Double.MAX_VALUE);
                input = date;
            } else if (field.key.equals("text") || field.key.equals("address")) {
                TextArea text = new TextArea(value);
                text.setWrapText(true);
                text.setPrefRowCount(field.key.equals("text") ? 4 : 2);
                input = text;
            } else {
                input = new TextField(value);
            }
            input.setId("edit_" + field.key);
            if (kind == Kind.CONTACT_RECORD && !adding && field.key.equals("contactType")) {
                input.setDisable(true);
            }
            input.setAccessibleText(field.label);
            input.setStyle(INPUT_STYLE);
            label.setLabelFor(input);
            Label error = new Label();
            error.setId("error_" + field.key);
            error.setWrapText(true);
            error.setStyle("-fx-text-fill: #b42318; -fx-font-size: 12px;");
            error.setVisible(false);
            error.setManaged(false);
            inputs.put(field.key, input);
            errors.put(field.key, error);
            form.getChildren().addAll(label, input, error);
            if (field.key.equals("value")) { valueNodes.add(label); valueNodes.add(input); }
        }
        if (kind == Kind.CONTACT_RECORD) {
            String[][] addressFields = {{"street", "Street address", "true"}, {"suburb", "Suburb", "false"},
                    {"city", "City", "true"}, {"postCode", "Post code", "false"}, {"country", "Country", "true"}};
            for (String[] f : addressFields) {
                boolean required = f[2].equals("true");
                Label label = required ? requiredLabel(f[1]) : new Label(f[1]);
                if (!required) label.setStyle("-fx-text-fill: #393a7b; -fx-font-weight: bold;");
                String initial = original.getOrDefault(f[0], "");
                // An address with no separate parts yet starts with its whole text in the street box
                if (f[0].equals("street") && !original.containsKey("street"))
                    initial = original.getOrDefault("value", "");
                TextField input = new TextField(initial);
                input.setId("edit_" + f[0]);
                input.setAccessibleText(f[1]);
                input.setStyle(INPUT_STYLE);
                label.setLabelFor(input);
                Label error = new Label();
                error.setId("error_" + f[0]);
                error.setWrapText(true);
                error.setStyle("-fx-text-fill: #b42318; -fx-font-size: 12px;");
                error.setVisible(false);
                error.setManaged(false);
                inputs.put(f[0], input);
                errors.put(f[0], error);
                form.getChildren().addAll(label, input, error);
                addressNodes.add(label);
                addressNodes.add(input);
            }
        }
        CheckBox primary = new CheckBox("Make primary");
        primary.setId("edit_primary");
        primary.setSelected(Boolean.parseBoolean(original.getOrDefault("primary", "false")));
        primary.setDisable(!adding && primary.isSelected());
        CheckBox texts = new CheckBox("Send TXT messages");
        texts.setId("edit_texts");
        texts.setSelected(Boolean.parseBoolean(original.getOrDefault("texts", "false")));
        if (kind == Kind.CONTACT_RECORD) {
            ComboBox<?> type = (ComboBox<?>) inputs.get("contactType");
            Runnable updatePreferences = () -> {
                primary.setText("Primary " + String.valueOf(type.getValue()).toLowerCase(java.util.Locale.ROOT));
                boolean phone = "Phone".equals(type.getValue());
                texts.setVisible(phone); texts.setManaged(phone);
                boolean address = "Address".equals(type.getValue());
                valueNodes.forEach(n -> { n.setVisible(!address); n.setManaged(!address); });
                addressNodes.forEach(n -> { n.setVisible(address); n.setManaged(address); });
            };
            type.valueProperty().addListener((observable, old, value) -> updatePreferences.run());
            updatePreferences.run();
            form.getChildren().addAll(primary, texts);
        }
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(kind == Kind.GUARANTOR ? 440 : 360);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");
        pane.setContent(scroll);
        Node saveButton = pane.lookupButton(save);
        saveButton.setStyle("-fx-background-color: #5b56b5; -fx-text-fill: white; -fx-background-radius: 6;");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            Map<String, String> values = new LinkedHashMap<>(draft);
            inputs.forEach((key, input) -> {
                String value;
                if (input instanceof TextInputControl text) value = text.getText().strip();
                else if (input instanceof ComboBox<?> choices) value = String.valueOf(choices.getValue());
                else if (input instanceof DatePicker date) value = date.getValue() == null
                        ? "" : date.getValue().toString();
                else throw new IllegalStateException("Unsupported editor control: " + input.getClass());
                values.put(key, value);
            });
            if (kind == Kind.CONTACT_RECORD) {
                values.put("primary", Boolean.toString(primary.isSelected()));
                values.put("texts", Boolean.toString("Phone".equals(values.get("contactType")) && texts.isSelected()));
            }
            if ("Address".equals(values.get("contactType"))) values.put("value", addressText(values));
            else ADDRESS_KEYS.forEach(values::remove);
            Map<String, String> invalid = validate(kind, values, adding);
            invalid.putAll(extraValidation.apply(values));
            errors.forEach((key, error) -> {
                error.setText(invalid.getOrDefault(key, ""));
                error.setVisible(invalid.containsKey(key));
                error.setManaged(invalid.containsKey(key));
            });
            inputs.forEach((key, input) -> input.setStyle(INPUT_STYLE
                    + (invalid.containsKey(key) ? "-fx-border-color: #b42318;" : "")));
            if (!invalid.isEmpty()) {
                event.consume();
                inputs.entrySet().stream().filter(entry -> invalid.containsKey(entry.getKey()))
                        .findFirst().ifPresent(entry -> entry.getValue().requestFocus());
                return;
            }
            draft.clear();
            draft.putAll(values);
        });
        dialog.setResultConverter(button -> button == save ? new LinkedHashMap<>(draft) : null);
        dialog.setOnHidden(event -> {
            if (dialog.getResult() != null) onSave.accept(dialog.getResult());
        });
        dialog.setOnShown(event -> {
            pane.applyCss();
            ButtonBar buttons = (ButtonBar) pane.lookup(".button-bar");
            // The expanding gap keeps Cancel on the left and Save on the right.
            buttons.setButtonOrder("C+O");
            inputs.values().stream().filter(input -> !input.isDisabled()).findFirst().ifPresent(Control::requestFocus);
        });
        return dialog;
    }

    private static Label requiredLabel(String text) {
        Label label = new Label(text + " ");
        label.setStyle("-fx-text-fill: #393a7b; -fx-font-weight: bold;");
        Label asterisk = new Label("*");
        asterisk.setStyle("-fx-text-fill: #b42318; -fx-font-weight: bold;");
        label.setGraphic(asterisk);
        label.setContentDisplay(ContentDisplay.RIGHT);
        label.setGraphicTextGap(0);
        label.setAccessibleText(text + " (required)");
        return label;
    }

    static Map<String, String> validate(Kind kind, Map<String, String> values, boolean adding) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (Field field : kind.fields) {
            String value = values.getOrDefault(field.key, "");
            if (value.isBlank()) {
                errors.put(field.key, "This field is required.");
            } else if (field.key.equals("email") && !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
                errors.put(field.key, "Enter a valid email address.");
            } else if (field.key.equals("phone") && !value.matches("\\+?[0-9 ()-]{7,20}")) {
                errors.put(field.key, "Enter a valid phone number.");
            } else if (field.key.equals("termMonths")) {
                try {
                    if (Integer.parseInt(value) <= 0) errors.put(field.key, "Enter a positive number of months.");
                } catch (NumberFormatException invalid) { errors.put(field.key, "Enter a positive whole number."); }
            } else if (List.of("income", "amount", "rate", "paymentAmount").contains(field.key)) {
                try {
                    BigDecimal number = new BigDecimal(value);
                    if (!value.matches("[0-9]+(\\.[0-9]{1,2})?") || number.signum() < 0
                            || (field.key.equals("rate") && number.compareTo(new BigDecimal("100")) > 0))
                        errors.put(field.key, field.key.equals("rate") ? "Enter a rate from 0 to 100." : "Enter a non-negative amount with up to two decimal places.");
                } catch (NumberFormatException invalid) { errors.put(field.key, "Enter a valid number."); }
            } else if (field.key.equals("birthDate")) {
                try {
                    if (!LocalDate.parse(value, BIRTH_DATE).isBefore(LocalDate.now()))
                        errors.put(field.key, "Enter a date before today.");
                } catch (java.time.DateTimeException invalid) { errors.put(field.key, "Use a valid date in dd-MM-yyyy format."); }
            } else if (field.key.equals("date")) {
                try {
                    LocalDate.parse(value);
                } catch (java.time.DateTimeException invalid) {
                    errors.put("date", "Choose a valid upload date.");
                }
            } else if (!field.options.isEmpty() && !field.options.contains(value)) {
                errors.put(field.key, "Select a value from the list.");
            }
        }
        if (kind == Kind.CONTACT_RECORD) {
            String value = values.getOrDefault("value", "");
            String type = values.getOrDefault("contactType", "");
            if (!value.isBlank() && type.equals("Email") && !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                errors.put("value", "Enter a valid email address.");
            if (!value.isBlank() && type.equals("Phone") && !value.matches("\\+?[0-9 ()-]{7,20}"))
                errors.put("value", "Enter a valid phone number.");
            if (type.equals("Address")) {
                errors.remove("value");
                for (String key : List.of("street", "city", "country"))
                    if (values.getOrDefault(key, "").isBlank()) errors.put(key, "This field is required.");
            }
        }
        return errors;
    }
}
