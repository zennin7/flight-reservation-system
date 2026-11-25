"""
Native Word (.docx) single-source documentation builder for Sky Royal Flight Reservation System.
Builds BOTH FULL (student numbers in Section 10 table only, external docs-out/ only) and PUBLIC
(student numbers replaced with 'on file with the lecturer', saved to docs-out/ and docs/) as
native Word .docx documents, then updates all TOC/LOF/LOT/SEQ fields via LibreOffice UNO and
exports the .pdf directly from the .docx (single source of truth, zero HTML/PyMuPDF PDF route).

Usage:
    D:\\p7-v2\\Scripts\\python.exe docs\\build\\build_docs.py
"""
import os
import re
import sys
import time
import shutil
import socket
import subprocess
from pathlib import Path

import pymupdf
from PIL import Image
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT
from docx.enum.section import WD_ORIENT, WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import qn, nsdecls

import doc_content as C

REPO_ROOT = Path(__file__).resolve().parents[2]
BUILD_DIR = Path(__file__).resolve().parent
DIAGRAM_DIR = BUILD_DIR / "diagrams"
ASSETS_DIR = BUILD_DIR / "assets"
ASSETS_DIR.mkdir(parents=True, exist_ok=True)

USER_PROFILE = Path(os.environ["USERPROFILE"])
DOCS_IN = USER_PROFILE / "sky-royal-agent" / "docs-in"
DOCS_OUT = USER_PROFILE / "sky-royal-agent" / "docs-out"
DOCS_OUT.mkdir(parents=True, exist_ok=True)
REPO_DOCS = REPO_ROOT / "docs"

LO_SOFFICE = r"C:\Program Files\LibreOffice\program\soffice.exe"
LO_PYTHON = r"C:\Program Files\LibreOffice\program\python.exe"
LO_PROFILE = "file:///D:/p7-final-tmp/lo_profile" if os.path.isdir("D:\\") else f"file:///{(REPO_ROOT / 'docs' / 'build' / 'lo_profile').as_posix()}"

# Sky Royal colour tokens (print-ready, white body pages, thinner dark banners)
HEX_INK = "15181c"
HEX_SKY = "4da3ff"
HEX_CREAM = "f5f2e8"
HEX_BORDER = "cbd5e1"
HEX_MUTED = "4b5563"
HEX_CODE_BG = "f6f8fa"

RGB_INK = RGBColor(0x15, 0x18, 0x1C)
RGB_SKY = RGBColor(0x4D, 0xA3, 0xFF)
RGB_MUTED = RGBColor(0x4B, 0x55, 0x63)
RGB_WHITE = RGBColor(0xFF, 0xFF, 0xFF)

# A4 geometry: 21.0 x 29.7 cm with 2.5 cm margins -> portrait text width = 16.0 cm, landscape = 24.7 cm
PORTRAIT_TEXT_CM = 16.0
LANDSCAPE_TEXT_CM = 24.7


def cm_to_dxa(cm_val: float) -> int:
    return int(round(cm_val * 566.929133858))


def parse_students_txt():
    """Read students.txt at runtime so student numbers never appear in repo source."""
    path = DOCS_IN / "students.txt"
    members = []
    if not path.exists():
        for name, meta in C.TEAM_META.items():
            members.append({
                "name": name,
                "student_no": "on file with the lecturer",
                "github": meta["github"],
                "role": meta["role"],
                "contribution": meta["contribution"],
            })
        return members

    lines = path.read_text(encoding="utf-8").splitlines()
    for line in lines:
        if "|" not in line or line.startswith("Full name"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 2:
            continue
        name = parts[0]
        snum = parts[1]
        meta = C.TEAM_META.get(name, {
            "github": parts[2] if len(parts) > 2 else "to be confirmed",
            "role": "Team Member",
            "contribution": "Contribution recorded in group repository.",
        })
        members.append({
            "name": name,
            "student_no": snum,
            "github": meta["github"],
            "role": meta["role"],
            "contribution": meta["contribution"],
        })
    return members


def extract_college_logo():
    """Extract the Gauteng City College circular logo from page 1 of the guideline PDF onto white BG."""
    logo_path = ASSETS_DIR / "gcc_logo.png"
    pdf_path = DOCS_IN / "Final_Year_Project_Assignment_Guideline.pdf"
    if logo_path.exists():
        return logo_path
    if not pdf_path.exists():
        return None
    doc = pymupdf.open(str(pdf_path))
    page = doc[0]
    pix = page.get_pixmap(dpi=250, clip=pymupdf.Rect(240, 48, 355, 149), alpha=False)
    pix.save(str(logo_path))
    doc.close()
    return logo_path


def get_approval_letter_png():
    """Return 200 DPI PNG of Appendix D approval letter (rendered without altering the letter)."""
    png_path = ASSETS_DIR / "appendix_d_approval_letter.png"
    if png_path.exists():
        return png_path
    # Fallback: check docs-in/approval-letter.pdf
    pdf_direct = DOCS_IN / "approval-letter.pdf"
    if pdf_direct.exists():
        doc = pymupdf.open(str(pdf_direct))
        pix = doc[0].get_pixmap(dpi=200, alpha=False)
        pix.save(str(png_path))
        doc.close()
        return png_path
    return None


def build_document_model(members, is_full: bool):
    """Assemble the complete 13-section structured model for the native Word builder."""
    s_members = []
    for m in members:
        s_members.append({
            "name": m["name"],
            "student_no": m["student_no"] if is_full else "on file with the lecturer",
            "github": m["github"],
            "role": m["role"],
            "contribution": m["contribution"],
        })

    def diag(name):
        return DIAGRAM_DIR / name

    def shot(name):
        return REPO_ROOT / "docs" / "screenshots" / "final" / name

    hero_photo = REPO_ROOT / "web" / "src" / "main" / "resources" / "public" / "photos" / "home-hero.jpg"
    logo_path = extract_college_logo()
    approval_png = get_approval_letter_png()

    sections = []

    # ---- SECTION 4: INTRODUCTION ----
    sections.append({
        "num": "4",
        "title": "Introduction",
        "blocks": [
            {"type": "h2", "num": "4.1", "text": "Project Background"},
            {"type": "p", "text": (
                "Commercial air travel relies on accurate seat inventory, clear fare rules, and dependable ticket verification. "
                "When travellers search for flights and select seats online or at an airline counter, the underlying system must keep both channels synchronized so two passengers can never reserve the same seat. "
                "Sky Royal Flight Reservation System is a dual-channel software platform built by Group 2 at Gauteng City College to model this real-world workflow."
            )},
            {"type": "p", "text": (
                "The platform combines a JavaFX 21 desktop application for airline agents and administrators with a server-rendered Javalin and Thymeleaf web application for travellers (OpenJFX, 2024; Javalin, 2024; Thymeleaf, 2024). "
                "Both clients share the same Sky Royal Glass styling, photography, and Java 17 core library backed by a single PostgreSQL 18 relational database (PostgreSQL Global Development Group, 2026). "
                "This multi-module Maven structure keeps all pricing, concurrency, session isolation, and security rules in one place (Apache Software Foundation, 2024)."
            )},
            {"type": "h2", "num": "4.2", "text": "Problem Statement"},
            {"type": "p", "text": (
                "Small and regional airline reservation prototypes often suffer from four recurring defects. "
                "First, desktop and web interfaces frequently duplicate SQL queries and pricing calculations, causing conflicting fares between channels. "
                "Second, without atomic seat locks, two users who click the same seat at the same second can trigger double bookings. "
                "Third, weak input validation, missing session isolation, and plain text password storage expose user accounts to basic attacks (OWASP Foundation, 2025a). "
                "Fourth, student projects often hardcode local database ports and passwords, which prevents lecturers or teammates from running the system on another computer."
            )},
            {"type": "h2", "num": "4.3", "text": "Aim and Objectives"},
            {"type": "p", "text": (
                "The aim of this project is to design, implement, test, and document a reliable flight reservation system that serves both desktop and web users from a single shared PostgreSQL database. "
                "To achieve this aim, Group 2 defined six measurable objectives:"
            )},
            {"type": "bullets", "items": [
                "Build a shared Java 17 core module containing domain models, PreparedStatement DAOs, transactional services, and pricing policies (Oracle, 2024).",
                "Deliver an interactive 2D cabin seat map with per-section cabin photos (Sections 1, 2, and 3) and 5-minute optimistic seat holds that prevent double booking under concurrent load.",
                "Implement end-to-end checkout, simulated card payment processing, atomic PNR generation, OpenPDF boarding pass export, and HMAC-signed QR verification (LibrePDF, 2024; ZXing Authors, 2024).",
                "Provide a traveller portal (My Bookings) with strict session isolation, RFC 5545 calendar export, and 24-hour free cancellation with simulated refunds (Desruisseaux, 2009).",
                "Provide an administrator operations dashboard showing live 7-day bookings, revenue, seats sold, cancellations, and cabin load factors.",
                "Package portable Windows setup and launch scripts using the Maven Wrapper so any evaluator can run the system in five steps."
            ]},
            {"type": "h2", "num": "4.4", "text": "Scope of the System"},
            {"type": "p", "text": (
                "The system covers scheduled one-way flights across domestic South African routes (including Johannesburg JNB, Cape Town CPT, Durban DUR, and Gqeberha PLZ) as well as seeded international destinations generated on a rolling 14-day schedule with 6 to 10 departures per day on popular routes. "
                "Core workflows include account registration, authentication, flight search and filtering, cabin seat selection with per-section cabin photos, dynamic VAT-inclusive pricing, simulated checkout, digital boarding pass issuance, QR verification, booking cancellation, and administrative reporting. "
                "Both the desktop and web applications share the same visual design language and photography, and the desktop application runs database queries asynchronously on background JavaFX Task threads so the interface stays responsive."
            )},
            {"type": "h2", "num": "4.5", "text": "Limitations of the Current System"},
            {"type": "p", "text": (
                "To keep this documentation honest and transparent, the following real limitations apply to the current release:"
            )},
            {"type": "bullets", "items": [
                "Simulated payment gateway: Web and desktop payments are handled locally by SimulatedPaymentGateway. Test card 4242 4242 4242 4242 approves and 4000 0000 0000 0002 declines. No real banking network is contacted and no real money moves.",
                "Unverified welcome email delivery by default: Welcome emails are rendered via Thymeleaf and saved locally to the outbox/ folder by FileOutboxEmailService unless external SMTP environment variables are configured.",
                "Plain text passport storage: Passenger passport numbers are validated by ValidationUtil and stored as plain text VARCHAR(20) in booking_seats.passport_number rather than encrypted at rest.",
                "Desktop and web layout parity: The JavaFX desktop client matches the web styling and uses the same photography, but desktop and web layouts are close rather than pixel identical.",
                "One-way flights only: The search and booking workflow supports one-way flights; round-trip and multi-leg itineraries are not supported.",
                "QR code verification reachability: The QR code verification link needs a reachable host. It works on the laptop running the web application, while scanning from a separate mobile phone requires a reachable network or public URL.",
                "JavaFX desktop toolkit: The desktop client uses JavaFX 21 instead of Java Swing, developed with written approval from Mr Owami (see Appendix D)."
            ]},
        ],
    })

    # ---- SECTION 5: REQUIREMENTS ANALYSIS ----
    sections.append({
        "num": "5",
        "title": "Requirements Analysis",
        "blocks": [
            {"type": "p", "text": (
                "Requirements were gathered by analysing standard airline booking workflows alongside the mandatory technical deliverables in the Gauteng City College assignment guideline. "
                "Every requirement listed below maps directly to implemented code and automated tests in the repository."
            )},
            {"type": "h2", "num": "5.1", "text": "Functional Requirements"},
            {"type": "table", "title": "Functional Requirements Specification (FR01 to FR18)",
             "headers": ["ID", "Feature", "Requirement Description"],
             "rows": C.FUNCTIONAL_REQUIREMENTS, "widths": [1.6, 3.6, 10.8]},
            {"type": "h2", "num": "5.2", "text": "Non-Functional Requirements"},
            {"type": "table", "title": "Non-Functional Requirements Specification (NFR01 to NFR10)",
             "headers": ["ID", "Quality Attribute", "Requirement Description"],
             "rows": C.NON_FUNCTIONAL_REQUIREMENTS, "widths": [1.8, 3.6, 10.6]},
            {"type": "h2", "num": "5.3", "text": "User Roles and Permissions"},
            {"type": "table", "title": "System User Roles and Access Scope",
             "headers": ["Role", "Identity Type", "Permitted Operations"],
             "rows": C.USER_ROLES, "widths": [3.2, 3.4, 9.4]},
            {"type": "h2", "num": "5.4", "text": "User Stories and Use Case Mapping"},
            {"type": "table", "title": "User Stories Mapped to System Use Cases",
             "headers": ["Story ID", "Actor", "Action (I want to...)", "Benefit (So that...)", "Use Case"],
             "rows": C.USER_STORIES, "widths": [1.6, 2.2, 4.6, 5.4, 2.2]},
        ],
    })

    # ---- SECTION 6: SYSTEM DESIGN ----
    sections.append({
        "num": "6",
        "title": "System Design",
        "blocks": [
            {"type": "p", "text": (
                "Sky Royal follows a strict three-tier modular architecture modelled using standard UML and Chen Entity Relationship notation (Object Management Group, 2017). "
                "Presentation logic lives in desktop/ and web/, business rules and persistence live in core/, and all persistent state resides in PostgreSQL."
            )},
            {"type": "h2", "num": "6.1", "text": "System Architecture"},
            {"type": "p", "text": (
                "Figure 1 shows how the JavaFX desktop client and Javalin web portal both call the same shared service layer in core/. "
                "Database connections are pooled through HikariCP (Wooldridge, 2024), payment requests route through SimulatedPaymentGateway, and registration emails default to FileOutboxEmailService."
            )},
            {"type": "figure", "path": diag("fig01_architecture.png"),
             "caption": "Sky Royal Multi-Module System Architecture showing Desktop, Web, Core, PostgreSQL, Payment Simulation, and Email Outbox."},
            {"type": "h2", "num": "6.2", "text": "Use Case Diagram"},
            {"type": "p", "text": (
                "Figure 2 illustrates the twelve primary use cases across Guest, Registered Customer, System Administrator, Simulated Payment Gateway, and Email Outbox actors."
            )},
            {"type": "figure", "path": diag("fig02_use_cases.png"),
             "caption": "Sky Royal System Use Case Diagram showing primary actors and twelve core use cases."},
            {"type": "h2", "num": "6.3", "text": "Class Diagrams"},
            {"type": "p", "text": (
                "To keep every class name, attribute, and method readable in print, the class design is split into two focused diagrams: Figure 3 covers the core domain models, services, and DAOs, while Figure 4 covers the web and desktop presentation classes."
            )},
            {"type": "figure", "path": diag("fig03_class_core.png"),
             "caption": "Core Module Class Diagram showing domain models, service classes, DAO persistence, pricing, and verification utilities."},
            {"type": "figure", "path": diag("fig04_class_ui.png"),
             "caption": "Presentation Layer Class Diagram showing Javalin web classes and JavaFX desktop controllers."},
            {"type": "h2", "num": "6.4", "text": "Entity Relationship Diagrams (Chen Notation)"},
            {"type": "p", "text": (
                "The database schema defined in core/sql/schema.sql and its five idempotent migration files contains ten normalized relational tables, one SQL pricing view, and six custom PostgreSQL enum types. "
                "To maintain print legibility, Figure 5 presents the full ten-table Chen ERD overview on a landscape page, followed by three detailed attribute ERDs in Figures 6, 7, and 8."
            )},
            {"type": "figure", "path": diag("fig05_erd_overview.png"), "landscape": True,
             "caption": "Entity Relationship Diagram Overview in Chen Notation showing all ten PostgreSQL tables and cardinality relationships."},
            {"type": "figure", "path": diag("fig06_erd_detail_1.png"), "landscape": True,
             "caption": "Chen ERD Detail 1: users, airports, and flights entities with all schema attributes and primary keys."},
            {"type": "figure", "path": diag("fig07_erd_detail_2.png"), "landscape": True,
             "caption": "Chen ERD Detail 2: aircraft_types, seat_class_pricing, seat_templates, and flight_seats with hold ownership attributes."},
            {"type": "figure", "path": diag("fig08_erd_detail_3.png"), "landscape": True,
             "caption": "Chen ERD Detail 3: bookings, booking_seats, and payments including idempotency, card metadata, and refund attributes."},
            {"type": "h2", "num": "6.5", "text": "Database Design and Normalisation (UNF to 3NF)"},
            {"type": "p", "text": (
                "A flight reservation record starts in Unnormalised Form (UNF) as a single flat ticket sheet containing repeating groups of seats, passenger names, airport descriptions, aircraft grid coordinates, and multiple payment attempts. "
                "Normalising this data step by step eliminates update, insertion, and deletion anomalies:"
            )},
            {"type": "bullets", "items": [
                "First Normal Form (1NF): Every column holds atomic values and repeating seat allocations are separated out of bookings into booking_seats. Every table receives a surrogate primary key (BIGSERIAL or SERIAL) or natural enum key (seat_class).",
                "Second Normal Form (2NF): Partial dependencies on composite keys are removed. Physical cabin geometry (seat_row, seat_col, display_row, display_col, is_window, is_aisle) depends only on the aircraft layout and moves to seat_templates, while per-flight availability (status, held_until, held_by) lives in flight_seats.",
                "Third Normal Form (3NF): Transitive dependencies are eliminated. Airport city, country, and IANA timezone depend on airport_id rather than flight_id, so they live in airports. Cabin price multipliers depend on seat_class rather than flight_seat_id, so they live in seat_class_pricing and are combined dynamically by seat_pricing_view. User profile details live in users, and financial metadata lives in payments."
            ]},
            {"type": "table", "title": "PostgreSQL Schema Tables and Views (core/sql/schema.sql plus Migrations)",
             "headers": ["Table / View", "Primary Key", "Key Columns and Constraints", "Purpose"],
             "rows": C.SCHEMA_TABLES, "widths": [3.0, 2.8, 5.8, 4.4]},
            {"type": "p", "text": (
                "Custom PostgreSQL enum types enforce domain validity at the database engine level: user_role (ADMIN, AGENT, CUSTOMER), seat_class (FIRST, ECONOMY), seat_status (AVAILABLE, HELD, BOOKED), flight_status (SCHEDULED, BOARDING, DEPARTED, CANCELLED), booking_status (PENDING, CONFIRMED, CANCELLED, REFUNDED), and payment_status (PENDING, COMPLETED, FAILED, REFUNDED). "
                "Five idempotent SQL migrations extend schema.sql: migration_airports_timezone.sql (mandatory IANA time zones), migration_flight_prefix_sr.sql (Sky Royal SR flight numbers), migration_payments_card_meta.sql (masked card_brand and card_last4), migration_users_email_ci.sql (case-insensitive unique email index ux_users_email_lower), and migration_p2_holds_payments_refund.sql (held_by, idempotency_key, refund_ref, refunded_at, and active-seat partial unique index)."
            )},
            {"type": "h2", "num": "6.6", "text": "Process Flowcharts"},
            {"type": "p", "text": (
                "Figures 9 through 12 document the four critical operational workflows: end-to-end flight booking, secure payment processing with idempotency, optimistic seat hold and release concurrency, and user authentication with role routing."
            )},
            {"type": "figure", "path": diag("fig09_flow_booking.png"),
             "caption": "End-to-End Flight Booking Flowchart from route search to PNR and boarding pass issuance."},
            {"type": "figure", "path": diag("fig10_flow_payment.png"),
             "caption": "Payment Processing Flowchart illustrating CSRF checks, rate throttling, server-side fare quoting, and ACID commit."},
            {"type": "figure", "path": diag("fig11_flow_seathold.png"),
             "caption": "Seat Hold and Release Concurrency Flowchart showing atomic SQL updates and 5-minute expiration."},
            {"type": "figure", "path": diag("fig12_flow_login.png"),
             "caption": "Authentication, Registration, and Role-Based Access Routing Flowchart."},
            {"type": "h2", "num": "6.7", "text": "Sequence Diagram"},
            {"type": "p", "text": (
                "Figure 13 traces the exact message sequence between the customer browser, WebController, core services, SimulatedPaymentGateway, and PostgreSQL during seat holding and booking confirmation."
            )},
            {"type": "figure", "path": diag("fig13_sequence_booking.png"),
             "caption": "Booking and Payment Sequence Diagram showing message flow across web, service, payment, and database tiers."},
            {"type": "h2", "num": "6.8", "text": "Interface Designs and Wireframes"},
            {"type": "p", "text": (
                "Both the web and desktop interfaces follow the Sky Royal Glass design system built around dark frosted panels, Ink (#15181c), Sky (#4da3ff), and Cream (#f1eedf) tokens, avoiding legacy navy or gold literals. "
                "Figure 14 presents the annotated structural wireframes for the four primary booking screens."
            )},
            {"type": "figure", "path": diag("fig14_wireframes.png"),
             "caption": "Annotated Interface Wireframes for Home/Search, Interactive Cabin Seat Map, Checkout, and Confirmation screens."},
        ],
    })

    # ---- SECTION 7: IMPLEMENTATION ----
    sections.append({
        "num": "7",
        "title": "Implementation",
        "blocks": [
            {"type": "h2", "num": "7.1", "text": "Technologies and Tools Used"},
            {"type": "p", "text": (
                "All dependencies and plugin versions are managed centrally in the root pom.xml file so that core, desktop, and web compile against identical library releases. "
                "Table 6 lists the exact versions verified in the repository."
            )},
            {"type": "table", "title": "Sky Royal Technology Stack and Exact Dependency Versions",
             "headers": ["Layer", "Technology", "Exact Version", "Role in System"],
             "rows": C.TECH_STACK, "widths": [2.8, 3.4, 3.0, 6.8]},
            {"type": "h2", "num": "7.2", "text": "Description of Main Modules"},
            {"type": "bullets", "items": [
                "Core Module (core/): Holds configuration loaders (AppConfig with unified port probing, DatabaseConfig, AppInfo), domain models (User, Airport, FlightSearchResult, FlightSeat, BookingRecord, PriceQuote, TicketData), JDBC DAO implementations, transactional services (AuthService, RegistrationService, FlightService, SeatMapService, PricingService, BookingService, AdminDashboardService, BoardingPassVerificationService), and generators for OpenPDF boarding passes, ZXing QR codes, Code128 barcodes, and RFC 5545 .ics calendar files.",
                "Web Module (web/): Runs an embedded Javalin 6 server (WebApplication) with automatic port fallback starting at 8080, Thymeleaf server-rendered HTML templates (home with Top destinations, Why Sky Royal, Our best flights, Travel tips, and footer; flights; seats with Section 1, 2, and 3 cabin photos; checkout; confirmation; my-bookings; cancel; verify; login; register; admin; about; contact; error), and responsive Sky Royal Glass CSS.",
                "Desktop Module (desktop/): Provides a JavaFX 21 client (App and MainShellController) with 14 FXML view controllers covering login, home, flight search, interactive seat map with 5-minute seat hold and per-section cabin photos, checkout, confirmation, My Bookings with strict guest session isolation, and Admin Dashboard/Bookings. It matches the web styling using the same Sky Royal Glass tokens and photography, and executes database calls asynchronously on JavaFX Task background threads."
            ]},
            {"type": "h2", "num": "7.3", "text": "Security Implementation"},
            {"type": "p", "text": (
                "Security controls follow OWASP recommendations across authentication, session isolation, database access, web transport, and ticket verification (OWASP Foundation, 2025a):"
            )},
            {"type": "bullets", "items": [
                "Password Hashing: PasswordUtil uses jBCrypt with work factor 12 (BCrypt.gensalt(12)) and normalizes $2b$/$2y$ prefixes during verification (OWASP Foundation, 2025b). Plain text passwords are never persisted or logged.",
                "SQL Injection Prevention: Every DAO class uses JDBC PreparedStatement with explicit typed parameter bindings; zero SQL strings are built by user input concatenation (OWASP Foundation, 2025c).",
                "Input Validation: ValidationUtil enforces format rules for names, emails, 6 to 20 character alphanumeric passport numbers, routes, and booking references before any database write executes.",
                "CSRF Protection and Security Headers: WebApplication registers a before-filter that sets X-Content-Type-Options: nosniff, X-Frame-Options: DENY, X-XSS-Protection: 1; mode=block, and Referrer-Policy: strict-origin-when-cross-origin, and validates a session-bound UUID csrf_token on every POST request.",
                "Session Isolation and Owner-Only Access: On desktop, unauthenticated guests who open My Bookings see a dedicated 'Sign in to see your bookings' screen and never see other users' reservations. On web, seat releases, checkout, confirmation QR/PDF/ICS downloads, and cancellations verify that the signed-in user owns the hold or booking (returning HTTP 403 otherwise). LoginThrottle, RegistrationThrottle, PaymentThrottle (10/10m/IP), and VerifyThrottle (30/m/IP) protect sensitive endpoints.",
                "HMAC-SHA256 QR Verification: BoardingPassVerificationService signs booking references with HMAC-SHA256 using an auto-generated local secret (~/.sky-royal/qr-secret or SKY_ROYAL_QR_SECRET) and verifies signatures in constant time with MessageDigest.isEqual (Krawczyk, Bellare and Canetti, 1997)."
            ]},
            {"type": "h2", "num": "7.4", "text": "Error Handling and Logging"},
            {"type": "p", "text": (
                "Domain failures throw dedicated checked or runtime exceptions including AuthenticationException, DuplicateEmailException, DuplicateUsernameException, SeatUnavailableException, PriceChangedException, RefundFailedException, and ValidationException. "
                "In the web module, WebApplication maps HTTP 404 and unhandled exceptions to a sanitized error.html template so stack traces and database details never leak to the browser. "
                "In the desktop module, background JavaFX Task workers catch database and validation errors cleanly and update UI banners on the JavaFX Application Thread without freezing the window. "
                "Structured diagnostic logging uses SLF4J with Logback (QOS.ch, 2024a; QOS.ch, 2024b), configured via web/src/main/resources/logback.xml to suppress verbose Jetty request buffers and prevent credential or card number leakage. "
                "AppConfig.toString() masks the database password as ***."
            )},
            {"type": "h2", "num": "7.5", "text": "Web Application Screenshots"},
            {"type": "p", "text": (
                "Figures 15 to 25 present fresh captures of the running Javalin and Thymeleaf web application backed by a newly reseeded PostgreSQL database (sr_docs) with zero real personal data."
            )},
            {"type": "figure", "path": shot("web-home-hero.png"),
             "caption": "Web Application: Home landing hero with live popular routes panel and one-way flight search bar."},
            {"type": "figure", "path": shot("web-home-destinations.png"),
             "caption": "Web Application: Home page Top destinations gallery and Why Sky Royal value cards."},
            {"type": "figure", "path": shot("web-home-flights-tips-footer.png"),
             "caption": "Web Application: Home page Our best flights showcase, Travel tips section, and site footer."},
            {"type": "figure", "path": shot("web-flights.png"),
             "caption": "Web Application: Flight search results for Johannesburg (JNB) to Cape Town (CPT) showing rolling 14-day departures and filters."},
            {"type": "figure", "path": shot("web-seats-s1.png"),
             "caption": "Web Application: Interactive cabin seat map showing Section 1 (First Class) with updated First Class cabin photo."},
            {"type": "figure", "path": shot("web-seats-s2.png"),
             "caption": "Web Application: Interactive cabin seat map showing Section 2 (Economy front) with Economy cabin photo."},
            {"type": "figure", "path": shot("web-seats-s3.png"),
             "caption": "Web Application: Interactive cabin seat map showing Section 3 (Economy aft) with aft cabin photo."},
            {"type": "figure", "path": shot("web-checkout.png"),
             "caption": "Web Application: Single-page passenger and simulated card checkout with live fare breakdown and 5-minute hold countdown."},
            {"type": "figure", "path": shot("web-confirmation.png"),
             "caption": "Web Application: Booking confirmation hero screen displaying PNR reference, Code128 barcode, and HMAC QR code."},
            {"type": "figure", "path": shot("web-my-bookings.png"),
             "caption": "Web Application: My Bookings traveller portal with Upcoming and Cancelled lists and boarding pass inspector."},
            {"type": "figure", "path": shot("web-admin.png"),
             "caption": "Web Application: Administrator Operations Dashboard showing KPI summary cards and upcoming cabin load factors."},
            {"type": "h2", "num": "7.6", "text": "Desktop Application (JavaFX) Screenshots"},
            {"type": "p", "text": (
                "Figures 26 to 33 show fresh captures of every JavaFX 21 desktop application screen using the shared Sky Royal Glass design tokens and destination photography. "
                "As noted in Section 4.5, JavaFX was used with written approval from Mr Owami."
            )},
            {"type": "figure", "path": shot("desktop-login.png"),
             "caption": "Desktop Application (JavaFX): Split glass login screen with email and password authentication."},
            {"type": "figure", "path": shot("desktop-flight-search.png"),
             "caption": "Desktop Application (JavaFX): Flight search screen with airport pickers, scrollable filter sidebar, and live SR routes."},
            {"type": "figure", "path": shot("desktop-seat-map.png"),
             "caption": "Desktop Application (JavaFX): Interactive cabin seat map with cream selection card, 5-minute hold ring, and per-section cabin photo."},
            {"type": "figure", "path": shot("desktop-checkout.png"),
             "caption": "Desktop Application (JavaFX): Checkout screen with passenger form, simulated card preview, and VAT-inclusive fare summary."},
            {"type": "figure", "path": shot("desktop-confirmation.png"),
             "caption": "Desktop Application (JavaFX): Booking confirmation screen with white boarding pass card, barcode, and PDF export button."},
            {"type": "figure", "path": shot("desktop-my-bookings.png"),
             "caption": "Desktop Application (JavaFX): My Bookings view listing authenticated traveller reservations (guests see a Sign in prompt)."},
            {"type": "figure", "path": shot("desktop-admin-dashboard.png"),
             "caption": "Desktop Application (JavaFX): Administrator Dashboard displaying revenue KPIs, 7-day trend chart, cabin load, and recent bookings."},
            {"type": "figure", "path": shot("desktop-admin-bookings.png"),
             "caption": "Desktop Application (JavaFX): Administrator Bookings management table with search, status filtering, and booking details."},
            {"type": "h2", "num": "7.7", "text": "Important Code Explanations"},
            {"type": "p", "text": (
                "Four mechanisms anchor the technical reliability of Sky Royal. "
                "First, SeatDaoImpl uses a single conditional UPDATE ... RETURNING held_until statement so PostgreSQL row locking guarantees that concurrent users cannot hold the same seat. "
                "Second, PricingService computes authoritative fares on the server from base_price, cabin class multiplier, occupancy demand tier (1.00 to 1.20), and booking timing tier (0.95 to 1.20), discarding any tampered client price. "
                "Third, BookingDaoImpl executes booking insertion, passenger seat allocation, payment recording (with unique idempotency_key), and seat status transition to BOOKED inside a single JDBC transaction, and similarly uses SELECT ... FOR UPDATE OF b when processing cancellations and simulated refunds. "
                "Fourth, WebApplication.startWithFallback probes TCP ports starting at 8080 so the web server never crashes when another application already uses port 8080, while desktop controllers run database reads and writes on background JavaFX Task threads to keep the UI responsive. "
                "Selected verbatim code excerpts appear in Appendix C."
            )},
        ],
    })

    # ---- SECTION 8: TESTING ----
    sections.append({
        "num": "8",
        "title": "Testing",
        "blocks": [
            {"type": "h2", "num": "8.1", "text": "Test Plan and Verification Strategy"},
            {"type": "p", "text": (
                "Quality assurance combines automated JUnit 5 tests across all three Maven modules with live end-to-end proofs executed against isolated throwaway PostgreSQL databases (named sr_* on port 5433) so the primary database is never altered (JUnit Team, 2024). "
                "Testing covers five layers: unit tests for pricing, validation, password hashing, and calendar generation; database integration and concurrency race tests; security, session isolation, and design token guard tests; JavaFX layout tests; and live HTTP flow proofs covering security invariants A1 through A9."
            )},
            {"type": "h2", "num": "8.2", "text": "Automated JUnit 5 Test Suite Results"},
            {"type": "p", "text": (
                "Table 7 summarizes the exact automated test counts recorded in the module Surefire XML reports (core/target/surefire-reports, desktop/target/surefire-reports, and web/target/surefire-reports). "
                "Across all 82 test suites, 258 automated tests passed with zero failures, zero errors, and zero skipped tests."
            )},
            {"type": "table", "title": "Automated JUnit 5 Test Execution Summary by Module",
             "headers": ["Maven Module", "Test Suite Files", "Tests Run", "Failures", "Errors", "Skipped", "Status"],
             "rows": [
                 ["core", "43", "150", "0", "0", "0", "PASS"],
                 ["desktop", "23", "67", "0", "0", "0", "PASS"],
                 ["web", "16", "41", "0", "0", "0", "PASS"],
                 ["Total Reactor", "82", "258", "0", "0", "0", "PASS"],
             ], "widths": [3.0, 2.4, 2.2, 2.0, 2.0, 2.0, 2.4]},
            {"type": "h2", "num": "8.3", "text": "Test Cases and Execution Results"},
            {"type": "p", "text": (
                "Table 8 documents 34 executed test cases drawn directly from the JUnit 5 suite and live throwaway database proofs (sr_p2, sr_p3b, sr_run, and sr_docs)."
            )},
            {"type": "table", "title": "Executed System Test Cases (TC01 to TC34)", "landscape": True,
             "headers": ["ID", "Test Area", "Test Steps", "Expected Result", "Actual Result", "Result"],
             "rows": C.TEST_CASES, "widths": [1.3, 3.6, 6.5, 5.8, 5.9, 1.6]},
            {"type": "h2", "num": "8.4", "text": "Known Bugs and Limitations"},
            {"type": "bullets", "items": [
                "Simulated payments only: No live payment gateway integration exists; only local test card rules apply.",
                "File outbox email default: Welcome emails are stored locally under outbox/ unless external SMTP credentials are supplied; external delivery is unverified by default.",
                "Plain text passport column: booking_seats.passport_number is stored as plain text rather than encrypted at rest.",
                "Desktop and web layout parity: Both clients share the Sky Royal Glass palette and photography, but their layouts are close rather than pixel identical.",
                "One-way itineraries only: Round-trip and multi-city searches are not supported.",
                "QR code verification reachability: Works on the laptop running the web app; scanning from a phone requires a reachable network or public URL.",
                "Low-RAM Windows test forks: Running the full desktop JavaFX headless test suite requires roughly 1 GB of free physical RAM; on constrained laptops, core tests can be run with MAVEN_OPTS='-Xms64m -Xmx384m -XX:ReservedCodeCacheSize=96m'."
            ]},
        ],
    })

    # ---- SECTION 9: USER MANUAL & INSTALLATION GUIDE ----
    sections.append({
        "num": "9",
        "title": "User Manual & Installation Guide",
        "blocks": [
            {"type": "h2", "num": "9.1", "text": "System Prerequisites"},
            {"type": "bullets", "items": [
                "Operating System: Windows 10 or Windows 11 (64-bit).",
                "Java Development Kit: Temurin OpenJDK 17 LTS or newer (full JDK including javac).",
                "Database: PostgreSQL 14 to 18 with psql.exe available (port 5432 or 5433 auto-detected).",
                "Build Tool: None required globally; the repository includes the Apache Maven Wrapper (mvnw.cmd)."
            ]},
            {"type": "h2", "num": "9.2", "text": "Five-Step Windows Quick Start"},
            {"type": "p", "text": (
                "Follow the five steps from README.md to install, seed, and launch Sky Royal on a fresh Windows computer:"
            )},
            {"type": "bullets", "items": [
                "Step 1 (Install Java 17+): Download Temurin JDK 17 LTS from adoptium.net or run winget install EclipseAdoptium.Temurin.17.JDK, then reopen your terminal.",
                "Step 2 (Install PostgreSQL): Install PostgreSQL from postgresql.org/download/windows (or winget install PostgreSQL.PostgreSQL.18) and note the postgres password chosen during setup.",
                "Step 3 (Clone the Repository): Run git clone https://github.com/zennin7/flight-reservation-system.git and cd flight-reservation-system.",
                "Step 4 (Run One-Time Setup): Execute .\\scripts\\run-setup.cmd (or double-click it). The script checks Java, Maven Wrapper, and PostgreSQL, probes ports 5432 and 5433, creates and seeds the database safely, builds all modules, and prints Setup OK.",
                "Step 5 (Start Web or Desktop): Run .\\scripts\\run-web.cmd and open the printed http://localhost:<port> URL (defaults to 8080 and falls back automatically if busy), or run .\\scripts\\run-desktop.cmd to launch the JavaFX desktop application."
            ]},
            {"type": "code", "caption": "Quick Start Commands (Windows PowerShell or Command Prompt)",
             "code": (
                 "git clone https://github.com/zennin7/flight-reservation-system.git\n"
                 "cd flight-reservation-system\n"
                 ".\\scripts\\run-setup.cmd\n"
                 ".\\scripts\\run-web.cmd\n"
                 ".\\scripts\\run-desktop.cmd\n"
                 ".\\scripts\\reseed.cmd"
             )},
            {"type": "h2", "num": "9.3", "text": "Demo Logins and Simulated Test Cards"},
            {"type": "p", "text": (
                "Both the web login page (/login) and the desktop login screen accept either the username or email address listed in Table 9."
            )},
            {"type": "table", "title": "Seeded Demo Accounts and Simulated Payment Cards",
             "headers": ["Category", "Username / Card", "Email / Purpose", "Password / Details"],
             "rows": [
                 ["Admin Account", "testadmin", "testadmin@example.com", "Passw0rd!"],
                 ["Customer Account", "customer", "customer@example.com", "Passw0rd!"],
                 ["Approved Test Card", "4242 4242 4242 4242", "Simulates approved payment", "Any future MM/YY + 3-digit CVV"],
                 ["Declined Test Card", "4000 0000 0000 0002", "Simulates bank decline (HTTP 402)", "Any future MM/YY + 3-digit CVV"],
             ], "widths": [3.2, 3.8, 4.6, 4.4]},
            {"type": "h2", "num": "9.4", "text": "Troubleshooting Guide"},
            {"type": "table", "title": "Common Setup and Runtime Issues",
             "headers": ["Problem", "Cause and Resolution"],
             "rows": C.TROUBLESHOOTING_ROWS, "widths": [4.2, 11.8]},
            {"type": "h2", "num": "9.5", "text": "Using the Web and Desktop Applications"},
            {"type": "p", "text": (
                "Traveller Booking Workflow (Web and Desktop): Open the Home screen, choose origin and destination airports (for example JNB to CPT), pick a departure date within the rolling 14-day schedule (6 to 10 departures daily on popular routes), and click Search. "
                "Select a flight card to open the interactive cabin seat map, switch between Sections 1, 2, and 3 to inspect the cabin photos, click an available Economy or First Class seat to start a 5-minute hold, and proceed to Checkout. "
                "Enter passenger name, email, passport number, date of birth, and test card 4242 4242 4242 4242, then click Confirm and pay. "
                "On the Confirmation page, download the PDF boarding pass, verify the QR code on the running host, or open My Bookings (after signing in) to download an .ics calendar file or cancel an eligible trip."
            )},
            {"type": "p", "text": (
                "Administrator Workflow: Sign in with testadmin and Passw0rd!. "
                "On the web portal, click Admin in the top navigation bar (/admin) to inspect 7-day totals, revenue, seats sold, cancellations, and flight load factors. "
                "In the desktop application, use the Dashboard and Bookings tabs to view trend charts, top destinations, and all passenger bookings."
            )},
        ],
    })

    # ---- SECTION 10: GITHUB & TEAM COLLABORATION ----
    sections.append({
        "num": "10",
        "title": "GitHub & Team Collaboration",
        "blocks": [
            {"type": "h2", "num": "10.1", "text": "Repository Link, Branches, and Pull Requests"},
            {"type": "p", "text": (
                f"All source code, SQL migrations, automated tests, scripts, and public documentation are hosted on GitHub at: {C.GITHUB_URL}"
            )},
            {"type": "p", "text": (
                "Group 2 used a feature-branch and pull-request workflow targeting the integration branch dev. "
                "Across the full repository history from 15 September 2026 to 25 November 2026, the team recorded 217 commits across all branches (200 commits on dev), 28 remote branches, 19 local branches, and 23 merged pull requests (#1 through #23). "
                "Every group member created their own branch and contributed code, designs, or documentation through commits and pull requests (PR #6 was a demonstration commit from lecturer Mr Owami under handle brelinx). "
                "All 38 subsequent website integration commits carry explicit Git Co-authored-by trailers crediting SinikiweMasondo and ThelediMaluleka alongside David Zenn."
            )},
            {"type": "h2", "num": "10.2", "text": "Contribution History"},
            {"type": "p", "text": (
                "Figure 34 summarizes commit counts (direct and co-authored) and repository milestones extracted directly from git log --all on 25 November 2026."
            )},
            {"type": "figure", "path": diag("fig30_git_activity.png"),
             "caption": "Repository Commit Distribution per Group Member and GitHub Collaboration Summary (git log --all)."},
            {"type": "slot", "label": "[Optional Slot: Insert GitHub Insights > Contributors web screenshot (github-contributors.png) before final binding if required.]"},
            {"type": "h2", "num": "10.3", "text": "Group Member Roles and Contributions"},
            {"type": "p", "text": (
                "Table 11 records each Group 2 member's GitHub identity, assigned role, and verified contribution. "
                "In the public repository edition, student numbers are replaced with 'on file with the lecturer' to protect student privacy."
            )},
            {"type": "table", "title": "Group 2 Member Roles and Contributions",
             "headers": ["Full Name", "Student Number", "GitHub Username", "Role", "Contribution"],
             "rows": [[m["name"], m["student_no"], m["github"], m["role"], m["contribution"]] for m in s_members],
             "widths": [2.8, 3.1, 2.5, 2.8, 4.8]},
        ],
    })

    # ---- SECTION 11: CONCLUSION & RECOMMENDATIONS ----
    sections.append({
        "num": "11",
        "title": "Conclusion & Recommendations",
        "blocks": [
            {"type": "h2", "num": "11.1", "text": "Summary of Achievements"},
            {"type": "p", "text": (
                "Group 2 delivered a working, multi-module flight reservation system that connects a JavaFX 21 desktop client and a Javalin 6 web application to one shared PostgreSQL 18 database. "
                "All six project objectives from Section 4.3 were met: zero duplicated SQL or pricing logic, atomic 5-minute seat holds with per-section cabin photos, end-to-end simulated payment checkout with idempotency, OpenPDF boarding passes with HMAC-SHA256 QR verification, 24-hour self-service cancellation with strict session isolation, an administrator operations dashboard, and a five-step Windows setup kit backed by 258 passing JUnit 5 tests."
            )},
            {"type": "h2", "num": "11.2", "text": "Challenges Faced and Solutions"},
            {"type": "bullets", "items": [
                "Concurrent seat selection races: Solved by moving hold locking into an atomic PostgreSQL conditional UPDATE ... RETURNING query with explicit held_by ownership and 5-minute server-side expiry.",
                "Desktop responsiveness and session isolation: Solved by moving database calls onto background JavaFX Task threads and guarding My Bookings so guests see a 'Sign in to see your bookings' screen instead of another user's trips.",
                "Portability across student and lecturer laptops: Solved by bundling the Maven Wrapper (mvnw.cmd), adding environment variable overrides (SKY_ROYAL_DB_*), probing PostgreSQL on ports 5432 and 5433, and implementing automatic web port fallback from 8080.",
                "Locale decimal formatting discrepancies: Windows South African regional settings formatted decimals with commas in certain templates. Solved by introducing a locale-invariant MoneyFormatter and automated LocaleFormattingTest.",
                "Low-RAM test execution on Windows: Solved by configuring Maven Surefire fork reuse and documenting bounded MAVEN_OPTS memory flags.",
                "Brand and colour consistency across modules: Solved by centralizing AppInfo.NAME = 'Sky Royal' in core and enforcing automated BrandGuardTest and WebTemplateTokenGuardTest checks."
            ]},
            {"type": "h2", "num": "11.3", "text": "Lessons Learned"},
            {"type": "p", "text": (
                "Separating the shared core engine early saved substantial integration time when both the desktop and web modules matured. "
                "Writing automated guard tests, concurrency tests, and throwaway database verification scripts caught subtle bugs (such as Jetty debug log leakage, expired-hold edge cases, and guest session leakage) long before final submission. "
                "Structured pull requests and clear team-intake specifications also made integrating ten contributors' work manageable."
            )},
            {"type": "h2", "num": "11.4", "text": "Future Recommendations"},
            {"type": "bullets", "items": [
                "Encrypt sensitive passenger fields such as booking_seats.passport_number at rest using AES-GCM with managed keys.",
                "Integrate a real sandbox payment provider (such as PayFast or Stripe Test Mode) behind the existing PaymentService interface.",
                "Add round-trip and multi-city flight search and booking support.",
                "Configure a production SMTP relay with SPF/DKIM verification so welcome and confirmation emails deliver externally.",
                "Deploy the web application behind a public HTTPS hostname so QR codes scan from any passenger's mobile phone.",
                "Continue refining responsive alignment between the JavaFX desktop views and web templates."
            ]},
        ],
    })

    # ---- SECTION 12: REFERENCES ----
    sections.append({
        "num": "12",
        "title": "References",
        "blocks": [
            {"type": "h2", "num": "12.1", "text": "Harvard Reference List"},
            {"type": "p", "text": (
                "All sources below are real technical specifications and official documentation verified online on 24 November 2026 and cited in the text above."
            )},
            {"type": "bullets", "items": [ref[1] for ref in C.HARVARD_REFERENCES]},
            {"type": "h2", "num": "12.2", "text": "Photography and Visual Asset Credits"},
            {"type": "p", "text": (
                "Table 12 lists the photographer attributions and licences from web/src/main/resources/public/photos/CREDITS_TO_FILL.md for all Unsplash and project team visual assets used across the web and desktop applications."
            )},
            {"type": "table", "title": "Photography and Visual Asset Credits",
             "headers": ["Asset File", "Photographer / Source", "Licence"],
             "rows": C.PHOTO_CREDITS, "widths": [5.8, 6.2, 4.0]},
        ],
    })

    # ---- SECTION 13: APPENDICES ----
    app_d_blocks = [
        {"type": "h2", "num": "13.4", "text": "Appendix D: Lecturer Written Approval for JavaFX", "page_break_before": True},
        {"type": "p", "text": "Approval letter from Mr Owami. The signed copy is attached to the printed submission."},
    ]
    if approval_png and approval_png.exists():
        app_d_blocks.append({
            "type": "full_page_image",
            "path": approval_png,
        })
    else:
        app_d_blocks.append({
            "type": "slot",
            "label": "[Marked Slot: Insert approval-letter.pdf (Mr Owami's written approval permitting JavaFX 21 for the desktop application) here.]",
        })

    sections.append({
        "num": "13",
        "title": "Appendices",
        "blocks": [
            {"type": "h2", "num": "13.1", "text": "Appendix A: Demo Logins, Test Cards, and Run Commands"},
            {"type": "p", "text": (
                "Admin login: testadmin (testadmin@example.com) / Passw0rd!. "
                "Customer login: customer (customer@example.com) / Passw0rd!. "
                "Simulated approved card: 4242 4242 4242 4242 (any future expiry + 3-digit CVV). "
                "Simulated declined card: 4000 0000 0000 0002."
            )},
            {"type": "code", "caption": "Core Run and Verification Commands (Windows PowerShell or Command Prompt)",
             "code": (
                 "# 1. One-time database setup and module build\n"
                 ".\\scripts\\run-setup.cmd\n\n"
                 "# 2. Launch web application (http://localhost:8080 or next free port)\n"
                 ".\\scripts\\run-web.cmd\n\n"
                 "# 3. Launch JavaFX desktop application\n"
                 ".\\scripts\\run-desktop.cmd\n\n"
                 "# 4. Refresh rolling 14-day flight schedule\n"
                 ".\\scripts\\reseed.cmd\n\n"
                 "# 5. Run automated tests\n"
                 ".\\mvnw.cmd clean verify"
             )},
            {"type": "h2", "num": "13.2", "text": "Appendix B: Repository Folder Structure"},
            {"type": "code", "caption": "Sky Royal Multi-Module Repository Layout (repository root)",
             "code": (
                 "flight-reservation-system/\n"
                 "|-- pom.xml, mvnw, mvnw.cmd, README.md, LAUNCH.md\n"
                 "|-- core/\n"
                 "|   |-- sql/            (schema.sql, 5 migrations, 5 seed scripts)\n"
                 "|   |-- src/main/java/  (config, dao, exception, model, service, util)\n"
                 "|   |-- src/test/java/  (43 JUnit 5 test suites, 150 tests)\n"
                 "|-- desktop/\n"
                 "|   |-- src/main/       (App.java, 14 FXML controllers, Glass CSS, photos)\n"
                 "|   |-- src/test/java/  (23 JavaFX layout, guard, and bridge suites, 67 tests)\n"
                 "|-- web/\n"
                 "|   |-- src/main/       (WebApplication, WebController, 14 Thymeleaf templates)\n"
                 "|   |-- src/test/java/  (16 web flow, security, and port suites, 41 tests)\n"
                 "|-- scripts/            (run-setup, run-web, run-desktop, reseed, pg-common)\n"
                 "|-- docs/               (build scripts, PUBLIC docs, fresh screenshots)"
             )},
            {"type": "h2", "num": "13.3", "text": "Appendix C: Selected Code Snippets"},
            *[
                {"type": "code", "caption": title, "code": code}
                for title, code in C.CODE_SNIPPETS
            ],
            *app_d_blocks,
            {"type": "h2", "num": "13.5", "text": "Appendix E: Group Meeting Minutes", "page_break_before": True},
            {"type": "slot", "label": "[Marked Slot: Insert Group 2 meeting minutes (minutes*.txt) here. No minutes file was present in docs-in\\ during this build.]"},
        ],
    })

    # Assign sequential figure and table numbers (used as initial cached display before UNO refresh)
    fig_num = 0
    tbl_num = 0
    for sec in sections:
        for blk in sec["blocks"]:
            if blk["type"] == "figure":
                fig_num += 1
                blk["fig_num"] = fig_num
            elif blk["type"] == "table":
                tbl_num += 1
                blk["tbl_num"] = tbl_num

    return {
        "is_full": is_full,
        "members": s_members,
        "hero_photo": hero_photo,
        "logo_path": logo_path,
        "sections": sections,
        "figure_count": fig_num,
        "table_count": tbl_num,
    }


# ==============================================================================
# NATIVE WORD (.DOCX) XML HELPERS (ZERO TEXT BOXES, ZERO ANCHORS)
# ==============================================================================
def set_cell_shading(cell, fill_hex: str):
    tcPr = cell._tc.get_or_add_tcPr()
    for old in tcPr.findall(qn("w:shd")):
        tcPr.remove(old)
    tcPr.append(parse_xml(f'<w:shd {nsdecls("w")} w:val="clear" w:color="auto" w:fill="{fill_hex}"/>'))


def set_cell_margins(cell, top=60, bottom=60, left=95, right=95):
    tcPr = cell._tc.get_or_add_tcPr()
    for old in tcPr.findall(qn("w:tcMar")):
        tcPr.remove(old)
    tcPr.append(parse_xml(
        f'<w:tcMar {nsdecls("w")}>'
        f'<w:top w:w="{top}" w:type="dxa"/>'
        f'<w:bottom w:w="{bottom}" w:type="dxa"/>'
        f'<w:left w:w="{left}" w:type="dxa"/>'
        f'<w:right w:w="{right}" w:type="dxa"/>'
        f'</w:tcMar>'
    ))


def set_cell_width_dxa(cell, dxa: int):
    tcPr = cell._tc.get_or_add_tcPr()
    for old in tcPr.findall(qn("w:tcW")):
        tcPr.remove(old)
    tcPr.append(parse_xml(f'<w:tcW {nsdecls("w")} w:w="{dxa}" w:type="dxa"/>'))


def set_table_borders(table, color=HEX_BORDER, sz="4", inside=True):
    tblPr = table._tbl.tblPr
    for old in tblPr.findall(qn("w:tblBorders")):
        tblPr.remove(old)
    inner = (
        f'<w:insideH w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:insideV w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
    ) if inside else (
        '<w:insideH w:val="none"/><w:insideV w:val="none"/>'
    )
    tblPr.append(parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        f'<w:top w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:left w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:bottom w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:right w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'{inner}'
        f'</w:tblBorders>'
    ))


def set_table_borderless(table, bottom_border_color=None, bottom_sz="16"):
    tblPr = table._tbl.tblPr
    for old in tblPr.findall(qn("w:tblBorders")):
        tblPr.remove(old)
    bot = (
        f'<w:bottom w:val="single" w:sz="{bottom_sz}" w:space="0" w:color="{bottom_border_color}"/>'
        if bottom_border_color else '<w:bottom w:val="none"/>'
    )
    tblPr.append(parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        f'<w:top w:val="none"/><w:left w:val="none"/>{bot}<w:right w:val="none"/>'
        f'<w:insideH w:val="none"/><w:insideV w:val="none"/>'
        f'</w:tblBorders>'
    ))


def set_table_fixed_layout(table, col_widths_dxa):
    """Apply w:tblLayout fixed, explicit w:tblW, and exact w:tblGrid summing to text width."""
    total_dxa = sum(col_widths_dxa)
    tblPr = table._tbl.tblPr
    for tag in ("w:tblW", "w:tblLayout"):
        for old in tblPr.findall(qn(tag)):
            tblPr.remove(old)
    tblPr.append(parse_xml(f'<w:tblW {nsdecls("w")} w:w="{total_dxa}" w:type="dxa"/>'))
    tblPr.append(parse_xml(f'<w:tblLayout {nsdecls("w")} w:type="fixed"/>'))

    tblGrid = table._tbl.tblGrid
    if tblGrid is None:
        tblGrid = OxmlElement("w:tblGrid")
        table._tbl.insert(1, tblGrid)
    for old in list(tblGrid):
        tblGrid.remove(old)
    for w in col_widths_dxa:
        gc = OxmlElement("w:gridCol")
        gc.set(qn("w:w"), str(w))
        tblGrid.append(gc)


def set_row_cant_split(row):
    trPr = row._tr.get_or_add_trPr()
    if trPr.find(qn("w:cantSplit")) is None:
        trPr.append(OxmlElement("w:cantSplit"))


def set_row_header(row):
    trPr = row._tr.get_or_add_trPr()
    if trPr.find(qn("w:tblHeader")) is None:
        trPr.append(OxmlElement("w:tblHeader"))


def set_paragraph_shading(para, fill_hex: str):
    pPr = para._p.get_or_add_pPr()
    for old in pPr.findall(qn("w:shd")):
        pPr.remove(old)
    pPr.append(parse_xml(f'<w:shd {nsdecls("w")} w:val="clear" w:color="auto" w:fill="{fill_hex}"/>'))


def set_paragraph_borders(para, top=None, bottom=None, left=None, right=None):
    pPr = para._p.get_or_add_pPr()
    for old in pPr.findall(qn("w:pBdr")):
        pPr.remove(old)
    parts = []
    for side, spec in (("top", top), ("left", left), ("bottom", bottom), ("right", right)):
        if spec:
            val, sz, space, color = spec
            parts.append(f'<w:{side} w:val="{val}" w:sz="{sz}" w:space="{space}" w:color="{color}"/>')
    if parts:
        pPr.append(parse_xml(f'<w:pBdr {nsdecls("w")}>{"".join(parts)}</w:pBdr>'))


def add_field(para, instr_text: str, cached_text: str = "", dirty: bool = True,
              font_name: str = None, font_size_pt: float = None, bold: bool = None, color: RGBColor = None):
    """Insert a real native Word complex field (begin / instrText / separate / cached / end)."""
    def style_run(r):
        if font_name:
            r.font.name = font_name
        if font_size_pt is not None:
            r.font.size = Pt(font_size_pt)
        if bold is not None:
            r.bold = bold
        if color is not None:
            r.font.color.rgb = color

    r_begin = para.add_run()
    style_run(r_begin)
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    if dirty:
        fld_begin.set(qn("w:dirty"), "true")
    r_begin._r.append(fld_begin)

    r_instr = para.add_run()
    style_run(r_instr)
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = f" {instr_text} "
    r_instr._r.append(instr)

    r_sep = para.add_run()
    style_run(r_sep)
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    r_sep._r.append(fld_sep)

    r_val = para.add_run(cached_text)
    style_run(r_val)

    r_end = para.add_run()
    style_run(r_end)
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    r_end._r.append(fld_end)


def set_section_a4(sec, landscape: bool = False):
    """Configure A4 dimensions with 2.5 cm margins and 1.5 cm header/footer distance."""
    if landscape:
        sec.orientation = WD_ORIENT.LANDSCAPE
        sec.page_width = Cm(29.7)
        sec.page_height = Cm(21.0)
    else:
        sec.orientation = WD_ORIENT.PORTRAIT
        sec.page_width = Cm(21.0)
        sec.page_height = Cm(29.7)
    sec.top_margin = Cm(2.5)
    sec.bottom_margin = Cm(2.5)
    sec.left_margin = Cm(2.5)
    sec.right_margin = Cm(2.5)
    sec.header_distance = Cm(1.5)
    sec.footer_distance = Cm(1.5)


def set_section_page_num_format(sec, fmt: str = "decimal", start: int = None):
    sectPr = sec._sectPr
    for old in sectPr.findall(qn("w:pgNumType")):
        sectPr.remove(old)
    start_attr = f' w:start="{start}"' if start is not None else ""
    sectPr.append(parse_xml(f'<w:pgNumType {nsdecls("w")} w:fmt="{fmt}"{start_attr}/>'))


def configure_header_footer(sec, text_width_cm: float, show_page_prefix: bool = True, link_prev: bool = False):
    sec.header.is_linked_to_previous = link_prev
    sec.footer.is_linked_to_previous = link_prev
    if link_prev:
        return

    # Running Header
    hp = sec.header.paragraphs[0]
    hp.text = ""
    hp.paragraph_format.space_before = Pt(0)
    hp.paragraph_format.space_after = Pt(2)
    hp.paragraph_format.tab_stops.clear_all()
    hp.paragraph_format.tab_stops.add_tab_stop(Cm(text_width_cm), WD_TAB_ALIGNMENT.RIGHT)
    set_paragraph_borders(hp, bottom=("single", "4", "3", HEX_BORDER))
    r1 = hp.add_run(f"{C.PROJECT_TITLE}\t{C.COLLEGE_NAME} | {C.GROUP_NAME}")
    r1.font.name = "Arial"
    r1.font.size = Pt(8.5)
    r1.font.color.rgb = RGB_MUTED

    # Running Footer with real PAGE field
    fp = sec.footer.paragraphs[0]
    fp.text = ""
    fp.paragraph_format.space_before = Pt(2)
    fp.paragraph_format.space_after = Pt(0)
    fp.paragraph_format.tab_stops.clear_all()
    fp.paragraph_format.tab_stops.add_tab_stop(Cm(text_width_cm), WD_TAB_ALIGNMENT.RIGHT)
    set_paragraph_borders(fp, top=("single", "4", "3", HEX_BORDER))
    rf = fp.add_run("Final Year Project Documentation\t" + ("Page " if show_page_prefix else ""))
    rf.font.name = "Arial"
    rf.font.size = Pt(8.5)
    rf.font.color.rgb = RGB_MUTED
    add_field(fp, "PAGE", cached_text="1", dirty=True, font_name="Arial", font_size_pt=8.5, color=RGB_MUTED)


def enable_update_fields_on_open(doc: Document):
    settings = doc.settings.element
    for old in settings.findall(qn("w:updateFields")):
        settings.remove(old)
    settings.append(parse_xml(f'<w:updateFields {nsdecls("w")} w:val="true"/>'))


def setup_styles(doc: Document):
    """Configure Normal, Heading 1, Heading 2, and Caption styles cleanly."""
    normal = doc.styles["Normal"]
    normal.font.name = "Arial"
    normal.font.size = Pt(11)
    normal.font.color.rgb = RGB_INK
    normal.paragraph_format.line_spacing = 1.5
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(5)

    # Heading 1: Thinner dark band banner (Times New Roman 15 pt Bold White, page_break_before=True)
    h1 = doc.styles["Heading 1"]
    h1.font.name = "Times New Roman"
    h1.font.size = Pt(15)
    h1.font.bold = True
    h1.font.color.rgb = RGB_WHITE
    h1.paragraph_format.page_break_before = True
    h1.paragraph_format.keep_with_next = True
    h1.paragraph_format.space_before = Pt(0)
    h1.paragraph_format.space_after = Pt(10)
    h1.paragraph_format.line_spacing = 1.15

    # Heading 2: Times New Roman 12.5 pt Bold Ink with Sky accent underline
    h2 = doc.styles["Heading 2"]
    h2.font.name = "Times New Roman"
    h2.font.size = Pt(12.5)
    h2.font.bold = True
    h2.font.color.rgb = RGB_INK
    h2.paragraph_format.keep_with_next = True
    h2.paragraph_format.space_before = Pt(10)
    h2.paragraph_format.space_after = Pt(4)
    h2.paragraph_format.line_spacing = 1.2

    # Caption style for Figures & Tables (used with SEQ fields)
    cap = doc.styles["Caption"]
    cap.font.name = "Arial"
    cap.font.size = Pt(9.5)
    cap.font.bold = True
    cap.font.italic = False
    cap.font.color.rgb = RGB_MUTED
    cap.paragraph_format.line_spacing = 1.15
    cap.paragraph_format.space_before = Pt(3)
    cap.paragraph_format.space_after = Pt(8)


def add_section_banner(doc: Document, title_text: str, page_break_before: bool = True):
    """Add a Heading 1 styled as a thinner print-friendly dark shaded paragraph banner (zero shapes)."""
    p = doc.add_paragraph(style="Heading 1")
    p.paragraph_format.page_break_before = page_break_before
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(10)
    p.paragraph_format.line_spacing = 1.15
    set_paragraph_shading(p, HEX_INK)
    set_paragraph_borders(
        p,
        top=("single", "12", "4", HEX_INK),
        left=("single", "24", "8", HEX_SKY),
        bottom=("single", "18", "4", HEX_SKY),
        right=("single", "12", "6", HEX_INK),
    )
    r = p.add_run(title_text)
    r.font.name = "Times New Roman"
    r.font.size = Pt(15)
    r.font.bold = True
    r.font.color.rgb = RGB_WHITE
    return p


def add_h2(doc: Document, text: str, page_break_before: bool = False):
    p = doc.add_paragraph(style="Heading 2")
    p.paragraph_format.page_break_before = page_break_before
    p.paragraph_format.keep_with_next = True
    set_paragraph_borders(p, bottom=("single", "10", "2", HEX_SKY))
    r = p.add_run(text)
    r.font.name = "Times New Roman"
    r.font.size = Pt(12.5)
    r.font.bold = True
    r.font.color.rgb = RGB_INK
    return p


def add_body_p(doc: Document, text: str, space_after_pt: float = 5):
    p = doc.add_paragraph(style="Normal")
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(space_after_pt)
    r = p.add_run(text)
    r.font.name = "Arial"
    r.font.size = Pt(11)
    r.font.color.rgb = RGB_INK
    return p


def add_bullets(doc: Document, items):
    for it in items:
        p = doc.add_paragraph(style="List Bullet")
        p.paragraph_format.line_spacing = 1.5
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(3)
        r = p.add_run(it)
        r.font.name = "Arial"
        r.font.size = Pt(11)
        r.font.color.rgb = RGB_INK


def add_native_table(doc: Document, title: str, tbl_num: int, headers, rows, raw_widths, text_width_cm: float):
    """Insert a Caption with SEQ Table field followed by a fixed-layout native Word table (Arial 9 pt)."""
    cap = doc.add_paragraph(style="Caption")
    cap.alignment = WD_ALIGN_PARAGRAPH.LEFT
    cap.paragraph_format.keep_with_next = True
    cap.paragraph_format.space_before = Pt(6)
    cap.paragraph_format.space_after = Pt(3)
    r_pre = cap.add_run("Table ")
    r_pre.font.name = "Arial"
    r_pre.font.size = Pt(9.5)
    r_pre.bold = True
    r_pre.font.color.rgb = RGB_INK
    add_field(cap, r"SEQ Table \* ARABIC", cached_text=str(tbl_num), dirty=True,
              font_name="Arial", font_size_pt=9.5, bold=True, color=RGB_INK)
    r_post = cap.add_run(f": {title}")
    r_post.font.name = "Arial"
    r_post.font.size = Pt(9.5)
    r_post.bold = True
    r_post.font.color.rgb = RGB_INK

    # Compute exact column widths in DXA summing precisely to text_width_cm
    total_dxa = cm_to_dxa(text_width_cm)
    tot_raw = sum(raw_widths)
    col_dxa = [int(round(total_dxa * w / tot_raw)) for w in raw_widths]
    col_dxa[-1] += total_dxa - sum(col_dxa)

    tbl = doc.add_table(rows=1 + len(rows), cols=len(headers))
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    set_table_fixed_layout(tbl, col_dxa)
    set_table_borders(tbl, color=HEX_BORDER, sz="4", inside=True)

    # Header row (repeated across pages, cannot split)
    hdr_row = tbl.rows[0]
    set_row_header(hdr_row)
    set_row_cant_split(hdr_row)
    for j, h_text in enumerate(headers):
        cell = hdr_row.cells[j]
        set_cell_width_dxa(cell, col_dxa[j])
        set_cell_shading(cell, HEX_INK)
        set_cell_margins(cell, top=65, bottom=65, left=85, right=85)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.1
        r = p.add_run(str(h_text))
        r.font.name = "Arial"
        r.font.size = Pt(9)
        r.bold = True
        r.font.color.rgb = RGB_WHITE

    # Data rows (cannot split, zebra cream shading, Arial 9 pt)
    for idx, row_vals in enumerate(rows):
        row = tbl.rows[idx + 1]
        set_row_cant_split(row)
        bg = HEX_CREAM if idx % 2 == 1 else None
        for j, val in enumerate(row_vals):
            cell = row.cells[j]
            set_cell_width_dxa(cell, col_dxa[j])
            if bg:
                set_cell_shading(cell, bg)
            set_cell_margins(cell, top=55, bottom=55, left=85, right=85)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.TOP
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.15
            r = p.add_run(str(val))
            r.font.name = "Arial"
            r.font.size = Pt(9)
            r.font.color.rgb = RGB_INK

    # Small breathing space after table
    sp = doc.add_paragraph(style="Normal")
    sp.paragraph_format.space_before = Pt(0)
    sp.paragraph_format.space_after = Pt(4)
    sp.paragraph_format.line_spacing = 1.0


def add_inline_figure(doc: Document, path: Path, fig_num: int, caption: str, landscape: bool = False):
    """Insert an inline picture sized cleanly to fit page bounds + Caption with SEQ Figure field."""
    if not path.exists():
        add_slot(doc, f"[Missing Figure {fig_num}: {path.name}]")
        return

    with Image.open(str(path)) as im:
        iw, ih = im.size

    if landscape:
        max_w_cm = 23.8
        max_h_cm = 13.6
    else:
        max_w_cm = 15.6
        max_h_cm = 10.6

    aspect = iw / float(ih)
    w_cm = max_w_cm
    h_cm = w_cm / aspect
    if h_cm > max_h_cm:
        h_cm = max_h_cm
        w_cm = h_cm * aspect

    p_img = doc.add_paragraph(style="Normal")
    p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_img.paragraph_format.keep_with_next = True
    p_img.paragraph_format.space_before = Pt(4)
    p_img.paragraph_format.space_after = Pt(2)
    p_img.paragraph_format.line_spacing = 1.0
    run = p_img.add_run()
    run.add_picture(str(path), width=Cm(w_cm))

    cap = doc.add_paragraph(style="Caption")
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.space_before = Pt(2)
    cap.paragraph_format.space_after = Pt(8)
    r_pre = cap.add_run("Figure ")
    r_pre.font.name = "Arial"
    r_pre.font.size = Pt(9)
    r_pre.bold = True
    r_pre.font.color.rgb = RGB_MUTED
    add_field(cap, r"SEQ Figure \* ARABIC", cached_text=str(fig_num), dirty=True,
              font_name="Arial", font_size_pt=9, bold=True, color=RGB_MUTED)
    r_post = cap.add_run(f": {caption}")
    r_post.font.name = "Arial"
    r_post.font.size = Pt(9)
    r_post.bold = True
    r_post.font.color.rgb = RGB_MUTED


def add_code_block(doc: Document, caption: str, code_text: str):
    """Insert a code block (Courier New 9 pt, max 15 lines) as shaded paragraphs (zero text boxes)."""
    lines = code_text.splitlines()
    if len(lines) > 15:
        lines = lines[:15]
    trimmed_code = "\n".join(lines)

    p_hdr = doc.add_paragraph(style="Normal")
    p_hdr.paragraph_format.keep_with_next = True
    p_hdr.paragraph_format.space_before = Pt(6)
    p_hdr.paragraph_format.space_after = Pt(0)
    p_hdr.paragraph_format.line_spacing = 1.15
    set_paragraph_shading(p_hdr, HEX_CREAM)
    set_paragraph_borders(
        p_hdr,
        top=("single", "4", "3", HEX_BORDER),
        left=("single", "18", "6", HEX_SKY),
        bottom=("single", "4", "2", HEX_BORDER),
        right=("single", "4", "4", HEX_BORDER),
    )
    rh = p_hdr.add_run(caption)
    rh.font.name = "Arial"
    rh.font.size = Pt(9)
    rh.bold = True
    rh.font.color.rgb = RGB_INK

    p_code = doc.add_paragraph(style="Normal")
    p_code.paragraph_format.keep_together = True
    p_code.paragraph_format.space_before = Pt(0)
    p_code.paragraph_format.space_after = Pt(8)
    p_code.paragraph_format.line_spacing = 1.15
    set_paragraph_shading(p_code, HEX_CODE_BG)
    set_paragraph_borders(
        p_code,
        top=("none", "0", "0", "auto"),
        left=("single", "18", "6", HEX_SKY),
        bottom=("single", "4", "4", HEX_BORDER),
        right=("single", "4", "4", HEX_BORDER),
    )
    rc = p_code.add_run(trimmed_code)
    rc.font.name = "Courier New"
    rc.font.size = Pt(9)
    rc.font.color.rgb = RGB_INK


def add_slot(doc: Document, label: str):
    p = doc.add_paragraph(style="Normal")
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.line_spacing = 1.3
    set_paragraph_shading(p, HEX_CREAM)
    set_paragraph_borders(
        p,
        top=("single", "8", "6", HEX_SKY),
        left=("single", "8", "8", HEX_SKY),
        bottom=("single", "8", "6", HEX_SKY),
        right=("single", "8", "8", HEX_SKY),
    )
    r = p.add_run(label)
    r.font.name = "Arial"
    r.font.size = Pt(10)
    r.italic = True
    r.font.color.rgb = RGB_INK


def build_native_docx(model, out_docx: Path):
    """Build the entire 13-section native Word document cleanly from scratch."""
    doc = Document()
    setup_styles(doc)
    enable_update_fields_on_open(doc)

    # ==========================================================================
    # SECTION 1: COVER PAGE (ONE A4 PORTRAIT PAGE, UNNUMBERED)
    # ==========================================================================
    sec_cover = doc.sections[0]
    set_section_a4(sec_cover, landscape=False)
    sec_cover.different_first_page_header_footer = True
    set_section_page_num_format(sec_cover, fmt="lowerRoman", start=1)

    # College Header Table (Logo inline + College Name)
    hdr_tbl = doc.add_table(rows=1, cols=2)
    hdr_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr_tbl.autofit = False
    hdr_widths = [cm_to_dxa(2.4), cm_to_dxa(PORTRAIT_TEXT_CM - 2.4)]
    set_table_fixed_layout(hdr_tbl, hdr_widths)
    set_table_borderless(hdr_tbl, bottom_border_color=HEX_SKY, bottom_sz="16")
    set_row_cant_split(hdr_tbl.rows[0])

    c_logo = hdr_tbl.rows[0].cells[0]
    set_cell_width_dxa(c_logo, hdr_widths[0])
    set_cell_margins(c_logo, top=20, bottom=80, left=0, right=40)
    c_logo.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    p_logo = c_logo.paragraphs[0]
    p_logo.paragraph_format.space_before = Pt(0)
    p_logo.paragraph_format.space_after = Pt(0)
    p_logo.paragraph_format.line_spacing = 1.0
    if model["logo_path"] and Path(model["logo_path"]).exists():
        p_logo.add_run().add_picture(str(model["logo_path"]), width=Cm(1.85))

    c_col = hdr_tbl.rows[0].cells[1]
    set_cell_width_dxa(c_col, hdr_widths[1])
    set_cell_margins(c_col, top=20, bottom=80, left=40, right=0)
    c_col.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    p_col = c_col.paragraphs[0]
    p_col.paragraph_format.space_before = Pt(0)
    p_col.paragraph_format.space_after = Pt(2)
    p_col.paragraph_format.line_spacing = 1.15
    rc1 = p_col.add_run(C.COLLEGE_NAME.upper())
    rc1.font.name = "Times New Roman"
    rc1.font.size = Pt(16)
    rc1.bold = True
    rc1.font.color.rgb = RGB_INK
    p_sub = c_col.add_paragraph()
    p_sub.paragraph_format.space_before = Pt(0)
    p_sub.paragraph_format.space_after = Pt(0)
    p_sub.paragraph_format.line_spacing = 1.1
    rc2 = p_sub.add_run("Department of Information Technology | Final Year Project Documentation")
    rc2.font.name = "Arial"
    rc2.font.size = Pt(9.5)
    rc2.font.color.rgb = RGB_MUTED

    # Cover Title Banner (Heading 1 #1 -> total Heading 1 count = 13!)
    p_title = doc.add_paragraph(style="Heading 1")
    p_title.paragraph_format.page_break_before = False
    p_title.paragraph_format.space_before = Pt(10)
    p_title.paragraph_format.space_after = Pt(2)
    p_title.paragraph_format.line_spacing = 1.15
    set_paragraph_shading(p_title, HEX_INK)
    set_paragraph_borders(
        p_title,
        top=("single", "18", "6", HEX_INK),
        left=("single", "30", "10", HEX_SKY),
        bottom=("single", "18", "6", HEX_SKY),
        right=("single", "18", "8", HEX_INK),
    )
    rt = p_title.add_run(f"1. {C.PROJECT_TITLE}")
    rt.font.name = "Times New Roman"
    rt.font.size = Pt(19)
    rt.bold = True
    rt.font.color.rgb = RGB_WHITE

    p_tag = doc.add_paragraph(style="Normal")
    p_tag.paragraph_format.space_before = Pt(2)
    p_tag.paragraph_format.space_after = Pt(8)
    p_tag.paragraph_format.line_spacing = 1.15
    rtag = p_tag.add_run("Multi-Channel JavaFX Desktop and Javalin Web Airline Reservation Platform")
    rtag.font.name = "Arial"
    rtag.font.size = Pt(10)
    rtag.italic = True
    rtag.font.color.rgb = RGB_MUTED

    # Cover Hero Photo (inline, cropped/sized neatly so entire Cover stays strictly on ONE page)
    if model["hero_photo"].exists():
        # Create a 16:5 widescreen crop of home-hero.jpg for a sleek print cover banner
        hero_crop = ASSETS_DIR / "cover_hero_banner.jpg"
        with Image.open(str(model["hero_photo"])) as im:
            iw, ih = im.size
            target_h = int(iw * 0.34)
            top_y = max(0, (ih - target_h) // 2)
            im.crop((0, top_y, iw, min(ih, top_y + target_h))).save(str(hero_crop), quality=92)
        p_hero = doc.add_paragraph(style="Normal")
        p_hero.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_hero.paragraph_format.space_before = Pt(2)
        p_hero.paragraph_format.space_after = Pt(8)
        p_hero.paragraph_format.line_spacing = 1.0
        p_hero.add_run().add_picture(str(hero_crop), width=Cm(15.6))

    # Project Metadata Table (real Word table, Arial 9 pt)
    meta_rows = [
        ("Course and Year", C.COURSE_SLOT),
        ("Group", C.GROUP_NAME),
        ("Lecturer", C.LECTURER_NAME),
        ("Submission Date", C.SUBMISSION_DATE),
        ("GitHub Repository", C.GITHUB_URL),
    ]
    meta_tbl = doc.add_table(rows=len(meta_rows), cols=2)
    meta_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    meta_tbl.autofit = False
    meta_w = [cm_to_dxa(4.2), cm_to_dxa(PORTRAIT_TEXT_CM - 4.2)]
    set_table_fixed_layout(meta_tbl, meta_w)
    set_table_borders(meta_tbl, color=HEX_BORDER, sz="4", inside=True)
    for i, (k, v) in enumerate(meta_rows):
        row = meta_tbl.rows[i]
        set_row_cant_split(row)
        c0, c1 = row.cells[0], row.cells[1]
        set_cell_width_dxa(c0, meta_w[0])
        set_cell_width_dxa(c1, meta_w[1])
        set_cell_shading(c0, HEX_CREAM)
        set_cell_margins(c0, top=45, bottom=45, left=85, right=85)
        set_cell_margins(c1, top=45, bottom=45, left=85, right=85)
        p0 = c0.paragraphs[0]
        p0.paragraph_format.space_before = Pt(0); p0.paragraph_format.space_after = Pt(0); p0.paragraph_format.line_spacing = 1.1
        rk = p0.add_run(k); rk.font.name = "Arial"; rk.font.size = Pt(9); rk.bold = True; rk.font.color.rgb = RGB_INK
        p1 = c1.paragraphs[0]
        p1.paragraph_format.space_before = Pt(0); p1.paragraph_format.space_after = Pt(0); p1.paragraph_format.line_spacing = 1.1
        rv = p1.add_run(v); rv.font.name = "Arial"; rv.font.size = Pt(9); rv.font.color.rgb = RGB_INK

    # Group Members Title + Compact 2-Column Names Table (student numbers in Section 10 table ONLY)
    p_mt = doc.add_paragraph(style="Normal")
    p_mt.paragraph_format.space_before = Pt(8)
    p_mt.paragraph_format.space_after = Pt(3)
    p_mt.paragraph_format.line_spacing = 1.1
    rmt = p_mt.add_run("Group 2 Members")
    rmt.font.name = "Times New Roman"
    rmt.font.size = Pt(11.5)
    rmt.bold = True
    rmt.font.color.rgb = RGB_INK

    m_names = [m["name"] for m in model["members"]]
    half = (len(m_names) + 1) // 2
    mem_tbl = doc.add_table(rows=half, cols=2)
    mem_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    mem_tbl.autofit = False
    mem_w = [cm_to_dxa(PORTRAIT_TEXT_CM / 2), cm_to_dxa(PORTRAIT_TEXT_CM / 2)]
    set_table_fixed_layout(mem_tbl, mem_w)
    set_table_borders(mem_tbl, color=HEX_BORDER, sz="4", inside=True)
    for i in range(half):
        row = mem_tbl.rows[i]
        set_row_cant_split(row)
        bg = HEX_CREAM if i % 2 == 1 else None
        for col_i, idx in enumerate((i, i + half)):
            cell = row.cells[col_i]
            set_cell_width_dxa(cell, mem_w[col_i])
            if bg:
                set_cell_shading(cell, bg)
            set_cell_margins(cell, top=42, bottom=42, left=85, right=85)
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(0); p.paragraph_format.space_after = Pt(0); p.paragraph_format.line_spacing = 1.1
            txt = f"{idx + 1}. {m_names[idx]}" if idx < len(m_names) else ""
            r = p.add_run(txt); r.font.name = "Arial"; r.font.size = Pt(9); r.font.color.rgb = RGB_INK

    # ==========================================================================
    # FRONT MATTER SECTION (ROMAN NUMERALS starting at ii): SECTIONS 2 & 3
    # ==========================================================================
    sec_front = doc.add_section(WD_SECTION.NEW_PAGE)
    set_section_a4(sec_front, landscape=False)
    sec_front.different_first_page_header_footer = False
    set_section_page_num_format(sec_front, fmt="lowerRoman", start=2)
    configure_header_footer(sec_front, PORTRAIT_TEXT_CM, show_page_prefix=False, link_prev=False)

    # ---- SECTION 2: DECLARATION (Heading 1 #2) ----
    add_section_banner(doc, "2. Declaration", page_break_before=False)
    add_body_p(doc, (
        f"We, the undersigned members of {C.GROUP_NAME} at {C.COLLEGE_NAME}, hereby declare that the "
        f"{C.PROJECT_TITLE} software application, source code, diagrams, and written documentation submitted for the "
        "Final Year Project are our own original work. All external libraries, documentation sources, and photographic assets "
        "used in this project have been acknowledged in Section 12 (References). This work has not been submitted previously "
        "for any other course or academic assessment."
    ))

    # Declaration Signature Table (no student numbers here; student numbers are in Section 10 table only)
    decl_tbl = doc.add_table(rows=1 + len(model["members"]), cols=3)
    decl_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    decl_tbl.autofit = False
    decl_w = [cm_to_dxa(6.8), cm_to_dxa(5.8), cm_to_dxa(PORTRAIT_TEXT_CM - 12.6)]
    set_table_fixed_layout(decl_tbl, decl_w)
    set_table_borders(decl_tbl, color=HEX_BORDER, sz="4", inside=True)

    set_row_header(decl_tbl.rows[0])
    set_row_cant_split(decl_tbl.rows[0])
    for j, h in enumerate(["Full Name", "Signature", "Date"]):
        c = decl_tbl.rows[0].cells[j]
        set_cell_width_dxa(c, decl_w[j])
        set_cell_shading(c, HEX_INK)
        set_cell_margins(c, top=65, bottom=65, left=85, right=85)
        p = c.paragraphs[0]
        p.paragraph_format.space_before = Pt(0); p.paragraph_format.space_after = Pt(0); p.paragraph_format.line_spacing = 1.1
        r = p.add_run(h); r.font.name = "Arial"; r.font.size = Pt(9); r.bold = True; r.font.color.rgb = RGB_WHITE

    for idx, m in enumerate(model["members"]):
        row = decl_tbl.rows[idx + 1]
        set_row_cant_split(row)
        bg = HEX_CREAM if idx % 2 == 1 else None
        for j, val in enumerate([m["name"], "", ""]):
            c = row.cells[j]
            set_cell_width_dxa(c, decl_w[j])
            if bg:
                set_cell_shading(c, bg)
            set_cell_margins(c, top=105, bottom=105, left=85, right=85)
            p = c.paragraphs[0]
            p.paragraph_format.space_before = Pt(0); p.paragraph_format.space_after = Pt(0); p.paragraph_format.line_spacing = 1.1
            r = p.add_run(val); r.font.name = "Arial"; r.font.size = Pt(9); r.font.color.rgb = RGB_INK

    # ---- SECTION 3: TABLE OF CONTENTS (Heading 1 #3) ----
    add_section_banner(doc, "3. Table of Contents", page_break_before=True)
    p_toc = doc.add_paragraph(style="Normal")
    p_toc.paragraph_format.space_before = Pt(2)
    p_toc.paragraph_format.space_after = Pt(4)
    p_toc.paragraph_format.line_spacing = 1.15
    add_field(
        p_toc,
        r'TOC \o "1-2" \h \z \u',
        cached_text="Right-click and select Update Field in Microsoft Word to refresh the Table of Contents.",
        dirty=True, font_name="Arial", font_size_pt=10, color=RGB_MUTED,
    )

    # ---- LIST OF FIGURES (own page, Heading 2 so Heading 1 count stays exactly 13) ----
    add_h2(doc, "3.1 List of Figures", page_break_before=True)
    p_lof = doc.add_paragraph(style="Normal")
    p_lof.paragraph_format.space_before = Pt(2)
    p_lof.paragraph_format.space_after = Pt(4)
    p_lof.paragraph_format.line_spacing = 1.15
    add_field(
        p_lof,
        r'TOC \h \z \c "Figure"',
        cached_text="Right-click and select Update Field in Microsoft Word to refresh the List of Figures.",
        dirty=True, font_name="Arial", font_size_pt=10, color=RGB_MUTED,
    )

    # ---- LIST OF TABLES (own page, Heading 2) ----
    add_h2(doc, "3.2 List of Tables", page_break_before=True)
    p_lot = doc.add_paragraph(style="Normal")
    p_lot.paragraph_format.space_before = Pt(2)
    p_lot.paragraph_format.space_after = Pt(4)
    p_lot.paragraph_format.line_spacing = 1.15
    add_field(
        p_lot,
        r'TOC \h \z \c "Table"',
        cached_text="Right-click and select Update Field in Microsoft Word to refresh the List of Tables.",
        dirty=True, font_name="Arial", font_size_pt=10, color=RGB_MUTED,
    )

    # ==========================================================================
    # BODY SECTIONS (ARABIC NUMERALS starting at 1): SECTIONS 4 .. 13
    # ==========================================================================
    sec_body = doc.add_section(WD_SECTION.NEW_PAGE)
    set_section_a4(sec_body, landscape=False)
    sec_body.different_first_page_header_footer = False
    set_section_page_num_format(sec_body, fmt="decimal", start=1)
    configure_header_footer(sec_body, PORTRAIT_TEXT_CM, show_page_prefix=True, link_prev=False)

    in_landscape = False

    def switch_orientation(want_landscape: bool):
        nonlocal in_landscape
        if want_landscape == in_landscape:
            return
        new_sec = doc.add_section(WD_SECTION.NEW_PAGE)
        set_section_a4(new_sec, landscape=want_landscape)
        new_sec.different_first_page_header_footer = False
        # Continuous Arabic numbering (no w:start reset)
        set_section_page_num_format(new_sec, fmt="decimal", start=None)
        tw = LANDSCAPE_TEXT_CM if want_landscape else PORTRAIT_TEXT_CM
        configure_header_footer(new_sec, tw, show_page_prefix=True, link_prev=False)
        in_landscape = want_landscape

    for sec_idx, sec in enumerate(model["sections"]):
        # Always return to portrait before starting a new top-level section
        if in_landscape:
            switch_orientation(False)
            need_pb = False
        else:
            need_pb = (sec_idx > 0)

        add_section_banner(doc, f"{sec['num']}. {sec['title']}", page_break_before=need_pb)

        for blk in sec["blocks"]:
            t = blk["type"]
            want_land = bool(blk.get("landscape", False))

            if want_land and not in_landscape:
                switch_orientation(True)
            elif not want_land and in_landscape:
                switch_orientation(False)

            cur_tw = LANDSCAPE_TEXT_CM if in_landscape else PORTRAIT_TEXT_CM

            if t == "h2":
                add_h2(doc, f"{blk['num']} {blk['text']}", page_break_before=bool(blk.get("page_break_before", False)))
            elif t == "p":
                add_body_p(doc, blk["text"])
            elif t == "bullets":
                add_bullets(doc, blk["items"])
            elif t == "table":
                add_native_table(doc, blk["title"], blk["tbl_num"], blk["headers"], blk["rows"], blk["widths"], cur_tw)
            elif t == "figure":
                # Put each landscape Chen ERD on its own clean landscape page
                if in_landscape and blk["fig_num"] > 5 and sec["num"] == "6":
                    doc.add_page_break()
                add_inline_figure(doc, Path(blk["path"]), blk["fig_num"], blk["caption"], landscape=in_landscape)
            elif t == "code":
                add_code_block(doc, blk["caption"], blk["code"])
            elif t == "slot":
                add_slot(doc, blk["label"])
            elif t == "full_page_image":
                # Appendix D: render letter inline at text width so heading + 1 line + letter fit on ONE A4 page
                p_img = doc.add_paragraph(style="Normal")
                p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
                p_img.paragraph_format.space_before = Pt(2)
                p_img.paragraph_format.space_after = Pt(0)
                p_img.paragraph_format.line_spacing = 1.0
                # Heading (~1.0 cm) + 1 line (~0.8 cm) + 21.8 cm image = ~23.6 cm <= 24.7 cm text height!
                with Image.open(str(blk["path"])) as im:
                    iw, ih = im.size
                aspect = iw / float(ih)
                max_w = 15.6
                max_h = 21.4
                w_cm = max_w
                h_cm = w_cm / aspect
                if h_cm > max_h:
                    h_cm = max_h
                    w_cm = h_cm * aspect
                p_img.add_run().add_picture(str(blk["path"]), width=Cm(w_cm))

    out_docx.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(out_docx))
    print(f"Saved native DOCX: {out_docx} ({out_docx.stat().st_size:,} B)")


def pick_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def export_pdfs_via_libreoffice_uno(pairs):
    """Launch ONE headless LibreOffice UNO server, update all fields & indexes, and export each DOCX -> PDF."""
    port = pick_free_port()
    lo_cmd = [
        LO_SOFFICE,
        f"-env:UserInstallation={LO_PROFILE}",
        "--headless",
        "--norestore",
        "--nologo",
        "--nofirststartwizard",
        f"--accept=socket,host=127.0.0.1,port={port};urp;StarOffice.ServiceManager",
    ]
    print(f"Starting LibreOffice headless UNO listener on port {port}...")
    proc = subprocess.Popen(lo_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        helper = BUILD_DIR / "lo_update_and_export.py"
        for docx_path, pdf_path in pairs:
            print(f"Updating fields & exporting PDF: {docx_path.name} -> {pdf_path.name}")
            res = subprocess.run(
                [LO_PYTHON, str(helper), str(docx_path), str(pdf_path), str(port)],
                capture_output=True, text=True, timeout=180
            )
            if res.stdout.strip():
                print(" ", res.stdout.strip())
            if res.stderr.strip():
                print("  STDERR:", res.stderr.strip())
            if res.returncode != 0 or not pdf_path.exists():
                raise RuntimeError(f"LibreOffice UNO export failed for {docx_path}: rc={res.returncode}")
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()
        subprocess.run(["taskkill", "/F", "/IM", "soffice.bin"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    members = parse_students_txt()
    print(f"Loaded {len(members)} Group 2 members.")

    full_docx = DOCS_OUT / "Sky_Royal_Documentation_FULL.docx"
    full_pdf = DOCS_OUT / "Sky_Royal_Documentation_FULL.pdf"
    pub_docx = DOCS_OUT / "Sky_Royal_Documentation_PUBLIC.docx"
    pub_pdf = DOCS_OUT / "Sky_Royal_Documentation_PUBLIC.pdf"

    for is_full, out_docx in [(True, full_docx), (False, pub_docx)]:
        label = "FULL" if is_full else "PUBLIC"
        print(f"=== Building native {label} DOCX ===")
        model = build_document_model(members, is_full=is_full)
        build_native_docx(model, out_docx)

    print("=== Updating all Word fields via LibreOffice UNO and exporting PDFs ===")
    export_pdfs_via_libreoffice_uno([
        (full_docx, full_pdf),
        (pub_docx, pub_pdf),
    ])

    # Copy PUBLIC pair into committed docs/ folder
    shutil.copy2(pub_docx, REPO_DOCS / "Sky_Royal_Documentation_PUBLIC.docx")
    shutil.copy2(pub_pdf, REPO_DOCS / "Sky_Royal_Documentation_PUBLIC.pdf")
    print("Copied PUBLIC pair to docs/")
    print("BUILD COMPLETE.")


if __name__ == "__main__":
    main()
