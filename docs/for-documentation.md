# SkyVoyage Flight Reservation System — Documentation & Architecture Pack

> **System Name:** SkyVoyage  
> **Tagline:** Fly Beyond the Ordinary  
> **Architecture:** Modular Multi-tier Java Application (Core Engine, JavaFX Desktop Client, Javalin Server-Rendered Web Portal)  
> **Database:** PostgreSQL with HikariCP Connection Pooling  
> **Target Audience:** Group 2 Team, Course Lecturers, and Technical Reviewers

---

## 1. Executive Summary & Overview

SkyVoyage is an enterprise-grade commercial airline reservation platform providing dual-client access to a unified PostgreSQL database:
1. **Desktop Client:** A hardware-accelerated JavaFX 21 desktop application tailored for airline ticketing agents and administrative supervisors, featuring live visual cabin seat maps, real-time demand pricing, animated stat charts, and printable PDF boarding pass vouchers.
2. **Web Portal:** A lightweight, server-rendered web application built on Javalin 6 and Thymeleaf with responsive CSS grid styling, zero client-side JavaScript frameworks, complete mobile and desktop responsiveness, and automatic light/dark theme detection.
3. **Core Shared Engine:** A shared Java library containing domain entities, transactional business services, Data Access Objects (DAOs), BCrypt credential security, and OpenPDF document generation, ensuring zero business logic duplication between desktop and web.

---

## 2. Technology Stack & Exact Versions

| Component / Layer | Technology | Exact Version | Purpose & Rationale |
| :--- | :--- | :--- | :--- |
| **Java Platform** | OpenJDK | Java 17 LTS / 21 LTS | Long-term support Java platform (compiled for release 17) |
| **Build System** | Apache Maven | 3.9.9 | Multi-module reactor build system managing dependencies & plugins |
| **Desktop GUI** | OpenJFX (JavaFX) | 21.0.3 | High-performance desktop UI with CSS stylesheets & FXML views |
| **Web Server** | Javalin | 6.1.3 | Lightweight, embedded Jetty-backed web framework |
| **Template Engine** | Thymeleaf | 3.1.2.RELEASE | Server-rendered HTML5 templates with clean placeholder markup |
| **Database** | PostgreSQL | 18 (Driver: 42.7.4) | ACID-compliant relational database with enum types & foreign key cascades |
| **Connection Pool** | HikariCP | 5.1.0 | High-performance JDBC connection pool managing active DB sessions |
| **PDF Generation** | OpenPDF (LibrePDF) | 2.0.3 / 1.3.42 | Open-source iText fork for generating airline boarding pass documents |
| **Password Hashing** | jBCrypt | 0.4 | Salted Blowfish password hashing (work factor 12) |
| **Configuration** | Jackson Databind | 2.17.2 | Type-safe JSON parsing for `app.json` configuration |
| **Logging Framework** | SLF4J + Logback | 2.0.16 / 1.5.8 | Standard facade and high-speed logging implementation |
| **Testing Framework** | JUnit Jupiter (JUnit 5) | 5.11.0 | Unit, integration, concurrency race, and transaction test suites |
| **Compiler Plugin** | maven-compiler-plugin | 3.13.0 | Target release 17 compiler |
| **Surefire Plugin** | maven-surefire-plugin | 3.3.1 | Automated test runner during `mvn test` and `mvn verify` |
| **JavaFX Plugin** | javafx-maven-plugin | 0.0.8 | Direct CLI execution for JavaFX runtime (`mvn -pl desktop javafx:run`) |

---

## 3. Module Tree & Folder Structure

```
flight-reservation-system/
├── pom.xml                               # Parent POM (packaging: pom, shared versions)
├── app.json.example                      # Database configuration template
├── README.md                             # Repository setup & execution guide
├── core/                                 # Shared Domain, DAO, Service & Utility Engine
│   ├── pom.xml
│   ├── sql/
│   │   ├── schema.sql                    # Full PostgreSQL schema with enums & indexes
│   │   ├── seed_auth_data.sql            # Initial administrative & customer users
│   │   └── seed_flights_data.sql         # Real airports, aircraft & scheduled flights
│   └── src/
│       ├── main/java/com/group2/flightres/
│       │   ├── config/                   # AppConfig, BrandConfig, DatabaseConfig
│       │   ├── dao/                      # DAO Interfaces (FlightDao, SeatDao, BookingDao, etc.)
│       │   │   └── impl/                 # JDBC PreparedStatements Implementations
│       │   ├── exception/                # AuthenticationException, SeatUnavailableException, etc.
│       │   ├── model/                    # BookingContext, FlightSeat, User, DashboardStats, etc.
│       │   ├── service/                  # FlightService, SeatMapService, BookingService, AuthService
│       │   └── util/                     # BoardingPassPdfGenerator, PasswordUtil, ValidationUtil
│       └── test/java/com/group2/flightres/ # 24 automated unit & integration tests
├── desktop/                              # JavaFX Desktop Application
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/group2/flightres/
│       │   ├── App.java                  # JavaFX Application Main Class
│       │   ├── component/                # Shared UI controls (NavBar, StepperBar)
│       │   ├── controller/               # FXML Controllers (Home, Search, SeatMap, Checkout, etc.)
│       │   ├── tools/                    # ScreenshotRunner (automated dual-theme capture)
│       │   └── util/                     # Motion (animations), ThemeManager (CSS switching)
│       └── resources/
│           ├── fxml/                     # Screen view layouts (home, login, flight_search, etc.)
│           ├── css/                      # skyvoyage.css, theme-light.css, theme-dark.css
│           ├── images/                   # Airline brand logos & high-res destination photography
│           └── fonts/                    # Clean typographic web & desktop fonts
├── web/                                  # Javalin + Thymeleaf Web Application
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/group2/flightres/web/
│       │   ├── WebApplication.java       # Javalin lifecycle, security filters & route mapping
│       │   └── WebController.java        # HTTP handlers, form validation & model binding
│       └── resources/
│           ├── templates/                # HTML5 templates (home, flights, seats, confirmation, etc.)
│           └── public/
│               ├── css/style.css         # Responsive styling with prefers-color-scheme tokens
│               └── images/               # High-res destination photos & airline icons
├── docs/                                 # Design specs, reference standards & deliverables
│   ├── REFERENCE_STANDARD.md             # Developer drop-in standard for team members
│   ├── for-documentation.md              # This comprehensive documentation pack
│   ├── boarding-pass.pdf                 # Sample vector boarding pass output
│   ├── screenshots/                      # 15 High-res Light Theme PNGs (01-home to 15-web-contact)
│   └── screenshots/dark/                 # 15 High-res Dark Theme PNGs (01-home to 15-web-contact)
└── scripts/                              # Automated PowerShell & batch generation tools
    ├── generate_screenshots.cmd          # Light web screenshot generator via headless Edge
    ├── generate_dark_web_screenshots.cmd # Dark web screenshot generator via headless Edge
    └── render_pdf.ps1                    # Native Windows WinRT PDF to PNG render tool
```

---

## 4. Database Architecture & ERD Specifications

The PostgreSQL schema uses strict relational integrity with customized ENUM types, cascade deletes, composite unique keys, and B-Tree indexes for fast route querying.

### 4.1 Custom Database Enumerations (ENUMs)
- `user_role`: `'ADMIN'`, `'AGENT'`, `'CUSTOMER'`
- `seat_class`: `'FIRST'`, `'ECONOMY'`
- `seat_status`: `'AVAILABLE'`, `'HELD'`, `'BOOKED'`
- `flight_status`: `'SCHEDULED'`, `'BOARDING'`, `'DEPARTED'`, `'CANCELLED'`
- `booking_status`: `'PENDING'`, `'CONFIRMED'`, `'CANCELLED'`, `'REFUNDED'`
- `payment_status`: `'PENDING'`, `'COMPLETED'`, `'FAILED'`, `'REFUNDED'`

### 4.2 Database Tables & Schema Details

```sql
-- 1. USERS: Authentication and Role-Based Access Control
CREATE TABLE users (
    user_id         BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  UNIQUE NOT NULL,
    password_hash   VARCHAR(60)  NOT NULL,       -- BCrypt hash ($2a$12$...)
    role            user_role    NOT NULL DEFAULT 'CUSTOMER',
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(120) UNIQUE NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 2. AIRPORTS: Flight Origin and Destination Stations
CREATE TABLE airports (
    airport_id      SERIAL PRIMARY KEY,
    iata_code       CHAR(3) UNIQUE NOT NULL,     -- 'JNB', 'CPT', 'DUR', 'PLZ'
    name            VARCHAR(120) NOT NULL,
    city            VARCHAR(80)  NOT NULL,
    country         VARCHAR(80)  NOT NULL
);

-- 3. SEAT_CLASS_PRICING: Pricing Multipliers by Cabin Tier
CREATE TABLE seat_class_pricing (
    seat_class       seat_class PRIMARY KEY,
    price_multiplier NUMERIC(4,2) NOT NULL CHECK (price_multiplier > 0) -- 3.50 for FIRST, 1.00 for ECONOMY
);

-- 4. AIRCRAFT_TYPES: Fleet Configurations
CREATE TABLE aircraft_types (
    aircraft_type_id SERIAL PRIMARY KEY,
    model            VARCHAR(60) NOT NULL,       -- 'Airbus A320-200'
    total_rows       SMALLINT NOT NULL CHECK (total_rows > 0),
    grid_width       SMALLINT NOT NULL CHECK (grid_width > 0)
);

-- 5. SEAT_TEMPLATES: Physical Cabin Geometry & Spatial Layout
CREATE TABLE seat_templates (
    seat_template_id BIGSERIAL PRIMARY KEY,
    aircraft_type_id SMALLINT NOT NULL REFERENCES aircraft_types(aircraft_type_id) ON DELETE CASCADE,
    seat_row         SMALLINT NOT NULL,          -- e.g. 1 to 30
    seat_col         CHAR(1)  NOT NULL,          -- 'A', 'B', 'C', 'D', 'E', 'F'
    seat_class       seat_class NOT NULL,
    display_row      SMALLINT NOT NULL,          -- Zero-based grid row
    display_col      SMALLINT NOT NULL,          -- Zero-based grid col (aisles accounted for)
    is_window        BOOLEAN NOT NULL DEFAULT FALSE,
    is_aisle         BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (aircraft_type_id, seat_row, seat_col),
    UNIQUE (aircraft_type_id, display_row, display_col)
);

-- 6. FLIGHTS: Scheduled Departures & Dynamic Pricing
CREATE TABLE flights (
    flight_id         BIGSERIAL PRIMARY KEY,
    flight_number     VARCHAR(10) NOT NULL,       -- e.g. 'GRP 204'
    aircraft_type_id  SMALLINT NOT NULL REFERENCES aircraft_types(aircraft_type_id),
    origin_airport_id SMALLINT NOT NULL REFERENCES airports(airport_id),
    dest_airport_id   SMALLINT NOT NULL REFERENCES airports(airport_id) CHECK (dest_airport_id <> origin_airport_id),
    departure_time    TIMESTAMPTZ NOT NULL,
    arrival_time      TIMESTAMPTZ NOT NULL CHECK (arrival_time > departure_time),
    base_price        NUMERIC(10,2) NOT NULL CHECK (base_price > 0),
    status            flight_status NOT NULL DEFAULT 'SCHEDULED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. FLIGHT_SEATS: Real-Time Per-Flight Seat Availability & Optimistic Locking
CREATE TABLE flight_seats (
    flight_seat_id   BIGSERIAL PRIMARY KEY,
    flight_id        BIGINT NOT NULL REFERENCES flights(flight_id) ON DELETE CASCADE,
    seat_template_id BIGINT NOT NULL REFERENCES seat_templates(seat_template_id),
    status           seat_status NOT NULL DEFAULT 'AVAILABLE',
    held_until       TIMESTAMPTZ,                -- Soft-lock timeout (e.g. 10 minutes)
    UNIQUE (flight_id, seat_template_id)
);

-- 8. BOOKINGS: Central Reservation Headers
CREATE TABLE bookings (
    booking_id       BIGSERIAL PRIMARY KEY,
    booking_ref      VARCHAR(10) UNIQUE NOT NULL, -- 6-character PNR code (e.g. '4GZUFL')
    user_id          BIGINT NOT NULL REFERENCES users(user_id),
    flight_id        BIGINT NOT NULL REFERENCES flights(flight_id),
    status           booking_status NOT NULL DEFAULT 'PENDING',
    total_amount     NUMERIC(10,2) NOT NULL CHECK (total_amount >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 9. BOOKING_SEATS: Passenger Allocations & Fare Snapshots
CREATE TABLE booking_seats (
    booking_seat_id BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    flight_seat_id  BIGINT NOT NULL UNIQUE REFERENCES flight_seats(flight_seat_id),
    passenger_name  VARCHAR(120) NOT NULL,
    passport_number VARCHAR(20)  NOT NULL,
    date_of_birth   DATE NOT NULL,
    price_paid      NUMERIC(10,2) NOT NULL CHECK (price_paid >= 0)
);

-- 10. PAYMENTS: Financial Transactions & Audit Trail
CREATE TABLE payments (
    payment_id      BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    amount          NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    method          VARCHAR(30) NOT NULL,        -- 'CREDIT_CARD', 'EFT', etc.
    status          payment_status NOT NULL DEFAULT 'PENDING',
    transaction_ref VARCHAR(60),
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 11. SEAT_PRICING_VIEW: Real-Time Dynamic Fare Evaluation View
CREATE VIEW seat_pricing_view AS
SELECT
    fs.flight_seat_id,
    fs.flight_id,
    st.seat_row,
    st.seat_col,
    st.seat_class,
    st.display_row,
    st.display_col,
    fs.status,
    ROUND(f.base_price * scp.price_multiplier, 2) AS calculated_price
FROM flight_seats fs
JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
JOIN flights f ON f.flight_id = fs.flight_id
JOIN seat_class_pricing scp ON scp.seat_class = st.seat_class;
```

### 4.3 Entity-Relationship Diagram (ERD) Structure

```
+----------------+          +--------------------+          +---------------------+
|    airports    |          |   aircraft_types   |          | seat_class_pricing  |
+----------------+          +--------------------+          +---------------------+
| PK airport_id  |          | PK aircraft_type_id|          | PK seat_class       |
|    iata_code   |          |    model           |          |    price_multiplier |
|    name, city  |          |    total_rows      |          +----------+----------+
+-------+--------+          |    grid_width      |                     |
        |                   +---+------------+---+                     |
        | 1                     | 1          | 1                       |
        |                       |            +-------------------+     |
        | M                     | M                              |     |
+-------v--------+              |                     +----------v-----v----+
|    flights     |              |                     |   seat_templates    |
+----------------+              |                     +---------------------+
| PK flight_id   |              |                     | PK seat_template_id |
| FK origin_id   |              |                     | FK aircraft_type_id |
| FK dest_id     |              |                     |    seat_row, col    |
| FK aircraft_id |<-------------+                     |    display_row, col |
|    dep/arr_time|                                    +----------+----------+
|    base_price  |                                               | 1
+-------+--------+                                               |
        | 1                                                      |
        |                                                        | M
        | M                                           +----------v----------+
        +-------------------------------------------->|    flight_seats     |
        |                                             +---------------------+
        | 1                                           | PK flight_seat_id   |
        |                                             | FK flight_id        |
+-------v--------+          +-----------------+       | FK seat_template_id |
|    bookings    |<---------+      users      |       |    status, held_until|
+----------------+ M      1 +-----------------+       +----------+----------+
| PK booking_id  |          | PK user_id      |                  | 1
| FK user_id     |          |    username     |                  |
| FK flight_id   |          |    password_hash|                  |
|    booking_ref |          |    role, email  |                  |
|    total_amount|          +-----------------+                  |
+---+--------+---+                                               |
    | 1      | 1                                                 |
    |        |                                                   |
    | M      | M                                                 | 1 (Unique)
+---v----+ +-v---------------+                                   |
|payments| |  booking_seats  |<----------------------------------+
+--------+ +-----------------+
|PK pay_id | PK booking_seat_id
|FK book_id| FK booking_id   |
|   amount | FK flight_seat_id
|   ref    |    passenger_name
+--------+ |    passport_no  |
           +-----------------+
```

---

## 5. Main Classes & Responsibilities

### 5.1 Core Engine Module (`core`)

| Class | Category | Primary Responsibility |
| :--- | :--- | :--- |
| `AppConfig` | Configuration | Loads JSON environment configuration (`app.json`) including database host, port, credentials, and pool sizes. |
| `BrandConfig` | Configuration | Centralized brand identity constant holder (`APP_NAME = "SkyVoyage"`, `TAGLINE`, `LOGO_TEXT`). Ensures consistent branding across all screens. |
| `DatabaseConfig` | Configuration | Singleton HikariCP connection pool provider. Initializes thread-safe pooled `Connection` objects for JDBC operations. |
| `BookingDaoImpl` | Persistence | Performs transactional booking insertion, optimistic seat locking, booking retrieval by PNR reference, and payment recording. |
| `DashboardStatsDaoImpl` | Persistence | Computes real-time analytical metrics: revenue totals, bookings by class, passenger counts, 7-day velocity, and top destinations. |
| `FlightDaoImpl` | Persistence | Executes parameterized SQL search queries matching origin, destination, and departure dates. |
| `SeatDaoImpl` | Persistence | Reads physical cabin layout grids and manages atomic seat holding (`UPDATE flight_seats SET status='HELD' ...`). |
| `UserDaoImpl` | Persistence | Manages user lookups by username or email for authentication and authorization. |
| `BookingService` | Business Logic | Orchestrates atomic end-to-end checkout transactions: validates inputs, asserts seat holds, commits booking headers, registers passengers, and sets final booked states. |
| `FlightService` | Business Logic | Business facade for flight route validation and dynamic schedule search. |
| `SeatMapService` | Business Logic | Evaluates visual cabin layouts, enforces concurrency hold expirations, and executes optimistic seat reservations. |
| `DashboardStatsService` | Business Logic | Surfaces aggregate data models for administrative charts and operational reporting. |
| `AuthService` | Business Logic / Security | Validates user credentials using BCrypt verification and enforces role-based authorization. |
| `BoardingPassPdfGenerator` | Document Utility | Uses OpenPDF to construct high-resolution boarding pass vouchers with tear-off stubs, barcode strips, and flight metadata. |
| `ValidationUtil` | Security / Validation | Validates email syntax, passport numbers, names, phone numbers, and booking references using compiled Regex patterns. |
| `PasswordUtil` | Security / Crypto | Provides salted BCrypt hashing and verification routines. |

### 5.2 Desktop Application Module (`desktop`)

| Class | Category | Primary Responsibility |
| :--- | :--- | :--- |
| `App` | Entrypoint | JavaFX Application launcher initializing the primary stage, `ThemeManager`, and `MainShellController`. |
| `MainShellController` | Navigation / Shell | Orchestrates top-level application navigation, screen transitions, view switching, and breadcrumb stepper tracking. |
| `NavBar` | UI Component | Reusable navigation header featuring brand logo, navigation links, and theme toggle buttons. |
| `StepperBar` | UI Component | Multi-step progress visualizer tracking reservation progress (Search -> Select Seat -> Checkout -> Confirmation). |
| `HomeController` | View Controller | Landing screen featuring hero welcome, quick booking launch cards, and destination highlights. |
| `LoginController` | View Controller | Authentication portal handling user login, role differentiation, and administrative redirection. |
| `FlightSearchController` | View Controller | Route search screen with date pickers, airport comboboxes, and real-time result table styling. |
| `SeatMapController` | View Controller | Visual interactive cabin layout rendering First and Economy seats with live selection feedback and pricing pills. |
| `SeatMapFallback` | View Controller | Graceful fallback cabin generator ensuring seat selection remains functional under disconnected test profiles. |
| `CheckoutController` | View Controller | Booking finalization screen capturing passenger details, passport numbers, payment info, and executing checkout transactions. |
| `BookingConfirmationController` | View Controller | Booking receipt screen presenting confirmation reference (PNR), booking summary, and one-click PDF voucher generation. |
| `AdminDashboardController` | View Controller | Executive monitoring console rendering live revenue cards, class distribution donuts, and 7-day booking volume charts. |
| `ThemeManager` | Styling / Utility | Centralized CSS manager that swaps light and dark stylesheets across active scene graphs dynamically. |
| `Motion` | Animation Utility | Configures subtle hardware-accelerated transitions (fade-in, slide-in) with support for an accessibility-friendly reduced motion switch. |
| `ScreenshotRunner` | Testing / Tooling | Automated headless screenshot generator rendering all 7 desktop screens across both light and dark themes. |

### 5.3 Web Portal Module (`web`)

| Class | Category | Primary Responsibility |
| :--- | :--- | :--- |
| `WebApplication` | Server Lifecycle | Boots embedded Javalin 6 server on port 7070, registers security headers, CSRF filters, static asset routing, and template engines. |
| `WebController` | HTTP Controller | Handles all server-rendered endpoints (`/`, `/flights`, `/flights/{id}/seats`, `/book`, `/confirmation/{ref}`, `/login`, `/about`, `/contact`). |

---

## 6. Software Design Patterns Applied

1. **Model-View-Controller (MVC):**
   - **Model:** Independent Java records and POJOs in `core.model` representing domain data.
   - **View:** FXML templates and CSS in `desktop`; Thymeleaf HTML templates and responsive CSS in `web`.
   - **Controller:** Distinct controllers handling user events and bridging data to views without holding business logic.

2. **Data Access Object (DAO) Pattern:**
   - Complete abstraction of database persistence behind Java interfaces (`FlightDao`, `SeatDao`, `BookingDao`, etc.).
   - Prevents SQL leakage into presentation or service tiers.

3. **Service Layer Pattern:**
   - Centralizes transactional business logic, multi-step operations, and business rule enforcement in dedicated service classes shared by both Desktop and Web modules.

4. **Singleton Connection Pool Pattern:**
   - `DatabaseConfig` manages a thread-safe, lazily initialized `HikariDataSource` pool instance, reusing database connections efficiently and preventing resource exhaustion.

5. **Optimistic Locking Pattern (Concurrency Race Protection):**
   - Atomic conditional update statements (`UPDATE flight_seats SET status='HELD' WHERE flight_seat_id=? AND status='AVAILABLE'`) prevent double-booking race conditions when multiple users select the same seat simultaneously.

6. **Strategy & Factory Patterns:**
   - `BoardingPassPdfGenerator` modularizes PDF cell generation and barcode rendering via discrete factory methods.
   - `ThemeManager` applies styling strategies based on selected visual modes.

---

## 7. Security Features & Implementation

1. **Salted Password Hashing (BCrypt):**
   - Stored in database using 60-character BCrypt hashes with automatic work factor 12 salting via `jBCrypt`. Raw passwords are never persisted or logged.

2. **PreparedStatements & SQL Injection Prevention:**
   - 100% of SQL queries across the entire codebase utilize parameterized `PreparedStatement` with explicit typed binders (`setString`, `setInt`, `setTimestamp`, `setBigDecimal`). Zero string concatenation is used in query assembly.

3. **Server-Side Input Validation & Sanitization:**
   - Centralized `ValidationUtil` validates passenger names, passport numbers, email addresses, date ranges, and booking reference formats. Invalid inputs throw strongly typed `ValidationException` without executing database commands.

4. **Cross-Site Request Forgery (CSRF) Protection:**
   - Unique session-bound cryptographically random tokens (`UUID`) are generated on all HTML forms and verified by `WebApplication` on all incoming POST requests (`/book`, `/login`).

5. **Role-Based Access Control (RBAC):**
   - Granular permissions (`ADMIN`, `AGENT`, `CUSTOMER`) defined in PostgreSQL schema enums, validated upon login, and enforced in UI navigation gates.

6. **HTTP Hardening & Security Headers:**
   - Javalin responses automatically inject `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, and `Referrer-Policy: strict-origin-when-cross-origin`.

7. **Zero Information Leakage:**
   - Global exception mappers intercept all unexpected server errors, logging full diagnostic traces to SLF4J logs while returning a sanitized user-friendly error page (`error.html`) without leaking stack traces or internal DB schema details.

---

## 8. Complete Visual Screenshot Catalog

All 15 application screens have been captured in both Light and Dark themes at native resolution and are located in the repository under `docs/screenshots/` (Light) and `docs/screenshots/dark/` (Dark).

| Screen ID | File Name | Screen Description | Theme Support |
| :---: | :--- | :--- | :---: |
| **01** | `01-home.png` | Desktop Home Landing Screen (Hero photo, booking shortcuts) | Light & Dark |
| **02** | `02-login.png` | Desktop Agent & Admin Split Photographic Login Portal | Light & Dark |
| **03** | `03-flight-search.png` | Desktop Flight Search & Route Results Table | Light & Dark |
| **04** | `04-seat-map.png` | Desktop Interactive Cabin Seat Map (First & Economy) | Light & Dark |
| **05** | `05-checkout.png` | Desktop Passenger Checkout & Payment Finalization | Light & Dark |
| **06** | `06-booking-confirmation.png` | Desktop Booking Confirmation Voucher with PNR reference | Light & Dark |
| **07** | `07-admin-dashboard.png` | Desktop Real-time Admin Monitoring & Analytics Dashboard | Light & Dark |
| **08** | `08-boarding-pass.png` | Vector PDF Boarding Pass Voucher with Barcode & Tear-Off Stub | Universal Print |
| **09** | `09-web-home.png` | Website Landing Page (Hero, search widget, why-fly cards) | Light & Dark |
| **10** | `10-web-flights.png` | Website Flight Search Results & Route Selection Cards | Light & Dark |
| **11** | `11-web-seats.png` | Website Responsive Visual Seat Selection Cabin Grid | Light & Dark |
| **12** | `12-web-confirmation.png` | Website Booking Confirmation Voucher & Travel Summary | Light & Dark |
| **13** | `13-web-login.png` | Website Split Photographic User Authentication Page | Light & Dark |
| **14** | `14-web-about.png` | Website About Us, Fleet Modernization & Values Page | Light & Dark |
| **15** | `15-web-contact.png` | Website Customer Inquiry, Help Desk & Support Form | Light & Dark |

---

## 9. Setup, Configuration & How to Run

### 9.1 Database Setup (PostgreSQL)

1. Ensure PostgreSQL (version 14 or higher, tested on PostgreSQL 18) is running on port `5433` (or `5432`).
2. Create the target database:
   ```sql
   CREATE DATABASE flight_reservation;
   ```
3. Execute the SQL schema and seed files in sequential order:
   ```bash
   psql -U postgres -d flight_reservation -f core/sql/schema.sql
   psql -U postgres -d flight_reservation -f core/sql/seed_auth_data.sql
   psql -U postgres -d flight_reservation -f core/sql/seed_flights_data.sql
   ```
4. Copy `app.json.example` to `app.json` in the project root and confirm your credentials:
   ```json
   {
     "database": {
       "host": "localhost",
       "port": 5433,
       "name": "flight_reservation",
       "username": "postgres",
       "password": "YOUR_PASSWORD",
       "maximumPoolSize": 10
     }
   }
   ```

### 9.2 Build and Run Test Suite

To verify compilation, static checks, and execute all 24 unit and integration test suites:
```bash
mvn clean verify
```

### 9.3 Launch Desktop Application (JavaFX)

To launch the JavaFX desktop client:
```bash
mvn -pl desktop javafx:run
```
- **Default Test Users:**
  - Administrator: `admin` / `AdminPass2026!`
  - Travel Agent: `agent1` / `AgentPass2026!`
  - Customer: `customer1` / `CustomerPass2026!`

### 9.4 Launch Web Application (Javalin)

To launch the embedded web portal:
```bash
mvn -pl web exec:java
```
- Access the web application in any modern browser at:  
  `http://localhost:7070/`

### 9.5 Automated Screenshot Suite Generation

- **Desktop Screens:**  
  `mvn -pl desktop javafx:run -Pscreenshots`
- **Web Portal Light Mode:**  
  `scripts\generate_screenshots.cmd`
- **Web Portal Dark Mode:**  
  `scripts\generate_dark_web_screenshots.cmd`
- **Boarding Pass PDF Render:**  
  `powershell -ExecutionPolicy Bypass -File scripts\render_pdf.ps1`
