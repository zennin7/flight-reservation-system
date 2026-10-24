# ✈️ SkyVoyage — Flight Reservation System

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)]()
[![JavaFX](https://img.shields.io/badge/JavaFX-21-orange.svg)]()
[![Javalin](https://img.shields.io/badge/Javalin-6.1.3-purple.svg)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg)]()
[![License](https://img.shields.io/badge/license-Academic-lightgrey.svg)]()

> A production-grade multi-channel airline flight reservation platform featuring a high-fidelity **JavaFX desktop application** and a server-rendered **responsive web application**, backed by a shared business service layer and a unified PostgreSQL database.

---

## 📖 About the Project

**SkyVoyage** was engineered to go far beyond a typical university database project. Instead of basic forms wired to tables, the platform delivers an authentic, end-to-end airline booking experience across both desktop and web channels:

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
| **Database** | PostgreSQL | 18 (Port 5433) | Relational database with ACID transactions and constraints |
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
                      │  PostgreSQL 18 (Port 5433)│
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

## 🗄️ Database Setup (PostgreSQL)

The system connects to **PostgreSQL** running on port **`5433`** with the database named **`flight_reservation`**.

### 1. Initialize the Database
Open PostgreSQL via `psql` or pgAdmin:
```sql
CREATE DATABASE flight_reservation;
```

### 2. Run Database Migrations
Execute the SQL migration scripts in order from the repository root:
```bash
# 1. Base schema: tables, enums, triggers, and dynamic pricing view
psql -h localhost -p 5433 -U postgres -d flight_reservation -f core/sql/schema.sql

# 2. Authentication seed data: default users and role-based accounts
psql -h localhost -p 5433 -U postgres -d flight_reservation -f core/sql/seed_auth_data.sql

# 3. Flights and seat seed data: domestic routes, aircraft layouts, and seats
psql -h localhost -p 5433 -U postgres -d flight_reservation -f core/sql/seed_flights_data.sql
```

### 3. Configure Database Connection (`app.json`)
Copy the configuration template:
```bash
cp core/src/main/resources/com/group2/flightres/app.json.example core/src/main/resources/com/group2/flightres/app.json
```

Verify that `app.json` contains your local PostgreSQL credentials:
```json
{
  "dbHost": "localhost",
  "dbPort": 5433,
  "dbName": "flight_reservation",
  "dbUsername": "postgres",
  "dbPassword": "YourPasswordHere",
  "poolSize": 10
}
```

---

## 🚀 Running the Applications

### 1. Build and Test the Complete Platform
Compile all modules and run all automated tests (unit tests, optimistic hold race tests, and web flow integration tests):
```bash
mvn clean verify
```

### 2. Run the JavaFX Desktop Application
```bash
mvn -pl desktop javafx:run
```

- **Default Admin Account:** `admin` / `admin123`
- **Default Customer Account:** `david` / `password123`

### 3. Run the Web Application
```bash
mvn -pl web exec:java
```

Once started, open your browser at:
👉 **`http://localhost:7070`**

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
