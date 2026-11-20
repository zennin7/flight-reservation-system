ackage com.group2.flightres.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class BookingConfirmationController {

    @FXML
    private Button downloadButton;

    @FXML
    private Button homeButton;

    @FXML
    private void handleDownload() {
        // Placeholder only. No PDF is generated.
        System.out.println(
            "Download Boarding Pass clicked."
        );
        System.out.println(
            "PDF functionality will be added later."
        );
    }

    @FXML
    private void handleBackToHome() {
        // Placeholder only. No navigation occurs.
        System.out.println(
            "Back to Home clicked."
        );
        System.out.println(
            "Navigation will be added later."
        );
    }
}