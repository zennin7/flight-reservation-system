package com.group2.flightres.component;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.util.ThemeManager;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable SkyVoyage navigation bar (64px high).
 * Supports standard customer mode and admin variant, active link underline,
 * and integrated theme toggling.
 */
public class NavBar extends HBox {

    private static final String SVG_PLANE_LOGO =
            "M3 21 L35 4 L38 7 L26 16 L28 28 L24 29 L18 19 L9 24 L6 22 Z";
    private static final String SVG_USER_ICON =
            "M 9 2.8 A 3.2 3.2 0 1 1 8.99 2.8 Z M 2.5 16 C 3.3 12.8 5.9 11.2 9 11.2 C 12.1 11.2 14.7 12.8 15.5 16";
    private static final String SVG_SUN_ICON =
            "M 10 6 A 4 4 0 1 0 10 14 A 4 4 0 1 0 10 6 Z M 10 1 L 10 3.5 M 10 16.5 L 10 19 M 3.6 3.6 L 5.4 5.4 M 14.6 14.6 L 16.4 16.4 M 1 10 L 3.5 10 M 16.5 10 L 19 10 M 3.6 16.4 L 5.4 14.6 M 14.6 5.4 L 16.4 3.6";
    private static final String SVG_MOON_ICON =
            "M 15 10.5 A 6.5 6.5 0 1 1 7.5 3 A 7.5 7.5 0 0 0 15 10.5 Z";

    private final HBox linksBox = new HBox();
    private final HBox rightBox = new HBox();
    private final List<Button> navButtons = new ArrayList<>();

    private Button userButton;
    private Button helpButton;
    private Button logoutButton;
    private Label adminLabel;
    private Button themeToggleBtn;
    private SVGPath themeIcon;

    private boolean adminMode = false;
    private String activeLink = "Home";
    private Consumer<String> navigationHandler;

    public NavBar() {
        initUI();
    }

    private void initUI() {
        getStyleClass().add("nav-bar");
        setAlignment(Pos.CENTER_LEFT);
        setMinHeight(64);
        setPrefHeight(64);
        setMaxHeight(64);

        // --- Logo (Left) ---
        SVGPath planeIcon = new SVGPath();
        planeIcon.setContent(SVG_PLANE_LOGO);
        planeIcon.getStyleClass().add("nav-logo-icon");

        Label skyLabel = new Label(BrandConfig.LOGO_PREFIX);
        skyLabel.getStyleClass().add("logo-text-sky");

        Label voyageLabel = new Label(BrandConfig.LOGO_SUFFIX);
        voyageLabel.getStyleClass().add("logo-text-voyage");

        HBox brandNameBox = new HBox(0, skyLabel, voyageLabel);
        brandNameBox.setAlignment(Pos.BASELINE_LEFT);

        Label subtitleLabel = new Label("FLIGHT RESERVATIONS");
        subtitleLabel.getStyleClass().add("logo-subtitle");

        VBox brandTextBox = new VBox(0, brandNameBox, subtitleLabel);
        brandTextBox.setAlignment(Pos.CENTER_LEFT);

        HBox logoBox = new HBox(8, planeIcon, brandTextBox);
        logoBox.getStyleClass().add("nav-logo-box");
        logoBox.setOnMouseClicked(e -> handleNavigation(adminMode ? "Dashboard" : "Home"));

        // --- Nav Links (Center) ---
        linksBox.getStyleClass().add("nav-links-box");
        linksBox.setAlignment(Pos.CENTER);

        // --- Spacer 1 ---
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        // --- Spacer 2 ---
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        // --- Right Controls ---
        rightBox.getStyleClass().add("nav-right-box");
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        initRightControls();
        buildNavLinks();

        getChildren().addAll(logoBox, spacer1, linksBox, spacer2, rightBox);

        // Listen for theme changes to update the sun/moon icon
        ThemeManager.getInstance().addThemeChangeListener(this::updateThemeIcon);
        updateThemeIcon(ThemeManager.getInstance().getCurrentTheme());
    }

    private void initRightControls() {
        // User Button
        SVGPath userIcon = new SVGPath();
        userIcon.setContent(SVG_USER_ICON);
        userIcon.getStyleClass().add("nav-user-icon");

        userButton = new Button("Login / Sign Up", userIcon);
        userButton.getStyleClass().add("nav-user-btn");
        userButton.setOnAction(e -> handleNavigation("Login"));

        // Help Button
        helpButton = new Button("Help");
        helpButton.getStyleClass().add("btn-gold");
        helpButton.setOnAction(e -> handleNavigation("Help"));

        // Admin Controls
        adminLabel = new Label("Admin");
        adminLabel.getStyleClass().add("nav-admin-label");

        logoutButton = new Button("Log Out");
        logoutButton.getStyleClass().add("btn-red");
        logoutButton.setOnAction(e -> handleNavigation("Logout"));

        // Theme Toggle Button
        themeIcon = new SVGPath();
        themeIcon.getStyleClass().add("theme-toggle-icon");

        themeToggleBtn = new Button("", themeIcon);
        themeToggleBtn.getStyleClass().add("theme-toggle-btn");
        themeToggleBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());

        updateRightControls();
    }

    private void updateRightControls() {
        rightBox.getChildren().clear();
        if (adminMode) {
            rightBox.getChildren().addAll(adminLabel, logoutButton, themeToggleBtn);
        } else {
            rightBox.getChildren().addAll(userButton, helpButton, themeToggleBtn);
        }
    }

    private void buildNavLinks() {
        linksBox.getChildren().clear();
        navButtons.clear();

        String[] links = adminMode
                ? new String[]{"Dashboard", "Flights", "Bookings", "Reports"}
                : new String[]{"Home", "Flights", "My Bookings", "About", "Contact"};

        for (String link : links) {
            Button btn = new Button(link);
            btn.getStyleClass().add("nav-link");
            btn.setOnAction(e -> handleNavigation(link));
            if (link.equalsIgnoreCase(activeLink)) {
                btn.getStyleClass().add("nav-link-active");
            }
            navButtons.add(btn);
            linksBox.getChildren().add(btn);
        }
    }

    private void handleNavigation(String destination) {
        setActiveLink(destination);
        if (navigationHandler != null) {
            navigationHandler.accept(destination);
        }
    }

    public void setAdminMode(boolean admin) {
        if (this.adminMode != admin) {
            this.adminMode = admin;
            this.activeLink = admin ? "Dashboard" : "Home";
            buildNavLinks();
            updateRightControls();
        }
    }

    public boolean isAdminMode() {
        return adminMode;
    }

    public void setActiveLink(String linkName) {
        this.activeLink = linkName;
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("nav-link-active");
            if (btn.getText().equalsIgnoreCase(linkName)) {
                btn.getStyleClass().add("nav-link-active");
            }
        }
    }

    public String getActiveLink() {
        return activeLink;
    }

    public void setNavigationHandler(Consumer<String> navigationHandler) {
        this.navigationHandler = navigationHandler;
    }

    public void setUserDisplayName(String name) {
        if (userButton != null) {
            userButton.setText(name != null && !name.isBlank() ? name : "Login / Sign Up");
        }
    }

    private void updateThemeIcon(ThemeManager.Theme theme) {
        if (theme == ThemeManager.Theme.DARK) {
            // Sun icon to toggle to light
            themeIcon.setContent(SVG_SUN_ICON);
            themeIcon.setStroke(Color.web("#d4a63c"));
            themeIcon.setFill(Color.web("#d4a63c"));
            themeIcon.setStrokeWidth(1.2);
            themeToggleBtn.setAccessibleText("Switch to Light Theme");
        } else {
            // Moon icon to toggle to dark
            themeIcon.setContent(SVG_MOON_ICON);
            themeIcon.setStroke(Color.web("#6b7280"));
            themeIcon.setFill(Color.web("#6b7280"));
            themeIcon.setStrokeWidth(1.0);
            themeToggleBtn.setAccessibleText("Switch to Dark Theme");
        }
    }
}
