package com.group2.flightres;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // TEMP: loads login.fxml directly, standalone, to test the
        // Auth backend before Flight Search exists for it to hand off
        // to. Will be replaced with main-shell.fxml once Flight Search
        // is built and Login is wired into BookingStep as step 0.
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/group2/flightres/fxml/login.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 500, 500);
        scene.getStylesheets().add(
                getClass().getResource("/com/group2/flightres/css/styles.css").toExternalForm());

        stage.setTitle("Flight Reservation System — Login Test Build");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
