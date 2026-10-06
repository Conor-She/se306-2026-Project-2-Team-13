package com.lavs.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws java.io.IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("login.fxml"));
        Parent root = loader.load();
        Parent dashboard = FXMLLoader.load(Main.class.getResource("dashboard.fxml"));
        Scene scene = new Scene(root, 868, 490);
        LoginController controller = loader.getController();
        // Temporary UI preview: any non-empty credentials open the dashboard.
        // Replace this callback with the AuthService integration in the backend issue.
        controller.configure((username, password) -> controller.loginSucceeded(),
                () -> scene.setRoot(dashboard));

        primaryStage.setTitle("LAVS");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(490);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

}
