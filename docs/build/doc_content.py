"""
Structured content for Sky Royal Flight Reservation System documentation.
All prose follows plain student English with no em dashes, no en dashes,
no spaced hyphens used as dashes, and no banned marketing buzzwords.
Student numbers are NEVER stored here; they are injected at build time from
%USERPROFILE%\\sky-royal-agent\\docs-in\\students.txt for FULL builds only.
"""

PROJECT_TITLE = "Sky Royal Flight Reservation System"
COLLEGE_NAME = "Gauteng City College"
COURSE_SLOT = "Information Technology (System Development and System Support), Final Year 2026"
GROUP_NAME = "Group 2"
LECTURER_NAME = "Mr Owami"
SUBMISSION_DATE = "27 November 2026"
GITHUB_URL = "https://github.com/zennin7/flight-reservation-system"

# Team member metadata (names match students.txt; roles/contributions verified from git log --all + team-intake)
TEAM_META = {
    "Buhlebamaqwabe Gumede": {
        "github": "Buhlebamaqwabe Gumede (handle to confirm)",
        "role": "Booking Confirmation UI Developer",
        "contribution": "Built the JavaFX booking confirmation screen prototype (app.java, index.fxml, steyle.css) on branch feature/buhle/confirmation-screen (PR #9, 1 direct commit).",
        "inferred": True,
    },
    "David Elamenji": {
        "github": "zennin7",
        "role": "Technical Integrator and Lead Architect",
        "contribution": "Architecture, database, core services, desktop app parity, website integration, payments, QR boarding pass, testing and code review (199 commits across David Zenn and zennin7).",
        "inferred": False,
    },
    "Gaorekwe Petja": {
        "github": "petjagaorekwe-dot",
        "role": "Admin Dashboard Developer",
        "contribution": "Submitted Admin Dashboard implementation code on branch petjagaorekwe/submission (PR #12, 1 direct commit).",
        "inferred": True,
    },
    "Keahana Campbell": {
        "github": "Keahana Campbell (handle to confirm)",
        "role": "Booking Confirmation Developer",
        "contribution": "Built the Maven JavaFX booking confirmation module (BookingConfirmationController.java, booking-confirmation.fxml, styles.css) on branch submission/keahana (PR #3, 1 direct commit).",
        "inferred": True,
    },
    "Koketso Makweya": {
        "github": "Koketso Makweya (handle to confirm)",
        "role": "Login and Authentication UI Developer",
        "contribution": "Built the JavaFX login module (LoginController.java, Main.java, login.fxml, login.css) on branch submission/koketsomakweya (PR #7, 1 direct commit).",
        "inferred": True,
    },
    "Luyanda Njolo": {
        "github": "Yandaaaaa",
        "role": "Admin Dashboard Developer",
        "contribution": "Submitted Admin Dashboard implementation code and merged PR #13 on branch Yandaaaaa/submission (2 direct commits).",
        "inferred": True,
    },
    "Omphemetse Sinikiwe Masondo": {
        "github": "SinikiweMasondo",
        "role": "Web Frontend and Checkout Developer",
        "contribution": "Built the original website home page (index.html, style.css, script.js), assigned to checkout flow, merged PR #5 (3 direct commits, plus 38 co-authored website integration commits).",
        "inferred": True,
    },
    "Theledi Ramouna Maluleka": {
        "github": "ThelediMaluleka",
        "role": "Web Login Developer and Project Coordinator",
        "contribution": "Built the original website login page (index.html, styles.css), worked on the original website with Nikki, merged PR #4 (3 direct commits, plus 38 co-authored website commits), and led team coordination, presentation and documentation material.",
        "inferred": True,
    },
    "Tshepiso Leepile": {
        "github": "Tshepiso-Sky",
        "role": "Networking and Security Researcher",
        "contribution": "Produced security notes, Cisco Packet Tracer network topology, and security documentation on branch feature/Tshepiso/networking-and-security (PR #11, 1 direct commit).",
        "inferred": True,
    },
    "Ubuntu Ayabonga Mhlongo": {
        "github": "UAM-IN-TECH",
        "role": "Flight Search Developer",
        "contribution": "Built the JavaFX flight search controller, FXML view, DAO interface and implementation, and SQL (FlightSearchController.java, FlightDaoImpl.java, Flight.fxml, flight.sql) and merged PR #10 (2 direct commits).",
        "inferred": True,
    },
}

# Verified HTTP-200 Harvard References (all accessed 24 November 2026, all cited in prose)
HARVARD_REFERENCES = [
    ("Apache Software Foundation (2024)",
     "Apache Software Foundation (2024) Introduction to the POM. Apache Maven Documentation. Available at: https://maven.apache.org/guides/introduction/introduction-to-the-pom.html (Accessed: 24 November 2026)."),
    ("Desruisseaux, B. (2009)",
     "Desruisseaux, B. (2009) Internet Calendaring and Scheduling Core Object Specification (iCalendar), RFC 5545. IETF. Available at: https://www.rfc-editor.org/rfc/rfc5545 (Accessed: 24 November 2026)."),
    ("FasterXML (2024)",
     "FasterXML (2024) Jackson Project Home: JSON Processor for Java. Available at: https://github.com/FasterXML/jackson (Accessed: 24 November 2026)."),
    ("Javalin (2024)",
     "Javalin (2024) Javalin 6 Documentation. Available at: https://javalin.io/documentation (Accessed: 24 November 2026)."),
    ("JUnit Team (2024)",
     "JUnit Team (2024) JUnit 5 User Guide. Available at: https://junit.org/junit5/docs/current/user-guide/ (Accessed: 24 November 2026)."),
    ("Krawczyk, H., Bellare, M. and Canetti, R. (1997)",
     "Krawczyk, H., Bellare, M. and Canetti, R. (1997) HMAC: Keyed-Hashing for Message Authentication, RFC 2104. IETF. Available at: https://www.rfc-editor.org/rfc/rfc2104 (Accessed: 24 November 2026)."),
    ("LibrePDF (2024)",
     "LibrePDF (2024) OpenPDF: Open Source Java Library for Creating and Editing PDF Files. Available at: https://github.com/LibrePDF/OpenPDF (Accessed: 24 November 2026)."),
    ("Object Management Group (2017)",
     "Object Management Group (2017) Unified Modeling Language (UML) Specification. Available at: https://www.omg.org/spec/UML/ (Accessed: 24 November 2026)."),
    ("OpenJFX (2024)",
     "OpenJFX (2024) JavaFX Documentation and Runtime Overview. Available at: https://openjfx.io/ (Accessed: 24 November 2026)."),
    ("Oracle (2024)",
     "Oracle (2024) Java Platform, Standard Edition 17 Documentation. Available at: https://docs.oracle.com/en/java/javase/17/ (Accessed: 24 November 2026)."),
    ("OWASP Foundation (2025a)",
     "OWASP Foundation (2025a) OWASP Top Ten Web Application Security Risks. Available at: https://owasp.org/www-project-top-ten/ (Accessed: 24 November 2026)."),
    ("OWASP Foundation (2025b)",
     "OWASP Foundation (2025b) Password Storage Cheat Sheet. Available at: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html (Accessed: 24 November 2026)."),
    ("OWASP Foundation (2025c)",
     "OWASP Foundation (2025c) SQL Injection Prevention Cheat Sheet. Available at: https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html (Accessed: 24 November 2026)."),
    ("PostgreSQL Global Development Group (2026)",
     "PostgreSQL Global Development Group (2026) PostgreSQL Official Documentation. Available at: https://www.postgresql.org/docs/ (Accessed: 24 November 2026)."),
    ("QOS.ch (2024a)",
     "QOS.ch (2024a) Logback Documentation. Available at: https://logback.qos.ch/documentation.html (Accessed: 24 November 2026)."),
    ("QOS.ch (2024b)",
     "QOS.ch (2024b) SLF4J User Manual. Available at: https://www.slf4j.org/manual.html (Accessed: 24 November 2026)."),
    ("Thymeleaf (2024)",
     "Thymeleaf (2024) Thymeleaf 3.1 Official Documentation. Available at: https://www.thymeleaf.org/documentation.html (Accessed: 24 November 2026)."),
    ("Wooldridge, B. (2024)",
     "Wooldridge, B. (2024) HikariCP: High-Performance JDBC Connection Pool. Available at: https://github.com/brettwooldridge/HikariCP (Accessed: 24 November 2026)."),
    ("ZXing Authors (2024)",
     "ZXing Authors (2024) ZXing (Zebra Crossing) Barcode and QR Code Library. Available at: https://github.com/zxing/zxing (Accessed: 24 November 2026)."),
]

# Functional Requirements (18 real requirements)
FUNCTIONAL_REQUIREMENTS = [
    ["FR01", "User Registration", "Guests shall register a new customer account with full name, unique username, case-insensitive unique email, and password."],
    ["FR02", "Welcome Email Outbox", "Upon registration, the system shall render a Thymeleaf welcome email and write it to the local outbox directory or optional SMTP server."],
    ["FR03", "User Authentication", "Users shall sign in using either username or email and a BCrypt-verified password on both desktop and web clients."],
    ["FR04", "Role-Based Routing", "Administrators (ADMIN) shall access the operations dashboard and bookings manager; customers (CUSTOMER) shall access booking and travel pages."],
    ["FR05", "Flight Route Search", "Users shall search one-way scheduled flights by origin airport, destination airport, departure date, and cabin class."],
    ["FR06", "Flight Filtering", "Users shall filter search results by departure time window, maximum fare, and cabin class (Economy or First Class)."],
    ["FR07", "Sales Cutoff Enforcement", "The system shall block bookings within 60 minutes of scheduled departure (FlightSalesPolicy)."],
    ["FR08", "Interactive Seat Map", "Users shall view a live 2D cabin seat map showing First Class (2-2) and Economy (3-3) seats with Available, Held, and Taken states."],
    ["FR09", "Optimistic Seat Hold", "Selecting an available seat shall atomically place a 5-minute soft lock owned by the user or session to prevent double booking."],
    ["FR10", "Manual and Timed Seat Release", "A user holding a seat shall be able to release it immediately; expired holds shall become reclaimable after 5 minutes."],
    ["FR11", "Dynamic Fare Calculation", "The server shall calculate VAT-inclusive fares from base price, seat class multiplier, occupancy demand tier, and booking timing tier."],
    ["FR12", "Simulated Card Payment", "Checkout shall validate passenger details and process simulated card payments (4242 4242 4242 4242 approves; 4000 0000 0000 0002 declines)."],
    ["FR13", "Atomic Booking & PNR", "Approved payment shall commit booking, passenger seat allocation, payment metadata, and BOOKED seat state in one database transaction, generating a 6-character PNR."],
    ["FR14", "PDF Boarding Pass Export", "Confirmed bookings shall generate a downloadable OpenPDF boarding pass containing flight details, Code128 barcode, and HMAC-signed QR code."],
    ["FR15", "Public QR Verification", "Scanning the QR code shall open /verify/{pnr}?sig=... and verify authenticity via constant-time HMAC-SHA256 without exposing full passenger names."],
    ["FR16", "My Bookings & Calendar Export", "Signed-in customers shall view Upcoming, Past, and Cancelled bookings and download RFC 5545 .ics calendar invitations."],
    ["FR17", "24-Hour Cancellation & Refund", "Customers shall cancel confirmed bookings at least 24 hours before departure, releasing the seat and recording a simulated refund."],
    ["FR18", "Admin Operations Dashboard", "Administrators shall view live 7-day booking totals, revenue, seats sold, cancellations, cabin load factors, and all bookings."],
]

# Non-Functional Requirements (10 real requirements)
NON_FUNCTIONAL_REQUIREMENTS = [
    ["NFR01", "Security (Passwords)", "Passwords shall be hashed with jBCrypt at work factor 12 and never stored or logged in plain text."],
    ["NFR02", "Security (SQL Injection)", "100% of database queries shall use parameterized JDBC PreparedStatements with typed bindings."],
    ["NFR03", "Security (Web Hardening)", "All web POST routes shall enforce session-bound UUID CSRF tokens; responses shall include nosniff, X-Frame-Options DENY, and Referrer-Policy headers."],
    ["NFR04", "Rate Limiting", "Login, registration, payment (10 requests per 10 minutes per IP), and QR verification (30 requests per minute per IP) shall enforce server-side throttles."],
    ["NFR05", "Data Integrity", "Booking confirmation and cancellation shall execute inside single ACID PostgreSQL transactions with row-level locking and idempotency keys."],
    ["NFR06", "Concurrency Safety", "Simultaneous seat holds on the same seat shall allow exactly one winner using atomic conditional SQL updates."],
    ["NFR07", "Portability", "The project shall run on Windows with Java 17+, PostgreSQL on port 5432 or 5433, bundled Maven Wrapper (mvnw.cmd), and automatic web port fallback from 8080."],
    ["NFR08", "Usability & Contrast", "All web and desktop glass interfaces shall maintain WCAG AA text contrast (>= 4.5:1) and responsive layouts from 390 px to 1440 px."],
    ["NFR09", "Maintainability", "Business logic and SQL shall live exclusively in the shared core module so desktop and web clients share one source of truth."],
    ["NFR10", "Observability", "Application events and errors shall be logged through SLF4J and Logback without printing database passwords, card PANs, or CVVs."],
]

USER_ROLES = [
    ["Guest Visitor", "Unauthenticated user", "Browse home page, search and filter flights, inspect seat availability, register a customer account, and verify a QR boarding pass URL."],
    ["Customer (CUSTOMER)", "Signed-in traveller", "All guest actions plus hold and release seats, complete checkout and simulated payment, view own bookings, download PDF and .ics files, and cancel eligible bookings (>= 24 hours before departure)."],
    ["Administrator (ADMIN)", "Airline operations supervisor", "All customer actions plus access /admin and the desktop Admin Dashboard and Admin Bookings views to monitor KPIs, revenue, cabin loads, and all reservations."],
    ["Agent (AGENT enum)", "Defined in PostgreSQL user_role enum", "Included in schema.sql for future travel-agent desk expansion; current authentication and UI routes distinguish ADMIN and CUSTOMER roles."],
]

USER_STORIES = [
    ["US01", "Guest", "Search flights by route, date, and cabin", "Find available domestic or international departures quickly.", "UC1"],
    ["US02", "Guest", "Create a new customer account online", "Book flights and manage my own reservations.", "UC2"],
    ["US03", "Customer", "Sign in with username or email", "Access my saved bookings securely on desktop or web.", "UC3"],
    ["US04", "Customer", "View a visual cabin seat map and hold a seat", "Choose a preferred window or aisle seat without losing it during checkout.", "UC5"],
    ["US05", "Customer", "See live base, demand, and timing fare breakdown", "Know the exact VAT-inclusive price before paying.", "UC6"],
    ["US06", "Customer", "Pay with a simulated card and receive a PNR", "Complete a booking safely in this academic system.", "UC6"],
    ["US07", "Customer", "Download a PDF boarding pass and .ics calendar event", "Keep travel details ready offline and in my calendar.", "UC7"],
    ["US08", "Gate Staff / Guest", "Scan a boarding pass QR code to check validity", "Confirm the ticket is active without exposing private data.", "UC4"],
    ["US09", "Customer", "View upcoming, past, and cancelled trips in My Bookings", "Track all reservations in one place.", "UC8"],
    ["US10", "Customer", "Cancel an upcoming booking at least 24 hours early", "Receive a full simulated refund and release the seat.", "UC9"],
    ["US11", "Administrator", "Monitor 7-day revenue, bookings, and cabin load", "Track airline operational performance in real time.", "UC10, UC11"],
    ["US12", "Administrator", "Refresh the rolling 14-day flight schedule", "Keep demo and operational flights current on any date.", "UC12"],
]

SCHEMA_TABLES = [
    ["users", "user_id (BIGSERIAL PK)", "username, password_hash, role (user_role), full_name, email (unique CI index), is_active, created_at", "Stores user accounts and BCrypt hashes for RBAC."],
    ["airports", "airport_id (SERIAL PK)", "iata_code (CHAR 3 UNIQUE), name, city, country, timezone (VARCHAR 50 NOT NULL)", "Origin and destination airports with IANA time zones."],
    ["seat_class_pricing", "seat_class (ENUM PK)", "price_multiplier (NUMERIC 4,2 CHECK > 0)", "Cabin pricing multipliers (FIRST 3.50, ECONOMY 1.00)."],
    ["aircraft_types", "aircraft_type_id (SERIAL PK)", "model, total_rows, grid_width", "Aircraft models and physical grid dimensions."],
    ["seat_templates", "seat_template_id (BIGSERIAL PK)", "aircraft_type_id (FK), seat_row, seat_col, seat_class (FK), display_row, display_col, is_window, is_aisle", "Reusable physical cabin geometry per aircraft type."],
    ["flights", "flight_id (BIGSERIAL PK)", "flight_number (SR ...), aircraft_type_id (FK), origin_airport_id (FK), dest_airport_id (FK), departure_time, arrival_time, base_price, status, created_at", "Scheduled departures with route and time constraints."],
    ["flight_seats", "flight_seat_id (BIGSERIAL PK)", "flight_id (FK), seat_template_id (FK), status (seat_status), held_until, held_by (VARCHAR 120)", "Per-flight live seat availability and 5-minute soft holds."],
    ["bookings", "booking_id (BIGSERIAL PK)", "booking_ref (VARCHAR 10 UNIQUE), user_id (FK), flight_id (FK), status (booking_status), total_amount, created_at", "Central reservation header with 6-character PNR code."],
    ["booking_seats", "booking_seat_id (BIGSERIAL PK)", "booking_id (FK), flight_seat_id (FK), passenger_name, passport_number, date_of_birth, price_paid, is_active (partial unique index)", "Passenger seat allocation; partial unique index allows cancelled seats to be rebooked."],
    ["payments", "payment_id (BIGSERIAL PK)", "booking_id (FK), amount, method, status, transaction_ref, processed_at, card_brand, card_last4, idempotency_key (UNIQUE), refund_ref, refunded_at", "Payment audit trail, idempotency key, masked card metadata, and refund tracking."],
    ["seat_pricing_view (VIEW)", "flight_seat_id", "flight_id, seat_row, seat_col, seat_class, display_row, display_col, status, calculated_price", "SQL view joining flight_seats, seat_templates, flights, and seat_class_pricing."],
]

TECH_STACK = [
    ["Language & Runtime", "Java (OpenJDK Temurin)", "17 LTS (compiler release 17)", "Core language across core, desktop, and web modules (Oracle, 2024)."],
    ["Build System", "Apache Maven & Wrapper", "3.9.9 (mvnw.cmd)", "Multi-module reactor build and dependency management (Apache Software Foundation, 2024)."],
    ["Desktop GUI", "OpenJFX (JavaFX)", "21.0.3", "Desktop UI with FXML views and Sky Royal Glass CSS; approved in writing by Mr Owami (OpenJFX, 2024)."],
    ["Web Framework", "Javalin", "6.1.3", "Embedded Jetty HTTP server with route handlers and security filters (Javalin, 2024)."],
    ["Template Engine", "Thymeleaf", "3.1.2.RELEASE", "Server-rendered HTML5 templates and welcome email rendering (Thymeleaf, 2024)."],
    ["Database", "PostgreSQL & JDBC Driver", "PostgreSQL 18 / Driver 42.7.4", "Relational storage with custom enums, partial indexes, and ACID transactions (PostgreSQL Global Development Group, 2026)."],
    ["Connection Pool", "HikariCP", "5.1.0", "Thread-safe JDBC connection pool (default maximumPoolSize 10) (Wooldridge, 2024)."],
    ["Password Hashing", "jBCrypt", "0.4", "Salted BCrypt hashing at cost factor 12 (OWASP Foundation, 2025b)."],
    ["PDF Generation", "OpenPDF (LibrePDF)", "1.3.42", "Vector PDF boarding pass generation with tear-off stub (LibrePDF, 2024)."],
    ["QR & Barcodes", "ZXing (core & javase)", "3.5.3", "QR code PNG generation (ECC level M) and Code128 barcodes (ZXing Authors, 2024)."],
    ["JSON Config", "Jackson Databind", "2.17.2", "Parsing app.json configuration files (FasterXML, 2024)."],
    ["Email Adapter", "Jakarta Mail & Angus Mail", "2.1.3 / 2.0.3", "Optional SMTP delivery; defaults to FileOutboxEmailService writing to outbox/."],
    ["Logging", "SLF4J & Logback", "2.0.16 / 1.5.8", "Structured diagnostic logging with Jetty noise suppression (QOS.ch, 2024a; QOS.ch, 2024b)."],
    ["Testing", "JUnit 5 (Jupiter) & PDFBox", "5.11.0 / 2.0.31", "Automated unit, concurrency, security, layout, and PDF verification tests (JUnit Team, 2024)."],
]

# 32 Real Test Cases (from JUnit 5 suites and live throwaway DB proofs)
TEST_CASES = [
    ["TC01", "BCrypt Password Hashing", "Hash a password with PasswordUtil.hash() and verify via PasswordUtil.verify() with correct and wrong inputs.", "Correct password returns true; wrong password returns false; hash uses cost 12.", "Matches expected (PasswordUtilTest).", "PASS"],
    ["TC02", "User Authentication (Valid)", "Sign in as customer (Passw0rd!) using username and then using email address.", "AuthService.authenticate() returns active User with CUSTOMER role.", "Matches expected (AuthServiceTest).", "PASS"],
    ["TC03", "User Authentication (Invalid)", "Submit wrong password or deactivated account credentials.", "AuthenticationException thrown; LoginThrottle increments attempt counter.", "Matches expected (AuthServiceTest, LoginThrottleTest).", "PASS"],
    ["TC04", "Customer Registration", "Submit valid /register form with unique username, unique email, password, and consent.", "User inserted with BCrypt hash; welcome email written to outbox/.", "Matches expected (RegistrationServiceTest, WebRegistrationFlowTest).", "PASS"],
    ["TC05", "Case-Insensitive Email Uniqueness", "Register second user using same email in uppercase letters.", "DuplicateEmailException raised via ux_users_email_lower index.", "Matches expected (RegistrationServiceTest).", "PASS"],
    ["TC06", "Registration Rate Throttle", "Exceed allowed registration attempts from one IP address.", "RegistrationThrottle blocks excess requests cleanly.", "Matches expected (RegistrationThrottleTest).", "PASS"],
    ["TC07", "Flight Route Search", "Search JNB to CPT on a seeded date within the 14-day rolling window.", "Matching SR flights returned with departure, arrival, duration, and available seat count.", "Matches expected (FlightServiceTest).", "PASS"],
    ["TC08", "Same Origin and Destination Guard", "Attempt to search JNB to JNB.", "ValidationUtil.validateRoute() rejects identical origin and destination.", "Matches expected (ValidationUtilTest).", "PASS"],
    ["TC09", "Airport Time Zone Formatting", "Format departure and arrival times across airports with IANA time zones.", "Local times displayed accurately per airport timezone column.", "Matches expected (FlightSearchResultTimezoneTest, FlightTimesTest).", "PASS"],
    ["TC10", "Sales Cutoff Policy (60 min)", "Attempt to book a flight departing in less than 60 minutes.", "FlightSalesPolicy rejects booking as sales closed.", "Matches expected (FlightSalesPolicyTest, WebSalesCutoffAndDateTest).", "PASS"],
    ["TC11", "Dynamic Demand Pricing Tiers", "Quote fares at <40%, 40 to 60%, 60 to 80%, and >=80% occupancy.", "Multipliers 1.00, 1.05, 1.12, and 1.20 applied accurately.", "Matches expected (PricingPolicyTest, PricingServiceProofTest).", "PASS"],
    ["TC12", "Dynamic Timing Pricing Tiers", "Quote fares using injected Clock at >=240h, 120 to 240h, 48 to 120h, 24 to 48h, and <24h.", "Multipliers 0.95, 1.00, 1.07, 1.12, and 1.20 applied with 15% VAT breakdown.", "Matches expected (PricingServiceClockInjectionTest).", "PASS"],
    ["TC13", "Concurrent Seat Hold Race", "Launch competing threads attempting to hold the exact same flight_seat_id simultaneously.", "Exactly one thread succeeds; all others receive SeatUnavailableException.", "Matches expected (SeatHoldRaceTest).", "PASS"],
    ["TC14", "Seat Hold Ownership & Stranger Release", "User A holds seat 6C; User B sends POST /flights/{id}/seats/release for seat 6C.", "Server returns HTTP 403; seat remains HELD by User A.", "HTTP 403 returned; DB still HELD by user:2 (P3b live proof).", "PASS"],
    ["TC15", "Seat Hold Owner Release", "User A clicks Release seat for held seat 6C.", "Server returns HTTP 200; DB status returns to AVAILABLE and held_by becomes NULL.", "HTTP 200 returned; DB status AVAILABLE (P3b live proof).", "PASS"],
    ["TC16", "Seat Hold Expiry (5 Minutes)", "Allow held_until timestamp to pass and request GET /checkout and POST /pay.", "Server returns HTTP 410 Gone and releases expired hold.", "HTTP 410 returned on both routes (SeatHoldExpiryTest, P3b live proof).", "PASS"],
    ["TC17", "CSRF Token Enforcement (A1)", "Send POST /pay without matching session csrf_token.", "WebApplication before-filter blocks request with HTTP 403.", "HTTP 403 returned (WebPaymentFlowTest, P3b live proof).", "PASS"],
    ["TC18", "Checkout Passenger Validation", "Submit POST /pay with invalid passport number or blank passenger name.", "HTTP 400 returned preserving form values; 0 bookings created.", "HTTP 400 returned, bookings=0 (P3b live proof).", "PASS"],
    ["TC19", "Simulated Card Decline (A2)", "Submit POST /pay with test decline card 4000 0000 0000 0002.", "SimulatedPaymentGateway declines; HTTP 402 returned; no booking committed.", "HTTP 402 returned (SimulatedPaymentGatewayTest, P3b live proof).", "PASS"],
    ["TC20", "Simulated Card Approval & Atomic Booking", "Submit POST /pay with test approval card 4242 4242 4242 4242 and future expiry.", "HTTP 302 to /confirmation/{PNR}; booking, seat, and payment committed in 1 transaction.", "HTTP 302 to /confirmation/ADW5MT; payment VISA 4242 COMPLETED (P3b proof).", "PASS"],
    ["TC21", "Idempotent Double-Pay & Price Tampering (A5)", "Resubmit POST /pay with same idempotency key and a tampered client-side fare.", "Server ignores tampered price, returns original PNR without duplicate charge.", "Single DB payment row at server fare R 1,284.00; same PNR returned (P3b proof).", "PASS"],
    ["TC22", "Payment Rate Throttle (A8)", "Send more than 10 payment attempts within 10 minutes from one IP.", "PaymentThrottle rejects excess attempts with HTTP 429.", "HTTP 429 returned (PaymentThrottleTest, P3b live proof).", "PASS"],
    ["TC23", "Sensitive Card Data Log Audit (A9)", "Inspect application logs after checkout and payment execution.", "Zero full card PANs and zero CVV numbers appear in logs.", "panLeak=False, cvvLeak=False (P3b live proof).", "PASS"],
    ["TC24", "PDF Boarding Pass & Barcode/QR", "Download GET /confirmation/{ref}/boarding-pass.pdf and decode embedded codes.", "Valid PDF with PNR, Code128 barcode, and scannable HMAC QR code.", "Verified via PDFBox and ZXing (QrBoardingPassPdfTest, BarcodeTest).", "PASS"],
    ["TC25", "QR Verification (Valid & Tampered)", "Open /verify/{pnr}?sig=<valid> and then tamper with sig parameter.", "Valid sig returns HTTP 200 VALID with masked name; tampered returns uniform HTTP 404.", "HTTP 200 valid; HTTP 404 byte-identical on tampered/unknown (WebQrVerifyTest).", "PASS"],
    ["TC26", "My Bookings & ICS Calendar Export", "Request GET /my-bookings and GET /bookings/{ref}/calendar.ics as owner, stranger, and guest.", "Owner gets 200 with VCALENDAR data; stranger gets 403; guest gets 302 to /login.", "Owner 200 (hasVCAL=True), stranger 403, guest 302 (WebMyBookingsCancelTest).", "PASS"],
    ["TC27", "Cancellation Inside 24h Cutoff", "Attempt to cancel a confirmed booking departing within 24 hours.", "CancellationPolicy blocks cancellation and server returns HTTP 409.", "HTTP 409 returned (CancellationPolicyTest, P3b live proof).", "PASS"],
    ["TC28", "Cancellation >= 24h & Atomic Refund", "Cancel a confirmed booking departing more than 24 hours away.", "Booking CANCELLED, payment REFUNDED with REF-TXN ref, seat AVAILABLE, PDF/ICS return 410.", "Verified in DB and HTTP 410 on PDF/ICS (BookingCancellationServiceTest, P3b proof).", "PASS"],
    ["TC29", "Admin Dashboard & Locale Formatting", "Sign in as testadmin and open /admin and desktop Admin Dashboard.", "KPI cards, load factors, and dot-decimal ZAR amounts render without comma-decimal bugs.", "HTTP 200, hasCommaDecimal=False (AdminDashboardServiceTest, LocaleFormattingTest).", "PASS"],
    ["TC30", "Design Token & Brand Guards", "Run BrandGuardTest and WebTemplateTokenGuardTest across codebase.", "Zero legacy brand strings (Altitude, skyvoyage, APP_NAME) and zero illegal hex literals.", "100% pass (BrandGuardTest, WebTemplateTokenGuardTest).", "PASS"],
    ["TC31", "Portable Config & Busy Port Fallback", "Hold port 18090 busy and launch WebApplication with SKY_ROYAL_WEB_PORT=18090.", "WebApplication falls back automatically to port 18091 and updates QR base URL.", "Bound http://localhost:18091, GET / HTTP 200 (WebPortFallbackTest, P4 proof).", "PASS"],
    ["TC32", "Fresh Stranger-Clone Setup & Idempotency", "Run scripts\\run-setup.cmd twice against throwaway DB sr_run on port 5433.", "First run creates/seeds DB and prints Setup OK; second run skips existing DB safely.", "Exit 0 on both runs; Setup OK printed (P4 live proof).", "PASS"],
    ["TC33", "Desktop Session Isolation (Guest Guard)", "Open desktop My Bookings view while unauthenticated (AppSession.getCurrentUser() == null).", "Guest sees a 'Sign in to see your bookings' screen and never sees another user's bookings.", "Verified by MyBookingsSessionIsolationTest and P8 live proof.", "PASS"],
    ["TC34", "Rolling 14-Day Schedule & Cabin Photos", "Seed sr_docs and query popular routes (JNB-CPT, JNB-DUR) plus seat map sections 1, 2, and 3.", "6 to 10 departures per day on popular routes; distinct cabin photo per section and JNB/PLZ destination photos.", "Verified on sr_docs and P5f/P8 desktop and web checks.", "PASS"],
]

TROUBLESHOOTING_ROWS = [
    ["Port busy", "run-web.cmd automatically probes starting at 8080 and moves to the next free port (such as 8081), printing the active URL and updating QR verification links. You may also set SKY_ROYAL_WEB_PORT before launching."],
    ["PostgreSQL not found or wrong password", "Confirm PostgreSQL is installed and its Windows service (postgresql-x64-*) is running. Re-run .\\scripts\\run-setup.cmd with the exact postgres password; scripts probe the configured port, then 5432, then 5433."],
    ["Java version too old", "Java 17 or newer (full JDK with javac) is required. Run java -version; if below 17, install Temurin JDK 17+ from adoptium.net and reopen the terminal."],
    ["Maven not installed globally", "Use the bundled Maven Wrapper (.\\mvnw.cmd) and helper scripts from the repository root. Internet access is needed on the first build to populate %USERPROFILE%\\.m2."],
    ["Windows Firewall prompt", "Click Allow access on Private networks when java.exe binds the web port so local browsers and phones on the same Wi-Fi can open the site and verify QR codes."],
    ["Low-RAM desktop test fork crash", "JavaFX headless fork tests require roughly 1 GB of free physical RAM on Windows. Close heavy apps or pass MAVEN_OPTS='-Xms64m -Xmx384m -XX:ReservedCodeCacheSize=96m' when testing core."],
    ["Blank web page", "Wait until 'Sky Royal Website is running at: http://localhost:<port>' appears in the terminal, visit the exact printed port, hard-refresh (Ctrl+F5), and confirm run-setup.cmd ended with Setup OK."],
]

PHOTO_CREDITS = [
    ["aerial-1.jpg", "Elizabeth Camp (Unsplash)", "Unsplash licence"],
    ["aerial-2.jpg", "Patrick Langwallner (Unsplash)", "Unsplash licence"],
    ["aerial-3.jpg", "Max Andrey (Unsplash)", "Unsplash licence"],
    ["cabin-economy-1.jpg", "Mohammad Arrahmanur (Unsplash)", "Unsplash licence"],
    ["karsten-winegeart-TkKTqPPkWP4-unsplash.jpg", "Karsten Winegeart (Unsplash)", "Unsplash licence"],
    ["lukas-souza-tgO1I1Mo0Z0-unsplash.jpg", "Lukas Souza (Unsplash)", "Unsplash licence"],
    ["tom-barrett-M0AWNxnLaMw-unsplash.jpg", "Tom Barrett (Unsplash)", "Unsplash licence"],
    ["sam-mgrdichian-K-xpEUtdtGY-unsplash.jpg", "Sam Mgrdichian (Unsplash)", "Unsplash licence"],
    ["thanuj-mathew-BWmxrpY9h3M-unsplash.jpg", "Thanuj Mathew (Unsplash)", "Unsplash licence"],
    ["evgeni-tcherkasski-_-tEJGpBS5w-unsplash.jpg", "Evgeni Tcherkasski (Unsplash)", "Unsplash licence"],
    ["dest-johannesburg.jpg", "Jolame Chirwa (Unsplash)", "Unsplash licence"],
    ["dest-gqeberha.jpg", "Christian Asare-Baah (Unsplash)", "Unsplash licence"],
    ["dest-dubai.jpg", "Zq Lee (Unsplash)", "Unsplash licence"],
    ["dest-rome.jpg", "Matteo Del Piano (Unsplash)", "Unsplash licence"],
    ["dest-swiss-alps.jpg", "Ronnie Schmutz (Unsplash)", "Unsplash licence"],
    ["dest-lake-como.jpg", "Julia Solonina (Unsplash)", "Unsplash licence"],
    ["dest-santorini.jpg", "James Ting (Unsplash)", "Unsplash licence"],
    ["dest-monaco.jpg", "Victor He (Unsplash)", "Unsplash licence"],
    ["dest-cinque-terre.jpg", "Wojciech Wyszkowski (Unsplash)", "Unsplash licence"],
    ["dest-valais.jpg", "Patrick Robert Doyle (Unsplash)", "Unsplash licence"],
    ["home-hero.jpg", "Supplied by Group 2", "Project team asset"],
    ["dest-capetown.jpg", "Supplied by Group 2 (scene-capetown-sunset.jpg)", "Project team asset"],
    ["dest-durban.jpg", "Supplied by Group 2", "Project team asset"],
]

# Selected real code snippets (max 15 lines each, exact file paths)
CODE_SNIPPETS = [
    ("Snippet C.1: Salted BCrypt Password Hashing (core/src/main/java/com/group2/flightres/util/PasswordUtil.java)",
     """public static String hash(String plainPassword) {
    return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
}

public static boolean verify(String plainPassword, String hashedPassword) {
    if (plainPassword == null || hashedPassword == null || hashedPassword.isBlank()) {
        return false;
    }
    String normalized = hashedPassword;
    if (normalized.startsWith("$2b$") || normalized.startsWith("$2y$")) {
        normalized = "$2a$" + normalized.substring(4);
    }
    return BCrypt.checkpw(plainPassword, normalized);
}"""),
    ("Snippet C.2: Optimistic Atomic Seat Hold SQL (core/src/main/java/com/group2/flightres/dao/impl/SeatDaoImpl.java)",
     """private static final String HOLD_SEAT_WITH_OWNER_QUERY = \"\"\"
    UPDATE flight_seats
    SET status = 'HELD', held_until = NOW() + make_interval(secs => ?), held_by = ?
    WHERE flight_seat_id = ?
      AND (status = 'AVAILABLE' OR (status = 'HELD' AND held_by = ?))
    RETURNING held_until
    \"\"\";

private static final String RELEASE_EXPIRED_HOLDS_QUERY = \"\"\"
    UPDATE flight_seats
    SET status = 'AVAILABLE', held_until = NULL
    WHERE status = 'HELD' AND held_until < NOW()
    \"\"\";"""),
    ("Snippet C.3: Simulated Payment Approval and Decline Logic (core/src/main/java/com/group2/flightres/service/SimulatedPaymentGateway.java)",
     """if (!isValidLuhn(sanitizedCard)) {
    return PaymentResult.declined("Card number failed Luhn checksum", brand, last4);
}
if (!isValidExpiry(expiry)) {
    return PaymentResult.declined("Card has expired or invalid format", brand, last4);
}
if (DECLINED_TEST_CARD.equals(sanitizedCard)) {
    logger.info("Simulated payment DECLINED for test card ending in {}", last4);
    return PaymentResult.declined("Card was declined by issuing bank", brand, last4);
}
String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
return PaymentResult.approved(txnRef, brand, last4);"""),
    ("Snippet C.4: 24-Hour Free Cancellation Policy (core/src/main/java/com/group2/flightres/service/CancellationPolicy.java)",
     """Instant now = clock.instant();
Duration remaining = Duration.between(now, departureInstant);
if (remaining.isZero() || remaining.isNegative()) {
    return new Decision(false, false, BigDecimal.ZERO,
            "This flight has already departed and cannot be cancelled.");
}
if (remaining.compareTo(MINIMUM_NOTICE) < 0) {
    return new Decision(false, false, BigDecimal.ZERO,
            "Free cancellation closes 24 hours before scheduled departure.");
}
BigDecimal refund = totalPaid != null ? totalPaid : BigDecimal.ZERO;
return new Decision(true, false, refund,
        "Eligible for a 100% full refund (departure is at least 24 hours away).");"""),
    ("Snippet C.5: HTTP Security Headers and CSRF Filter (web/src/main/java/com/group2/flightres/web/WebApplication.java)",
     """app.before(ctx -> {
    ctx.header("X-Content-Type-Options", "nosniff");
    ctx.header("X-Frame-Options", "DENY");
    ctx.header("X-XSS-Protection", "1; mode=block");
    ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
    if (ctx.sessionAttribute("csrf_token") == null) {
        ctx.sessionAttribute("csrf_token", UUID.randomUUID().toString());
    }
    if ("POST".equalsIgnoreCase(ctx.method().name())) {
        String sTok = ctx.sessionAttribute("csrf_token");
        String fTok = ctx.formParam("csrf_token");
        if (sTok == null || !sTok.equals(fTok)) {
            ctx.status(403); ctx.skipRemainingHandlers();
        }
    }
});"""),
]
