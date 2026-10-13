package com.lavs.ui;

import com.lavs.data.MainframeException;
import com.lavs.data.MainframeGateway;
import com.lavs.data.MainframeGatewayImpl;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import mainframe.client.Connection;
import mainframe.client.HttpConnection;

import java.io.IOException;
import java.net.URI;

public class Main extends Application {
    private static final String MAINFRAME_URL_PROPERTY = "lavs.mainframe.url";
    private static final String MAINFRAME_URL_ENV = "LAVS_MAINFRAME_URL";
    private static final String ORGANISATION_NAME_PROPERTY = "lavs.organisation.name";
    private static final String ORGANISATION_NAME_ENV = "LAVS_ORGANISATION_NAME";

    private Connection connection;
    private MainframeGateway gateway;

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("fxml/login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 868, 490);
        LoginController controller = loader.getController();
        String[] loginName = new String[1];

        String mainframeUrl = configuredValue(MAINFRAME_URL_PROPERTY, MAINFRAME_URL_ENV);
        boolean secureMainframeUrl = mainframeUrl != null && isSecureMainframeUrl(mainframeUrl);
        if (secureMainframeUrl) {
            connection = new HttpConnection(mainframeUrl);
            String organisationName = configuredValue(
                    ORGANISATION_NAME_PROPERTY, ORGANISATION_NAME_ENV);
            gateway = new MainframeGatewayImpl(connection,
                    organisationName == null ? "" : organisationName);
        }

        controller.configure((username, password) -> {
            if (gateway == null) {
                if (mainframeUrl == null) {
                    controller.showError("Sign in is not configured. Set LAVS_MAINFRAME_URL and restart the app.");
                } else {
                    controller.showError("Use HTTPS for the mainframe URL. HTTP is allowed only for localhost.");
                }
                return;
            }

            String submittedUsername = username.strip();
            controller.setLoginInProgress(true);
            Task<Void> loginTask = new Task<>() {
                @Override
                protected Void call() {
                    gateway.login(submittedUsername, password);
                    return null;
                }
            };
            loginTask.setOnSucceeded(event -> {
                loginName[0] = submittedUsername;
                controller.loginSucceeded();
            });
            loginTask.setOnFailed(event -> {
                controller.setLoginInProgress(false);
                Throwable failure = loginTask.getException();
                if (failure instanceof MainframeException mainframeFailure) {
                    if (mainframeFailure.getType() == MainframeException.Type.BUSINESS_ERROR) {
                        controller.loginFailed();
                    } else {
                        controller.showError("The sign-in service is unavailable. Check the URL and your connection.");
                    }
                } else {
                    controller.showError("Unable to sign in right now. Please try again later.");
                }
            });

            Thread loginThread = new Thread(loginTask, "lavs-login");
            loginThread.setDaemon(true);
            loginThread.start();
        }, () -> {
            try {
                FXMLLoader dashboardLoader = new FXMLLoader(Main.class.getResource("fxml/dashboard.fxml"));
                Parent dashboard = dashboardLoader.load();
                DashboardController dashboardController = dashboardLoader.getController();
                dashboardController.configure(loginName[0], () -> {
                    gateway.logout();
                    loginName[0] = null;
                    scene.setRoot(root);
                });
                scene.setRoot(dashboard);
            } catch (IOException exception) {
                gateway.logout();
                exception.printStackTrace();
                controller.showError("Unable to open the dashboard. Please try again.");
            }
        });

        primaryStage.setTitle("LAVS");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    private static String configuredValue(String property, String environmentVariable) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean isSecureMainframeUrl(String value) {
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return uri.getHost() != null;
            }
            if ("http".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                String host = uri.getHost();
                return "localhost".equalsIgnoreCase(host)
                        || "127.0.0.1".equals(host)
                        || "::1".equals(host);
            }
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }


}
