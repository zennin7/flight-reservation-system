
package com.group2.flightres;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
            App.class.getResource(
                "/com/group2/flightres/fxml/booking-confirmation.fxml"
            )
        );

        Scene scene = new Scene(loader.load(), 700, 500);

        stage.setTitle("Flight Booking Confirmation");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

