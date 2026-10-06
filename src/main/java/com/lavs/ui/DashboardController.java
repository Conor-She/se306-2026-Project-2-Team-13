package com.lavs.ui;

import java.util.List;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.beans.binding.Bindings;

/** Handles dashboard navigation and user interactions. */
public class DashboardController {
    @FXML private Label profileName, welcomeHeading;
    @FXML private Button homeNav, customersNav, loansNav;
    @FXML private VBox homeView, customersView, loansView;
    @FXML private VBox searchCard, addCustomerCard;
    @FXML private FlowPane homeCards;
    @FXML private StackPane content;
    @FXML private ScrollPane dashboardScroll;
    @FXML private String navStyle, selectedNavStyle;
    private Runnable onLogout;

    @FXML private void initialize() {
        content.paddingProperty().bind(Bindings.createObjectBinding(() -> {
            double padding = Math.max(12, Math.min(36, content.getWidth() * 0.06));
            return new Insets(padding);
        }, content.widthProperty()));
        responsiveText(welcomeHeading, homeView, 32, 60, 0.048);
        for (javafx.scene.Node node : homeView.getChildren()) {
            if (node instanceof Label label && label != welcomeHeading) {
                responsiveText(label, homeView, 14, 25, 0.020);
            }
        }
        for (VBox card : List.of(searchCard, addCustomerCard)) {
            // Use two columns when each card has at least 300px; otherwise fill one row per card.
            card.prefWidthProperty().bind(Bindings.when(homeCards.widthProperty().greaterThanOrEqualTo(624))
                    .then(homeCards.widthProperty().subtract(homeCards.getHgap() + 1).divide(2))
                    .otherwise(Bindings.max(0, homeCards.widthProperty())));
            // Fill the viewport below the introduction, keeping the outer padding as a bottom margin.
            card.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> {
                int rows = homeCards.getWidth() >= 624 ? 1 : 2;
                double available = dashboardScroll.getViewportBounds().getHeight()
                        - content.getInsets().getTop() - content.getInsets().getBottom()
                        - homeCards.getLayoutY();
                return Math.max(300, (available - (rows - 1) * homeCards.getVgap()) / rows);
            }, dashboardScroll.viewportBoundsProperty(), content.paddingProperty(),
                    homeCards.layoutYProperty(), homeCards.widthProperty()));
            for (javafx.scene.Node node : card.getChildren()) {
                if (node instanceof Labeled text) {
                    if (text.getStyleClass().contains("card-title")) {
                        responsiveText(text, card, 24, 44, 0.065);
                    } else if (text.getStyleClass().contains("card-copy")) {
                        responsiveText(text, card, 17, 30, 0.044);
                    } else if (text.getStyleClass().contains("card-icon")) {
                        responsiveText(text, card, 36, 48, 0.070);
                        var iconSize = Bindings.max(64, Bindings.min(88, card.widthProperty().multiply(0.13)));
                        text.minWidthProperty().bind(iconSize);
                        text.prefWidthProperty().bind(iconSize);
                        text.maxWidthProperty().bind(iconSize);
                        text.minHeightProperty().bind(iconSize);
                        text.prefHeightProperty().bind(iconSize);
                        text.maxHeightProperty().bind(iconSize);
                    } else if (text instanceof Button) {
                        responsiveText(text, card, 17, 24, 0.035);
                    }
                }
            }
        }
        showHome();
    }

    private void responsiveText(Labeled text, javafx.scene.layout.Region container,
            double minimum, double maximum, double ratio) {
        String baseStyle = text.getStyle();
        text.styleProperty().bind(Bindings.createStringBinding(() ->
                baseStyle + " -fx-font-size: "
                        + Math.max(minimum, Math.min(maximum, container.getWidth() * ratio)) + "px;",
                container.widthProperty()));
    }

    public void configure(String username, Runnable onLogout) {
        this.onLogout = onLogout;
        profileName.setText(username);
        welcomeHeading.setText("Welcome, " + username);
        showHome();
    }

    private void navigate(Button selected, VBox view) {
        for (Button button : List.of(homeNav, customersNav, loansNav)) {
            button.getStyleClass().remove("selected");
            button.setStyle(navStyle);
        }
        selected.getStyleClass().add("selected");
        selected.setStyle(selectedNavStyle);
        for (VBox page : List.of(homeView, customersView, loansView)) {
            page.setVisible(page == view);
            page.setManaged(page == view);
        }
    }

    @FXML private void showHome() { navigate(homeNav, homeView); }

    @FXML private void showCustomers() { navigate(customersNav, customersView); }

    @FXML private void showLoans() { navigate(loansNav, loansView); }

    @FXML private void logout() { onLogout.run(); }
}