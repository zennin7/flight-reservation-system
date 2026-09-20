package com.group2.flightres;

import com.group2.flightres.controller.MainShellController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/group2/flightres/fxml/main-shell.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1000, 750);
        scene.getStylesheets().add(
                getClass().getResource("/com/group2/flightres/css/styles.css").toExternalForm());

        stage.setTitle("Flight Reservation System");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
