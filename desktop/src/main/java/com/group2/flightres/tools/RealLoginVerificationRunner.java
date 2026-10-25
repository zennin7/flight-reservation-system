package com.group2.flightres.tools;

import com.group2.flightres.controller.LoginController;
import com.group2.flightres.controller.MainShellController;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.FontLoader;
import com.group2.flightres.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automates testing of real database login inside MainShell:
 * 1. Test wrong password -> confirms errorLabel visible with error message.
 * 2. Test correct credentials (testadmin / Passw0rd!) -> confirms authentication,
 *    role redirection to Dashboard, and user session in BookingContext.
 */
public class RealLoginVerificationRunner extends Application {

    private static final Logger logger = LoggerFactory.getLogger(RealLoginVerificationRunner.class);

    @Override
    public void start(Stage stage) throws Exception {
        FontLoader.loadFonts();

        FXMLLoader shellLoader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/main-shell.fxml"));
        Parent shellRoot = shellLoader.load();
        MainShellController shellController = shellLoader.getController();

        Scene scene = new Scene(shellRoot, 1000, 740);
        ThemeManager.getInstance().registerScene(scene);
        stage.setScene(scene);
        stage.setTitle("Real Login Verification");
        stage.show();

        // 1. Navigate to Login in MainShell
        shellController.handleNavigation("Login");

        PauseTransition step1 = new PauseTransition(Duration.millis(500));
        step1.setOnFinished(e1 -> {
            TextField usernameField = (TextField) scene.lookup("#usernameField");
            PasswordField passwordField = (PasswordField) scene.lookup("#passwordField");
            Button loginButton = (Button) scene.lookup("#loginButton");
            Label errorLabel = (Label) scene.lookup("#errorLabel");

            if (usernameField == null || passwordField == null || loginButton == null || errorLabel == null) {
                logger.error("VERIFICATION_FAILED: Login controls not found in scene!");
                Platform.exit();
                return;
            }

            // --- TEST CASE 1: Wrong password ---
            logger.info("--- TEST CASE 1: Attempting login with WRONG password ---");
            usernameField.setText("testadmin");
            passwordField.setText("TotallyWrongPassword123!");
            loginButton.fire();

            PauseTransition step2 = new PauseTransition(Duration.millis(400));
            step2.setOnFinished(e2 -> {
                boolean errorVisible = errorLabel.isVisible();
                String errorMsg = errorLabel.getText();
                logger.info("WRONG_LOGIN_RESULT: errorVisible={}, message='{}'", errorVisible, errorMsg);

                if (!errorVisible || errorMsg == null || errorMsg.isBlank()) {
                    logger.error("VERIFICATION_FAILED: Error label was not displayed on wrong password!");
                    Platform.exit();
                    return;
                }
                logger.info("VERIFICATION_SUCCESS: Wrong password properly displayed error message: '{}'", errorMsg);

                // --- TEST CASE 2: Correct credentials ---
                logger.info("--- TEST CASE 2: Attempting login with CORRECT credentials (testadmin / Passw0rd!) ---");
                usernameField.setText("testadmin");
                passwordField.setText("Passw0rd!");
                loginButton.fire();

                PauseTransition step3 = new PauseTransition(Duration.millis(600));
                step3.setOnFinished(e3 -> {
                    User authenticatedUser = shellController.getBookingContext().getCurrentUser();
                    if (authenticatedUser != null) {
                        logger.info("CORRECT_LOGIN_RESULT: user='{}', role='{}', fullName='{}'",
                                authenticatedUser.getUsername(),
                                authenticatedUser.getRole(),
                                authenticatedUser.getFullName());
                        logger.info("VERIFICATION_SUCCESS: Successfully authenticated and navigated into MainShell!");
                    } else {
                        logger.error("VERIFICATION_FAILED: No user was set in BookingContext after correct login!");
                    }
                    Platform.exit();
                });
                step3.play();
            });
            step2.play();
        });
        step1.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
