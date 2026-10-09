package com.lavs.ui;

import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Handles dashboard navigation and user interactions. */
public class DashboardController {
    // Hysteresis: switch to 2 columns above 650px, back to 1 column below 600px.
    // The gap is wider than a scrollbar (~15px), so a scrollbar appearing can't flip the layout.
    private static final double TWO_COL_ON = 650;
    private static final double TWO_COL_OFF = 600;

    @FXML private Label profileName, welcomeHeading;
    @FXML private Button homeNav, customersNav, loansNav;
    @FXML private VBox homeView, customersView, loansView;
    @FXML private CustomersController customersViewController;
    @FXML private LoansController loansViewController;
    @FXML private VBox searchCard, addCustomerCard;
    @FXML private FlowPane homeCards;
    @FXML private StackPane content;
    @FXML private ScrollPane dashboardScroll;
    @FXML private String navStyle, selectedNavStyle;
    private Runnable onLogout;

    private final BooleanProperty twoColumns = new SimpleBooleanProperty(false);

    @FXML private void initialize() {
        content.paddingProperty().bind(Bindings.createObjectBinding(() -> {
            double padding = Math.round(Math.max(12, Math.min(36, content.getWidth() * 0.06)));
            return new Insets(padding);
        }, content.widthProperty()));

        homeCards.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue();
            if (w >= TWO_COL_ON) {
                twoColumns.set(true);
            } else if (w <= TWO_COL_OFF) {
                twoColumns.set(false);
            }
        });

        responsiveText(welcomeHeading, homeView, 32, 60, 0.048);
        for (javafx.scene.Node node : homeView.getChildren()) {
            if (node instanceof Label label && label != welcomeHeading) {
                responsiveText(label, homeView, 14, 25, 0.020);
            }
        }

        for (VBox card : List.of(searchCard, addCustomerCard)) {
            // Width only. Height is minHeight 300 + computed content (set in the FXML),
            // so it no longer depends on the viewport or on layoutY.
            card.prefWidthProperty().bind(Bindings.when(twoColumns)
                    .then(homeCards.widthProperty().subtract(homeCards.getHgap() + 1).divide(2))
                    .otherwise(Bindings.max(0, homeCards.widthProperty())));

            for (javafx.scene.Node node : card.getChildren()) {
                if (node instanceof Labeled text) {
                    if (text.getStyleClass().contains("card-title")) {
                        responsiveText(text, card, 24, 44, 0.065);
                    } else if (text.getStyleClass().contains("card-copy")) {
                        responsiveText(text, card, 13, 20, 0.032);
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
                        + Math.round(Math.max(minimum, Math.min(maximum, container.getWidth() * ratio))) + "px;",
                container.widthProperty()));
    }

    public void configure(String username, Runnable onLogout) {
        this.onLogout = onLogout;
        profileName.setText(username);
        welcomeHeading.setText("Welcome, " + username);
        showHome();
    }

    private void navigate(Button selected, VBox view) {
        dashboardScroll.setFitToHeight(view == customersView || view == loansView);
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

    @FXML private void showHome() {
        if (customersViewController != null && !customersViewController.confirmLeave()) return;
        if (customersViewController != null) customersViewController.onShown();
        navigate(homeNav, homeView);
    }

    @FXML private void showCustomers() {
        if (!customersViewController.confirmLeave()) return;
        navigate(customersNav, customersView);
        customersViewController.onShown();
    }

    @FXML private void showAddCustomer() {
        navigate(customersNav, customersView);
        customersViewController.openAddCustomer();
    }

    @FXML private void showLoans() {
        if (!customersViewController.confirmLeave()) return;
        navigate(loansNav, loansView);
        loansViewController.onShown();
    }

    @FXML private void logout() {
        if (!customersViewController.confirmLeave()) return;
        customersViewController.onShown();
        onLogout.run();
    }
}
