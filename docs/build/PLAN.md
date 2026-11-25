# Sky Royal Flight Reservation System - Documentation Plan (P7-docs)

This plan maps every mandatory requirement from `Final_Year_Project_Assignment_Guideline.pdf` (Gauteng City College, Mr Owami, 200 marks total, 40 marks for Documentation) to the Sky Royal Flight Reservation System codebase on branch `dev`.

Note: Student numbers are omitted from this file and from all repository files. They are read at build time from `%USERPROFILE%\sky-royal-agent\docs-in\students.txt` exclusively when generating `docs-out\Sky_Royal_Documentation_FULL.docx` and `.pdf`.

## 1. Output Files and Build Pipeline

- Build script: `docs/build/build_docs.py` (single Python script using `python-docx` for `.docx` and 2-pass headless Microsoft Edge + PyMuPDF for `.pdf` with verified page-number synchronization).
- External outputs (`%USERPROFILE%\sky-royal-agent\docs-out\`):
  1. `Sky_Royal_Documentation_FULL.docx` and `Sky_Royal_Documentation_FULL.pdf` (contains student numbers for submission)
  2. `Sky_Royal_Documentation_PUBLIC.docx` and `Sky_Royal_Documentation_PUBLIC.pdf` (replaces every student number with `on file with the lecturer`)
- Committed repository outputs:
  - `docs/build/` (plan, diagram generators, build script, QA script)
  - `docs/Sky_Royal_Documentation_PUBLIC.docx` and `docs/Sky_Royal_Documentation_PUBLIC.pdf`
  - `docs/P7-docs-report.txt`

## 2. Format, Typography, and Design Tokens

- Paper: A4 portrait (`210 mm x 297 mm`), 2.5 cm margins on all sides, 1.5 line spacing.
- Fonts: Times New Roman for cover title and section banners; Arial 11 pt for body text and tables; Courier New 9 pt for code blocks and SQL/CLI snippets.
- Sky Royal palette: Ink `#15181c`, Sky `#4da3ff`, Cream `#f1eedf`, White `#ffffff`.
- Page numbering: Cover unnumbered; Roman numerals (ii, iii, iv) for front matter (Declaration, TOC, Lists); Arabic numerals starting at 1 on Section 4 (Introduction).
- Every one of the 13 sections starts on a new page (`page_break_before = True` on Heading 1).
- Automatic Word TOC, List of Figures, and List of Tables fields (`w:dirty="true"`) plus verified cached page numbers synchronized from the 2-pass PDF render.
- Style rules: Plain student English, no marketing buzzwords, no em dashes, no en dashes, no spaced hyphens used as dashes, solid lines only in all diagrams.

## 3. Exact 13-Section Outline (Guideline Section 5 Order)

1. **Cover Page**: Gauteng City College name and logo (extracted from guideline PDF), project title (`Sky Royal Flight Reservation System`), hero photograph (`home-hero.jpg`), course and year (marked slot: `<Course name and year to be confirmed>`), Group 2, lecturer (`Mr Owami`), submission date (`27 November 2026`), GitHub link (`https://github.com/zennin7/flight-reservation-system`), and all 10 member names (with student numbers in FULL only).
2. **Declaration**: Academic integrity statement confirming the work is Group 2's own, with signature and date lines for all 10 members.
3. **Table of Contents**: Automatic Table of Contents (numbered sections with page numbers), List of Figures, and List of Tables.
4. **Introduction**: Project background, problem statement, aim and objectives, scope, and honest limitations (simulated payment, file outbox email default, plain text passport storage, JavaFX desktop with written approval from Mr Owami, one-way flights only, desktop and web visual differences).
5. **Requirements Analysis**: Functional requirements (FR01 to FR18), non-functional requirements (NFR01 to NFR10), user roles (`CUSTOMER`, `ADMIN`, `AGENT` enum note), and user stories / use cases.
6. **System Design**: Multi-module architecture (Core, Desktop, Web, PostgreSQL, Simulated Payment Gateway, File Outbox Email), Use Case Diagram, Class Diagrams (Domain & Services; Web & Desktop Controllers), Chen-style Entity Relationship Diagram (Overview + 3 Detail ERDs matching `erd-reference.png`), Database Design & Normalization (UNF to 1NF, 2NF, 3NF, exact 10 tables + 1 view + 6 enums from `core/sql/schema.sql` and all 5 migrations), Flowcharts (Booking, Payment, Seat Hold and Release, Login and Roles), Sequence Diagram (Booking and Payment), and Interface Wireframes / Annotated Screens.
7. **Implementation**: Technology stack and exact Maven versions (`pom.xml`), module descriptions (`core`, `desktop`, `web`), security implementation (BCrypt cost 12, PreparedStatements, `ValidationUtil`, CSRF UUID tokens, HTTP security headers, owner-only access, rate throttles, HMAC-SHA256 QR verification), error handling and logging (SLF4J + Logback, global Javalin exception handler), real web screenshots (1440 px / 1280 px) and real desktop screenshots, and important code explanations.
8. **Testing**: Test plan, automated JUnit 5 test suite summary (`core`: 149 tests, `desktop`: 58 tests, `web`: 39 tests; 246 total passing tests from surefire reports, plus live verification proofs), 32 real test cases in structured tables (ID, area, steps, expected, actual, status), and known bugs / honest limitations.
9. **User Manual & Installation Guide**: Prerequisites, 5-step Windows Quick Start from `README.md` (`run-setup.cmd`, `run-web.cmd`, `run-desktop.cmd`, `reseed.cmd`), demo logins and simulated test cards, troubleshooting table, and step-by-step usage guide for both desktop and web applications.
10. **GitHub & Team Collaboration**: GitHub repository URL, summary of commits (195 commits across all branches, 181 on `dev`), branches (16 local, 26 remote), and pull requests (#1 to #21), contribution history chart/visual (plus marked slot if GitHub web screenshot is added manually), and the 10-member team contribution table.
11. **Conclusion & Recommendations**: Summary of achievements against objectives, technical challenges faced and resolved (low-RAM Maven forks, seat hold race conditions, PostgreSQL port portability, locale formatting, visual design consistency), lessons learned, and future recommendations.
12. **References**: At least 15 real, verified Harvard-style references (Java 17, JavaFX, Javalin, PostgreSQL, Thymeleaf, HikariCP, jBCrypt, OWASP Top 10, OWASP Password Storage, OWASP SQL Injection Prevention, ZXing, OpenPDF, JUnit 5, Apache Maven, OMG UML, RFC 5545 iCalendar, Logback, SLF4J), all accessed 24 November 2026, all cited in text, plus Photo Credits table from `CREDITS_TO_FILL.md`.
13. **Appendices**: Appendix A (Demo logins, simulated test cards, and run commands), Appendix B (Repository folder structure), Appendix C (Selected code snippets in Courier New, max 15 lines each with file path), Appendix D (Mr Owami JavaFX approval slot), Appendix E (Meeting minutes slot).

## 4. Planned Figures (300 DPI PNG, Solid Lines Only)

- Figure 1: Sky Royal Multi-Module System Architecture (`fig01_architecture.png`)
- Figure 2: System Use Case Diagram (`fig02_use_cases.png`)
- Figure 3: Core Domain and Service Layer Class Diagram (`fig03_class_core.png`)
- Figure 4: Web and Desktop Presentation Layer Class Diagram (`fig04_class_ui.png`)
- Figure 5: Entity Relationship Diagram Overview in Chen Notation (`fig05_erd_overview.png`)
- Figure 6: Chen ERD Detail 1 - Users, Airports, and Flights (`fig06_erd_detail_1.png`)
- Figure 7: Chen ERD Detail 2 - Aircraft, Seat Templates, and Flight Seats (`fig07_erd_detail_2.png`)
- Figure 8: Chen ERD Detail 3 - Bookings, Booking Seats, and Payments (`fig08_erd_detail_3.png`)
- Figure 9: Booking Flow Diagram (`fig09_flow_booking.png`)
- Figure 10: Payment Processing Flow Diagram (`fig10_flow_payment.png`)
- Figure 11: Seat Hold and Release Concurrency Flow (`fig11_flow_seathold.png`)
- Figure 12: Login, Registration, and Role Routing Flow (`fig12_flow_login.png`)
- Figure 13: Booking and Payment Sequence Diagram (`fig13_sequence_booking.png`)
- Figure 14: Annotated Web and Desktop Interface Wireframes (`fig14_wireframes.png`)
- Figures 15 to 22: Real Web Application Screenshots (Home/Search, Flight Results, Seat Map, Checkout, Confirmation, My Bookings, Boarding Pass PDF, Admin Dashboard)
- Figures 23 to 29: Real Desktop Application (JavaFX) Screenshots (Login, Flight Search, Seat Map, Checkout, Confirmation, My Bookings, Admin Dashboard)
- Figure 30: Git Commit and Pull Request Activity Timeline (`fig30_git_activity.png`)

## 5. Inferred Team Roles, Usernames, and Contributions

Derived from `git log --all`, pull request merge history (#1 to #21), `docs/team-intake/*.md`, and `students.txt`:

| Member | GitHub Username | Role | Contribution | Status |
|---|---|---|---|---|
| Buhlebamaqwabe Gumede | (commit author: Buhlebamaqwabe Gumede; handle to confirm) | Booking Confirmation UI Developer | Built JavaFX booking confirmation screen prototype (`app.java`, `index.fxml`, `steyle.css`) on branch `feature/buhle/confirmation-screen` (PR #9). | Inferred from git & team-intake |
| David Elamenji | zennin7 | Technical Integrator and Lead Architect | Architecture, database, core services, desktop app, website integration, payments, QR boarding pass, testing and code review (177 commits). | Confirmed in students.txt |
| Gaorekwe Petja | petjagaorekwe-dot | Admin Dashboard Developer | Submitted Admin Dashboard implementation code on branch `petjagaorekwe/submission` (PR #12). | Inferred from git & team-intake |
| Keahana Campbell | (commit author: Keahana Campbell; handle to confirm) | Booking Confirmation Developer | Built Maven JavaFX booking confirmation module (`BookingConfirmationController.java`, `booking-confirmation.fxml`, `styles.css`) on branch `submission/keahana` (PR #3). | Inferred from git & team-intake |
| Koketso Makweya | (commit author: Koketso Makweya; handle to confirm) | Login and Authentication UI Developer | Built JavaFX login module (`LoginController.java`, `Main.java`, `login.fxml`, `login.css`) on branch `submission/koketsomakweya` (PR #7). | Inferred from git & team-intake |
| Luyanda Njolo | Yandaaaaa | Admin Dashboard Developer | Submitted Admin Dashboard implementation code and merged PR #13 on branch `Yandaaaaa/submission`. | Inferred from git & team-intake |
| Omphemetse Sinikiwe Masondo | SinikiweMasondo | Web Frontend and Checkout Developer | Built the original website home page (`index.html`, `style.css`, `script.js`), assigned to checkout flow, merged PR #5, and co-authored 36 website integration commits. | Inferred from git & team-intake |
| Theledi Ramouna Maluleka | ThelediMaluleka | Web Login Developer and Project Coordinator | Built the original website login page (`index.html`, `styles.css`), worked on the original website with Nikki, merged PR #4, co-authored 36 website commits, and handled team coordination, presentation and documentation material. | Partially confirmed in students.txt; role & commits inferred |
| Tshepiso Leepile | Tshepiso-Sky | Networking and Security Researcher | Produced security notes, Cisco Packet Tracer network topology, and security documentation on branch `feature/Tshepiso/networking-and-security` (PR #11). | Inferred from git |
| Ubuntu Ayabonga Mhlongo | UAM-IN-TECH | Flight Search Developer | Built JavaFX flight search controller, FXML view, DAO interface/implementation, and SQL (`FlightSearchController.java`, `FlightDaoImpl.java`, `Flight.fxml`, `flight.sql`) and merged PR #10. | Inferred from git & team-intake |
