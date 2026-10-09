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

/** Resizable five-step editor. Business rules are supplied by the registration service. */
public class AddCustomerController {
    private static final String[] STEPS = {"Personal Details", "Contact", "Employment", "Notes", "Review"};
    private static final int REVIEW_STEP = 4;
    private final CustomerRegistrationService service;
    private final Runnable onCancel;
    private final BiConsumer<String, CustomerDraft> onSaved;
    private final CustomerDraft draft = new CustomerDraft();
    private final Map<String, Label> fieldErrors = new LinkedHashMap<>();
    private final Map<String, Control> fields = new LinkedHashMap<>();
    private final Set<String> expanded = new HashSet<>();
    private final boolean[] completed = new boolean[STEPS.length];
    @FXML private VBox view, body, personalPane, contactPane, employmentPane, notesPane, reviewPane;
    @FXML private VBox addressRows, phoneRows, emailRows, employerRows, noteRows;
    @FXML private VBox employmentReview, notesReview;
    @FXML private FlowPane personalReview, contactReview;
    @FXML private FlowPane footer;
    @FXML private Label message, reviewNotice, guarantorNote;
    @FXML private Label marker0, marker1, marker2, marker3, marker4, stepName0, stepName1, stepName2, stepName3, stepName4;
    @FXML private ComboBox<String> title;
    @FXML private TextField firstName, lastName, dateOfBirth, nationality;
    @FXML private Label titleError, firstNameError, lastNameError, dateOfBirthError, nationalityError;
    @FXML private String markerStyle, activeMarkerStyle, stepNameStyle, activeStepNameStyle, inputStyle, invalidInputStyle;
    @FXML private ScrollPane customerScroll;
    @FXML private String scrollBarStyle, scrollTrackStyle, scrollThumbStyle, scrollButtonStyle;
    @FXML private Button cancel, back, skip, next;
    private int step;
    private int renderedStep = -1;
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
        title.getItems().setAll("Mr", "Mrs", "Ms", "Miss");
        title.minHeightProperty().bind(firstName.heightProperty());
        title.prefHeightProperty().bind(firstName.heightProperty());
        title.maxHeightProperty().bind(firstName.heightProperty());
        fields.put("title", title);
        fieldErrors.put("title", titleError);
        title.setValue(draft.personal.get("title"));
        title.valueProperty().addListener((obs, old, value) -> {
            draft.personal.put("title", value == null ? "" : value);
            refreshValidation();
        });
        bindField("firstName", firstName, firstNameError, draft.personal, "firstName");
        bindField("lastName", lastName, lastNameError, draft.personal, "lastName");
        dateOfBirth.setTextFormatter(new TextFormatter<>(AddCustomerController::formatDateOfBirth));
        bindField("dateOfBirth", dateOfBirth, dateOfBirthError, draft.personal, "dateOfBirth");
        bindField("nationality", nationality, nationalityError, draft.personal, "nationality");
        render();
    }

    private static TextFormatter.Change formatDateOfBirth(TextFormatter.Change change) {
        if (!change.isContentChange()) return change;
        String proposed = change.getControlNewText();
        if (!proposed.matches("[0-9-]*")) return null;

        // Deleting a generated dash should delete the adjacent digit as well.
        if (change.isDeleted() && change.getText().isEmpty()
                && change.getControlText().substring(change.getRangeStart(), change.getRangeEnd()).equals("-")) {
            int start = change.getRangeStart();
            int end = change.getRangeEnd();
            if (change.getControlCaretPosition() == end && start > 0) start--;
            else if (end < change.getControlText().length()) end++;
            change.setRange(start, end);
            proposed = change.getControlNewText();
        }
        String digits = proposed.replace("-", "");
        if (digits.length() > 8) return null;
        int rawCaret = Math.min(change.getRangeStart() + change.getText().length(), proposed.length());
        int digitsBeforeCaret = proposed.substring(0, rawCaret).replace("-", "").length();
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            formatted.append(digits.charAt(i));
            if (i == 1 || i == 3) formatted.append('-');
        }
        int caret = digitsBeforeCaret + (digitsBeforeCaret >= 2 ? 1 : 0) + (digitsBeforeCaret >= 4 ? 1 : 0);
        change.setRange(0, change.getControlText().length());
        change.setText(formatted.toString());
        change.selectRange(Math.min(caret, formatted.length()), Math.min(caret, formatted.length()));
        return change;
    }

    public void focusTitle() {
        javafx.application.Platform.runLater(() -> {
            if (view.getScene() == null || step != 0) return;
            view.applyCss();
            view.layout();
            title.requestFocus();
        });
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
        Label[] markers = {marker0, marker1, marker2, marker3, marker4};
        Label[] names = {stepName0, stepName1, stepName2, stepName3, stepName4};
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
            case 3 -> {
                entries(noteRows, "notes", draft.notes);
                body.getChildren().setAll(notesPane);
            }
            case REVIEW_STEP -> { review(); body.getChildren().setAll(reviewPane); }
            default -> throw new IllegalStateException("Unknown step");
        }
        if (renderedStep != step) {
            customerScroll.setVvalue(0);
            renderedStep = step;
        }
        footer.getChildren().clear();
        if (step > 0 && !editingReview) footer.getChildren().add(back);
        if (step == 1 || step == 2 || step == 3) footer.getChildren().add(skip);
        next.setText(step == REVIEW_STEP ? "Save customer" : editingReview ? "Save and return to Review" : "Next");
        footer.getChildren().add(next);
        refreshValidation();
    }

    @FXML private void cancel() { if (confirmLeave()) onCancel.run(); }
    @FXML private void back() { step--; showErrors = false; render(); }
    @FXML private void skip() { advance(); }
    @FXML private void next() { if (step == REVIEW_STEP) save(); else advance(); }
    @FXML private void addAddress() { addEntry("addresses", draft.addresses); }
    @FXML private void addPhone() { addEntry("phones", draft.phones); }
    @FXML private void addEmail() { addEntry("emails", draft.emails); }
    @FXML private void addEmployer() { addEntry("employers", draft.employers); }
    @FXML private void addNote() { addEntry("notes", draft.notes); }
    @FXML private void editPersonal() { editStep(0); }
    @FXML private void editContact() { editStep(1); }
    @FXML private void editEmployment() { editStep(2); }
    @FXML private void editNotes() { editStep(3); }

    private void editStep(int target) {
        step = target;
        editingReview = true;
        showErrors = false;
        render();
    }

    private void addEntry(String kind, List<Map<String, String>> values) {
        List<ValidationError> errors = validate().stream()
            .filter(error -> error.step() == step).toList();
        if (!errors.isEmpty()) {
            showErrors = true;
            render();
            setMessage("Finish or remove the incomplete entry before adding another.");
            focusFirst(errors);
            return;
        }
        Optional<String> openEntry = expanded.stream().filter(path -> step == 3
            ? path.startsWith("notes.") : step == 2
            ? path.startsWith("employers.")
            : path.startsWith("addresses.") || path.startsWith("phones.") || path.startsWith("emails."))
            .findFirst();
        if (openEntry.isPresent()) {
            String path = openEntry.get();
            setMessage("Click Done on the open entry before adding another, or remove it.");
            fields.entrySet().stream().filter(entry -> entry.getKey().startsWith(path + "."))
                .map(Map.Entry::getValue).findFirst().ifPresent(Control::requestFocus);
            return;
        }
        Map<String, String> value = new LinkedHashMap<>();
        if (kind.equals("phones")) value.put("type", "Mobile");
        values.add(value);
        String path = kind + "." + (values.size() - 1);
        expanded.add(path);
        render();
        String firstField = switch (kind) {
            case "addresses" -> "street";
            case "phones" -> "number";
            case "emails" -> "email";
            case "employers" -> "name";
            case "notes" -> "text";
            default -> throw new IllegalArgumentException("Unknown entry kind: " + kind);
        };
        fields.get(path + "." + firstField).requestFocus();
    }

    private void bindField(String path, TextInputControl input, Label error, Map<String, String> values, String key) {
        fields.put(path, input);
        fieldErrors.put(path, error);
        if (key.equals("number") || key.equals("phone")) {
            input.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\+?[0-9 ()-]*") ? change : null));
        }
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
                        List<ValidationError> errors = validate().stream()
                            .filter(error -> error.field().startsWith(path + ".")).toList();
                        if (!errors.isEmpty()) {
                            showErrors = true;
                            render();
                            focusFirst(errors);
                            return;
                        }
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
                    case "notes" -> "customerNote.fxml";
                    default -> throw new IllegalArgumentException("Unknown entry kind: " + kind);
                });
                Map<String, Object> editorNodes = editor.getNamespace();
                for (var entry : editorNodes.entrySet()) {
                    if (entry.getValue() instanceof TextInputControl input) {
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
                    fields.put(path + ".employmentType", type);
                    fieldErrors.put(path + ".employmentType", (Label) editorNodes.get("employmentTypeError"));
                    type.valueProperty().addListener((o, a, b) -> {
                        value.put("employmentType", b == null ? "" : b);
                        refreshValidation();
                    });
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
            reviewValue("Name", join(draft.personal.get("firstName"), draft.personal.get("lastName"))),
            reviewValue("Date of birth", draft.personal.get("dateOfBirth")),
            reviewValue("Nationality", draft.personal.get("nationality")),
            reviewValue("Customer ID", "Assigned on save"));
        contactReview.getChildren().setAll(
            reviewValue("Addresses", reviewEntries("addresses", draft.addresses)),
            reviewValue("Phone numbers", reviewEntries("phones", draft.phones)),
            reviewValue("Emails", reviewEntries("emails", draft.emails)));
        employmentReview.getChildren().setAll(reviewValue("Employers", reviewEntries("employers", draft.employers)));
        notesReview.getChildren().clear();
        if (draft.notes.isEmpty()) notesReview.getChildren().add(reviewValue("", "None added"));
        for (int i = 0; i < draft.notes.size(); i++) {
            notesReview.getChildren().add(reviewValue("Note " + (i + 1), draft.notes.get(i).get("text")));
        }
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
            case "notes" -> value.getOrDefault("text", "");
            case "addresses" -> join(value.get("street"), value.get("suburb"), value.get("city"), value.get("postcode"), value.get("country")) + ("true".equals(value.get("mailing")) ? "  • Mailing" : "");
            case "phones" -> join(value.get("type"), value.get("number")) + ("true".equals(value.get("primary")) ? "  • Primary" : "") + ("true".equals(value.get("texts")) ? "  • Send TXT" : "");
            case "emails" -> value.getOrDefault("email", "") + ("true".equals(value.get("primary")) ? "  • Primary" : "");
            case "employers" -> join(value.get("name"), value.get("role"), value.get("employmentType"))
                    + (value.getOrDefault("income", "").isBlank() ? "" : "  • Annual income: NZD " + value.get("income"))
                    + (value.getOrDefault("address", "").isBlank() ? "" : "  • Address: " + value.get("address"))
                    + (value.getOrDefault("phone", "").isBlank() ? "" : "  • Phone: " + value.get("phone"));
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
            Control input = fields.get(path);
            String style = text.isEmpty() ? inputStyle : invalidInputStyle;
            // ComboBox cells provide their own padding inside the matched field height.
            input.setStyle(input instanceof ComboBox<?> ? style + " -fx-padding: 0;" : style);
        });
        next.setDisable(step == REVIEW_STEP && !errors.isEmpty());
        List<String> top = errors.stream().filter(e -> step == REVIEW_STEP || (e.step() == step && e.field().isBlank()))
                .map(e -> (e.field().isBlank() ? "" : STEPS[e.step()] + ": ") + e.message()).distinct().toList();
        if (step == REVIEW_STEP && !top.isEmpty()) setMessage("Please edit the listed sections before saving.\n" + String.join("\n", top));
        else if (showErrors && !top.isEmpty()) setMessage(String.join("\n", top));
        else { message.setVisible(false); message.setManaged(false); }
    }

    private void removeEmptyEntries(String kind, List<Map<String, String>> values) {
        if (values.removeIf(value -> !CustomerDraft.hasEntryDetails(kind, value))) {
            // Removing rows changes their indexes; rebuild editor and error bindings.
            expanded.removeIf(path -> path.startsWith(kind + "."));
            fields.keySet().removeIf(path -> path.startsWith(kind + "."));
            fieldErrors.keySet().removeIf(path -> path.startsWith(kind + "."));
        }
    }

    private void advance() {
        if (step == 1) {
            removeEmptyEntries("addresses", draft.addresses);
            removeEmptyEntries("phones", draft.phones);
            removeEmptyEntries("emails", draft.emails);
        } else if (step == 2) {
            removeEmptyEntries("employers", draft.employers);
        } else if (step == 3) {
            removeEmptyEntries("notes", draft.notes);
        }
        List<ValidationError> errors = validate().stream().filter(error -> error.step() == step).toList();
        if (!errors.isEmpty()) {
            showErrors = true;
            render();
            focusFirst(errors);
            return;
        }
        completed[step] = true;
        step = editingReview ? REVIEW_STEP : step + 1;
        editingReview = false;
        showErrors = step == REVIEW_STEP;
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
        step = Math.max(0, Math.min(REVIEW_STEP - 1, errors.get(0).step()));
        editingReview = step < REVIEW_STEP;
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
