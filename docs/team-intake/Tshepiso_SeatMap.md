# Team Intake Specification: Interactive Seat Map & Concurrency
**Assigned Member:** Tshepiso  
**Module Focus:** Interactive Cabin Fuselage, Real-Time Seat Status & Optimistic Concurrency Locking

---

## 1. Mapping to Shadow Repository Files

| Shadow Component | File Path |
|---|---|
| **FXML View** | `desktop/src/main/resources/com/group2/flightres/fxml/seat-map.fxml` |
| **JavaFX Controller** | `desktop/src/main/java/com/group2/flightres/controller/SeatMapController.java` |
| **Fallback Cabin Generator** | `desktop/src/main/java/com/group2/flightres/controller/SeatMapFallback.java` |
| **Seat Map Service** | `core/src/main/java/com/group2/flightres/service/SeatMapService.java` |
| **Seat DAO** | `core/src/main/java/com/group2/flightres/dao/impl/SeatDaoImpl.java` |
| **Concurrency Exception** | `core/src/main/java/com/group2/flightres/exception/SeatUnavailableException.java` |
| **Web Equivalent** | `web/src/main/resources/templates/seats.html` |

---

## 2. What the Teammate Must Provide

1. **Fuselage & Seat Map Layout (`seat-map.fxml`)**:
   - Visual cabin representation with First Class (2×2 layout) and Economy Class (3×3 layout).
   - Seat nodes styled with visual cues for Available, Held, and Booked states.
   - Dynamic selection summary pill displaying selected seat number, cabin class, and calculated price.
   - Navigation actions: "Continue to Checkout" (disabled until a seat is selected) and "Back to Flights".
2. **Controller Wiring (`SeatMapController.java`)**:
   - Implements `WizardStepController` and `Initializable`.
   - Queries `SeatMapService.getAvailableSeatsForFlight(flightId)` to render live PostgreSQL seat availability.
   - Enforces optimistic concurrency locking via `SeatMapService.holdSeat(flightSeatId)`.
   - Handles `SeatUnavailableException` gracefully by displaying an alert and prompting the user to select another seat.
   - Populates selected seat data into `BookingContext`.
3. **No Direct SQL in Controller**:
   - All database updates must delegate cleanly through `SeatMapService` and `SeatDaoImpl`.

---

## 3. Comparison Criteria & Evaluation Rubric

- **Concurrency Protection**: Does the seat selection execute atomic optimistic holds (`UPDATE flight_seats SET status='HELD' ...`)?
- **Data Integrity**: Are seat pricing tiers calculated dynamically from `seat_pricing_view` without hardcoded prices?
- **Visual Distinction**: Are First Class and Economy seats visually differentiated with proper spacing and aisle gaps?
- **Theme Adaptation**: Does the cabin map maintain high contrast and legibility across both Light and Dark modes?

---

## 4. Shadow Baseline (What We Keep If Incoming Is Weaker)

- If incoming code lacks concurrency locking or allows double-booking race conditions, we keep the shadow's **`SeatMapService.holdSeat()`** and **`SeatHoldRaceTest`** implementation.
- If incoming cabin UI has broken grid alignment or hardcoded seat arrays, we retain the shadow's **`seat-map.fxml`** and **`SeatMapController.java`**.
- If offline database handling causes unhandled null pointers, we keep the shadow's **`SeatMapFallback`** mechanism.
