package com.lavs.ui;

import com.lavs.business.CustomerRegistrationService;
import com.lavs.business.CustomerRegistrationService.ValidationError;
import com.lavs.model.CustomerDraft;
import java.util.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Personal-details stage of customer registration. */
public class AddCustomerController {
    private final CustomerRegistrationService service;
    private final Runnable onCancel;
    private final CustomerDraft draft = new CustomerDraft();
    private final Map<String, Label> fieldErrors = new LinkedHashMap<>();
    private final Map<String, Control> fields = new LinkedHashMap<>();
    private int step;
    private final Set<String> expanded = new HashSet<>();
    @FXML private VBox view, body, personalPane, contactPane, addressRows, phoneRows, emailRows;
    @FXML private FlowPane footer;
    @FXML private Button back, skip, next;
    @FXML private Label message;
    @FXML private Label marker0, marker1, stepName0, stepName1;
    @FXML private TextField title, firstName, lastName, dateOfBirth, nationality;
    @FXML private Label titleError, firstNameError, lastNameError, dateOfBirthError, nationalityError;
    @FXML private String markerStyle, stepNameStyle, activeMarkerStyle, activeStepNameStyle, inputStyle, invalidInputStyle;
    @FXML private ScrollPane customerScroll;
    @FXML private String scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle;
    private boolean showErrors;

    public AddCustomerController(CustomerRegistrationService service, Runnable onCancel) {
        this.service = service;
        this.onCancel = onCancel;
    }
    @FXML private void initialize() {
        ScrollPaneAppearance.install(customerScroll, scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle);
        bindField("title", title, titleError, draft.personal, "title");
        bindField("firstName", firstName, firstNameError, draft.personal, "firstName");
        bindField("lastName", lastName, lastNameError, draft.personal, "lastName");
        bindField("dateOfBirth", dateOfBirth, dateOfBirthError, draft.personal, "dateOfBirth");
        bindField("nationality", nationality, nationalityError, draft.personal, "nationality");
        render();
    }

    public boolean confirmLeave() {
        if (!draft.hasData()) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "The customer has not been saved. Discard the entered details?", ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle("Discard customer details?");
        alert.setHeaderText("Leave Add New Customer?");
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @FXML private void cancel() { if (confirmLeave()) onCancel.run(); }

    @FXML private void next() {
        showErrors = true;
        refreshValidation();
        List<ValidationError> errors = validate().stream().filter(error -> error.step() == step).toList();
        if (!errors.isEmpty()) {
            Control field = fields.get(errors.get(0).field());
            if (field != null) field.requestFocus();
        } else if (step == 0) {
            step = 1;
            showErrors = false;
            render();
        } else {
            message.setText("Contact details are valid. The next stage is not implemented yet.");
            message.setVisible(true);
            message.setManaged(true);
        }
    }

    private void bindField(String path, TextField input, Label error, Map<String, String> values, String key) {
        fields.put(path, input);
        fieldErrors.put(path, error);
        input.setText(values.getOrDefault(key, ""));
        input.textProperty().addListener((obs, old, value) -> {
            values.put(key, value.strip());
            refreshValidation();
        });
    }

    private List<ValidationError> validate() {
        try { return service.validate(draft); }
        catch (RuntimeException exception) {
            return List.of(new ValidationError(step, "", "Unable to validate the customer. Please try again."));
        }
    }

    private void refreshValidation() {
        List<ValidationError> errors = validate();
        fieldErrors.forEach((path, label) -> {
            String text = showErrors ? errors.stream().filter(e -> e.field().equals(path)).map(ValidationError::message).findFirst().orElse("") : "";
            label.setText(text);
            label.setVisible(!text.isEmpty());
            label.setManaged(!text.isEmpty());
            fields.get(path).setStyle(text.isEmpty() ? inputStyle : invalidInputStyle);
        });
        String text = showErrors ? String.join("\n", errors.stream().filter(e -> e.step() == step && e.field().isBlank()).map(ValidationError::message).toList()) : "";
        message.setText(text);
        message.setVisible(!text.isBlank());
        message.setManaged(!text.isBlank());
    }
    private void render() {
        marker0.setStyle(activeMarkerStyle);
        marker1.setStyle(step == 1 ? activeMarkerStyle : markerStyle);
        stepName0.setStyle(step == 0 ? activeStepNameStyle : stepNameStyle);
        stepName1.setStyle(step == 1 ? activeStepNameStyle : stepNameStyle);
        if (step == 0) body.getChildren().setAll(personalPane);
        else {
            entries(addressRows, "addresses", draft.addresses);
            entries(phoneRows, "phones", draft.phones);
            entries(emailRows, "emails", draft.emails);
            body.getChildren().setAll(contactPane);
        }
        footer.getChildren().setAll(next);
        if (step == 1) footer.getChildren().setAll(back, skip, next);
        customerScroll.setVvalue(0);
        refreshValidation();
    }

    @FXML private void back() { step = 0; showErrors = false; render(); }
    @FXML private void skip() {
        message.setText("The next stage is not implemented yet. Your contact details are kept.");
        message.setVisible(true);
        message.setManaged(true);
    }
    @FXML private void addAddress() { addEntry("addresses", draft.addresses); }
    @FXML private void addPhone() { addEntry("phones", draft.phones); }
    @FXML private void addEmail() { addEntry("emails", draft.emails); }

    private void addEntry(String kind, List<Map<String, String>> values) {
        Map<String, String> value = new LinkedHashMap<>();
        if (kind.equals("phones")) value.put("type", "Mobile");
        values.add(value);
        expanded.add(kind + "." + (values.size() - 1));
        render();
    }

    private void entries(VBox rows, String kind, List<Map<String, String>> values) {
        rows.getChildren().clear();
        fields.keySet().removeIf(path -> path.startsWith(kind + "."));
        fieldErrors.keySet().removeIf(path -> path.startsWith(kind + "."));
        if (values.isEmpty()) rows.getChildren().add(reviewValue("", "None added"));
        for (int i = 0; i < values.size(); i++) {
            int index = i;
            Map<String, String> value = values.get(i);
            String path = kind + "." + i;
            boolean invalid = showErrors && validate().stream().anyMatch(e -> e.field().startsWith(path + "."));
            FXMLLoader rowLoader = fragment("customerEntry.fxml");
            VBox row = rowLoader.getRoot();
            Map<String, Object> nodes = rowLoader.getNamespace();
            ((Label) nodes.get("summary")).setText(describe(kind, value));
            Button edit = (Button) nodes.get("edit");
            edit.setText(expanded.contains(path) || invalid ? "Done" : "Edit");
            edit.setOnAction(event -> {
                if (expanded.contains(path) || invalid) {
                    if (!CustomerDraft.hasEntryDetails(kind, value)) {
                        values.remove(index);
                        expanded.removeIf(key -> key.startsWith(kind + "."));
                    } else {
                        expanded.remove(path);
                    }
                } else {
                    expanded.add(path);
                }
                render();
            });
            ((Button) nodes.get("remove")).setOnAction(event -> {
                values.remove(index);
                expanded.removeIf(key -> key.startsWith(kind + "."));
                render();
            });
            if (expanded.contains(path) || invalid) {
                FXMLLoader editor = fragment(switch (kind) {
                    case "addresses" -> "customerAddress.fxml";
                    case "phones" -> "customerPhone.fxml";
                    case "emails" -> "customerEmail.fxml";
                    default -> throw new IllegalArgumentException("Unknown contact kind: " + kind);
                });
                Map<String, Object> editorNodes = editor.getNamespace();
                for (var entry : editorNodes.entrySet()) {
                    if (entry.getValue() instanceof TextField input) {
                        String key = entry.getKey();
                        bindField(path + "." + key, input, (Label) editorNodes.get(key + "Error"), value, key);
                    }
                }
                if (kind.equals("phones")) {
                    @SuppressWarnings("unchecked") ComboBox<String> type = (ComboBox<String>) editorNodes.get("type");
                    type.setValue(value.getOrDefault("type", "Mobile"));
                    type.valueProperty().addListener((o, a, b) -> value.put("type", b));
                    bindFlag((CheckBox) editorNodes.get("primary"), "primary", value, values);
                    bindFlag((CheckBox) editorNodes.get("texts"), "texts", value, null);
                } else if (kind.equals("addresses")) {
                    bindFlag((CheckBox) editorNodes.get("mailing"), "mailing", value, values);
                } else if (kind.equals("emails")) {
                    bindFlag((CheckBox) editorNodes.get("primary"), "primary", value, values);

                }
                ((VBox) nodes.get("editorContainer")).getChildren().add(editor.getRoot());
            }
            rows.getChildren().add(row);
        }
    }

    private void bindFlag(CheckBox check, String key, Map<String, String> value, List<Map<String, String>> exclusive) {
        check.setSelected("true".equals(value.get(key)));
        check.setOnAction(event -> {
            if (exclusive != null && check.isSelected()) exclusive.forEach(entry -> entry.put(key, "false"));
            value.put(key, String.valueOf(check.isSelected()));
            render();
        });
    }

    private Node reviewValue(String title, String value) {
        FXMLLoader loader = fragment("customerReviewValue.fxml");
        Label heading = (Label) loader.getNamespace().get("heading");
        heading.setText(title);
        heading.setVisible(!title.isBlank());
        heading.setManaged(!title.isBlank());
        ((Label) loader.getNamespace().get("value")).setText(value == null || value.isBlank() ? "None added" : value);
        return loader.getRoot();
    }

    private static FXMLLoader fragment(String filename) {
        FXMLLoader loader = new FXMLLoader(AddCustomerController.class.getResource("fxml/" + filename));
        try { loader.load(); return loader; }
        catch (IOException exception) { throw new UncheckedIOException("Unable to load customer form " + filename, exception); }
    }

    private static String describe(String kind, Map<String, String> value) {
        String result = switch (kind) {
            case "addresses" -> join(value.get("street"), value.get("suburb"), value.get("city"), value.get("postcode"), value.get("country")) + ("true".equals(value.get("mailing")) ? "  • Mailing" : "");
            case "phones" -> join(value.get("type"), value.get("number")) + ("true".equals(value.get("primary")) ? "  • Primary" : "") + ("true".equals(value.get("texts")) ? "  • Send TXT" : "");
            case "emails" -> value.getOrDefault("email", "") + ("true".equals(value.get("primary")) ? "  • Primary" : "");
            default -> "";
        };
        return result.isBlank() ? "New entry — enter details" : result;
    }

    private static String join(String... values) {
        return String.join(" ", Arrays.stream(values).filter(Objects::nonNull).filter(v -> !v.isBlank()).toList());
    }

}
