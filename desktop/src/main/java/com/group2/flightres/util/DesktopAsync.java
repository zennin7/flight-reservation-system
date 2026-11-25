package com.group2.flightres.util;

import javafx.application.Platform;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Shared background executor for running all database, service, and heavy I/O calls
 * off the JavaFX Application Thread using JavaFX {@link Task}s with timing logs.
 */
public final class DesktopAsync {

    private static final Logger logger = LoggerFactory.getLogger(DesktopAsync.class);

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "sr-bg-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private DesktopAsync() {}

    public static ExecutorService executor() {
        return EXECUTOR;
    }

    /**
     * Submits a JavaFX {@link Task} to the background executor, logs its duration,
     * and invokes {@code onSuccess} or {@code onError} on the JavaFX Application Thread.
     */
    public static <T> Task<T> run(String operationName,
                                  Callable<T> work,
                                  Consumer<T> onSuccess,
                                  Consumer<Throwable> onError) {
        long submittedNs = System.nanoTime();
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                long startNs = System.nanoTime();
                T result = work.call();
                long bgMs = (System.nanoTime() - startNs) / 1_000_000L;
                long totalMs = (System.nanoTime() - submittedNs) / 1_000_000L;
                logger.info("[TIMING] {} completed in {} ms (bg={} ms, thread={})",
                        operationName, totalMs, bgMs, Thread.currentThread().getName());
                return result;
            }
        };

        task.setOnSucceeded(e -> {
            if (onSuccess != null) {
                onSuccess.accept(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            logger.warn("[TIMING] {} failed: {}", operationName, ex != null ? ex.getMessage() : "unknown");
            if (onError != null) {
                onError.accept(ex);
            }
        });

        EXECUTOR.submit(task);
        return task;
    }

    public static void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
