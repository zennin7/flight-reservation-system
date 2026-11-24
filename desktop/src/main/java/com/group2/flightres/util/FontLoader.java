package com.group2.flightres.util;

import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads and registers the bundled Inter sans-serif fonts into JavaFX's global font registry,
 * matching the Sky Royal web typography.
 */
public final class FontLoader {

    private static final Logger logger = LoggerFactory.getLogger(FontLoader.class);
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private static final String[] FONT_FILES = {
            "/com/group2/flightres/fonts/Inter-Regular.ttf",
            "/com/group2/flightres/fonts/Inter-Medium.ttf",
            "/com/group2/flightres/fonts/Inter-SemiBold.ttf",
            "/com/group2/flightres/fonts/Inter-Bold.ttf"
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
                        logger.info("FONT_LOADED name='{}' family='{}' from {}", font.getName(), font.getFamily(), fontFile);
                    } else {
                        logger.warn("JavaFX Font.loadFont returned null for: {}", fontFile);
                    }
                } catch (Exception e) {
                    logger.error("Failed to load font from resource: {}", fontFile, e);
                }
            }
            // Diagnostic: print registered Inter families so we can verify exact names
            java.util.List<String> families = Font.getFamilies();
            logger.info("FONT_FAMILIES_INTER: {}",
                    families.stream()
                            .filter(f -> f.toLowerCase().contains("inter"))
                            .collect(java.util.stream.Collectors.toList()));
            logger.info("ALL_FONT_FAMILIES_COUNT: {}", families.size());
        }
    }
}
