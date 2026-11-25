package com.group2.flightres;

import com.group2.flightres.config.AppConfig;
import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.util.AppSession;
import com.group2.flightres.util.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class App extends Application {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 760;
    private static final int MIN_WIDTH = 1024;
    private static final int MIN_HEIGHT = 680;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/group2/flightres/fxml/main-shell.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        ThemeManager.getInstance().registerScene(scene);

        stage.setTitle(BrandConfig.WINDOW_TITLE);
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setResizable(true);
        stage.show();

        logStartupDbDiagnostics();
    }

    private static void logStartupDbDiagnostics() {
        Thread t = new Thread(() -> {
            try {
                AppConfig cfg = AppConfig.getInstance();
                int upcoming = new FlightService().countUpcomingFlights();
                logger.info("Sky Royal Desktop DB: {}:{}/{} | upcomingFlights={}",
                        cfg.getDbHost(), cfg.getDbPort(), cfg.getDbName(), upcoming);
                AppSession.setDbStatus(cfg.getDbHost(), cfg.getDbPort(), cfg.getDbName(), upcoming);
            } catch (Exception e) {
                logger.warn("Sky Royal Desktop DB startup check failed: {}", e.getMessage());
            }
        }, "sr-startup-db-diag");
        t.setDaemon(true);
        t.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
