package org.example;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;


public class LoginController {

    //Declaration for FXML components

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private void handleForgotPassword() {
        System.out.println("Forgot Password selected");
    }

    @FXML
    private void handleGoogleLogin() {
        System.out.println("Google Sign-In selected");
    }

    @FXML
    private void handleAppleLogin() {
        System.out.println("Apple Sign-In selected");
    }


    //Login button click

    @FXML
    private void handleLogin(){

        String username = usernameField.getText();

        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()){

            errorLabel.setText(
                    "Please enter both username and password."
            );

            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
        else {

            errorLabel.setText(

                    "Login button is working!"
            );

            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }


}
