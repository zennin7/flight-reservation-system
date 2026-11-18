package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;


public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        //Login interface

        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("/login.fxml")
        );

        //Scene

        Scene scene = new Scene(loader.load(), 700, 900);

        //Application Window

        stage.setTitle("Flight Reservation System");
        stage.setScene(scene);
        stage.setResizable(false);

        // Display the application
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}