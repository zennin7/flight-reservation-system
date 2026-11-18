package com.group2.flightres.util;

import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Singleton managing active UI theme (LIGHT or DARK).
 * Swaps theme stylesheets on registered JavaFX scenes and persists the choice using Preferences.
 */
public class ThemeManager {

    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private static final String PREF_KEY_THEME = "Sky Royal_selected_theme";

    public enum Theme {
        LIGHT,
        DARK
    }

    private static final ThemeManager INSTANCE = new ThemeManager();

    private final Preferences preferences = Preferences.userNodeForPackage(ThemeManager.class);
    private final List<WeakReference<Scene>> registeredScenes = new ArrayList<>();
    private final List<Consumer<Theme>> themeListeners = new ArrayList<>();

    private Theme currentTheme;
    private final String lightThemeUrl;
    private final String darkThemeUrl;
    private final String skyRoyalCssUrl;
    private final String glassCssUrl;

    private ThemeManager() {
        this.lightThemeUrl = getResourceUrl("/com/group2/flightres/css/theme-light.css");
        this.darkThemeUrl = getResourceUrl("/com/group2/flightres/css/theme-dark.css");
        this.skyRoyalCssUrl = getResourceUrl("/com/group2/flightres/css/skyroyal.css");
        this.glassCssUrl = getResourceUrl("/com/group2/flightres/css/glass.css");

        // Load stored preference, defaulting to LIGHT
        String storedThemeName = preferences.get(PREF_KEY_THEME, Theme.LIGHT.name());
        try {
            this.currentTheme = Theme.valueOf(storedThemeName);
        } catch (IllegalArgumentException e) {
            this.currentTheme = Theme.LIGHT;
        }
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public synchronized Theme getCurrentTheme() {
        return currentTheme;
    }

    public synchronized void setTheme(Theme theme) {
        if (theme == null) {
            return;
        }
        this.currentTheme = theme;
        preferences.put(PREF_KEY_THEME, theme.name());
        logger.info("Theme switched to: {}", theme);

        updateRegisteredScenes();
        notifyListeners();
    }

    public synchronized void toggleTheme() {
        setTheme(currentTheme == Theme.LIGHT ? Theme.DARK : Theme.LIGHT);
    }

    /**
     * Registers a scene to automatically receive theme updates and immediately applies the current theme.
     */
    public synchronized void registerScene(Scene scene) {
        if (scene == null) return;

        // Clean up dead references
        registeredScenes.removeIf(ref -> ref.get() == null || ref.get() == scene);
        registeredScenes.add(new WeakReference<>(scene));

        applyTheme(scene);
    }

    /**
     * Applies the current theme to the given scene.
     */
    public synchronized void applyTheme(Scene scene) {
        if (scene == null) return;

        var stylesheets = scene.getStylesheets();
        stylesheets.remove(lightThemeUrl);
        stylesheets.remove(darkThemeUrl);
        stylesheets.remove(skyRoyalCssUrl);

        if (currentTheme == Theme.DARK) {
            if (darkThemeUrl != null) stylesheets.add(darkThemeUrl);
        } else {
            if (lightThemeUrl != null) stylesheets.add(lightThemeUrl);
        }

        if (skyRoyalCssUrl != null) {
            stylesheets.add(skyRoyalCssUrl);
        }

        if (scene.getRoot() != null) {
            scene.getRoot().applyCss();
        }
    }

    /**
     * Attaches the glass stylesheet (glass.css) to the specified scene.
     * Registers custom fonts (Inter and Lora) and attaches glass styling
     * without modifying global theme settings or affecting existing screens.
     *
     * @param scene the Scene to style with glass.css
     */
    public synchronized void applyGlassTheme(Scene scene) {
        if (scene == null || glassCssUrl == null) {
            return;
        }
        FontLoader.loadFonts();
        var stylesheets = scene.getStylesheets();
        if (!stylesheets.contains(glassCssUrl)) {
            stylesheets.add(glassCssUrl);
        }
        if (scene.getRoot() != null) {
            scene.getRoot().applyCss();
        }
    }

    public synchronized void addThemeChangeListener(Consumer<Theme> listener) {
        if (listener != null && !themeListeners.contains(listener)) {
            themeListeners.add(listener);
        }
    }

    public synchronized void removeThemeChangeListener(Consumer<Theme> listener) {
        themeListeners.remove(listener);
    }

    private synchronized void updateRegisteredScenes() {
        Iterator<WeakReference<Scene>> it = registeredScenes.iterator();
        while (it.hasNext()) {
            Scene scene = it.next().get();
            if (scene == null) {
                it.remove();
            } else {
                applyTheme(scene);
            }
        }
    }

    private synchronized void notifyListeners() {
        for (Consumer<Theme> listener : new ArrayList<>(themeListeners)) {
            try {
                listener.accept(currentTheme);
            } catch (Exception e) {
                logger.error("Error in theme change listener", e);
            }
        }
    }

    private String getResourceUrl(String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            logger.warn("CSS resource not found: {}", path);
            return null;
        }
        return resource.toExternalForm();
    }
}
