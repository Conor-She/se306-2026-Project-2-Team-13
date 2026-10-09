package com.lavs.ui;

import com.lavs.business.CustomerRegistrationService;
import com.lavs.business.CustomerRegistrationService.ValidationError;
import com.lavs.model.CustomerDraft;
import java.util.*;
import java.util.function.BiConsumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Resizable four-step editor. Business rules are supplied by the registration service. */
public class AddCustomerController {
    private static final String[] STEPS = {"Personal Details", "Contact", "Employment", "Review"};
    private final CustomerRegistrationService service;
    private final Runnable onCancel;
    private final BiConsumer<String, CustomerDraft> onSaved;
    private final CustomerDraft draft = new CustomerDraft();
    private final Map<String, Label> fieldErrors = new LinkedHashMap<>();
    private final Map<String, Control> fields = new LinkedHashMap<>();
    private final Set<String> expanded = new HashSet<>();
    private final boolean[] completed = new boolean[4];
    @FXML private VBox view, body, personalPane, contactPane, employmentPane, reviewPane;
    @FXML private VBox addressRows, phoneRows, emailRows, employerRows;
    @FXML private VBox employmentReview;
    @FXML private FlowPane personalReview, contactReview;
    @FXML private FlowPane footer;
    @FXML private Label message, reviewNotice, guarantorNote;
    @FXML private Label marker0, marker1, marker2, marker3, stepName0, stepName1, stepName2, stepName3;
    @FXML private TextField title, firstName, lastName, dateOfBirth, nationality;
    @FXML private Label titleError, firstNameError, lastNameError, dateOfBirthError, nationalityError;
    @FXML private String markerStyle, activeMarkerStyle, stepNameStyle, activeStepNameStyle, inputStyle, invalidInputStyle;
    @FXML private ScrollPane customerScroll;
    @FXML private String scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle;
    @FXML private Button cancel, back, skip, next;
    private int step;
    private boolean editingReview;
    private boolean showErrors;

    public AddCustomerController(CustomerRegistrationService service, Runnable onCancel,
                                 BiConsumer<String, CustomerDraft> onSaved) {
        this.service = service;
        this.onCancel = onCancel;
        this.onSaved = onSaved;
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

    private void render() {
        // The FXML defines every step; only the current one is attached to the scroll body.
        Label[] markers = {marker0, marker1, marker2, marker3};
        Label[] names = {stepName0, stepName1, stepName2, stepName3};
        for (int i = 0; i < STEPS.length; i++) {
            markers[i].setText(completed[i] && i != step ? "✓" : String.valueOf(i + 1));
            markers[i].setStyle(i == step || completed[i] ? activeMarkerStyle : markerStyle);
            names[i].setStyle(i == step ? activeStepNameStyle : stepNameStyle);
        }
        reviewNotice.setVisible(editingReview);
        reviewNotice.setManaged(editingReview);
        switch (step) {
            case 0 -> body.getChildren().setAll(personalPane);
            case 1 -> {
                entries(addressRows, "addresses", draft.addresses);
                entries(phoneRows, "phones", draft.phones);
                entries(emailRows, "emails", draft.emails);
                body.getChildren().setAll(contactPane);
            }
            case 2 -> {
                entries(employerRows, "employers", draft.employers);
                guarantorNote.setVisible(draft.employers.isEmpty());
                guarantorNote.setManaged(draft.employers.isEmpty());
                body.getChildren().setAll(employmentPane);
            }
            case 3 -> { review(); body.getChildren().setAll(reviewPane); }
            default -> throw new IllegalStateException("Unknown step");
        }
        customerScroll.setVvalue(0);
        footer.getChildren().clear();
        if (step > 0 && !editingReview) footer.getChildren().add(back);
        if (step == 1 || step == 2) footer.getChildren().add(skip);
        next.setText(step == 3 ? "Save customer" : editingReview ? "Save and return to Review" : "Next");
        footer.getChildren().add(next);
        refreshValidation();
    }

    @FXML private void cancel() { if (confirmLeave()) onCancel.run(); }
    @FXML private void back() { step--; showErrors = false; render(); }
    @FXML private void skip() { advance(false); }
    @FXML private void next() { if (step == 3) save(); else advance(true); }
    @FXML private void addAddress() { addEntry("addresses", draft.addresses); }
    @FXML private void addPhone() { addEntry("phones", draft.phones); }
    @FXML private void addEmail() { addEntry("emails", draft.emails); }
    @FXML private void addEmployer() { addEntry("employers", draft.employers); }
    @FXML private void editPersonal() { editStep(0); }
    @FXML private void editContact() { editStep(1); }
    @FXML private void editEmployment() { editStep(2); }

    private void editStep(int target) {
        step = target;
        editingReview = true;
        showErrors = false;
        render();
    }

    private void addEntry(String kind, List<Map<String, String>> values) {
        Map<String, String> value = new LinkedHashMap<>();
        if (kind.equals("phones")) value.put("type", "Mobile");
        values.add(value);
        expanded.add(kind + "." + (values.size() - 1));
        render();
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
                    case "employers" -> "customerEmployer.fxml";
                    default -> throw new IllegalArgumentException("Unknown entry kind: " + kind);
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
                } else if (kind.equals("employers")) {
                    @SuppressWarnings("unchecked") ComboBox<String> type = (ComboBox<String>) editorNodes.get("employmentType");
                    type.setValue(value.get("employmentType"));
                    type.valueProperty().addListener((o, a, b) -> value.put("employmentType", b == null ? "" : b));
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

    private void review() {
        personalReview.getChildren().setAll(
            reviewValue("Title", draft.personal.get("title")),
            reviewValue("Name", join(draft.personal.get("title"), draft.personal.get("firstName"), draft.personal.get("lastName"))),
            reviewValue("Date of birth", draft.personal.get("dateOfBirth")),
            reviewValue("Nationality", draft.personal.get("nationality")),
            reviewValue("Customer ID", "Assigned on save"));
        contactReview.getChildren().setAll(
            reviewValue("Addresses", reviewEntries("addresses", draft.addresses)),
            reviewValue("Phone numbers", reviewEntries("phones", draft.phones)),
            reviewValue("Emails", reviewEntries("emails", draft.emails)));
        employmentReview.getChildren().setAll(reviewValue("Employers", reviewEntries("employers", draft.employers)));
        if (draft.employers.isEmpty()) employmentReview.getChildren().add(
            reviewValue("", "A guarantor will be needed when a loan is started."));
    }

    private String reviewEntries(String kind, List<Map<String, String>> values) {
        return values.isEmpty() ? "None added" : String.join("\n", values.stream().map(value -> describe(kind, value)).toList());
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
            case "employers" -> join(value.get("name"), value.get("role"), value.get("employmentType"), value.get("address"), value.get("phone"))
                    + (value.getOrDefault("income", "").isBlank() ? "" : "  • Annual income: NZD " + value.get("income"));
            default -> "";
        };
        return result.isBlank() ? "New entry — enter details" : result;
    }

    private static String join(String... values) {
        return String.join(" ", Arrays.stream(values).filter(Objects::nonNull).filter(v -> !v.isBlank()).toList());
    }

    private List<ValidationError> validate() {
        try { return service.validate(draft); }
        catch (RuntimeException exception) {
            return List.of(new ValidationError(step, "", "Unable to validate the customer. Please try again."));
        }
    }

    private void refreshValidation() {
        if (next == null) return;
        List<ValidationError> errors = validate();
        fieldErrors.forEach((path, label) -> {
            String text = showErrors ? errors.stream().filter(e -> e.field().equals(path)).map(ValidationError::message).findFirst().orElse("") : "";
            label.setText(text);
            label.setVisible(!text.isEmpty());
            label.setManaged(!text.isEmpty());
            fields.get(path).setStyle(text.isEmpty() ? inputStyle : invalidInputStyle);
        });
        next.setDisable(step == 3 && !errors.isEmpty());
        List<String> top = errors.stream().filter(e -> step == 3 || (e.step() == step && e.field().isBlank()))
                .map(e -> (e.field().isBlank() ? "" : STEPS[e.step()] + ": ") + e.message()).distinct().toList();
        if (step == 3 && !top.isEmpty()) setMessage("Please edit the listed sections before saving.\n" + String.join("\n", top));
        else if (showErrors && !top.isEmpty()) setMessage(String.join("\n", top));
        else { message.setVisible(false); message.setManaged(false); }
    }

    private void advance(boolean validateCurrent) {
        if (validateCurrent) {
            List<ValidationError> errors = validate().stream().filter(e -> e.step() == step).toList();
            if (!errors.isEmpty()) { showErrors = true; render(); focusFirst(errors); return; }
        }
        completed[step] = true;
        step = editingReview ? 3 : step + 1;
        editingReview = false;
        showErrors = step == 3;
        render();
    }

    private void save() {
        List<ValidationError> errors = validate();
        if (!errors.isEmpty()) { showFailure(errors); return; }
        next.setDisable(true);
        try {
            String id = service.save(draft.copy());
            onSaved.accept(id, draft.copy());
        } catch (CustomerRegistrationService.RegistrationException exception) {
            showFailure(exception.errors().isEmpty() ? List.of(new ValidationError(step, "", "Customer could not be saved. Please try again.")) : exception.errors());
        } catch (RuntimeException exception) {
            next.setDisable(false);
            setMessage("Customer could not be saved. Your details are kept. Please try again.");
        }
    }

    private void showFailure(List<ValidationError> errors) {
        for (ValidationError error : errors) {
            int lastDot = error.field().lastIndexOf('.');
            if (lastDot > 0) expanded.add(error.field().substring(0, lastDot));
        }
        step = Math.max(0, Math.min(2, errors.get(0).step()));
        editingReview = step < 3;
        showErrors = true;
        render();
        for (ValidationError error : errors) {
            Label label = fieldErrors.get(error.field());
            if (label != null) { label.setText(error.message()); label.setVisible(true); label.setManaged(true); }
        }
        setMessage(String.join("\n", errors.stream().filter(e -> e.step() == step && (e.field().isBlank() || !fieldErrors.containsKey(e.field()))).map(ValidationError::message).toList()));
        focusFirst(errors);
    }

    private void focusFirst(List<ValidationError> errors) {
        Control field = fields.get(errors.get(0).field());
        if (field != null) javafx.application.Platform.runLater(field::requestFocus);
    }

    private void setMessage(String text) {
        message.setText(text);
        message.setVisible(!text.isBlank());
        message.setManaged(!text.isBlank());
    }

}
