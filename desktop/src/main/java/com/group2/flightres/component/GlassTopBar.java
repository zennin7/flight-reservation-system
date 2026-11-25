package com.group2.flightres.component;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.AppSession;
import com.group2.flightres.util.Navigator;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * Web-parity Sky Royal top navigation bar:
 * - Left: white rounded plane badge + "Sky Royal" (navigates to Home)
 * - Center: translucent dark glass pill with Home / Flights / My bookings (+ Admin only for admins)
 * - Right: translucent avatar pill with cream initials circle AND user's display name
 */
public class GlassTopBar extends HBox {

    private static final String WEB_PLANE_SVG =
            "M21 16v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z";

    private final HBox brandBox;
    private final HBox navPill;
    private final Button homeBtn;
    private final Button flightsBtn;
    private final Button myBookingsBtn;
    private final Button adminBtn;
    private final HBox avatarPill;
    private final Label avatarInitialsLabel;
    private final Label avatarNameLabel;

    private BookingContext bookingContext;
    private String activeNav = "flights";
    private Runnable onHomeAction;
    private Runnable onFlightsAction;
    private Runnable onMyBookingsAction;
    private Runnable onAdminAction;
    private Runnable onLogoutAction;

    public GlassTopBar() {
        getStyleClass().add("glass-nav-bar");
        setAlignment(Pos.CENTER_LEFT);
        setPrefHeight(54);
        setMinHeight(54);
        setMaxHeight(54);

        // Left: white rounded plane badge + "Sky Royal"
        SVGPath planeSvg = new SVGPath();
        planeSvg.setContent(WEB_PLANE_SVG);
        planeSvg.getStyleClass().add("nav-brand-badge-icon");
        planeSvg.setScaleX(0.82);
        planeSvg.setScaleY(0.82);

        StackPane planeBadge = new StackPane(planeSvg);
        planeBadge.getStyleClass().add("nav-brand-badge");
        planeBadge.setAlignment(Pos.CENTER);

        Label brandLabel = new Label(BrandConfig.APP_NAME);
        brandLabel.getStyleClass().add("nav-brand-text");

        brandBox = new HBox(10, planeBadge, brandLabel);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.getStyleClass().add("nav-brand-box");
        brandBox.setOnMouseClicked(e -> triggerHome());
        Tooltip dbTooltip = new Tooltip();
        dbTooltip.textProperty().bind(AppSession.dbStatusTextProperty());
        Tooltip.install(brandBox, dbTooltip);

        Region leftSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);

        // Center: pill nav bar
        homeBtn = createPillButton("Home", this::triggerHome);
        flightsBtn = createPillButton("Flights", this::triggerFlights);
        myBookingsBtn = createPillButton("My bookings", this::triggerMyBookings);
        adminBtn = createPillButton("Admin", this::triggerAdmin);
        adminBtn.setVisible(false);
        adminBtn.setManaged(false);

        navPill = new HBox(4, homeBtn, flightsBtn, myBookingsBtn, adminBtn);
        navPill.setAlignment(Pos.CENTER);
        navPill.getStyleClass().add("glass-nav-pill");

        Region rightSpacer = new Region();
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        // Right: avatar pill (cream initials circle + user display name)
        avatarInitialsLabel = new Label("DC");
        avatarInitialsLabel.getStyleClass().add("glass-avatar-initials");

        StackPane avatarCircle = new StackPane(avatarInitialsLabel);
        avatarCircle.getStyleClass().add("glass-avatar-circle");
        avatarCircle.setAlignment(Pos.CENTER);

        avatarNameLabel = new Label("Demo Customer");
        avatarNameLabel.getStyleClass().add("glass-avatar-name");

        avatarPill = new HBox(8, avatarCircle, avatarNameLabel);
        avatarPill.setAlignment(Pos.CENTER_LEFT);
        avatarPill.getStyleClass().add("glass-avatar-pill");
        avatarPill.setFocusTraversable(true);
        avatarPill.setOnMouseClicked(e -> showAvatarMenu());

        getChildren().addAll(brandBox, leftSpacer, navPill, rightSpacer, avatarPill);
        updateActiveStyles();
    }

    private Button createPillButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("glass-nav-link");
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    public String getActiveNav() {
        return activeNav;
    }

    public void setActiveNav(String activeNav) {
        this.activeNav = activeNav != null ? activeNav.trim().toLowerCase() : "flights";
        updateActiveStyles();
    }

    private void updateActiveStyles() {
        applyActive(homeBtn, "home".equals(activeNav));
        applyActive(flightsBtn, "flights".equals(activeNav));
        applyActive(myBookingsBtn, "my-bookings".equals(activeNav) || "my bookings".equals(activeNav));
        applyActive(adminBtn, "admin".equals(activeNav) || "dashboard".equals(activeNav) || "bookings".equals(activeNav));
    }

    private void applyActive(Button btn, boolean active) {
        btn.getStyleClass().remove("glass-nav-link-active");
        if (active) {
            btn.getStyleClass().add("glass-nav-link-active");
        }
    }

    public void bind(BookingContext context) {
        this.bookingContext = AppSession.ensureUser(context);
        User user = this.bookingContext.getCurrentUser();
        setUser(user);
    }

    public void setUser(User user) {
        if (user == null) {
            avatarInitialsLabel.setText("SR");
            avatarNameLabel.setText("Guest");
            adminBtn.setVisible(false);
            adminBtn.setManaged(false);
            return;
        }
        String displayName = deriveDisplayName(user);
        avatarNameLabel.setText(displayName);
        avatarInitialsLabel.setText(deriveInitials(displayName));

        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        adminBtn.setVisible(isAdmin);
        adminBtn.setManaged(isAdmin);
    }

    public void setOnHomeAction(Runnable onHomeAction) {
        this.onHomeAction = onHomeAction;
    }

    public void setOnFlightsAction(Runnable onFlightsAction) {
        this.onFlightsAction = onFlightsAction;
    }

    public void setOnMyBookingsAction(Runnable onMyBookingsAction) {
        this.onMyBookingsAction = onMyBookingsAction;
    }

    public void setOnAdminAction(Runnable onAdminAction) {
        this.onAdminAction = onAdminAction;
    }

    public void setOnLogoutAction(Runnable onLogoutAction) {
        this.onLogoutAction = onLogoutAction;
    }

    private void triggerHome() {
        if (onHomeAction != null) {
            onHomeAction.run();
        } else {
            Navigator.navigateToHome(this, bookingContext);
        }
    }

    private void triggerFlights() {
        if (onFlightsAction != null) {
            onFlightsAction.run();
        } else {
            Navigator.navigateToSearch(this, bookingContext);
        }
    }

    private void triggerMyBookings() {
        if (onMyBookingsAction != null) {
            onMyBookingsAction.run();
        } else {
            Navigator.navigateToMyBookings(this, bookingContext);
        }
    }

    private void triggerAdmin() {
        if (onAdminAction != null) {
            onAdminAction.run();
        } else {
            Navigator.navigateToAdminDashboard(this, bookingContext);
        }
    }

    private void showAvatarMenu() {
        ContextMenu menu = new ContextMenu();
        User user = bookingContext != null ? bookingContext.getCurrentUser() : AppSession.getCurrentUser();

        MenuItem myBookingsItem = new MenuItem("My bookings");
        myBookingsItem.setOnAction(e -> triggerMyBookings());
        menu.getItems().add(myBookingsItem);

        if (user == null) {
            MenuItem signInItem = new MenuItem("Sign in");
            signInItem.setOnAction(e -> Navigator.navigateToLogin(this, bookingContext));
            menu.getItems().add(signInItem);
        } else {
            if (user.getRole() == UserRole.ADMIN) {
                MenuItem adminDashItem = new MenuItem("Admin dashboard");
                adminDashItem.setOnAction(e -> triggerAdmin());
                MenuItem adminBookingsItem = new MenuItem("Admin bookings");
                adminBookingsItem.setOnAction(e -> Navigator.navigateToAdminBookings(this, bookingContext));
                menu.getItems().addAll(adminDashItem, adminBookingsItem);
            }

            MenuItem logoutItem = new MenuItem("Sign out");
            logoutItem.setOnAction(e -> {
                if (onLogoutAction != null) {
                    onLogoutAction.run();
                } else {
                    Navigator.signOut(this, bookingContext);
                }
            });
            menu.getItems().add(logoutItem);
        }
        menu.show(avatarPill, Side.BOTTOM, 0, 6);
    }

    public static String deriveDisplayName(User user) {
        if (user == null) {
            return "Guest";
        }
        String full = user.getFullName() != null ? user.getFullName().trim() : "";
        if (!full.isEmpty()) {
            return full;
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername().trim();
        }
        return "Traveller";
    }

    public static String deriveInitials(String name) {
        if (name == null || name.isBlank()) {
            return "SR";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + Character.toUpperCase(parts[0].charAt(0))
                    + Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        }
        String single = parts[0];
        if (single.length() >= 2) {
            return single.substring(0, 2).toUpperCase();
        }
        return single.toUpperCase();
    }

    public Button getHomeBtn() { return homeBtn; }
    public Button getFlightsBtn() { return flightsBtn; }
    public Button getMyBookingsBtn() { return myBookingsBtn; }
    public Button getAdminBtn() { return adminBtn; }
    public HBox getAvatarPill() { return avatarPill; }
    public Label getAvatarInitialsLabel() { return avatarInitialsLabel; }
    public Label getAvatarNameLabel() { return avatarNameLabel; }
}
