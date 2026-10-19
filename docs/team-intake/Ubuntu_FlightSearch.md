# Team Intake Specification: Flight Search Flow
**Assigned Member:** Ubuntu  
**Module Focus:** Flight Discovery, Corridor Filtering & Result Card Presentation

---

## 1. Mapping to Shadow Repository Files

| Shadow Component | File Path |
|---|---|
| **FXML View** | `desktop/src/main/resources/com/group2/flightres/fxml/flight-search.fxml` |
| **JavaFX Controller** | `desktop/src/main/java/com/group2/flightres/controller/FlightSearchController.java` |
| **Flight Service** | `core/src/main/java/com/group2/flightres/service/FlightService.java` |
| **Flight DAO** | `core/src/main/java/com/group2/flightres/dao/impl/FlightDaoImpl.java` |
| **Flight Model** | `core/src/main/java/com/group2/flightres/model/FlightSearchResult.java` |
| **Web Equivalent** | `web/src/main/resources/templates/flights.html` |

---

## 2. What the Teammate Must Provide

1. **Flight Search FXML (`flight-search.fxml`)**:
   - Filter card with `originCombo`, `destinationCombo`, `departureDatePicker`, and `searchButton`.
   - Results list container (`resultsContainer`), `resultsHeaderLabel`, and `noResultsPane` for empty state.
   - Clean magnifying glass SVGPath icon on the search button (circle + handle, not a key shape).
   - Results row cards with 18px right padding so the gold "Select" buttons are not clipped.
2. **Controller Wiring (`FlightSearchController.java`)**:
   - Implements `WizardStepController` and `Initializable`.
   - Calls `ValidationUtil.validateRoute(origin, destination)` to prevent identical origin/dest queries.
   - Calls `FlightService.searchFlights(...)` querying live PostgreSQL data.
   - Writes the chosen flight parameters to `BookingContext` and triggers `onNext.run()` to advance to the Seat Map.
3. **Empty State & Error Feedback**:
   - Graceful handling when no flights match the query (shows friendly empty state, no null pointer exceptions).

---

## 3. Comparison Criteria & Evaluation Rubric

- **Live Database Query**: Does the search query live flights from PostgreSQL based on corridor and date?
- **Card Layout & Padding**: Are the gold "Select Flight" buttons cleanly aligned with balanced 18px right margin?
- **Icon Quality**: Does the search button use the standard white magnifying glass vector?
- **Context Transmission**: Are `flightId`, `flightNumber`, route times, and `baseFare` written accurately into `BookingContext`?

---

## 4. Shadow Baseline (What We Keep If Incoming Is Weaker)

- If incoming controller has SQL queries or uses mock arrays, we retain the shadow's **`FlightService` and `FlightDaoImpl`**.
- If button clipping or misaligned card styling is present, we keep the shadow's **`flight-result-card` CSS and FXML layout**.
- If route validation is missing, we keep the shadow's **`ValidationUtil.validateRoute`** check.
