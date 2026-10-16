package com.group2.flightres.controller;

import com.group2.flightres.component.NavBar;
import com.group2.flightres.component.StepperBar;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingStep;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
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

    private final BookingContext bookingContext = new BookingContext();
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
            stepperWrapper.setVisible(true);
            stepperWrapper.setManaged(true);
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

            Object controller = loader.getController();
            if (controller instanceof WizardStepController wizardController) {
                wizardController.init(bookingContext, () -> goToStep(step.next()), () -> goToStep(step.previous()));
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
                goToStep(BookingStep.SEARCH);
                break;
            case "My Bookings":
                loadOptionalScreen("/com/group2/flightres/fxml/booking-confirmation.fxml", true, "My Bookings");
                break;
            case "Login":
                loadOptionalScreen("/com/group2/flightres/fxml/login.fxml", false, "Login / Sign Up");
                break;
            case "Dashboard":
                if (navBar != null) {
                    navBar.setAdminMode(true);
                }
                loadOptionalScreen("/com/group2/flightres/fxml/admin-dashboard.fxml", false, "Dashboard");
                break;
            case "Logout":
                if (navBar != null) {
                    navBar.setAdminMode(false);
                }
                handleNavigation("Home");
                break;
            case "Help":
                if (navBar != null) {
                    navBar.setAdminMode(!navBar.isAdminMode());
                    handleNavigation(navBar.isAdminMode() ? "Dashboard" : "Home");
                }
                break;
            case "About":
            case "Contact":
            case "Bookings":
            case "Reports":
            default:
                // No-op where screen does not exist yet
                logger.debug("Destination '{}' has no screen yet; doing nothing.", destination);
                break;
        }
    }

    private void loadOptionalScreen(String fxmlPath, boolean showStepper, String activeNav) {
        URL resource = getClass().getResource(fxmlPath);
        if (resource == null) {
            logger.info("Screen FXML does not exist yet: {}. Doing nothing.", fxmlPath);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(resource);
            Node view = loader.load();
            contentArea.getChildren().setAll(view);

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
                    User u = LoginController.getLastAuthenticatedUser();
                    if (u != null && u.getRole() == UserRole.ADMIN) {
                        if (navBar != null) {
                            navBar.setAdminMode(true);
                        }
                        handleNavigation("Dashboard");
                    } else {
                        handleNavigation("Home");
                    }
                });
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
