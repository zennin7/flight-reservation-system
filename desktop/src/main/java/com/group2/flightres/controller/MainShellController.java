package com.group2.flightres.controller;

import com.group2.flightres.component.NavBar;
import com.group2.flightres.component.StepperBar;
import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingStep;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.AppSession;
import com.group2.flightres.util.Motion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

public class MainShellController {

    private static final Logger logger = LoggerFactory.getLogger(MainShellController.class);

    @FXML private NavBar navBar;
    @FXML private VBox stepperWrapper;
    @FXML private StepperBar stepperBar;
    @FXML private StackPane contentArea;

    private final SeatMapService seatMapService;
    private BookingContext bookingContext = new BookingContext();

    public MainShellController() {
        this(new SeatMapService());
    }

    public MainShellController(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    public void resetBookingContext() {
        if (bookingContext != null) {
            String ref = bookingContext.getBookingReference();
            if ((ref == null || ref.isBlank()) && bookingContext.getSelectedSeats() != null) {
                for (FlightSeat seat : bookingContext.getSelectedSeats()) {
                    try {
                        if (seatMapService != null) {
                            seatMapService.deselectSeat(seat);
                            logger.info("Released seat hold for seat {} (ID: {}) during context reset",
                                    seat.getSeatLabel(), seat.getFlightSeatId());
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to release seat during reset: {}", e.getMessage());
                    }
                }
            }
        }
        User user = (bookingContext != null && bookingContext.getCurrentUser() != null)
                ? bookingContext.getCurrentUser()
                : AppSession.getCurrentUser();
        this.bookingContext = new BookingContext();
        if (user != null) {
            this.bookingContext.setCurrentUser(user);
            AppSession.setCurrentUser(user);
        }
    }

    public void setBookingContext(BookingContext bookingContext) {
        this.bookingContext = bookingContext;
    }

    public void setContentArea(StackPane contentArea) {
        this.contentArea = contentArea;
    }
    private BookingStep currentStep;

    @FXML
    public void initialize() {
        if (navBar != null) {
            navBar.setNavigationHandler(this::handleNavigation);
        }
        handleNavigation("Home");
    }

    public void goToStep(BookingStep step) {
        if (step == null) return;
        currentStep = step;

        if (stepperWrapper != null) {
            boolean showStepper = (step != BookingStep.CONFIRMATION);
            stepperWrapper.setVisible(showStepper);
            stepperWrapper.setManaged(showStepper);
        }
        if (stepperBar != null) {
            stepperBar.setActiveStep(step);
        }
        if (navBar != null) {
            if (step == BookingStep.CONFIRMATION) {
                navBar.setActiveLink("My Bookings");
            } else {
                navBar.setActiveLink("Flights");
            }
        }

        loadStepContent(step);
    }

    private void loadStepContent(BookingStep step) {
        if (contentArea == null) {
            return;
        }
        String fxmlPath = step.getFxmlPath();
        URL resource = getClass().getResource(fxmlPath);
        if (resource == null) {
            logger.info("FXML file does not exist yet for step {}: {}. Content area remains empty.", step, fxmlPath);
            contentArea.getChildren().clear();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(resource);
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
            Motion.screenTransition(view);

            Object controller = loader.getController();
            if (controller instanceof WizardStepController wizardController) {
                if (step == BookingStep.CONFIRMATION) {
                    wizardController.init(bookingContext, () -> {
                        resetBookingContext();
                        handleNavigation("Flights");
                    }, () -> goToStep(step.previous()));
                } else {
                    wizardController.init(bookingContext, () -> goToStep(step.next()), () -> goToStep(step.previous()));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load wizard step: {}", step, e);
            contentArea.getChildren().clear();
        }
    }

    public void handleNavigation(String destination) {
        logger.info("Navigating to: {}", destination);
        switch (destination) {
            case "Home":
                loadOptionalScreen("/com/group2/flightres/fxml/home.fxml", false, "Home");
                break;
            case "Flights":
                resetBookingContext();
                goToStep(BookingStep.SEARCH);
                break;
            case "My Bookings":
                loadOptionalScreen("/com/group2/flightres/fxml/my-bookings.fxml", false, "My Bookings");
                break;
            case "Login":
                loadOptionalScreen("/com/group2/flightres/fxml/login.fxml", false, "Login / Sign Up");
                break;
            case "Dashboard":
                boolean isAdmin = (bookingContext != null && bookingContext.getCurrentUser() != null
                        && bookingContext.getCurrentUser().getRole() == UserRole.ADMIN);
                if (isAdmin) {
                    if (navBar != null) {
                        navBar.setAdminMode(true);
                    }
                    loadOptionalScreen("/com/group2/flightres/fxml/admin-dashboard.fxml", false, "Dashboard");
                } else {
                    logger.warn("Access denied for route 'Dashboard'. Administrator privileges required.");
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.WARNING,
                            "Access denied. Administrator privileges are required to view the dashboard.");
                    alert.setHeaderText(null);
                    alert.showAndWait();
                }
                break;
            case "Logout":
                LoginController.clearLastAuthenticatedUser();
                this.bookingContext = AppSession.signOut(bookingContext);
                if (navBar != null) {
                    navBar.setAdminMode(false);
                }
                handleNavigation("Login");
                break;
            case "Help":
                logger.info("Help clicked.");
                javafx.scene.control.Alert helpAlert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION,
                        "Need assistance with your booking? Contact " + BrandConfig.APP_NAME + " support at "
                                + BrandConfig.SUPPORT_EMAIL + " or call " + BrandConfig.SUPPORT_PHONE + ".");
                helpAlert.setHeaderText(BrandConfig.APP_NAME + " Customer Support");
                helpAlert.showAndWait();
                break;
            case "About":
                loadOptionalScreen("/com/group2/flightres/fxml/about.fxml", false, "About");
                break;
            case "Contact":
                loadOptionalScreen("/com/group2/flightres/fxml/contact.fxml", false, "Contact");
                break;
            case "Bookings":
                boolean isAdminBookings = (bookingContext != null && bookingContext.getCurrentUser() != null
                        && bookingContext.getCurrentUser().getRole() == UserRole.ADMIN);
                if (isAdminBookings) {
                    loadOptionalScreen("/com/group2/flightres/fxml/admin-bookings.fxml", false, "Bookings");
                } else {
                    logger.warn("Access denied for route 'Bookings'. Administrator privileges required.");
                }
                break;
            case "Reports":
            default:
                // No-op where screen does not exist yet
                logger.debug("Destination '{}' has no screen yet; doing nothing.", destination);
                break;
        }
    }

    private void loadOptionalScreen(String fxmlPath, boolean showStepper, String activeNav) {
        if (contentArea == null) {
            return;
        }
        URL resource = getClass().getResource(fxmlPath);
        if (resource == null) {
            logger.info("Screen FXML does not exist yet: {}. Doing nothing.", fxmlPath);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(resource);
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
            Motion.screenTransition(view);

            if (stepperWrapper != null) {
                stepperWrapper.setVisible(showStepper);
                stepperWrapper.setManaged(showStepper);
            }
            if (navBar != null && activeNav != null) {
                navBar.setActiveLink(activeNav);
            }

            Object controller = loader.getController();
            if (controller instanceof LoginController loginCtrl) {
                loginCtrl.init(bookingContext, () -> {
                    User u = bookingContext.getCurrentUser() != null
                            ? bookingContext.getCurrentUser()
                            : LoginController.getLastAuthenticatedUser();
                    if (u != null) {
                        bookingContext.setCurrentUser(u);
                        AppSession.setCurrentUser(u);
                    }
                    if (navBar != null) {
                        navBar.setAdminMode(u != null && u.getRole() == UserRole.ADMIN);
                    }
                    handleNavigation("Flights");
                });
            } else if (controller instanceof AboutController aboutCtrl) {
                aboutCtrl.init(bookingContext, () -> handleNavigation("Home"));
            } else if (controller instanceof ContactController contactCtrl) {
                contactCtrl.init(bookingContext, () -> handleNavigation("Home"));
            } else if (controller instanceof AdminDashboardController adminDashCtrl) {
                adminDashCtrl.init(bookingContext);
            } else if (controller instanceof AdminBookingsController adminBookingsCtrl) {
                adminBookingsCtrl.init(bookingContext);
            } else if (controller instanceof MyBookingsController myBookingsCtrl) {
                myBookingsCtrl.init(bookingContext, () -> handleNavigation("Flights"));
            } else if (controller instanceof WizardStepController wizardController) {
                wizardController.init(bookingContext, () -> goToStep(BookingStep.SEARCH));
            }
        } catch (IOException e) {
            logger.error("Failed to load optional screen: {}", fxmlPath, e);
        }
    }

    public BookingContext getBookingContext() {
        return bookingContext;
    }

    public BookingStep getCurrentStep() {
        return currentStep;
    }

    public NavBar getNavBar() {
        return navBar;
    }

    public StepperBar getStepperBar() {
        return stepperBar;
    }
}
