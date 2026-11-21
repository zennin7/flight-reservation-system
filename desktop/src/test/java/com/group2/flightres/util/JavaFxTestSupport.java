package com.group2.flightres.util;

import javafx.application.Platform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared single-time JavaFX toolkit and font initializer for desktop JUnit tests.
 */
public final class JavaFxTestSupport {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean FONTS_LOADED = new AtomicBoolean(false);

    private JavaFxTestSupport() {
    }

    /**
     * Starts the JavaFX toolkit once for the test JVM and disables implicit exit.
     */
    public static synchronized void ensureStarted() {
        System.setProperty("skyroyal.testing", "true");
        if (STARTED.compareAndSet(false, true)) {
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException ignored) {
                // Toolkit already started by another entrypoint in this JVM.
            }
        }
        Platform.setImplicitExit(false);
    }

    /**
     * Starts the JavaFX toolkit (with implicit exit disabled) and loads application fonts once.
     */
    public static synchronized void ensureStartedWithFonts() {
        ensureStarted();
        if (FONTS_LOADED.compareAndSet(false, true)) {
            try {
                FontLoader.loadFonts();
            } catch (Throwable ignored) {
                // Allow headless/test environments without full font registration to continue cleanly.
            }
        }
    }
}
