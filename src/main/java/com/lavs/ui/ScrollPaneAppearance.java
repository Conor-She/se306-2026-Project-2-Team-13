package com.lavs.ui;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;

/** Applies FXML-supplied inline styles to the scrollbar parts created by JavaFX skins. */
final class ScrollPaneAppearance {
    private ScrollPaneAppearance() {}

    static void install(Control scroll, String barStyle, String trackStyle, String thumbStyle, String buttonStyle) {
        Runnable apply = () -> {
            for (Node node : scroll.lookupAll(".scroll-bar")) {
                // Nested scroll panes and lists each style their own bars.
                Node parent = node.getParent();
                while (parent != null && !(parent instanceof Control)) parent = parent.getParent();
                if (parent != scroll || !(node instanceof ScrollBar bar)) continue;
                if (Boolean.TRUE.equals(bar.getProperties().put("minimalAppearanceInstalled", true))) continue;
                bar.setStyle(barStyle);
                Runnable styleParts = () -> {
                    style(bar, ".track", trackStyle);
                    style(bar, ".track-background", trackStyle);
                    style(bar, ".thumb", thumbStyle);
                    for (String selector : new String[] {".increment-button", ".decrement-button", ".increment-arrow", ".decrement-arrow"})
                        style(bar, selector, buttonStyle);
                };
                bar.skinProperty().addListener((obs, old, skin) -> styleParts.run());
                styleParts.run();
            }
            style(scroll, ".corner", trackStyle);
        };
        scroll.skinProperty().addListener((obs, old, skin) -> apply.run());
        apply.run();
    }

    private static void style(Node root, String selector, String value) {
        root.lookupAll(selector).forEach(node -> node.setStyle(value));
    }
}
