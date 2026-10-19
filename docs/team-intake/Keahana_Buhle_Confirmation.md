# Team Intake Specification: Booking Confirmation & Boarding Pass
**Assigned Members:** Keahana & Buhle  
**Module Focus:** Confirmation Voucher, PNR Reference & PDF Boarding Pass Generation

---

## 1. Mapping to Shadow Repository Files

| Shadow Component | File Path |
|---|---|
| **FXML View** | `desktop/src/main/resources/com/group2/flightres/fxml/booking-confirmation.fxml` |
| **JavaFX Controller** | `desktop/src/main/java/com/group2/flightres/controller/BookingConfirmationController.java` |
| **Booking Service** | `core/src/main/java/com/group2/flightres/service/BookingService.java` |
| **Web Equivalent** | `web/src/main/resources/templates/confirmation.html` |

---

## 2. What the Teammate Must Provide

1. **Confirmation Layout (`booking-confirmation.fxml`)**:
   - Prominent e-ticket dashed voucher card.
   - Nodes: `bookingReferenceLabel`, `flightLabel`, `departureLabel`, `passengerLabel`, `seatLabel`, `totalPaidLabel`, `statusChip`.
   - Actions: `downloadPassBtn` (primary gold) and `backToHomeBtn` (secondary).
2. **Controller Wiring (`BookingConfirmationController.java`)**:
   - Implements `WizardStepController` and `Initializable`.
   - Populates fields solely from `BookingContext` (or queries `BookingService.getBookingByReference(pnr)`).
   - Generates a PDF Boarding Pass using **OpenPDF** with:
     - Real passenger name, corridor (e.g. JNB → CPT), flight number, departure time, seat number, and booking reference.
     - Interactive `FileChooser` allowing the user to select the save path, defaulting to `BoardingPass_<REFERENCE>.pdf`.
     - Confirmation message upon successful file export.
3. **No Hardcoded Defaults**:
   - No fixed strings like "7E", "FL204", "Jane Doe", or "R 1,522" hardcoded in the controller logic.

---

## 3. Comparison Criteria & Evaluation Rubric

- **PDF Functionality**: Does clicking "Download Boarding Pass (PDF)" generate a genuine, viewable PDF file? Does canceling the FileChooser do nothing without crashing?
- **Data Fidelity**: Does the voucher reflect the exact seat and passenger selected during checkout?
- **Visual Distinction**: Is the dashed voucher card styled with gold border tokens and proper padding in both themes?
- **Stepper Progress**: Are all four steps on the StepperBar highlighted as completed with checkmarks?

---

## 4. Shadow Baseline (What We Keep If Incoming Is Weaker)

- If PDF generation is non-functional or missing, we keep the shadow's **OpenPDF boarding pass generator**.
- If incoming voucher has broken alignment or hardcoded values, we retain the shadow's **`booking-confirmation.fxml`** and **`BookingConfirmationController.java`**.
- If reset to home fails to clear booking state, we keep the shadow's **`BookingContext.reset()`** flow.
