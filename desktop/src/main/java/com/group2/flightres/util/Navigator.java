package com.group2.flightres.util;

import com.group2.flightres.component.NavBar;
import com.group2.flightres.controller.AboutController;
import com.group2.flightres.controller.AdminBookingsController;
import com.group2.flightres.controller.AdminDashboardController;
import com.group2.flightres.controller.BookingConfirmationController;
import com.group2.flightres.controller.CheckoutController;
import com.group2.flightres.controller.ContactController;
import com.group2.flightres.controller.FlightSearchController;
import com.group2.flightres.controller.HomeController;
import com.group2.flightres.controller.LoginController;
import com.group2.flightres.controller.MyBookingsController;
import com.group2.flightres.controller.SeatMapController;
import com.group2.flightres.controller.WizardStepController;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
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
 * Navigation helper for the Sky Royal desktop Glass UI shell.
 * Preserves the authenticated user via {@link AppSession} across every screen transition
 * and wires every screen's forward/back flow cleanly.
 */
public final class Navigator {

    private static final Logger logger = LoggerFactory.getLogger(Navigator.class);

    public static final String MY_BOOKINGS_FXML = "/com/group2/flightres/fxml/my-bookings.fxml";
    public static final String FLIGHT_SEARCH_FXML = "/com/group2/flightres/fxml/flight-search.fxml";
    public static final String SEAT_MAP_FXML = "/com/group2/flightres/fxml/seat-map.fxml";
    public static final String CHECKOUT_FXML = "/com/group2/flightres/fxml/checkout.fxml";
    public static final String CONFIRMATION_FXML = "/com/group2/flightres/fxml/booking-confirmation.fxml";
    public static final String HOME_FXML = "/com/group2/flightres/fxml/home.fxml";
    public static final String LOGIN_FXML = "/com/group2/flightres/fxml/login.fxml";
    public static final String ADMIN_DASHBOARD_FXML = "/com/group2/flightres/fxml/admin-dashboard.fxml";
    public static final String ADMIN_BOOKINGS_FXML = "/com/group2/flightres/fxml/admin-bookings.fxml";

    private Navigator() {}

    public static void navigateToMyBookings(Node sourceNode, BookingContext context) {
        navigate(sourceNode, MY_BOOKINGS_FXML, false, "My Bookings", context);
    }

    public static void navigateToAdminDashboard(Node sourceNode, BookingContext context) {
        navigate(sourceNode, ADMIN_DASHBOARD_FXML, false, "Dashboard", context);
    }

    public static void navigateToAdminBookings(Node sourceNode, BookingContext context) {
        navigate(sourceNode, ADMIN_BOOKINGS_FXML, false, "Bookings", context);
    }

    public static void navigateToSearch(Node sourceNode, BookingContext context) {
        navigate(sourceNode, FLIGHT_SEARCH_FXML, true, "Flights", context);
    }

    public static void navigateToSeatMap(Node sourceNode, BookingContext context) {
        navigate(sourceNode, SEAT_MAP_FXML, true, "Flights", context);
    }

    public static void navigateToCheckout(Node sourceNode, BookingContext context) {
        navigate(sourceNode, CHECKOUT_FXML, true, "Flights", context);
    }

    public static void navigateToHome(Node sourceNode, BookingContext context) {
        navigate(sourceNode, HOME_FXML, false, "Home", context);
    }

    public static void navigateToLogin(Node sourceNode, BookingContext context) {
        navigate(sourceNode, LOGIN_FXML, false, "Login", context);
    }

    public static void navigateToConfirmation(Node sourceNode, BookingContext context) {
        navigate(sourceNode, CONFIRMATION_FXML, false, "My Bookings", context);
    }

    /**
     * Signs out the current user, clears all session and booking data, and navigates to the login screen.
     */
    public static void signOut(Node sourceNode, BookingContext context) {
        LoginController.clearLastAuthenticatedUser();
        BookingContext freshContext = AppSession.signOut(context);
        if (sourceNode != null && sourceNode.getScene() != null) {
            NavBar nav = (NavBar) sourceNode.getScene().lookup("#navBar");
            if (nav != null) {
                nav.setAdminMode(false);
            }
        }
        navigate(sourceNode, LOGIN_FXML, false, "Login", freshContext);
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

        long t0 = System.nanoTime();
        BookingContext effectiveContext = AppSession.ensureUser(context);

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
                if (nav != null) {
                    User u = effectiveContext.getCurrentUser();
                    nav.setAdminMode(u != null && u.getRole() == UserRole.ADMIN);
                    if (activeNav != null) {
                        nav.setActiveLink(activeNav);
                    }
                }
            } else {
                scene.setRoot(view);
                Motion.screenTransition(view);
            }

            Object controller = loader.getController();
            wireController(controller, view, effectiveContext);

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            logger.info("[TIMING] Screen transition {} took {} ms", activeNav != null ? activeNav : fxmlPath, elapsedMs);
        } catch (IOException e) {
            logger.error("Failed to navigate to {}: {}", fxmlPath, e.getMessage(), e);
        }
    }

    private static void wireController(Object controller, Parent view, BookingContext context) {
        if (controller instanceof HomeController homeCtrl) {
            homeCtrl.init(context, () -> navigateToSearch(view, context));
        } else if (controller instanceof FlightSearchController searchCtrl) {
            searchCtrl.init(context, () -> navigateToSeatMap(view, context));
        } else if (controller instanceof SeatMapController seatCtrl) {
            seatCtrl.init(context,
                    () -> navigateToCheckout(view, context),
                    () -> navigateToSearch(view, context));
        } else if (controller instanceof CheckoutController checkoutCtrl) {
            checkoutCtrl.init(context,
                    () -> navigateToConfirmation(view, context),
                    () -> navigateToSeatMap(view, context));
        } else if (controller instanceof BookingConfirmationController confirmCtrl) {
            confirmCtrl.init(context, () -> navigateToHome(view, context));
        } else if (controller instanceof MyBookingsController myBookingsCtrl) {
            myBookingsCtrl.init(context, () -> navigateToSearch(view, context));
        } else if (controller instanceof LoginController loginCtrl) {
            loginCtrl.init(context, () -> navigateToSearch(view, context));
        } else if (controller instanceof AdminDashboardController adminDashCtrl) {
            adminDashCtrl.init(context);
        } else if (controller instanceof AdminBookingsController adminBookingsCtrl) {
            adminBookingsCtrl.init(context);
        } else if (controller instanceof AboutController aboutCtrl) {
            aboutCtrl.init(context, () -> navigateToHome(view, context));
        } else if (controller instanceof ContactController contactCtrl) {
            contactCtrl.init(context, () -> navigateToHome(view, context));
        } else if (controller instanceof WizardStepController wzCtrl) {
            wzCtrl.init(context, () -> navigateToHome(view, context));
        }
    }
}
