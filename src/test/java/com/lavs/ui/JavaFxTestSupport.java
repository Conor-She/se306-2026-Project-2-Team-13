package com.lavs.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts one JavaFX toolkit for all UI tests in the JVM. */
final class JavaFxTestSupport {
    private static boolean started;

    private JavaFxTestSupport() {}

    static synchronized void startToolkit() throws Exception {
        if (started) return;
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready::countDown);
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "JavaFX did not start");
        run(() -> { Platform.setImplicitExit(false); return null; });
        started = true;
    }

    static <T> T run(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }
}
