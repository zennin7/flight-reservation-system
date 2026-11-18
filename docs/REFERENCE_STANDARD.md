# 📐 SkyVoyage Desktop Reference Standard

This document serves as the **contract and architectural reference standard** for the SkyVoyage flight reservation desktop application. Any teammate's screen can be dropped into the application seamlessly by complying with the specifications, conventions, and interfaces defined here.

---

## 🏛️ General Architectural & Code Rules

1. **Strict MVC / DAO Separation**:
   - **Controllers**: Handle UI events, form validation, and view binding only. **Zero SQL queries, zero JDBC connections, and zero direct database calls** are permitted in controllers.
   - **Services (`core`)**: Own all domain logic, transactions, pricing calculations, authentication rules, and input validations.
   - **DAOs (`core`)**: The only layer executing SQL PreparedStatements against PostgreSQL.
   - **Core Layer Integrity**: The `core` module must remain 100% free of `javafx.*` imports to allow headless execution, web server re-use, and isolated unit testing.
2. **Central Brand Authority**:
   - Never hardcode the brand name (`"SkyVoyage"`), support email, phone numbers, or window title as string literals in Java code or FXML.
   - Always reference `com.group2.flightres.config.BrandConfig`:
     - `BrandConfig.APP_NAME` ("SkyVoyage")
     - `BrandConfig.TAGLINE` ("Fly in Comfort across South Africa")
     - `BrandConfig.SUPPORT_EMAIL` ("support@skyvoyage.com")
     - `BrandConfig.SUPPORT_PHONE` ("0800 123 456")
3. **Design Tokens & Theme Compliance**:
   - Never hardcode hex color values (`Color.web("#...")` or `-fx-background-color: #...`) in FXML or Java controllers.
   - Use CSS classes defined in `skyvoyage.css` and token variables from `theme-light.css` and `theme-dark.css`:
     - `-sv-navy-dark`, `-sv-gold-primary`, `-sv-bg-primary`, `-sv-card-bg`, `-sv-text`, `-sv-muted`, `-sv-border`
   - Every screen must render correctly in both **Light** and **Dark** themes.
4. **Logging & Diagnostic Integrity**:
   - No `System.out.println` or `System.err.println` in production application code.
   - Use SLF4J logger: `private static final Logger logger = LoggerFactory.getLogger(YourController.class);`.

---

## 🖥️ The 7 Desktop Screens Reference

```
MainShell (NavBar + StepperBar)
 ├── 01. Home (home.fxml)
 ├── 02. Login (login.fxml)
 ├── 03. Flight Search (flight-search.fxml)   ──► Step 1: SEARCH
 ├── 04. Seat Map (seat-map.fxml)             ──► Step 2: SEAT_MAP
 ├── 05. Checkout (checkout.fxml)             ──► Step 3: CHECKOUT
 ├── 06. Confirmation (confirmation.fxml)     ──► Step 4: CONFIRMATION
 └── 07. Admin Dashboard (admin-dashboard.fxml)
```

---

### 1. Home Screen

- **FXML File:** `com/group2/flightres/fxml/home.fxml`
- **Controller Class:** `com.group2.flightres.controller.HomeController` (implements `Initializable`)
- **fx:id Inventory:**
  | fx:id | Node Type | Purpose |
  |---|---|---|
  | `heroTitle` | `Label` | Main headline ("Fly beyond the ordinary.") |
  | `heroSubtitle` | `Label` | Subheading explaining premium domestic travel |
  | `originCombo` | `ComboBox<String>` | Origin city dropdown (e.g. "Johannesburg (JNB)") |
  | `destinationCombo` | `ComboBox<String>` | Destination city dropdown (e.g. "Cape Town (CPT)") |
  | `departureDatePicker` | `DatePicker` | Travel date picker |
  | `searchFlightsBtn` | `Button` | Search CTA with gold styling and magnifying glass SVG |
  | `cardDest1` | `VBox` | Cape Town destination photo card |
  | `cardDest2` | `VBox` | Durban destination photo card |
  | `cardDest3` | `VBox` | Johannesburg destination photo card |
- **Core Service Calls:**
  - `ValidationUtil.validateRoute(String origin, String destination)`
- **Shared CSS Classes & Components:**
  - Classes: `home-root`, `hero-section`, `hero-badge`, `search-card`, `search-form-grid`, `btn-gold`, `destination-card`, `destination-meta`
  - Motion: `Motion.attachHoverLift(cardDest1)`, `Motion.attachHoverLift(searchFlightsBtn)`
- **BookingContext Interaction:**
  - **Writes:** `context.setOrigin(...)`, `context.setDestination(...)`, `context.setDepartureDate(...)`
- **Navigation:**
  - Calls `wizardNavigation.run()` or `MainShellController.goToStep(BookingStep.SEARCH)` when search is initiated.

---

### 2. Login Screen

- **FXML File:** `com/group2/flightres/fxml/login.fxml`
- **Controller Class:** `com.group2.flightres.controller.LoginController` (implements `WizardStepController`)
- **fx:id Inventory:**
  | fx:id | Node Type | Purpose |
  |---|---|---|
  | `usernameField` | `TextField` | Input for username or account email |
  | `passwordField` | `PasswordField` | Input for salted password |
  | `errorLabel` | `Label` | Red error notification (remains hidden until a failed login) |
  | `loginButton` | `Button` | Submit credentials button |
  | `demoCredentialsLabel` | `Label` | Subtle guidance showing demo credentials |
  | `heroBadge` | `Label` | Left-side photographic badge ("Seamless Travel") |
- **Core Service Calls:**
  ```java
  AuthService.authenticate(String username, String password) throws AuthenticationException -> User
  ```
- **Shared CSS Classes & Components:**
  - Classes: `login-root`, `login-split-container`, `login-hero-pane`, `login-form-pane`, `login-card`, `login-title`, `form-input`, `btn-gold`, `login-error-text`
- **BookingContext Interaction:**
  - **Writes:** `context.setCurrentUser(authenticatedUser)`
- **Navigation:**
  - On authentication success: checks `user.getRole() == UserRole.ADMIN`. If admin, calls `mainShell.handleNavigation("Dashboard")`; otherwise returns to previous screen or `"Home"`.

---

### 3. Flight Search Screen (Wizard Step 1: SEARCH)

- **FXML File:** `com/group2/flightres/fxml/flight-search.fxml`
- **Controller Class:** `com.group2.flightres.controller.FlightSearchController` (implements `WizardStepController`, `Initializable`)
- **fx:id Inventory:**
  | fx:id | Node Type | Purpose |
  |---|---|---|
  | `originCombo` | `ComboBox<String>` | Departure airport filter |
  | `destinationCombo` | `ComboBox<String>` | Arrival airport filter |
  | `departureDatePicker` | `DatePicker` | Date filter |
  | `searchButton` | `Button` | Execute search query button |
  | `resultsHeaderLabel` | `Label` | Shows corridor and count ("Available Flights: JNB → CPT") |
  | `resultsContainer` | `VBox` | Container for dynamically injected flight result rows |
  | `noResultsPane` | `VBox` | Empty state displayed when 0 flights match criteria |
  | `errorLabel` | `Label` | Validation alert for matching origin/destination |
- **Core Service Calls:**
  ```java
  ValidationUtil.validateRoute(String origin, String destination) throws ValidationException
  FlightService.searchFlights(String origin, String destination, LocalDate departureDate) -> List<FlightSearchResult>
  ```
- **Shared CSS Classes & Components:**
  - Classes: `flight-search-root`, `search-card`, `flight-result-card`, `flight-num`, `flight-route-segment`, `flight-price-amount`, `btn-gold`
  - Stepper: Registered as `BookingStep.SEARCH` (Active: Step 1)
- **BookingContext Interaction:**
  - **Reads:** `origin`, `destination`, `departureDate`
  - **Writes:** `flightId`, `flightNumber`, `origin`, `destination`, `departureDate`, `departureTime`, `arrivalTime`, `baseFare`
- **Navigation:**
  - Clicking "Select Flight →" triggers `onNext.run()` transitioning to `BookingStep.SEAT_MAP`.

---

### 4. Seat Map Screen (Wizard Step 2: SEAT_MAP)

- **FXML File:** `com/group2/flightres/fxml/seat-map.fxml`
- **Controller Class:** `com.group2.flightres.controller.SeatMapController` (implements `WizardStepController`, `Initializable`)
- **fx:id Inventory:**
  | fx:id | Node Type | Purpose |
  |---|---|---|
  | `flightTitleLabel` | `Label` | Flight route and schedule summary |
  | `cabinContainer` | `VBox` | Main fuselage cabin scroll/view |
  | `firstClassRows` | `VBox` | Rows 1–2 container (2×2 seating configuration) |
  | `economyRows` | `VBox` | Rows 6–11 container (3×3 seating configuration) |
  | `selectionSummaryPill` | `HBox` | Bottom floating pill showing selected seat & price |
  | `selectedSeatLabel` | `Label` | Label in summary pill ("Seat 6A · First Class") |
  | `totalPriceLabel` | `Label` | Dynamic total price formatted in ZAR |
  | `continueButton` | `Button` | Proceed to checkout button |
  | `backButton` | `Button` | Return to flight search button |
- **Core Service Calls:**
  ```java
  SeatMapService.loadSeatMap(long flightId) -> List<FlightSeat>
  SeatMapService.holdSeat(long flightSeatId) -> boolean
  SeatMapService.deselectSeat(FlightSeat seat)
  ```
- **Shared CSS Classes & Components:**
  - Classes: `seat-map-root`, `fuselage-card`, `cockpit-nose`, `seat-btn`, `seat-available`, `seat-selected`, `seat-taken`, `seat-legend`, `selection-summary-pill`, `btn-gold`
  - Stepper: Active Step 2
- **BookingContext Interaction:**
  - **Reads:** `flightId`, `flightNumber`, `baseFare`
  - **Writes:** `selectedSeats` (`List<FlightSeat>`), `seatLabel`, `seatClass`, `classMultiplier`, `demandPricing`, `taxesAndFees`, `totalPrice`
- **Navigation:**
  - `continueButton` verifies an active seat hold and invokes `onNext.run()` (`BookingStep.CHECKOUT`).
  - `backButton` invokes `onBack.run()` (`BookingStep.SEARCH`).

---

### 5. Checkout Screen (Wizard Step 3: CHECKOUT)

- **FXML File:** `com/group2/flightres/fxml/checkout.fxml`
- **Controller Class:** `com.group2.flightres.controller.CheckoutController` (implements `WizardStepController`)
- **fx:id Inventory:**
  | fx:id | Node Type | Purpose |
  |---|---|---|
  | `flightLabel` | `Label` | Flight number and corridor |
  | `departsLabel` | `Label` | Departure date and departure time |
  | `passengerLabel` | `Label` | Live passenger name reflection |
  | `seatLabel` | `Label` | Chosen seat label and cabin class |
  | `baseFareLabel` | `Label` | Base fare amount formatted |
  | `demandPricingLabel` | `Label` | Demand/peak adjustment amount |
  | `taxesFeesLabel` | `Label` | Airport security & service fees |
  | `totalLabel` | `Label` | Final total payable amount |
  | `fullNameField` | `TextField` | Passenger legal full name input |
  | `emailField` | `TextField` | Passenger contact email input |
  | `passportField` | `TextField` | Passport or national ID number input |
  | `confirmBookingButton`| `Button` | Authorize booking transaction button |
  | `backButton` | `Button` | Return to seat map button |
- **Core Service Calls:**
  ```java
  ValidationUtil.validatePassengerName(String fullName) throws ValidationException
  ValidationUtil.validateEmail(String email) throws ValidationException
  ValidationUtil.validatePassportNumber(String passport) throws ValidationException
  BookingService.createBooking(BookingContext context) throws SeatUnavailableException -> BookingRecord
  ```
- **Shared CSS Classes & Components:**
  - Classes: `checkout-root`, `checkout-card`, `price-summary-box`, `price-row`, `price-total`, `form-input`, `btn-gold`, `btn-grey`
  - Stepper: Active Step 3
- **BookingContext Interaction:**
  - **Reads:** `flightId`, `flightNumber`, `origin`, `destination`, `departureTime`, `arrivalTime`, `selectedSeats`, `seatLabel`, `totalPrice`
  - **Writes:** `passengerName`, `passengerEmail`, `passportNumber`, `bookingReference`
- **Navigation:**
  - On successful transaction: stores generated PNR into `bookingContext` and triggers `onNext.run()` (`BookingStep.CONFIRMATION`).

---

### 6. Booking Confirmation Screen (Wizard Step 4: CONFIRMATION)

- **FXML File:** `com/group2/flightres/fxml/booking-confirmation.fxml`
- **Controller Class:** `com.group2.flightres.controller.BookingConfirmationController` (implements `WizardStepController`, `Initializable`)
- **fx:id Inventory:**
  | fx:id | Node Type | Purpose |
  |---|---|---|
  | `bookingReferenceLabel` | `Label` | Prominent gold PNR display (e.g. `CQ67ZP`) |
  | `flightLabel` | `Label` | Confirmed flight number and corridor |
  | `departureLabel` | `Label` | Confirmed departure date and time |
  | `passengerLabel` | `Label` | Registered passenger name |
  | `seatLabel` | `Label` | Allocated cabin seat |
  | `totalPaidLabel` | `Label` | Total amount paid in ZAR |
  | `statusChip` | `Label` | Status chip styled in green ("CONFIRMED") |
  | `downloadPassBtn` | `Button` | Generates and exports OpenPDF boarding pass |
  | `backToHomeBtn` | `Button` | Returns to home landing screen |
- **Core Service Calls:**
  ```java
  BookingService.getBookingByReference(String reference) -> Optional<BookingRecord>
  ```
  - OpenPDF document creation with passenger, corridor, seat, barcode, and booking reference.
- **Shared CSS Classes & Components:**
  - Classes: `confirmation-root`, `confirmation-card`, `confirm-check-circle`, `voucher-box`, `voucher-item-label`, `voucher-item-val`, `btn-gold`, `btn-grey`
  - Stepper: All 4 steps marked completed (checkmark icons)
- **BookingContext Interaction:**
  - **Reads:** `bookingReference`, `passengerName`, `flightNumber`, `origin`, `destination`, `departureDate`, `departureTime`, `seatLabel`, `totalPrice`
- **Navigation:**
  - `backToHomeBtn` calls `onBack.run()` or resets wizard back to `"Home"`.

---

### 7. Admin Dashboard Screen

- **FXML File:** `com/group2/flightres/fxml/admin-dashboard.fxml`
- **Controller Class:** `com.group2.flightres.controller.AdminDashboardController` (implements `Initializable`)
- **fx:id Inventory:**
  | fx:id | Node Type | Purpose |
  |---|---|---|
  | `totalFlightsLabel` | `Label` | Aggregated count of scheduled flights |
  | `totalBookingsLabel` | `Label` | Aggregated count of confirmed bookings |
  | `revenueLabel` | `Label` | Aggregated confirmed revenue in ZAR |
  | `seatsSoldLabel` | `Label` | Fleet-wide percentage of booked seats |
  | `bookingTrendsChart`| `BarChart<String, Number>` | Bar chart of bookings per day over the last 7 days |
  | `refreshButton` | `Button` | Triggers immediate re-query of platform KPIs |
  | `cardFlights` | `VBox` | Stat card for total flights |
  | `cardBookings` | `VBox` | Stat card for total bookings |
  | `cardRevenue` | `VBox` | Stat card for revenue |
  | `cardSeats` | `VBox` | Stat card for seats sold load factor |
  | `cardChart` | `VBox` | Chart card container |
- **Core Service Calls:**
  ```java
  DashboardStatsService.getStats() -> DashboardStats
  ```
- **Shared CSS Classes & Components:**
  - Classes: `admin-dashboard-root`, `admin-dashboard-title`, `admin-stat-row`, `stat-card`, `stat-card-title`, `stat-card-value`, `chart-card`, `admin-bar-chart`, `btn-grey`
  - Motion: `Motion.countUp(...)` for numeric KPIs, `Motion.attachHoverLift(...)` for cards.
- **BookingContext Interaction:**
  - **Security Gate:** Requires `bookingContext.getCurrentUser().getRole() == UserRole.ADMIN`.
- **Navigation:**
  - Independent administrative screen navigated via `NavBar` admin button. Supports on-demand `refresh()` without leaving the screen.

---

## 📦 Package and Naming Rules

| Component Type | Directory / Package | Naming Pattern | Example |
|---|---|---|---|
| **FXML Views** | `desktop/src/main/resources/com/group2/flightres/fxml/` | Lowercase with hyphens (`kebab-case`) | `flight-search.fxml` |
| **JavaFX Controllers** | `desktop/src/main/java/com/group2/flightres/controller/` | PascalCase ending with `Controller` | `FlightSearchController.java` |
| **Core Models** | `core/src/main/java/com/group2/flightres/model/` | PascalCase domain entity noun | `FlightSeat.java`, `BookingRecord.java` |
| **Service Interfaces/Impls** | `core/src/main/java/com/group2/flightres/service/` | PascalCase ending with `Service` | `BookingService.java` |
| **DAO Interfaces** | `core/src/main/java/com/group2/flightres/dao/` | PascalCase ending with `Dao` | `BookingDao.java` |
| **DAO Implementations** | `core/src/main/java/com/group2/flightres/dao/impl/` | Interface name + `Impl` | `BookingDaoImpl.java` |
| **CSS Stylesheets** | `desktop/src/main/resources/com/group2/flightres/css/` | Lowercase with hyphens | `skyvoyage.css`, `theme-light.css` |

---

## 🛡️ Error Handling Style

1. **Validation Failures**:
   - Validation exceptions (`ValidationException`) thrown from `ValidationUtil` are caught by the controller.
   - Display a clean JavaFX warning alert:
     ```java
     Alert alert = new Alert(Alert.AlertType.WARNING, ve.getMessage());
     alert.setHeaderText("Validation Error");
     alert.showAndWait();
     ```
2. **Business & Concurrency Exceptions**:
   - If an optimistic hold or booking fails because another customer took the seat (`SeatUnavailableException`), display an informative alert and return the user to the seat map to pick another seat.
3. **Database & Connection Errors**:
   - Catch `Exception`, log the root cause via SLF4J (`logger.error(...)`), and present an alert asking the user to verify database connectivity.
   - **Never print raw stack traces to the user interface.**

---

## 🚀 How to Add a New Screen in 5 Steps

1. **Create the FXML File**:
   - Save your layout in `desktop/src/main/resources/com/group2/flightres/fxml/<screen-name>.fxml`.
   - Use root style classes like `<screen-name>-root` and standard tokens from `skyvoyage.css`.
2. **Implement the Controller**:
   - Create `com.group2.flightres.controller.<ScreenName>Controller.java`.
   - Implement `WizardStepController` if the screen is part of the sequential booking wizard, or standard JavaFX `Initializable`.
3. **Wire Core Services**:
   - Instantiate or inject the required service from `com.group2.flightres.service` (e.g. `FlightService`, `BookingService`).
   - Call only service methods; do not write SQL or instantiate DAOs directly in controllers.
4. **Register in `MainShellController` / Navigation**:
   - If it is a wizard step, add an entry to `BookingStep` enum.
   - If it is an independent screen (e.g. Profile, Reports), add a route case in `MainShellController.handleNavigation(String destination)`.
5. **Verify Themes & Motion**:
   - Add hover lift animations using `Motion.attachHoverLift(node)`.
   - Run the application in both Light Mode and Dark Mode (`ThemeManager`) to ensure all text and borders have proper contrast.
