# Team Intake Specification: Checkout Summary
**Assigned Member:** Sinikiwe  
**Module Focus:** Passenger Form, Dynamic Price Breakdown & Transaction Execution

---

## 1. Mapping to Shadow Repository Files

| Shadow Component | File Path |
|---|---|
| **FXML View** | `desktop/src/main/resources/com/group2/flightres/fxml/checkout.fxml` |
| **JavaFX Controller** | `desktop/src/main/java/com/group2/flightres/controller/CheckoutController.java` |
| **Booking Service** | `core/src/main/java/com/group2/flightres/service/BookingService.java` |
| **Booking DAO** | `core/src/main/java/com/group2/flightres/dao/impl/BookingDaoImpl.java` |
| **Validation Utility**| `core/src/main/java/com/group2/flightres/util/ValidationUtil.java` |
| **Web Equivalent** | `web/src/main/resources/templates/seats.html` (Right Column) |

---

## 2. What the Teammate Must Provide

1. **Checkout Layout (`checkout.fxml`)**:
   - Passenger details inputs: `fullNameField`, `emailField`, `passportField`.
   - Dynamic price breakdown: `baseFareLabel`, `demandPricingLabel`, `taxesFeesLabel`, `totalLabel`.
   - Flight summary labels: `flightLabel`, `departsLabel`, `seatLabel`, `passengerLabel`.
   - Navigation action buttons: `confirmBookingButton` (gold) and `backButton` (grey).
2. **Controller Logic (`CheckoutController.java`)**:
   - Implements `WizardStepController` with `init(BookingContext, Runnable onNext, Runnable onBack)`.
   - Reads flight, seat, class, and price directly from `BookingContext` (no hardcoded prices).
   - Validates inputs before transaction: full name, email format, passport format.
   - Executes transactional booking via `BookingService.createBooking(bookingContext)`.
   - Handles `SeatUnavailableException` gracefully if a seat hold expired.

---

## 3. Comparison Criteria & Evaluation Rubric

- **Price Calculation Integrity**: Does the price accurately reflect the chosen seat's class multiplier and demand surcharge?
- **Validation Quality**: Are invalid emails or blank names rejected with friendly warning alerts?
- **Transactional Safety**: Does confirming a booking insert rows into `bookings`, `booking_passengers`, and `booking_seats` within one atomic transaction?
- **Context Continuity**: Does the controller write the generated PNR reference back into `BookingContext`?

---

## 4. Shadow Baseline (What We Keep If Incoming Is Weaker)

- If incoming controller hardcodes prices or seat labels, we keep the shadow's **`BookingContext` pricing pipeline**.
- If incoming code performs multiple non-atomic database inserts, we retain the shadow's **`BookingService.createBooking` ACID transaction**.
- If seat conflict handling is missing, we keep the shadow's **optimistic hold and rollback logic**.
