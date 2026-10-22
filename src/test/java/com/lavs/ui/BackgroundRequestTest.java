package com.lavs.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.Window;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundRequestTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void slowRequestLeavesUiResponsiveAndClosesProgressOnCompletion(boolean fail) throws Exception {
        JavaFxTestSupport.startToolkit();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>();
        AtomicReference<Window> progress = new AtomicReference<>();
        JavaFxTestSupport.run(() -> {
            BackgroundRequest.run(null, "Test request", ForkJoinPool.commonPool(), () -> {
                assertFalse(Platform.isFxApplicationThread());
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                if (fail) throw new IllegalArgumentException("Request failed");
                return "Saved";
            }, result -> {
                outcome.set(Platform.isFxApplicationThread() ? result : "Wrong thread");
                completed.countDown();
            }, error -> {
                outcome.set(Platform.isFxApplicationThread() ? error.getMessage() : "Wrong thread");
                completed.countDown();
            });
            progress.set(Window.getWindows().stream().filter(Window::isShowing).findFirst().orElseThrow());
            return null;
        });
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS));
            // A second event reaches JavaFX before the backend request has completed.
            assertTrue(JavaFxTestSupport.run(() -> progress.get().isShowing()));
            assertEquals(1, completed.getCount());
        } finally {
            release.countDown();
        }
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertEquals(fail ? "Request failed" : "Saved", outcome.get());
        assertFalse(JavaFxTestSupport.run(() -> progress.get().isShowing()));
    }
}
