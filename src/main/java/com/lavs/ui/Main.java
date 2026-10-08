package com.lavs.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws java.io.IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("fxml/login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 868, 490);
        LoginController controller = loader.getController();
        // Temporary UI preview: any non-empty credentials open the dashboard.
        String[] loginName = new String[1];
        controller.configure((username, password) -> {
            loginName[0] = username.strip();
            controller.loginSucceeded();
        }, () -> {
            try {
                FXMLLoader dashboardLoader = new FXMLLoader(Main.class.getResource("fxml/dashboard.fxml"));
                Parent dashboard = dashboardLoader.load();
                DashboardController dashboardController = dashboardLoader.getController();
                dashboardController.configure(loginName[0], () -> scene.setRoot(root));
                scene.setRoot(dashboard);
            } catch (java.io.IOException exception) {
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

    public static void main(String[] args) {
        launch(args);
    }


}
