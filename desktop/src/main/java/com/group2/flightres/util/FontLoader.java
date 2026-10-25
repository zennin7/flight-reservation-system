package com.group2.flightres.util;

import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads and registers custom Google Fonts (Inter and Lora) into JavaFX's global font registry.
 */
public final class FontLoader {

    private static final Logger logger = LoggerFactory.getLogger(FontLoader.class);
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private static final String[] FONT_FILES = {
            "/com/group2/flightres/fonts/Inter-Regular.ttf",
            "/com/group2/flightres/fonts/Inter-Medium.ttf",
            "/com/group2/flightres/fonts/Inter-SemiBold.ttf",
            "/com/group2/flightres/fonts/Inter-Bold.ttf",
            "/com/group2/flightres/fonts/Lora-Medium.ttf",
            "/com/group2/flightres/fonts/Lora-SemiBold.ttf"
    };

    private FontLoader() {
        // Utility class
    }

    /**
     * Loads and registers all required TrueType fonts if not already loaded.
     */
    public static void loadFonts() {
        if (LOADED.compareAndSet(false, true)) {
            for (String fontFile : FONT_FILES) {
                try (InputStream is = FontLoader.class.getResourceAsStream(fontFile)) {
                    if (is == null) {
                        logger.warn("Font file resource not found: {}", fontFile);
                        continue;
                    }
                    Font font = Font.loadFont(is, 14.0);
                    if (font != null) {
                        logger.info("Successfully loaded font: {} ({}) from {}", font.getName(), font.getFamily(), fontFile);
                    } else {
                        logger.warn("JavaFX Font.loadFont returned null for: {}", fontFile);
                    }
                } catch (Exception e) {
                    logger.error("Failed to load font from resource: {}", fontFile, e);
                }
            }
        }
    }
}
