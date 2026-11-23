# ✈️ Sky Royal — Flight Reservation System

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)]()
[![JavaFX](https://img.shields.io/badge/JavaFX-21-orange.svg)]()
[![Javalin](https://img.shields.io/badge/Javalin-6.1.3-purple.svg)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg)]()
[![License](https://img.shields.io/badge/license-Academic-lightgrey.svg)]()

> A production-grade multi-channel airline flight reservation platform featuring a high-fidelity **JavaFX desktop application** and a server-rendered **responsive web application**, backed by a shared business service layer and a unified PostgreSQL database.

---

## 📖 About the Project

**Sky Royal** was engineered to go far beyond a typical university database project. Instead of basic forms wired to tables, the platform delivers an authentic, end-to-end airline booking experience across both desktop and web channels:

- 🪑 **Interactive Top-Down Cabin Fuselage** — Real 2D seat map with First Class (2×2) and Economy (3×3) seating layouts, live color-coded seat statuses (Available, Held, Booked), and optimistic concurrency locks.
- ⚡ **Optimistic Seat Hold Concurrency** — Atomic SQL seat holds (`UPDATE flight_seats SET status='HELD' WHERE status='AVAILABLE'`) that automatically expire if abandoned and prevent race conditions.
- 💳 **ACID Transactional Booking** — Single database transaction generating unique 6-character PNR booking references, inserting passengers and payment records, and transitioning held seats to booked.
- 📄 **Automated PDF Boarding Passes** — Instant e-ticket vouchers and OpenPDF boarding pass generation with flight corridors, seat assignments, and passenger barcodes.
- 📊 **Real-Time Admin Intelligence** — SQL aggregate dashboards displaying revenue, booking volumes, seat-load factors, and 7-day booking trajectory trends.
- 🌐 **Server-Rendered Responsive Web Experience** — Mobile-first web application built with Javalin 6 and Thymeleaf, free from client-side JavaScript frameworks, protected by CSRF session tokens and strict security headers.

---

## 🛠️ Technology Stack

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| **Language** | Java | 17 LTS / 21 LTS | Core programming language across all modules |
| **Desktop UI** | JavaFX | 21.0.3 | Native cross-platform desktop UI with CSS design tokens |
| **Web Server** | Javalin | 6.1.3 | Lightweight, embedded HTTP/REST server |
| **Template Engine**| Thymeleaf | 3.1.2.RELEASE | Server-rendered HTML5 templates with OGNL expressions |
| **Database** | PostgreSQL | 17 / 18 (Port 5432 or 5433) | Relational database with ACID transactions and constraints |
| **Connection Pool**| HikariCP | 5.1.0 | High-performance JDBC connection pooling |
| **Password Hashing**| jBCrypt | 0.4.3 | Secure salted password hashing for authentication |
| **PDF Generation** | OpenPDF | 1.3.39 | Dynamic boarding pass PDF compilation and export |
| **Data Serialization**| Jackson | 2.17.0 | JSON configuration parsing (`app.json`) |
| **Logging** | SLF4J + Logback | 2.0.12 / 1.5.3 | Structured diagnostic and transaction logging |
| **Build Tool** | Apache Maven | 3.9+ | Multi-module dependency management and build pipeline |
| **Testing** | JUnit 5 | 5.10.2 | Comprehensive unit, concurrency, and integration testing |

---

## 🏗️ Multi-Module Architecture

The repository is structured as a Maven multi-module project ensuring strict separation of concerns and zero duplicate SQL logic:

```
flight-reservation-system (Root Parent POM)
 ├── core/          # Domain models, DAO interfaces & implementations,
 │                  # SQL migrations, transactional services, and unit tests
 ├── desktop/       # JavaFX 21 desktop application, FXML views,
 │                  # CSS themes (Light & Dark), and UI controllers
 └── web/           # Javalin 6 web server, Thymeleaf templates,
                    # CSS design system, and automated web flow tests
```

```
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│       JavaFX Desktop App        │       │       Javalin Web Module        │
│  (FXML + Controllers + Motion)  │       │   (Thymeleaf + CSS Templates)   │
└────────────────┬────────────────┘       └────────────────┬────────────────┘
                 │                                         │
                 └──────────────────┬──────────────────────┘
                                    │
                                    ▼
                      ┌───────────────────────────┐
                      │    Core Service Layer     │
                      │  (FlightService,          │
                      │   SeatMapService,         │
                      │   BookingService,         │
                      │   DashboardStatsService)  │
                      └─────────────┬─────────────┘
                                    │
                                    ▼
                      ┌───────────────────────────┐
                      │    Data Access Objects    │
                      │  (PreparedStatement DAOs) │
                      └─────────────┬─────────────┘
                                    │
                                    ▼
                      ┌───────────────────────────┐
                      │ PostgreSQL (5432 / 5433) │
                      │  (Database & Views)       │
                      └───────────────────────────┘
```

---

## 🖼️ Application Screenshots

> Application screenshots are generated into `docs/screenshots/` via `ScreenshotRunner` (`mvn -pl desktop javafx:run -Pscreenshots`) and web capture scripts (`scripts/generate_screenshots.cmd`).

### 💻 Desktop Application (JavaFX 21)

| Screen | Light Mode | Dark Mode |
|---|---|---|
| **01 Home & Search** | Pending reskin capture | Pending reskin capture |
| **02 Split-Screen Login** | Pending reskin capture | Pending reskin capture |
| **03 Flight Results** | Pending reskin capture | Pending reskin capture |
| **04 Fuselage Seat Map** | Pending reskin capture | Pending reskin capture |
| **05 Checkout Summary** | Pending reskin capture | Pending reskin capture |
| **06 Confirmation Voucher** | Pending reskin capture | Pending reskin capture |
| **07 Admin Dashboard** | Pending reskin capture | Pending reskin capture |

### 🌐 Web Application (Javalin 6 + Thymeleaf)

| Page | Desktop View (1200×900) | Mobile View (390×844) |
|---|---|---|
| **01 Home Landing Page** | Pending reskin capture | Pending reskin capture |
| **02 Available Flights** | Pending reskin capture | Pending reskin capture |
| **03 Seat Selection & Checkout** | Pending reskin capture | Pending reskin capture |
| **04 E-Ticket Confirmation Voucher** | Pending reskin capture | Pending reskin capture |
| **05 Split Hero Login** | Pending reskin capture | Pending reskin capture |
| **06 About Us & Fleet** | Pending reskin capture | Pending reskin capture |
| **07 Concierge & Contact** | Pending reskin capture | Pending reskin capture |

---

## 🚀 Quick Start (5 Steps on Windows)

1. **Install Java 17+** (JDK, including `javac`): download Temurin JDK 17 LTS from [adoptium.net](https://adoptium.net) or run `winget install EclipseAdoptium.Temurin.17.JDK`, then reopen your terminal.
2. **Install PostgreSQL** from [postgresql.org/download/windows](https://www.postgresql.org/download/windows/) (or run `winget install PostgreSQL.PostgreSQL.18`) and **remember the `postgres` password** you choose during setup. *(Both port `5432` and `5433` are detected automatically.)*
3. **Clone the repository** (you do **not** need to install Maven separately — the repository includes the Maven Wrapper `mvnw.cmd`):
   ```cmd
   git clone <repository-url>
   cd flight-reservation-system
   ```
4. **Run setup once** (or double-click `scripts\run-setup.cmd`). It checks Java, Maven Wrapper, and PostgreSQL, probes `5432` / `5433`, creates and seeds the database safely, builds the modules, and prints `Setup OK`:
   ```cmd
   .\scripts\run-setup.cmd
   ```
5. **Start the website or desktop app**:
   ```cmd
   .\scripts\run-web.cmd
   ```
   Open the exact `http://localhost:...` URL printed in the terminal (defaults to `8080` and automatically picks the next free port if `8080` is busy). Or run the JavaFX desktop app in a terminal:
   ```cmd
   .\scripts\run-desktop.cmd
   ```

> **Important Notices (Academic Simulation):**
> - **Payment is simulated:** Entering card details simulates approval or decline locally. No real card network is contacted and no real money moves.
> - **Welcome email is not verified / does not send externally by default:** Unless SMTP credentials are configured via environment variables (`SKY_ROYAL_SMTP_USER` and `SKY_ROYAL_SMTP_PASSWORD`), welcome emails are written locally to the `outbox\` folder so registration never crashes or blocks.

### Demo Logins & Test Cards

Use either **username** or **email** on `/login` and in the desktop app:

| Role | Username | Email | Password |
|---|---|---|---|
| **Admin** | `testadmin` | `testadmin@example.com` | `Passw0rd!` |
| **Customer** | `customer` | `customer@example.com` | `Passw0rd!` |

- **Simulated Approved Card:** `4242 4242 4242 4242` (any future expiry date + 3-digit CVV)
- **Simulated Declined Card:** `4000 0000 0000 0002`

> Configuration can also be overridden with environment variables (`SKY_ROYAL_DB_HOST`, `SKY_ROYAL_DB_PORT`, `SKY_ROYAL_DB_NAME`, `SKY_ROYAL_DB_USER`, `SKY_ROYAL_DB_PASSWORD`, `SKY_ROYAL_WEB_PORT`, and legacy `LAUNCH_DB_*` / `PORT`). Refresh rolling 14-day flight dates any time with `.\scripts\reseed.cmd`, or run the full verification suite with `.\mvnw.cmd clean verify`.

---

## 🛠️ Troubleshooting

| Problem | Cause & Fix |
|---|---|
| **Port busy** | No manual change needed: `run-web.cmd` automatically probes starting at `8080`, moves to the next free port (e.g. `8081`), prints `Sky Royal Website is running at: http://localhost:<port>`, and updates QR verification links to match. You can also set `$env:SKY_ROYAL_WEB_PORT = "8085"` beforehand. |
| **Postgres not found / wrong password** | Ensure `PostgreSQL` is installed (`psql.exe` available) and its Windows service (`postgresql-x64-*`) is running in **Services**. If authentication fails, re-run `.\scripts\run-setup.cmd` and enter the exact `postgres` password set when PostgreSQL was installed (scripts probe configured port, `5432`, then `5433`). |
| **Java too old** | Java 17 or newer (full JDK) is required. Run `java -version`; if it reports `< 17` or a JRE without `javac`, install Temurin JDK 17+ from [adoptium.net](https://adoptium.net) (`winget install EclipseAdoptium.Temurin.17.JDK`) and open a new terminal. |
| **Maven problems** | Use the bundled wrapper (`.\mvnw.cmd`) and helper scripts instead of a global `mvn`. Ensure you run from the repository root and have internet access on the very first build so Maven can cache dependencies into `%USERPROFILE%\.m2`. |
| **Firewall prompt** | Windows Defender Firewall may prompt when `java.exe` binds the web port. Click **Allow access** (Private networks) so your browser (or another device on local Wi-Fi using the printed LAN IP) can connect cleanly. |
| **Intermittent test fork crash** (`Error occurred in starting fork` in `:desktop`) | JavaFX headless fork tests need roughly 1 GB+ of free physical RAM on Windows. Close browsers/other heavy apps to free RAM, then rerun `.\mvnw.cmd clean verify` once. |
| **Blank page** | Check the terminal and wait until `Sky Royal Website is running at: http://localhost:<port>` appears, make sure you visit the **exact port printed** (in case `8080` fell back to `8081`), hard-refresh (`Ctrl+F5`), and verify `.\scripts\run-setup.cmd` ended with `Setup OK`. |

---

## 👥 The Team & Roles

| Member Name | Role & Core Responsibilities |
|---|---|
| *[Fill in Name]* | Master Integrator & Multi-Module Architecture |
| *[Fill in Name]* | Database Schema, Concurrency Locks & Core Services |
| *[Fill in Name]* | JavaFX Desktop UI & Motion Animations |
| *[Fill in Name]* | Web Server Architecture & Responsive Thymeleaf Templates |
| *[Fill in Name]* | User Authentication, Role Permissions & CSRF Security |
| *[Fill in Name]* | Quality Assurance, Automated Tests & Documentation |

---

## 📄 Project Brief & Specification Reference

- 📋 **School Project Brief:** [Assignment Project Brief](docs/ASSIGNMENT_BRIEF.md) *(Reference specification for academic evaluation)*
- 🎨 **Design System Specification:** [Elevate Design Specification](docs/design/ELEVATE_SPEC.md) *(Detailed tokens, component ASCII schematics, and motion guidelines)*
- 🗃️ **Database Schema Documentation:** [SQL Schema Definition](core/sql/schema.sql)
