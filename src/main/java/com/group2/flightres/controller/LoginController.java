package com.group2.flightres.controller;

import com.group2.flightres.exception.AuthenticationException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import com.group2.flightres.service.AuthService;
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

    private final AuthService authService = new AuthService();
    private static User lastAuthenticatedUser;

    private BookingContext bookingContext;
    private Runnable onNext;

    public static User getLastAuthenticatedUser() {
        return lastAuthenticatedUser;
    }

    @Override
    public void init(BookingContext context, Runnable onNext) {
        this.bookingContext = context;
        this.onNext = onNext;
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        try {
            User authenticatedUser = authService.authenticate(username, password);
            lastAuthenticatedUser = authenticatedUser;
            if (bookingContext != null) {
                bookingContext.setCurrentUser(authenticatedUser);
            }
            hideError();
            if (onNext != null) {
                onNext.run();
            } else {
                // Standalone test mode (no wizard context wired in yet)
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION,
                        "Login successful! Welcome, " + authenticatedUser.getFullName()
                                + " (" + authenticatedUser.getRole() + ")");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        } catch (AuthenticationException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            logger.error("Authentication error during login: {}", e.getMessage(), e);
            showError("Unable to connect to the database. Please try again later.");
        }
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
}

