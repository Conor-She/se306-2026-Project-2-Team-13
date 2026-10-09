package com.lavs.ui;

import com.lavs.business.CustomerRegistrationService;
import com.lavs.business.CustomerRegistrationService.ValidationError;
import com.lavs.model.CustomerDraft;
import java.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Personal-details stage of customer registration. */
public class AddCustomerController {
    private final CustomerRegistrationService service;
    private final Runnable onCancel;
    private final CustomerDraft draft = new CustomerDraft();
    private final Map<String, Label> fieldErrors = new LinkedHashMap<>();
    private final Map<String, Control> fields = new LinkedHashMap<>();
    @FXML private VBox view, body, personalPane;
    @FXML private Label message;
    @FXML private Label marker0, stepName0;
    @FXML private TextField title, firstName, lastName, dateOfBirth, nationality;
    @FXML private Label titleError, firstNameError, lastNameError, dateOfBirthError, nationalityError;
    @FXML private String activeMarkerStyle, activeStepNameStyle, inputStyle, invalidInputStyle;
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
        body.getChildren().setAll(personalPane);
        marker0.setStyle(activeMarkerStyle);
        stepName0.setStyle(activeStepNameStyle);
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
        List<ValidationError> errors = validate();
        if (!errors.isEmpty()) {
            Control field = fields.get(errors.get(0).field());
            if (field != null) field.requestFocus();
        } else {
            message.setText("Personal details are valid. The next stage is not implemented yet.");
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
            return List.of(new ValidationError(0, "", "Unable to validate the customer. Please try again."));
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
        String text = showErrors ? String.join("\n", errors.stream().filter(e -> e.field().isBlank()).map(ValidationError::message).toList()) : "";
        message.setText(text);
        message.setVisible(!text.isBlank());
        message.setManaged(!text.isBlank());
    }
}
