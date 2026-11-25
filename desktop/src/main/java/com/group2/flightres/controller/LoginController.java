package com.group2.flightres.controller;

import com.group2.flightres.exception.AuthenticationException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.util.AppSession;
import com.group2.flightres.util.DesktopAsync;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    private final AuthService authService;
    private static User lastAuthenticatedUser;

    private BookingContext bookingContext;
    private Runnable onNext;

    public LoginController() {
        this(new AuthService());
    }

    public LoginController(AuthService authService) {
        this.authService = authService != null ? authService : new AuthService();
    }

    public static User getLastAuthenticatedUser() {
        return lastAuthenticatedUser;
    }

    public static void clearLastAuthenticatedUser() {
        lastAuthenticatedUser = null;
    }

    @Override
    public void init(BookingContext context, Runnable onNext) {
        this.bookingContext = context;
        this.onNext = onNext;
    }

    @FXML
    public void initialize() {
        if (loginButton != null) {
            com.group2.flightres.util.Motion.attachHoverLift(loginButton);
        }
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        hideError();
        if (loginButton != null) {
            loginButton.setDisable(true);
            loginButton.setText("Signing in...");
        }

        DesktopAsync.run("Login:authenticate", () -> authService.authenticate(username, password),
                authenticatedUser -> {
                    if (loginButton != null) {
                        loginButton.setDisable(false);
                        loginButton.setText("Sign in");
                    }
                    lastAuthenticatedUser = authenticatedUser;
                    AppSession.setCurrentUser(authenticatedUser);
                    if (bookingContext != null) {
                        bookingContext.setCurrentUser(authenticatedUser);
                    }
                    if (passwordField != null) {
                        passwordField.clear();
                    }
                    hideError();
                    if (onNext != null) {
                        onNext.run();
                    }
                },
                err -> {
                    if (loginButton != null) {
                        loginButton.setDisable(false);
                        loginButton.setText("Sign in");
                    }
                    if (err instanceof AuthenticationException authEx) {
                        showError(authEx.getMessage());
                    } else {
                        logger.error("Authentication error during login: {}", err != null ? err.getMessage() : "unknown", err);
                        showError("Unable to connect to the database. Please try again later.");
                    }
                });
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    @FXML
    private void handleForgotPassword() {
        showComingSoon("Password Recovery", "Password recovery isn't available yet.");
    }

    @FXML
    private void handleSignUp() {
        showComingSoon("Create Account", "Account registration isn't available yet.");
    }

    @FXML
    private void handleGoogleSignIn() {
        showComingSoon("Google Sign-In", "Sign in with Google isn't available yet.");
    }

    @FXML
    private void handleAppleSignIn() {
        showComingSoon("Apple Sign-In", "Sign in with Apple isn't available yet.");
    }

    private void showComingSoon(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION,
                message);
        alert.setTitle("Coming soon");
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
