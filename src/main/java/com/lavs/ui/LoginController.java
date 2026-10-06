package com.lavs.ui;

import java.util.function.BiConsumer;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.shape.SVGPath;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/** Handles the login form defined in login.fxml. */
public class LoginController {
    @FXML private HBox root;
    @FXML private Region colourPanel;
    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private TextField visiblePassword;
    @FXML private Button toggle;
    @FXML private SVGPath closedEye;
    @FXML private ImageView openEye;
    @FXML private Label error;
    @FXML private Button loginButton;
    private BiConsumer<String, String> onLogin;
    private Runnable onSuccess;

    @FXML
    private void initialize() {
        colourPanel.prefWidthProperty().bind(root.widthProperty().multiply(0.477));
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        username.textProperty().addListener((observable, oldValue, newValue) -> clearError());
        password.textProperty().addListener((observable, oldValue, newValue) -> clearError());
    }

    @FXML
    private void togglePassword() {
        boolean show = !visiblePassword.isVisible();
        visiblePassword.setVisible(show);
        visiblePassword.setManaged(show);
        password.setVisible(!show);
        password.setManaged(!show);
        toggle.setAccessibleText(show ? "Hide password" : "Show password");
        openEye.setVisible(show);
        openEye.setManaged(show);
        closedEye.setVisible(!show);
        closedEye.setManaged(!show);
        TextField active = show ? visiblePassword : password;
        active.requestFocus();
        active.positionCaret(active.getText().length());
    }

    @FXML
    private void login() {
        clearError();
        if (username.getText().isBlank() && password.getText().isEmpty()) {
            showError("Please enter your username and password.");
            return;
        }
        if (username.getText().isBlank()) {
            showError("Please enter your username.");
            return;
        }
        if (password.getText().isEmpty()) {
            showError("Please enter your password.");
            return;
        }
        if (onLogin == null) {
            showError("Sign in is not connected yet");
            return;
        }
        try {
            onLogin.accept(username.getText(), password.getText());
        } catch (RuntimeException exception) {
            showError("Unable to sign in right now. Please try again later.");
        }
    }

    public void configure(BiConsumer<String, String> onLogin, Runnable onSuccess) {
        this.onLogin = onLogin;
        this.onSuccess = onSuccess;
    }

    /** Called by the login integration on the JavaFX application thread. */
    public void loginSucceeded() {
        clearError();
        password.clear();
        onSuccess.run();
    }

    /** Called by the login integration on the JavaFX application thread. */
    public void loginFailed() {
        showError("Invalid username or password. Please try again.");
    }

    public void showError(String message) {
        error.setText("\u24d8 " + message);
    }

    public void clearError() {
        error.setText("");
    }
}
