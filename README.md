# ✈️ Flight Reservation System — Group 2

## Our Mission

> To work together as a team to design and develop a practical, reliable, and user-focused solution while applying the programming, database, system analysis, and development skills we've learned.

---

## About the Project

A production-grade desktop Flight Reservation System built with JavaFX and PostgreSQL. Beyond meeting the assignment brief, we set out to build something we'd genuinely be proud to demo — with a real interactive cabin seat map, dynamic pricing logic, and automated PDF boarding pass generation, not just static forms wired to a database.

### Key Features
- 🔐 **Secure Authentication** — role-based access control (Admin / Agent / Customer), BCrypt password hashing
- 🔎 **Flight Search** — search by origin, destination, and date against a live PostgreSQL backend
- 🪑 **Visual Cabin Experience** — an interactive, color-coded 2D seat map rendered from real seat coordinate data (not a dropdown list)
- 💰 **Dynamic Pricing** — seat-class-based pricing calculated from a base fare and multiplier
- 🧾 **PDF Boarding Passes** — generated automatically on booking confirmation
- 📊 **Admin Dashboard** — booking and revenue overview for administrative users

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| UI Framework | JavaFX 21 (modern CSS styling) |
| Database | PostgreSQL |
| Build Tool | Maven |
| Connection Pooling | HikariCP |
| PDF Generation | OpenPDF |
| Password Hashing | jBCrypt |
| Data Handling | Jackson |
| Logging | SLF4J + Logback |
| Testing | JUnit 5 |

---

## Architecture

The system follows a strict **MVC / DAO** architecture with clean separation of concerns:

```
Controller (JavaFX/FXML) → Service (business logic) → DAO (PreparedStatement queries) → PostgreSQL
```

- **Controllers** handle UI events only — no SQL, no business logic.
- **Services** own business rules (pricing, authentication, booking flow).
- **DAO interfaces + implementations** are the only layer that talks to the database, exclusively via PreparedStatements.

Full schema, folder structure, and design decisions are documented in [`sql/schema.sql`](./sql/schema.sql) and the project's SRS documentation.

---

## The Team

| Member | Role |
|---|---|
| David | Master Integrator, Seat Map UI, Network Architect |
| Theledi & Kay | Login & Authentication |
| Ubuntu | Flight Search Flow |
| Petja & Yanda | Admin Dashboard UI |
| Sinikiwe | Checkout Summary UI |
| Keahana & Buhle | Booking Confirmation UI |
| Tshepiso | Network Topology, Security & SRS Documentation |

We work in a hub-and-spoke model: isolated `feature/` branches reviewed and merged into `dev` by the Master Integrator, with `main` reserved for stable, presentation-ready releases.

---

## Getting Started

```bash
git clone https://github.com/zennin7/flight-reservation-system.git
cd flight-reservation-system
git checkout dev
```

1. Set up a local PostgreSQL database and run [`sql/schema.sql`](./sql/schema.sql).
2. Configure your database connection in `src/main/resources/com/group2/flightres/app.json`.
3. Build and run with Maven:
```bash
mvn clean javafx:run
```

---

## Branching Workflow

- `main` — protected, stable releases only
- `dev` — integration branch, all features merge here first via Pull Request
- `feature/<name>-<task>` — individual/pair working branches

No one pushes directly to `main` or `dev`. All merges go through Pull Request review.
