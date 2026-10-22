package com.lavs.ui;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Runs a request while a modal progress window prevents duplicate edits. */
final class BackgroundRequest {
    private BackgroundRequest() {}

    static <T> void run(Window owner, String message, Executor executor, Supplier<T> request,
            Consumer<T> success, Consumer<RuntimeException> failure) {
        Dialog<Void> progress = new Dialog<>();
        if (owner != null) progress.initOwner(owner);
        progress.setTitle(message);
        // JavaFX needs a cancel button type to permit programmatic dialog closure.
        progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        var cancel = progress.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancel.setVisible(false);
        cancel.setManaged(false);
        progress.getDialogPane().setContent(new VBox(12, new Label(message), new ProgressIndicator()));
        progress.setOnCloseRequest(event -> event.consume());
        progress.show();
        Consumer<Runnable> finish = action -> {
            Runnable update = () -> {
                progress.setOnCloseRequest(null);
                progress.close();
                action.run();
            };
            if (Platform.isFxApplicationThread()) update.run();
            else Platform.runLater(update);
        };
        try {
            executor.execute(() -> {
                T result;
                try {
                    result = request.get();
                } catch (RuntimeException exception) {
                    finish.accept(() -> failure.accept(exception));
                    return;
                }
                finish.accept(() -> success.accept(result));
            });
        } catch (RuntimeException exception) {
            finish.accept(() -> failure.accept(exception));
        }
    }
}
