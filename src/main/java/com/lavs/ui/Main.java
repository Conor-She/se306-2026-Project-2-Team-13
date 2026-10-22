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
    private static final String ORGANISATION_ID_PROPERTY = "lavs.organisation.id";
    private static final String ORGANISATION_ID_ENV = "LAVS_ORGANISATION_ID";

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
            Long organisationNumber = parseNumber(
                    configuredValue(ORGANISATION_ID_PROPERTY, ORGANISATION_ID_ENV));
            gateway = organisationNumber == null
                    ? new MainframeGatewayImpl(connection, "")
                    : new MainframeGatewayImpl(connection, organisationNumber);
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
                dashboardController.setCustomerService(new com.lavs.business.CustomerService(gateway));
                dashboardController.setLoanBrowsingService(new com.lavs.business.LoanBrowsingService(gateway));
                dashboardController.setLoanSubmissionService(
                        new com.lavs.business.LoanSubmissionService(gateway));
                dashboardController.setCoBorrowerService(
                        new com.lavs.business.CoBorrowerService(gateway));
                dashboardController.setPaymentService(
                        new com.lavs.business.PaymentService(gateway));
                dashboardController.setRegistrationServiceFactory(
                        () -> new com.lavs.business.MainframeCustomerRegistrationService(gateway));
                dashboardController.configure(loginName[0], () -> {
                    dashboardController.dispose();
                    gateway.logout();
                    loginName[0] = null;
                    scene.setRoot(root);
                });
                scene.setRoot(dashboard);
            } catch (IOException exception) {
                gateway.logout();
                java.util.logging.Logger.getLogger(Main.class.getName()).log(
                        java.util.logging.Level.WARNING, "Could not open the dashboard", exception);
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
                java.util.logging.Logger.getLogger(Main.class.getName()).log(
                        java.util.logging.Level.WARNING, "Could not close the connection", exception);
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

    // Reads a whole number from a setting, or null if it is missing or not a number
    private static Long parseNumber(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
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
