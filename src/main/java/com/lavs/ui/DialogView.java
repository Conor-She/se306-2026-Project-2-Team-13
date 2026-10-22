package com.lavs.ui;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;

// AI-assisted: Extracted dialog layouts and shared styling into a reusable FXML helper.
/** Loads dialog layouts and applies FXML-defined styles to JavaFX-generated controls. */
final class DialogView {
    private final FXMLLoader theme = load("themedDialog.fxml");
    private final FXMLLoader content;

    DialogView(Dialog<?> dialog, String contentFile) {
        content = load(contentFile);
        DialogPane pane = (DialogPane) theme.getRoot();
        pane.setContent(content.getRoot());
        dialog.setDialogPane(pane);
    }

    @SuppressWarnings("unchecked")
    <T> T get(String id) { return (T) content.getNamespace().get(id); }

    // Apply shared button, field and date-picker styles after JavaFX creates them.
    void apply(DialogPane pane) {
        pane.applyCss();
        Node container = pane.lookup(".button-bar > .container");
        if (container != null) container.setStyle(style("buttonContainerStyle"));
        for (ButtonType type : pane.getButtonTypes()) {
            Button button = (Button) pane.lookupButton(type);
            String base = style("buttonStyle") + (button.isDefaultButton() ? style("primaryButtonStyle") : "");
            Runnable refresh = () -> button.setStyle(base + (button.isDefaultButton() && button.isDisabled()
                    ? style("disabledStyle") : ""));
            button.disabledProperty().addListener((observable, old, value) -> refresh.run());
            refresh.run();
        }
        for (String id : new String[]{"paymentAmount", "paymentDate", "coborrowerCustomerSearch"}) {
            Control field = get(id);
            if (field == null) continue;
            String base = field.getStyle();
            Runnable refresh = () -> field.setStyle(base + (field.isFocused() ? style("focusedStyle") : ""));
            field.focusedProperty().addListener((observable, old, value) -> refresh.run());
            refresh.run();
            if (field instanceof DatePicker date) {
                date.getEditor().setStyle(style("dateEditorStyle"));
                Node arrow = date.lookup(".arrow-button");
                if (arrow != null) arrow.setStyle(style("dateArrowStyle"));
            }
        }
    }

    private String style(String id) { return (String) theme.getNamespace().get(id); }

    // Load the shared appearance for customer search results.
    static void styleCustomerCell(ListCell<?> cell) {
        FXMLLoader loader = new FXMLLoader(DialogView.class.getResource("fxml/coborrowerCustomerCell.fxml"));
        loader.setRoot(cell);
        try {
            loader.load();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load customer result cell", failure);
        }
        String base = cell.getStyle();
        Runnable refresh = () -> cell.setStyle(base + (cell.isEmpty() ? "" : cell.isSelected()
                ? loader.getNamespace().get("selectedStyle") : cell.isHover()
                ? loader.getNamespace().get("hoverStyle") : ""));
        cell.selectedProperty().addListener((observable, old, value) -> refresh.run());
        cell.hoverProperty().addListener((observable, old, value) -> refresh.run());
        cell.emptyProperty().addListener((observable, old, value) -> refresh.run());
        refresh.run();
    }

    // Load one reusable dialog layout from the FXML resources.
    private static FXMLLoader load(String file) {
        FXMLLoader loader = new FXMLLoader(DialogView.class.getResource("fxml/" + file));
        try {
            loader.load();
            return loader;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load dialog " + file, failure);
        }
    }
}
