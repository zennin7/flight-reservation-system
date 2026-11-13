package com.group2.flightres.util;

import com.group2.flightres.component.NavBar;
import com.group2.flightres.controller.BookingConfirmationController;
import com.group2.flightres.controller.MyBookingsController;
import com.group2.flightres.controller.WizardStepController;
import com.group2.flightres.model.BookingContext;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Stateless helper for navigating between screens in the Glass UI shell.
 */
public final class Navigator {

    private static final Logger logger = LoggerFactory.getLogger(Navigator.class);

    public static final String MY_BOOKINGS_FXML = "/com/group2/flightres/fxml/my-bookings.fxml";
    public static final String FLIGHT_SEARCH_FXML = "/com/group2/flightres/fxml/flight-search.fxml";
    public static final String CONFIRMATION_FXML = "/com/group2/flightres/fxml/booking-confirmation.fxml";
    public static final String HOME_FXML = "/com/group2/flightres/fxml/home.fxml";

    private Navigator() {}

    public static void navigateToMyBookings(Node sourceNode, BookingContext context) {
        navigate(sourceNode, MY_BOOKINGS_FXML, false, "My Bookings", context);
    }

    public static void navigateToSearch(Node sourceNode, BookingContext context) {
        navigate(sourceNode, FLIGHT_SEARCH_FXML, true, "Flights", context);
    }

    public static void navigateToHome(Node sourceNode, BookingContext context) {
        navigate(sourceNode, HOME_FXML, false, "Home", context);
    }

    public static void navigateToConfirmation(Node sourceNode, BookingContext context) {
        navigate(sourceNode, CONFIRMATION_FXML, false, "My Bookings", context);
    }

    public static void navigate(Node sourceNode, String fxmlPath, boolean showStepper, String activeNav, BookingContext context) {
        if (sourceNode == null) {
            logger.warn("Cannot navigate: sourceNode is null");
            return;
        }
        Scene scene = sourceNode.getScene();
        if (scene == null) {
            logger.warn("Cannot navigate: sourceNode has no attached Scene");
            return;
        }

        URL resource = Navigator.class.getResource(fxmlPath);
        if (resource == null) {
            logger.error("FXML resource not found for navigation: {}", fxmlPath);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(resource);
            Parent view = loader.load();

            StackPane contentArea = (StackPane) scene.lookup("#contentArea");
            if (contentArea != null) {
                contentArea.getChildren().setAll(view);
                Motion.screenTransition(view);

                VBox stepperWrapper = (VBox) scene.lookup("#stepperWrapper");
                if (stepperWrapper != null) {
                    stepperWrapper.setVisible(showStepper);
                    stepperWrapper.setManaged(showStepper);
                }

                NavBar nav = (NavBar) scene.lookup("#navBar");
                if (nav != null && activeNav != null) {
                    nav.setActiveLink(activeNav);
                }
            } else {
                scene.setRoot(view);
                Motion.screenTransition(view);
            }

            Object controller = loader.getController();
            if (controller instanceof MyBookingsController myBookingsCtrl) {
                myBookingsCtrl.init(context, () -> navigateToSearch(view, context));
            } else if (controller instanceof BookingConfirmationController confirmCtrl) {
                confirmCtrl.init(context, () -> navigateToHome(view, context));
            } else if (controller instanceof WizardStepController wzCtrl) {
                wzCtrl.init(context, () -> navigateToHome(view, context));
            }
        } catch (IOException e) {
            logger.error("Failed to navigate to {}: {}", fxmlPath, e.getMessage(), e);
        }
    }
}
