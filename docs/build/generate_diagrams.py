"""
Generate all 300 DPI PNG diagrams for Sky Royal Flight Reservation System documentation.
Uses matplotlib vector primitives rendered to 300 DPI PNGs with SOLID lines only.
"""
import os
import sys
import math

# Support packages installed on D:\p7-pylibs when C: is low on space
if os.path.isdir(r"D:\p7-pylibs") and r"D:\p7-pylibs" not in sys.path:
    sys.path.insert(0, r"D:\p7-pylibs")

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, Polygon, Ellipse, Rectangle, FancyArrowPatch

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "diagrams")
os.makedirs(OUT_DIR, exist_ok=True)

# Sky Royal design tokens
INK = "#15181c"
SKY = "#4da3ff"
SKY_LIGHT = "#e8f3ff"
CREAM = "#f1eedf"
SLATE = "#3b4450"
BORDER = "#7a8694"
WHITE = "#ffffff"
GREEN = "#1f8a5b"
GREEN_BG = "#e8f7ef"
RED = "#c0392b"
RED_BG = "#fdecea"

# Chen ERD exact reference palette (from docs-in/erd-reference.png)
ERD_TEAL = "#00a8b5"
ERD_DARKBLUE = "#0b4f71"
ERD_CRIMSON = "#b81242"
ERD_LINE = "#444444"
ERD_LEGEND_BORDER = "#aaaaaa"

plt.rcParams["font.family"] = "sans-serif"
plt.rcParams["font.sans-serif"] = ["Arial", "DejaVu Sans"]


def save_fig(fig, filename):
    path = os.path.join(OUT_DIR, filename)
    fig.savefig(path, dpi=300, bbox_inches="tight", facecolor=WHITE, pad_inches=0.18)
    plt.close(fig)
    print(f"Saved {path}")


def draw_box(ax, x, y, w, h, text, fc=WHITE, ec=INK, tc=INK, fontsize=9,
             bold=False, radius=0.08, lw=1.4, ha="center", va="center"):
    patch = FancyBboxPatch(
        (x - w / 2, y - h / 2), w, h,
        boxstyle=f"round,pad=0.02,rounding_size={radius}",
        facecolor=fc, edgecolor=ec, linewidth=lw, zorder=3
    )
    ax.add_patch(patch)
    ax.text(x, y, text, color=tc, fontsize=fontsize,
            fontweight="bold" if bold else "normal",
            ha=ha, va=va, zorder=4, linespacing=1.25)


def draw_rect(ax, x, y, w, h, text, fc=ERD_TEAL, ec=ERD_TEAL, tc=WHITE,
              fontsize=9.5, bold=True, lw=1.2):
    patch = Rectangle((x - w / 2, y - h / 2), w, h,
                      facecolor=fc, edgecolor=ec, linewidth=lw, zorder=4)
    ax.add_patch(patch)
    ax.text(x, y, text, color=tc, fontsize=fontsize,
            fontweight="bold" if bold else "normal",
            ha="center", va="center", zorder=5, linespacing=1.15)


def draw_diamond(ax, x, y, w, h, text, fc=ERD_DARKBLUE, ec=ERD_DARKBLUE,
                 tc=WHITE, fontsize=8.5, bold=True, lw=1.2):
    pts = [(x, y + h / 2), (x + w / 2, y), (x, y - h / 2), (x - w / 2, y)]
    patch = Polygon(pts, closed=True, facecolor=fc, edgecolor=ec, linewidth=lw, zorder=4)
    ax.add_patch(patch)
    ax.text(x, y, text, color=tc, fontsize=fontsize,
            fontweight="bold" if bold else "normal",
            ha="center", va="center", zorder=5, linespacing=1.1)


def draw_oval(ax, x, y, w, h, text, fc=ERD_CRIMSON, ec=ERD_CRIMSON,
              tc=WHITE, fontsize=8, underline=False, lw=1.0):
    patch = Ellipse((x, y), w, h, facecolor=fc, edgecolor=ec, linewidth=lw, zorder=4)
    ax.add_patch(patch)
    ax.text(x, y, text, color=tc, fontsize=fontsize,
            fontweight="bold" if underline else "normal",
            ha="center", va="center", zorder=5, linespacing=1.08)
    if underline:
        # Solid underline right below PK label
        approx_w = min(w * 0.72, max(0.35, len(text) * 0.068))
        ax.plot([x - approx_w / 2, x + approx_w / 2], [y - 0.14, y - 0.14],
                color=WHITE, linewidth=1.1, zorder=6)


def draw_arrow(ax, x1, y1, x2, y2, label="", color=INK, lw=1.3,
               label_offset=(0, 0.14), fontsize=8, style="-|>"):
    arrow = FancyArrowPatch(
        (x1, y1), (x2, y2),
        arrowstyle=style, mutation_scale=12,
        color=color, linewidth=lw, linestyle="solid", zorder=2
    )
    ax.add_patch(arrow)
    if label:
        mx, my = (x1 + x2) / 2 + label_offset[0], (y1 + y2) / 2 + label_offset[1]
        ax.text(mx, my, label, fontsize=fontsize, color=INK, ha="center", va="center",
                bbox=dict(boxstyle="round,pad=0.14", fc=WHITE, ec="none", alpha=0.9), zorder=6)


def draw_line(ax, x1, y1, x2, y2, color=ERD_LINE, lw=1.2):
    ax.plot([x1, x2], [y1, y2], color=color, linewidth=lw, linestyle="solid", zorder=2)


def draw_erd_legend(ax, x=0.35, y=7.65):
    box = FancyBboxPatch(
        (x, y), 6.5, 1.15,
        boxstyle="round,pad=0.05,rounding_size=0.15",
        facecolor=WHITE, edgecolor=ERD_LEGEND_BORDER, linewidth=1.1, zorder=3
    )
    ax.add_patch(box)
    draw_rect(ax, x + 0.85, y + 0.58, 1.25, 0.52, "Entity", fontsize=8.5)
    draw_diamond(ax, x + 2.35, y + 0.58, 1.25, 0.62, "Action", fontsize=8)
    draw_oval(ax, x + 3.85, y + 0.58, 1.25, 0.52, "Attribute", fontsize=8)
    draw_oval(ax, x + 5.45, y + 0.72, 1.35, 0.42, "Primary Key", fontsize=7.5, underline=True)
    ax.text(x + 5.45, y + 0.24, "1 = One   N = Many", fontsize=7.8,
            color=INK, ha="center", va="center", fontweight="bold", zorder=5)


# ==============================================================================
# FIGURE 1: Multi-Module System Architecture
# ==============================================================================
def gen_fig01_architecture():
    fig, ax = plt.subplots(figsize=(11.5, 7.2))
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 8.0)
    ax.axis("off")

    ax.text(6.0, 7.65, "Sky Royal Flight Reservation System Architecture",
            fontsize=14, fontweight="bold", color=INK, ha="center")

    # Presentation Tier container
    pres = FancyBboxPatch((0.4, 5.25), 11.2, 1.95, boxstyle="round,pad=0.05,rounding_size=0.12",
                          facecolor=SKY_LIGHT, edgecolor=SKY, linewidth=1.5, zorder=1)
    ax.add_patch(pres)
    ax.text(0.65, 6.95, "PRESENTATION TIER (Dual Clients)", fontsize=9, fontweight="bold", color=INK)

    draw_box(ax, 3.1, 6.0, 4.7, 1.15,
             "Desktop Module (desktop/)\nJavaFX 21.0.3 + FXML Views + Sky Royal Glass CSS\n14 Controllers, TicketView, StepperBar, ThemeManager",
             fc=WHITE, ec=INK, fontsize=8.5, bold=False)
    draw_box(ax, 8.9, 6.0, 4.7, 1.15,
             "Web Module (web/)\nJavalin 6.1.3 + Thymeleaf 3.1.2 + Responsive CSS\nWebApplication (Port 8080+), CSRF, Security Headers",
             fc=WHITE, ec=INK, fontsize=8.5, bold=False)

    # Core Tier container
    core = FancyBboxPatch((0.4, 2.55), 11.2, 2.2, boxstyle="round,pad=0.05,rounding_size=0.12",
                          facecolor=CREAM, edgecolor=INK, linewidth=1.5, zorder=1)
    ax.add_patch(core)
    ax.text(0.65, 4.5, "SHARED CORE ENGINE (core/)", fontsize=9, fontweight="bold", color=INK)

    draw_box(ax, 2.3, 3.7, 3.2, 1.05,
             "Service Layer\nAuth, Registration, Flight,\nSeatMap, Pricing, Booking,\nAdminDashboard, Verification",
             fc=WHITE, ec=INK, fontsize=8)
    draw_box(ax, 6.0, 3.7, 3.2, 1.05,
             "Security and Policies\nBCrypt (cost 12), ValidationUtil,\nSeatHoldPolicy (5m), FlightSalesPolicy\n(60m), CancellationPolicy (24h), Throttles",
             fc=WHITE, ec=INK, fontsize=7.8)
    draw_box(ax, 9.7, 3.7, 3.2, 1.05,
             "Document and Utilities\nOpenPDF BoardingPassPdfGenerator,\nZXing QrCodeGenerator (HMAC),\nCode128 Barcode, IcsCalendarGenerator",
             fc=WHITE, ec=INK, fontsize=7.8)
    draw_box(ax, 6.0, 2.88, 10.6, 0.45,
             "Data Access Layer (DAO Interfaces + JDBC PreparedStatement Implementations)  |  AppConfig + HikariCP Pool (max 10)",
             fc=INK, ec=INK, tc=WHITE, fontsize=8.3, bold=True)

    # Infrastructure Tier container
    infra = FancyBboxPatch((0.4, 0.35), 11.2, 1.75, boxstyle="round,pad=0.05,rounding_size=0.12",
                           facecolor="#f5f7fa", edgecolor=BORDER, linewidth=1.5, zorder=1)
    ax.add_patch(infra)
    ax.text(0.65, 1.85, "DATA AND EXTERNAL ADAPTERS", fontsize=9, fontweight="bold", color=INK)

    draw_box(ax, 2.4, 1.05, 3.5, 0.95,
             "PostgreSQL 18 Database\n10 Tables, 1 View, 6 Enums\nACID Transactions and Row Locks\nPorts 5432 / 5433 auto-probed",
             fc=WHITE, ec=ERD_DARKBLUE, fontsize=8)
    draw_box(ax, 6.0, 1.05, 3.2, 0.95,
             "Simulated Payment Gateway\nSimulatedPaymentGateway\nCard 4242... approves\nCard 4000... declines; Refunds",
             fc=WHITE, ec=ERD_DARKBLUE, fontsize=8)
    draw_box(ax, 9.6, 1.05, 3.5, 0.95,
             "Email Delivery Adapter\nEmailServiceFactory\nDefault: FileOutboxEmailService\n(outbox/) or Optional SMTP",
             fc=WHITE, ec=ERD_DARKBLUE, fontsize=8)

    # Arrows
    draw_arrow(ax, 3.1, 5.42, 3.1, 4.78, "Java calls")
    draw_arrow(ax, 8.9, 5.42, 8.9, 4.78, "Java calls")
    draw_arrow(ax, 3.65, 2.65, 3.65, 1.56, "JDBC / SQL", label_offset=(0.55, 0))
    draw_arrow(ax, 6.0, 2.65, 6.0, 1.56, "Charge / Refund", label_offset=(0.7, 0))
    draw_arrow(ax, 9.6, 2.65, 9.6, 1.56, "Welcome Email", label_offset=(0.68, 0))

    save_fig(fig, "fig01_architecture.png")


# ==============================================================================
# FIGURE 2: Use Case Diagram
# ==============================================================================
def draw_actor(ax, x, y, label):
    head = plt.Circle((x, y + 0.36), 0.14, fc=WHITE, ec=INK, lw=1.6, zorder=4)
    ax.add_patch(head)
    ax.plot([x, x], [y + 0.22, y - 0.14], color=INK, lw=1.6, zorder=4)
    ax.plot([x - 0.22, x + 0.22], [y + 0.1, y + 0.1], color=INK, lw=1.6, zorder=4)
    ax.plot([x, x - 0.18], [y - 0.14, y - 0.44], color=INK, lw=1.6, zorder=4)
    ax.plot([x, x + 0.18], [y - 0.14, y - 0.44], color=INK, lw=1.6, zorder=4)
    ax.text(x, y - 0.66, label, fontsize=8.5, fontweight="bold", color=INK, ha="center", va="top")


def gen_fig02_use_cases():
    fig, ax = plt.subplots(figsize=(11.5, 7.6))
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 8.4)
    ax.axis("off")

    ax.text(6.0, 8.05, "Sky Royal System Use Case Diagram",
            fontsize=14, fontweight="bold", color=INK, ha="center")

    # System boundary
    bound = Rectangle((2.5, 0.4), 7.0, 7.3, facecolor="#fbfcfe", edgecolor=INK, lw=1.6, zorder=1)
    ax.add_patch(bound)
    ax.text(6.0, 7.45, "Sky Royal Flight Reservation System (Web & Desktop)",
            fontsize=10, fontweight="bold", color=INK, ha="center")

    # Left actors
    draw_actor(ax, 1.0, 6.2, "Guest\nVisitor")
    draw_actor(ax, 1.0, 3.9, "Registered\nCustomer")
    draw_actor(ax, 1.0, 1.6, "System\nAdministrator")

    # Right actors
    draw_actor(ax, 11.0, 4.9, "Simulated\nPayment Gateway")
    draw_actor(ax, 11.0, 2.5, "Email Outbox\n/ SMTP")

    # Use cases
    ucs = [
        ("UC1: Browse and Search Flights", 4.5, 6.7),
        ("UC2: Register New Account", 7.4, 6.7),
        ("UC3: Log In and Log Out", 4.5, 5.75),
        ("UC4: Verify QR Boarding Pass", 7.4, 5.75),
        ("UC5: Select and Hold Seat (5 min)", 4.5, 4.8),
        ("UC6: Checkout and Pay Fare", 7.4, 4.8),
        ("UC7: Download PDF & Calendar (.ics)", 4.5, 3.85),
        ("UC8: View My Bookings Portal", 7.4, 3.85),
        ("UC9: Cancel Booking & Refund (24h)", 5.95, 2.9),
        ("UC10: View Admin KPI Dashboard", 4.5, 1.9),
        ("UC11: Inspect All Bookings & Loads", 7.4, 1.9),
        ("UC12: Reseed Rolling Flight Schedule", 5.95, 1.0),
    ]
    uc_pos = {}
    for label, x, y in ucs:
        patch = Ellipse((x, y), 2.65, 0.62, fc=SKY_LIGHT, ec=INK, lw=1.2, zorder=3)
        ax.add_patch(patch)
        ax.text(x, y, label, fontsize=7.6, color=INK, ha="center", va="center", zorder=4)
        uc_pos[label[:3]] = (x, y)

    # Solid association lines
    # Guest
    for k in ["UC1", "UC2", "UC3", "UC4"]:
        ux, uy = uc_pos[k]
        draw_line(ax, 1.25, 6.2, ux - 1.32, uy, color=SLATE, lw=1.1)
    # Customer
    for k in ["UC1", "UC3", "UC5", "UC6", "UC7", "UC8", "UC9"]:
        ux, uy = uc_pos[k]
        draw_line(ax, 1.25, 3.9, ux - 1.32, uy, color=INK, lw=1.1)
    # Admin
    for k in ["UC3", "UC1", "UC10", "UC11", "UC12"]:
        key = k if k in uc_pos else k[:3]
        if k == "UC10": ux, uy = (4.5, 1.9)
        elif k == "UC11": ux, uy = (7.4, 1.9)
        elif k == "UC12": ux, uy = (5.95, 1.0)
        else: ux, uy = uc_pos[k]
        draw_line(ax, 1.25, 1.6, ux - 1.32, uy, color=ERD_DARKBLUE, lw=1.1)

    # External actors
    draw_line(ax, 7.4 + 1.32, 4.8, 10.75, 4.9, color=ERD_DARKBLUE, lw=1.2)
    draw_line(ax, 5.95 + 1.32, 2.9, 10.75, 4.9, color=ERD_DARKBLUE, lw=1.2)
    draw_line(ax, 7.4 + 1.32, 6.7, 10.75, 2.5, color=ERD_DARKBLUE, lw=1.2)

    save_fig(fig, "fig02_use_cases.png")


# ==============================================================================
# FIGURE 3: Core Domain & Service Layer Class Diagram
# ==============================================================================
def draw_class_box(ax, x, y, w, h, title, attrs, methods, header_fc=INK):
    # Outer box
    outer = Rectangle((x - w / 2, y - h / 2), w, h, fc=WHITE, ec=INK, lw=1.2, zorder=3)
    ax.add_patch(outer)
    hh = 0.34
    header = Rectangle((x - w / 2, y + h / 2 - hh), w, hh, fc=header_fc, ec=INK, lw=1.2, zorder=4)
    ax.add_patch(header)
    ax.text(x, y + h / 2 - hh / 2, title, fontsize=8.2, fontweight="bold",
            color=WHITE, ha="center", va="center", zorder=5)

    body_h = h - hh
    attr_h = body_h * 0.56
    mid_y = y + h / 2 - hh - attr_h
    ax.plot([x - w / 2, x + w / 2], [mid_y, mid_y], color=INK, lw=0.9, zorder=4)

    ax.text(x - w / 2 + 0.08, y + h / 2 - hh - 0.06, "\n".join(attrs),
            fontsize=6.8, color=INK, ha="left", va="top", zorder=5, linespacing=1.2)
    ax.text(x - w / 2 + 0.08, mid_y - 0.06, "\n".join(methods),
            fontsize=6.8, color=SLATE, ha="left", va="top", zorder=5, linespacing=1.2)


def gen_fig03_class_core():
    fig, ax = plt.subplots(figsize=(12, 8.2))
    ax.set_xlim(0, 12.6)
    ax.set_ylim(0, 8.6)
    ax.axis("off")

    ax.text(6.3, 8.3, "Core Module Class Diagram (Domain Models, Services, and DAOs)",
            fontsize=13.5, fontweight="bold", color=INK, ha="center")

    # Row 1: Domain Models
    draw_class_box(ax, 1.65, 6.85, 2.8, 1.9, "User",
                   ["- userId: long", "- username: String", "- passwordHash: String", "- role: UserRole", "- email: String"],
                   ["+ isActive(): boolean", "+ getRole(): UserRole"], header_fc=ERD_DARKBLUE)
    draw_class_box(ax, 4.75, 6.85, 2.8, 1.9, "FlightSearchResult",
                   ["- flightId: long", "- flightNumber: String", "- originCode / destCode", "- departureTime: Instant", "- basePrice: BigDecimal"],
                   ["+ getAvailableSeats(): int", "+ getDuration(): Duration"], header_fc=ERD_DARKBLUE)
    draw_class_box(ax, 7.85, 6.85, 2.8, 1.9, "FlightSeat",
                   ["- flightSeatId: long", "- seatRow / seatCol", "- seatClass: SeatClass", "- status: SeatStatus", "- heldBy / heldUntil"],
                   ["+ getSeatLabel(): String", "+ isHeldBy(owner): boolean"], header_fc=ERD_DARKBLUE)
    draw_class_box(ax, 10.95, 6.85, 2.8, 1.9, "BookingRecord",
                   ["- bookingId: long", "- bookingRef: String", "- passengerName: String", "- passportNumber: String", "- totalAmount: BigDecimal"],
                   ["+ getStatus(): String", "+ getRefundRef(): String"], header_fc=ERD_DARKBLUE)

    # Row 2: Services
    draw_class_box(ax, 1.65, 4.1, 2.8, 2.0, "AuthService",
                   ["- userDao: UserDao", "- loginThrottle"],
                   ["+ authenticate(id, pw): User", "+ userExists(name): boolean", "+ register(req): User"], header_fc=INK)
    draw_class_box(ax, 4.75, 4.1, 2.8, 2.0, "FlightService",
                   ["- flightDao: FlightDao", "- airportDao: AirportDao"],
                   ["+ searchFlights(o, d, dt)", "+ getAllAirports()", "+ getPopularRoutes()"], header_fc=INK)
    draw_class_box(ax, 7.85, 4.1, 2.8, 2.0, "SeatMapService",
                   ["- seatDao: SeatDao", "- holdSeconds: int (300)"],
                   ["+ getSeatMap(flightId)", "+ holdSeat(id, owner)", "+ releaseSeat(id, owner)"], header_fc=INK)
    draw_class_box(ax, 10.95, 4.1, 2.8, 2.0, "BookingService",
                   ["- bookingDao: BookingDao", "- pricingService", "- paymentService"],
                   ["+ createBooking(ctx, pay)", "+ listBookingsForUser(uid)", "+ cancelBookingForUser()"], header_fc=INK)

    # Row 3: Persistence & Utilities
    draw_class_box(ax, 2.1, 1.35, 3.6, 1.9, "DAO Layer (JDBC PreparedStatement)",
                   ["UserDaoImpl, FlightDaoImpl", "SeatDaoImpl, BookingDaoImpl", "AdminDashboardDaoImpl"],
                   ["+ getConnection(): Connection", "+ Atomic UPDATE / FOR UPDATE"], header_fc=SLATE)
    draw_class_box(ax, 6.3, 1.35, 3.6, 1.9, "Pricing & Payment Services",
                   ["PricingService (Demand & Timing)", "SimulatedPaymentGateway", "CancellationPolicy (24h cutoff)"],
                   ["+ quote(flightId, seatClass)", "+ charge(card, amt, key)", "+ refundWithReference(ref)"], header_fc=SLATE)
    draw_class_box(ax, 10.5, 1.35, 3.6, 1.9, "Verification & Document Utils",
                   ["BoardingPassVerificationService", "BoardingPassPdfGenerator (OpenPDF)", "QrCodeGenerator (ZXing), IcsGen"],
                   ["+ sign(pnr): String (HMAC)", "+ verify(pnr, sig): Result", "+ generate(ticket, qr): byte[]"], header_fc=SLATE)

    # Relationships (solid arrows)
    draw_arrow(ax, 1.65, 5.1, 1.65, 5.9, "")
    draw_arrow(ax, 4.75, 5.1, 4.75, 5.9, "")
    draw_arrow(ax, 7.85, 5.1, 7.85, 5.9, "")
    draw_arrow(ax, 10.95, 5.1, 10.95, 5.9, "")

    draw_arrow(ax, 1.65, 3.1, 2.1, 2.32, "")
    draw_arrow(ax, 4.75, 3.1, 2.8, 2.32, "")
    draw_arrow(ax, 7.85, 3.1, 3.4, 2.32, "")
    draw_arrow(ax, 10.95, 3.1, 3.8, 2.32, "")
    draw_arrow(ax, 10.2, 3.1, 6.8, 2.32, "")
    draw_arrow(ax, 10.95, 3.1, 10.5, 2.32, "")

    save_fig(fig, "fig03_class_core.png")


# ==============================================================================
# FIGURE 4: Web & Desktop Presentation Layer Class Diagram
# ==============================================================================
def gen_fig04_class_ui():
    fig, ax = plt.subplots(figsize=(12, 7.2))
    ax.set_xlim(0, 12.6)
    ax.set_ylim(0, 7.6)
    ax.axis("off")

    ax.text(6.3, 7.25, "Web and Desktop Presentation Layer Class Diagram",
            fontsize=13.5, fontweight="bold", color=INK, ha="center")

    # Web Panel
    wpanel = FancyBboxPatch((0.3, 0.5), 5.8, 6.3, boxstyle="round,pad=0.05,rounding_size=0.12",
                            facecolor=SKY_LIGHT, edgecolor=SKY, lw=1.4, zorder=1)
    ax.add_patch(wpanel)
    ax.text(3.2, 6.55, "Web Module (com.group2.flightres.web)", fontsize=10, fontweight="bold", color=INK, ha="center")

    draw_class_box(ax, 3.2, 5.3, 5.2, 1.65, "WebApplication",
                   ["- app: Javalin", "- controller: WebController", "- actualPort: int (8080+)"],
                   ["+ startWithFallback(port): int", "- setupSecurityAndFilters()", "- setupRoutes() / setupErrorHandling()"], header_fc=INK)
    draw_class_box(ax, 3.2, 3.2, 5.2, 1.75, "WebController",
                   ["- flightService, seatMapService, bookingService", "- authService, registrationService, paymentService", "- verificationService, adminDashboardService"],
                   ["+ handleHome / handleFlights / handleSeats", "+ handleBook / handleCheckoutGet / handlePayPost", "+ handleConfirmation / handleMyBookings / handleAdmin"], header_fc=ERD_DARKBLUE)
    draw_class_box(ax, 1.75, 1.25, 2.5, 1.2, "CheckoutSession",
                   ["- flightId, seatId", "- idempotencyKey"],
                   ["+ isValid(): boolean"], header_fc=SLATE)
    draw_class_box(ax, 4.65, 1.25, 2.5, 1.2, "SeatMapLayoutBuilder",
                   ["- sections: List", "- topBands / bottomBands"],
                   ["+ build(seats): View"], header_fc=SLATE)

    draw_arrow(ax, 3.2, 4.46, 3.2, 4.08, "")
    draw_arrow(ax, 2.3, 2.3, 1.75, 1.88, "")
    draw_arrow(ax, 4.1, 2.3, 4.65, 1.88, "")

    # Desktop Panel
    dpanel = FancyBboxPatch((6.5, 0.5), 5.8, 6.3, boxstyle="round,pad=0.05,rounding_size=0.12",
                            facecolor=CREAM, edgecolor=INK, lw=1.4, zorder=1)
    ax.add_patch(dpanel)
    ax.text(9.4, 6.55, "Desktop Module (com.group2.flightres)", fontsize=10, fontweight="bold", color=INK, ha="center")

    draw_class_box(ax, 9.4, 5.3, 5.2, 1.65, "App & MainShellController",
                   ["- primaryStage: Stage", "- bookingContext: BookingContext", "- navigator: Navigator"],
                   ["+ start(stage): void", "+ navigateTo(route): void", "+ updateStepper(step): void"], header_fc=INK)
    draw_class_box(ax, 9.4, 3.2, 5.2, 1.75, "FXML View Controllers",
                   ["LoginController, HomeController", "FlightSearchController, SeatMapController", "CheckoutController, BookingConfirmationController", "MyBookingsController, AdminDashboardController"],
                   ["+ initialize(): void", "+ setBookingContext(ctx): void"], header_fc=ERD_DARKBLUE)
    draw_class_box(ax, 7.95, 1.25, 2.5, 1.2, "UI Components",
                   ["NavBar, StepperBar", "TicketView"],
                   ["+ bind(data): void"], header_fc=SLATE)
    draw_class_box(ax, 10.85, 1.25, 2.5, 1.2, "Glass Bridges & Utils",
                   ["ThemeManager, Motion", "GlassSeatMapBridge"],
                   ["+ applyTheme(scene)"], header_fc=SLATE)

    draw_arrow(ax, 9.4, 4.46, 9.4, 4.08, "")
    draw_arrow(ax, 8.5, 2.3, 7.95, 1.88, "")
    draw_arrow(ax, 10.3, 2.3, 10.85, 1.88, "")

    save_fig(fig, "fig04_class_ui.png")


# ==============================================================================
# FIGURE 5: CHEN ERD OVERVIEW (Matching docs-in/erd-reference.png EXACTLY)
# ==============================================================================
def draw_erd_link(ax, p1, p2, c1="", c2="", off1=(0, 0.2), off2=(0, 0.2)):
    draw_line(ax, p1[0], p1[1], p2[0], p2[1], color=ERD_LINE, lw=1.4)
    if c1:
        ax.text(p1[0] + (p2[0] - p1[0]) * 0.44 + off1[0],
                p1[1] + (p2[1] - p1[1]) * 0.44 + off1[1],
                c1, fontsize=10.5, fontweight="bold", color=INK, ha="center", va="center",
                bbox=dict(boxstyle="round,pad=0.14", fc=WHITE, ec="none", alpha=0.92), zorder=6)
    if c2:
        ax.text(p1[0] + (p2[0] - p1[0]) * 0.56 + off2[0],
                p1[1] + (p2[1] - p1[1]) * 0.56 + off2[1],
                c2, fontsize=10.5, fontweight="bold", color=INK, ha="center", va="center",
                bbox=dict(boxstyle="round,pad=0.14", fc=WHITE, ec="none", alpha=0.92), zorder=6)


def gen_fig05_erd_overview():
    fig, ax = plt.subplots(figsize=(14.5, 9.2))
    ax.set_xlim(0, 14.5)
    ax.set_ylim(0, 9.2)
    ax.axis("off")

    ax.text(7.25, 8.85, "Entity Relationship Diagram (Overview) - Sky Royal Schema",
            fontsize=15.5, fontweight="bold", color=INK, ha="center")
    draw_erd_legend(ax, x=0.35, y=7.45)

    E = {
        "airports": (1.8, 6.1),
        "aircraft_types": (7.2, 6.45),
        "seat_class_pricing": (12.5, 6.45),
        "flights": (1.8, 3.4),
        "seat_templates": (11.9, 4.1),
        "flight_seats": (7.2, 3.4),
        "users": (1.8, 0.95),
        "bookings": (6.5, 0.95),
        "booking_seats": (11.9, 2.0),
        "payments": (11.9, 0.65),
    }

    R = {
        "orig_dest": (1.8, 4.75, "Origin /\nDest"),
        "operates": (4.5, 5.1, "Operates"),
        "defines": (9.65, 5.35, "Defines"),
        "prices": (12.2, 5.35, "Prices"),
        "has_seats": (4.5, 3.4, "Has Seats"),
        "instantiates": (9.65, 3.75, "Instantiates"),
        "makes": (4.15, 0.95, "Makes"),
        "for_flight": (4.15, 2.18, "For Flight"),
        "allocates": (9.2, 1.82, "Allocates"),
        "reserves": (9.5, 2.8, "Reserves"),
        "paid_by": (9.2, 0.72, "Paid By"),
    }

    draw_erd_link(ax, E["airports"], (1.8, 4.75), "1", "", off1=(0.28, 0))
    draw_erd_link(ax, (1.8, 4.75), E["flights"], "", "N", off2=(0.28, 0))
    draw_erd_link(ax, E["aircraft_types"], (4.5, 5.1), "1", "", off1=(0, 0.28))
    draw_erd_link(ax, (4.5, 5.1), E["flights"], "", "N", off2=(-0.15, 0.28))
    draw_erd_link(ax, E["aircraft_types"], (9.65, 5.35), "1", "", off1=(0.1, 0.24))
    draw_erd_link(ax, (9.65, 5.35), E["seat_templates"], "", "N", off2=(0.1, 0.24))
    draw_erd_link(ax, E["seat_class_pricing"], (12.2, 5.35), "1", "", off1=(0.3, 0))
    draw_erd_link(ax, (12.2, 5.35), E["seat_templates"], "", "N", off2=(0.3, 0))
    draw_erd_link(ax, E["flights"], (4.5, 3.4), "1", "", off1=(0, 0.24))
    draw_erd_link(ax, (4.5, 3.4), E["flight_seats"], "", "N", off2=(0, 0.24))
    draw_erd_link(ax, E["seat_templates"], (9.65, 3.75), "1", "", off1=(-0.48, 0.3))
    draw_erd_link(ax, (9.65, 3.75), E["flight_seats"], "", "N", off2=(0, 0.24))
    draw_erd_link(ax, E["users"], (4.15, 0.95), "1", "", off1=(0, 0.24))
    draw_erd_link(ax, (4.15, 0.95), E["bookings"], "", "N", off2=(0, 0.24))
    draw_erd_link(ax, E["flights"], (4.15, 2.18), "1", "", off1=(0.22, 0.18))
    draw_erd_link(ax, (4.15, 2.18), E["bookings"], "", "N", off2=(0.22, 0.18))
    draw_erd_link(ax, E["bookings"], (9.2, 1.82), "1", "", off1=(0, 0.24))
    draw_erd_link(ax, (9.2, 1.82), E["booking_seats"], "", "N", off2=(0, 0.24))
    draw_erd_link(ax, E["flight_seats"], (9.5, 2.8), "1", "", off1=(0.18, 0.2))
    draw_erd_link(ax, (9.5, 2.8), E["booking_seats"], "", "N", off2=(0.18, 0.2))
    draw_erd_link(ax, E["bookings"], (9.2, 0.72), "1", "", off1=(0, -0.24))
    draw_erd_link(ax, (9.2, 0.72), E["payments"], "", "N", off2=(0, -0.24))

    for k, (rx, ry, lbl) in R.items():
        draw_diamond(ax, rx, ry, 1.75, 0.86, lbl, fontsize=9)

    for name, (ex, ey) in E.items():
        w = 2.55 if len(name) > 13 else 2.15
        draw_rect(ax, ex, ey, w, 0.66, name, fontsize=10.5)

    save_fig(fig, "fig05_erd_overview.png")


# Helper for Detail Chen ERDs
def draw_entity_with_attrs(ax, ex, ey, name, attrs, attr_positions, ew=2.45, eh=0.68):
    for (aname, is_pk), (axp, ayp) in zip(attrs, attr_positions):
        draw_line(ax, ex, ey, axp, ayp, color=ERD_LINE, lw=1.25)
        ow = max(1.55, len(aname) * 0.125 + 0.42)
        draw_oval(ax, axp, ayp, ow, 0.54, aname, fontsize=9.2, underline=is_pk)
    draw_rect(ax, ex, ey, ew, eh, name, fontsize=10.8)


# ==============================================================================
# FIGURE 6: CHEN ERD DETAIL 1 - users, airports, flights
# ==============================================================================
def gen_fig06_erd_detail_1():
    fig, ax = plt.subplots(figsize=(13.0, 8.6))
    ax.set_xlim(0, 14.0)
    ax.set_ylim(0, 9.0)
    ax.axis("off")

    ax.text(7.0, 8.65, "Chen ERD Detail 1: Users, Airports, and Flights",
            fontsize=14, fontweight="bold", color=INK, ha="center")
    draw_erd_legend(ax, x=0.35, y=7.3)

    # Entity 1: users (left)
    u_attrs = [
        ("user_id", True), ("username", False), ("password_hash", False),
        ("role", False), ("full_name", False), ("email", False),
        ("is_active", False), ("created_at", False)
    ]
    u_pos = [
        (1.3, 6.2), (3.1, 6.55), (4.6, 5.8), (4.5, 4.7),
        (4.2, 3.6), (2.7, 3.15), (1.1, 3.65), (0.95, 4.95)
    ]
    draw_entity_with_attrs(ax, 2.65, 4.9, "users", u_attrs, u_pos, ew=1.8)

    # Entity 2: airports (top right)
    a_attrs = [
        ("airport_id", True), ("iata_code", False), ("name", False),
        ("city", False), ("country", False), ("timezone", False)
    ]
    a_pos = [
        (8.6, 8.0), (10.5, 8.15), (12.4, 7.8),
        (12.8, 6.65), (12.3, 5.65), (8.3, 6.8)
    ]
    draw_entity_with_attrs(ax, 10.5, 6.8, "airports", a_attrs, a_pos, ew=1.9)

    # Entity 3: flights (bottom right)
    f_attrs = [
        ("flight_id", True), ("flight_number", False), ("aircraft_type_id (FK)", False),
        ("origin_airport_id (FK)", False), ("dest_airport_id (FK)", False),
        ("departure_time", False), ("arrival_time", False),
        ("base_price", False), ("status", False), ("created_at", False)
    ]
    f_pos = [
        (6.4, 3.5), (6.1, 2.35), (6.1, 1.25), (7.9, 0.68),
        (11.1, 0.68), (12.6, 1.35), (12.85, 2.45),
        (12.6, 3.55), (8.4, 3.85), (11.7, 4.2)
    ]
    draw_entity_with_attrs(ax, 9.6, 2.4, "flights", f_attrs, f_pos, ew=1.9)

    # Relationship airports -> flights (Origin / Destination)
    draw_erd_link(ax, (10.5, 6.49), (10.1, 4.95), "1", "", off1=(0.3, 0))
    draw_erd_link(ax, (10.1, 4.95), (9.6, 2.71), "", "N", off2=(0.3, 0))
    draw_diamond(ax, 10.1, 4.95, 2.1, 0.85, "Origin /\nDestination", fontsize=8)

    # Note linking users -> bookings (shown in Detail 3)
    draw_box(ax, 2.65, 1.3, 3.4, 0.9,
             "Links to Detail 3:\nusers (1) -- Makes -- (N) bookings\nflights (1) -- For Flight -- (N) bookings",
             fc=CREAM, ec=INK, fontsize=8)

    save_fig(fig, "fig06_erd_detail_1.png")


# ==============================================================================
# FIGURE 7: CHEN ERD DETAIL 2 - aircraft_types, seat_class_pricing, seat_templates, flight_seats
# ==============================================================================
def gen_fig07_erd_detail_2():
    fig, ax = plt.subplots(figsize=(13.2, 8.8))
    ax.set_xlim(0, 14.2)
    ax.set_ylim(0, 9.2)
    ax.axis("off")

    ax.text(7.1, 8.85, "Chen ERD Detail 2: Aircraft Types, Seat Templates, Pricing, and Flight Seats",
            fontsize=13.5, fontweight="bold", color=INK, ha="center")
    draw_erd_legend(ax, x=0.35, y=7.55)

    # aircraft_types (top-left below legend)
    at_attrs = [("aircraft_type_id", True), ("model", False), ("total_rows", False), ("grid_width", False)]
    at_pos = [(1.35, 6.7), (3.4, 6.7), (1.1, 4.65), (3.1, 4.45)]
    draw_entity_with_attrs(ax, 2.35, 5.65, "aircraft_types", at_attrs, at_pos, ew=2.2)

    # seat_class_pricing (top-right)
    scp_attrs = [("seat_class", True), ("price_multiplier", False)]
    scp_pos = [(9.7, 8.15), (12.4, 8.15)]
    draw_entity_with_attrs(ax, 11.1, 7.05, "seat_class_pricing", scp_attrs, scp_pos, ew=2.6)

    # seat_templates (mid-right)
    st_attrs = [
        ("seat_template_id", True), ("aircraft_type_id (FK)", False), ("seat_row", False),
        ("seat_col", False), ("seat_class (FK)", False), ("display_row", False),
        ("display_col", False), ("is_window", False), ("is_aisle", False)
    ]
    st_pos = [
        (8.4, 6.7), (7.6, 5.85), (12.85, 5.65), (13.0, 4.7),
        (12.9, 3.7), (12.6, 2.75), (10.7, 2.55), (8.9, 2.85), (12.7, 6.5)
    ]
    draw_entity_with_attrs(ax, 10.4, 4.45, "seat_templates", st_attrs, st_pos, ew=2.3)

    # flight_seats (bottom-left)
    fs_attrs = [
        ("flight_seat_id", True), ("flight_id (FK)", False), ("seat_template_id (FK)", False),
        ("status", False), ("held_until", False), ("held_by", False)
    ]
    fs_pos = [
        (1.3, 3.0), (1.05, 1.85), (1.55, 0.75), (4.4, 0.72), (5.8, 1.15), (6.0, 2.3)
    ]
    draw_entity_with_attrs(ax, 3.55, 2.1, "flight_seats", fs_attrs, fs_pos, ew=2.1)

    # Relationships
    # aircraft_types -> seat_templates
    draw_erd_link(ax, (3.45, 5.65), (5.65, 5.3), "1", "", off1=(0, 0.25))
    draw_erd_link(ax, (5.65, 5.3), (9.25, 4.45), "", "N", off2=(0, 0.25))
    draw_diamond(ax, 5.65, 5.3, 1.7, 0.8, "Defines", fontsize=8.5)

    # seat_class_pricing -> seat_templates
    draw_erd_link(ax, (11.1, 6.74), (10.75, 5.75), "1", "", off1=(0.3, 0))
    draw_erd_link(ax, (10.75, 5.75), (10.4, 4.76), "", "N", off2=(0.3, 0))
    draw_diamond(ax, 10.75, 5.75, 1.65, 0.78, "Prices", fontsize=8.5)

    # seat_templates -> flight_seats
    draw_erd_link(ax, (9.25, 4.25), (6.7, 3.25), "1", "", off1=(0, 0.22))
    draw_erd_link(ax, (6.7, 3.25), (4.6, 2.25), "", "N", off2=(0, 0.22))
    draw_diamond(ax, 6.7, 3.25, 1.9, 0.82, "Instantiates", fontsize=8)

    save_fig(fig, "fig07_erd_detail_2.png")


# ==============================================================================
# FIGURE 8: CHEN ERD DETAIL 3 - bookings, booking_seats, payments
# ==============================================================================
def gen_fig08_erd_detail_3():
    fig, ax = plt.subplots(figsize=(13.2, 8.8))
    ax.set_xlim(0, 14.2)
    ax.set_ylim(0, 9.2)
    ax.axis("off")

    ax.text(7.1, 8.85, "Chen ERD Detail 3: Bookings, Booking Seats, and Payments",
            fontsize=14, fontweight="bold", color=INK, ha="center")
    draw_erd_legend(ax, x=0.35, y=7.55)

    # bookings (left-center)
    b_attrs = [
        ("booking_id", True), ("booking_ref", False), ("user_id (FK)", False),
        ("flight_id (FK)", False), ("status", False), ("total_amount", False), ("created_at", False)
    ]
    b_pos = [
        (1.3, 6.45), (3.1, 6.65), (4.8, 6.15),
        (1.0, 5.0), (1.15, 3.65), (2.45, 2.95), (3.85, 2.45)
    ]
    draw_entity_with_attrs(ax, 2.95, 4.95, "bookings", b_attrs, b_pos, ew=2.0)

    # booking_seats (top-right)
    bs_attrs = [
        ("booking_seat_id", True), ("booking_id (FK)", False), ("flight_seat_id (FK)", False),
        ("passenger_name", False), ("passport_number", False), ("date_of_birth", False),
        ("price_paid", False), ("is_active", False)
    ]
    bs_pos = [
        (8.15, 8.25), (10.7, 8.25), (12.8, 7.95),
        (13.0, 6.85), (12.85, 5.75), (11.1, 5.25),
        (9.1, 5.35), (7.6, 7.25)
    ]
    draw_entity_with_attrs(ax, 10.65, 6.75, "booking_seats", bs_attrs, bs_pos, ew=2.25)

    # payments (bottom-right)
    p_attrs = [
        ("payment_id", True), ("booking_id (FK)", False), ("amount", False),
        ("method", False), ("status", False), ("transaction_ref", False),
        ("processed_at", False), ("card_brand", False), ("card_last4", False),
        ("idempotency_key", False), ("refund_ref", False), ("refunded_at", False)
    ]
    p_pos = [
        (7.0, 3.95), (8.7, 4.15), (10.4, 4.05), (12.1, 3.75),
        (13.1, 2.85), (13.0, 1.75), (12.2, 0.75), (10.2, 0.55),
        (8.3, 0.65), (6.5, 1.05), (5.8, 1.95), (6.0, 2.85)
    ]
    draw_entity_with_attrs(ax, 9.65, 2.25, "payments", p_attrs, p_pos, ew=2.0)

    # Relationships
    draw_erd_link(ax, (3.95, 5.2), (6.4, 6.1), "1", "", off1=(0, -0.26))
    draw_erd_link(ax, (6.4, 6.1), (9.5, 6.75), "", "N", off2=(0.4, -0.3))
    draw_diamond(ax, 6.4, 6.1, 1.95, 0.9, "Allocates", fontsize=9.5)

    draw_erd_link(ax, (3.95, 4.65), (5.2, 3.6), "1", "", off1=(0.18, 0.24))
    draw_erd_link(ax, (5.2, 3.6), (8.65, 2.25), "", "N", off2=(0.18, 0.24))
    draw_diamond(ax, 5.2, 3.6, 1.85, 0.88, "Paid By", fontsize=9.5)

    save_fig(fig, "fig08_erd_detail_3.png")


# ==============================================================================
# FIGURE 9: Booking Flow Diagram
# ==============================================================================
def gen_fig09_flow_booking():
    fig, ax = plt.subplots(figsize=(10.5, 7.6))
    ax.set_xlim(0, 11)
    ax.set_ylim(0, 8.2)
    ax.axis("off")

    ax.text(5.5, 7.85, "End-to-End Flight Booking Flowchart",
            fontsize=14, fontweight="bold", color=INK, ha="center")

    draw_box(ax, 3.2, 7.1, 3.2, 0.58, "Start: User Visits Home / Search", fc=INK, ec=INK, tc=WHITE, bold=True, radius=0.25)
    draw_box(ax, 3.2, 6.05, 3.6, 0.65, "Select Origin, Destination, Date & Cabin\n(FlightService.searchFlights)", fc=SKY_LIGHT, ec=INK)
    draw_diamond(ax, 3.2, 4.8, 3.4, 1.0, "Flights Found &\n> 60m to Departure?", fc=CREAM, ec=INK, tc=INK, fontsize=8.2)
    draw_box(ax, 8.2, 4.8, 3.4, 0.72, "Show Empty State or\nSales Closed Message", fc=RED_BG, ec=RED, tc=INK)
    draw_box(ax, 3.2, 3.55, 3.8, 0.68, "Open Cabin Seat Map & Select Seat\n(SeatMapService holds seat for 5 min)", fc=SKY_LIGHT, ec=INK)
    draw_box(ax, 3.2, 2.45, 3.8, 0.68, "Enter Passenger Details & Simulated Card\n(ValidationUtil + CSRF + Throttle)", fc=SKY_LIGHT, ec=INK)
    draw_diamond(ax, 3.2, 1.25, 3.2, 0.95, "Payment Approved &\nHold Still Valid?", fc=CREAM, ec=INK, tc=INK, fontsize=8.2)
    draw_box(ax, 8.2, 2.45, 3.4, 0.72, "Return HTTP 402 (Declined)\nor HTTP 410 (Hold Expired)", fc=RED_BG, ec=RED, tc=INK)
    draw_box(ax, 8.2, 1.25, 3.6, 0.82, "Atomic DB Commit -> Generate PNR,\nHMAC QR Code & PDF Boarding Pass", fc=GREEN_BG, ec=GREEN, tc=INK, bold=True)

    draw_arrow(ax, 3.2, 6.8, 3.2, 6.4)
    draw_arrow(ax, 3.2, 5.7, 3.2, 5.32)
    draw_arrow(ax, 4.9, 4.8, 6.48, 4.8, "No")
    draw_arrow(ax, 3.2, 4.28, 3.2, 3.92, "Yes", label_offset=(0.35, 0))
    draw_arrow(ax, 3.2, 3.18, 3.2, 2.82)
    draw_arrow(ax, 3.2, 2.08, 3.2, 1.75)
    draw_arrow(ax, 4.8, 1.25, 6.38, 1.25, "Yes")
    draw_arrow(ax, 4.8, 1.48, 6.48, 2.28, "No", label_offset=(-0.25, 0.25))

    save_fig(fig, "fig09_flow_booking.png")


# ==============================================================================
# FIGURE 10: Payment Processing Flowchart
# ==============================================================================
def gen_fig10_flow_payment():
    fig, ax = plt.subplots(figsize=(11.0, 7.6))
    ax.set_xlim(0, 11.5)
    ax.set_ylim(0, 8.2)
    ax.axis("off")

    ax.text(5.75, 7.85, "Payment Processing, Security, and Idempotency Flowchart",
            fontsize=13.5, fontweight="bold", color=INK, ha="center")

    draw_box(ax, 3.2, 7.1, 3.6, 0.56, "POST /pay Received with Form Data", fc=INK, ec=INK, tc=WHITE, bold=True, radius=0.22)
    draw_diamond(ax, 3.2, 5.95, 3.5, 0.92, "Valid CSRF Token &\nWithin Throttle (10/10m)?", fc=CREAM, ec=INK, tc=INK, fontsize=8)
    draw_box(ax, 8.6, 5.95, 3.6, 0.68, "Reject Request\nHTTP 403 (CSRF) or HTTP 429 (Limit)", fc=RED_BG, ec=RED, tc=INK)

    draw_diamond(ax, 3.2, 4.6, 3.5, 0.92, "Valid Passenger Input &\nActive Owned Seat Hold?", fc=CREAM, ec=INK, tc=INK, fontsize=8)
    draw_box(ax, 8.6, 4.6, 3.6, 0.68, "HTTP 400 (Validation Error)\nor HTTP 410 (Hold Expired) / 403", fc=RED_BG, ec=RED, tc=INK)

    draw_box(ax, 3.2, 3.35, 4.1, 0.68, "Server Re-Quotes Price (PricingService)\nIgnores Any Client-Tampered Price", fc=SKY_LIGHT, ec=INK)
    draw_diamond(ax, 3.2, 2.1, 3.6, 0.95, "SimulatedPaymentGateway\nCard 4242... Approved?", fc=CREAM, ec=INK, tc=INK, fontsize=8)
    draw_box(ax, 8.6, 2.1, 3.6, 0.7, "Declined (4000 0000 0000 0002)\nReturn HTTP 402; Keep Hold Active", fc=RED_BG, ec=RED, tc=INK)

    draw_box(ax, 3.2, 0.8, 4.6, 0.78, "Single ACID Transaction (BookingDaoImpl):\nInsert bookings + booking_seats + payments (idempotency_key)\nUpdate flight_seats -> BOOKED; Redirect 302 Confirmation", fc=GREEN_BG, ec=GREEN, tc=INK, bold=True, fontsize=7.8)

    draw_arrow(ax, 3.2, 6.8, 3.2, 6.43)
    draw_arrow(ax, 4.95, 5.95, 6.78, 5.95, "No")
    draw_arrow(ax, 3.2, 5.47, 3.2, 5.08, "Yes", label_offset=(0.35, 0))
    draw_arrow(ax, 4.95, 4.6, 6.78, 4.6, "No")
    draw_arrow(ax, 3.2, 4.12, 3.2, 3.72, "Yes", label_offset=(0.35, 0))
    draw_arrow(ax, 3.2, 2.98, 3.2, 2.6)
    draw_arrow(ax, 5.0, 2.1, 6.78, 2.1, "No")
    draw_arrow(ax, 3.2, 1.6, 3.2, 1.22, "Yes", label_offset=(0.35, 0))

    save_fig(fig, "fig10_flow_payment.png")


# ==============================================================================
# FIGURE 11: Seat Hold and Release Concurrency Flow
# ==============================================================================
def gen_fig11_flow_seathold():
    fig, ax = plt.subplots(figsize=(11.0, 6.8))
    ax.set_xlim(0, 11.5)
    ax.set_ylim(0, 7.2)
    ax.axis("off")

    ax.text(5.75, 6.85, "Seat Hold and Release Optimistic Concurrency Flow",
            fontsize=13.5, fontweight="bold", color=INK, ha="center")

    draw_box(ax, 2.4, 5.7, 3.4, 0.7, "User Clicks Available Seat\n(holdOwner = user:<id> or session)", fc=INK, ec=INK, tc=WHITE, bold=True)
    draw_box(ax, 2.4, 4.35, 4.0, 0.88, "Atomic SQL Update (SeatDaoImpl):\nUPDATE flight_seats SET status='HELD',\nheld_by=?, held_until=NOW()+5 min\nWHERE id=? AND (AVAILABLE OR expired)", fc=SKY_LIGHT, ec=INK, fontsize=7.8)
    draw_diamond(ax, 2.4, 2.85, 3.0, 0.95, "Updated Rows\n== 1 ?", fc=CREAM, ec=INK, tc=INK, fontsize=8.5)
    draw_box(ax, 2.4, 1.2, 3.6, 0.72, "Seat Already Taken / Held\nThrow SeatUnavailableException", fc=RED_BG, ec=RED, tc=INK)

    draw_box(ax, 7.7, 5.2, 3.8, 0.8, "Seat Status = HELD (5:00 Timer)\nVisible as 'Yours' to Owner,\n'Held by another' to Other Users", fc=CREAM, ec=INK, bold=True)

    draw_box(ax, 6.2, 3.2, 2.5, 0.85, "Outcome A:\nCheckout Paid\nStatus -> BOOKED", fc=GREEN_BG, ec=GREEN, tc=INK, bold=True, fontsize=8)
    draw_box(ax, 9.2, 3.2, 2.7, 0.85, "Outcome B:\nOwner Clicks Release\nStatus -> AVAILABLE", fc=SKY_LIGHT, ec=INK, fontsize=8)
    draw_box(ax, 7.7, 1.35, 4.2, 0.85, "Outcome C: 5-Minute Timer Expires\n(held_until < NOW())\nSeat Reclaimable by Next Atomic Hold", fc="#fff8e1", ec="#b7791f", tc=INK, fontsize=8)

    draw_arrow(ax, 2.4, 5.33, 2.4, 4.82)
    draw_arrow(ax, 2.4, 3.88, 2.4, 3.35)
    draw_arrow(ax, 2.4, 2.35, 2.4, 1.58, "No", label_offset=(0.3, 0))
    draw_arrow(ax, 3.9, 2.85, 5.78, 5.05, "Yes", label_offset=(-0.25, 0.25))
    draw_arrow(ax, 7.1, 4.78, 6.2, 3.65, "")
    draw_arrow(ax, 8.3, 4.78, 9.2, 3.65, "")
    draw_arrow(ax, 7.7, 4.78, 7.7, 1.8, "Timeout", label_offset=(0.0, -0.95))

    save_fig(fig, "fig11_flow_seathold.png")


# ==============================================================================
# FIGURE 12: Login, Registration, and Role Routing Flow
# ==============================================================================
def gen_fig12_flow_login():
    fig, ax = plt.subplots(figsize=(11.2, 7.2))
    ax.set_xlim(0, 11.6)
    ax.set_ylim(0, 7.6)
    ax.axis("off")

    ax.text(5.8, 7.25, "Authentication, Registration, and Role-Based Access Flow",
            fontsize=13.5, fontweight="bold", color=INK, ha="center")

    # Left branch: Login
    draw_box(ax, 2.8, 6.3, 3.4, 0.58, "User Submits Login\n(Username or Email + Password)", fc=INK, ec=INK, tc=WHITE, bold=True)
    draw_diamond(ax, 2.8, 5.05, 3.2, 0.92, "LoginThrottle OK &\nAccount Active?", fc=CREAM, ec=INK, tc=INK, fontsize=8)
    draw_diamond(ax, 2.8, 3.55, 3.2, 0.92, "BCrypt.checkpw\nHash Matches?", fc=CREAM, ec=INK, tc=INK, fontsize=8)
    draw_box(ax, 2.8, 2.15, 3.4, 0.65, "Create Authenticated Session\nCheck UserRole Enum", fc=SKY_LIGHT, ec=INK, bold=True)
    draw_box(ax, 1.35, 0.9, 2.2, 0.72, "ADMIN Role\nAdmin Dashboard\n(/admin)", fc=GREEN_BG, ec=GREEN, tc=INK, bold=True, fontsize=8)
    draw_box(ax, 4.25, 0.9, 2.4, 0.72, "CUSTOMER Role\nHome / My Bookings\n(Owner-Only Access)", fc=GREEN_BG, ec=GREEN, tc=INK, bold=True, fontsize=8)

    # Center error box
    draw_box(ax, 6.0, 4.3, 2.1, 0.85, "Reject Login\nIncrement Throttle\nGeneric Error", fc=RED_BG, ec=RED, tc=INK, fontsize=7.8)

    # Right branch: Register
    draw_box(ax, 9.2, 6.3, 3.4, 0.58, "User Submits Registration\n(/register Form + Consent)", fc=ERD_DARKBLUE, ec=ERD_DARKBLUE, tc=WHITE, bold=True)
    draw_box(ax, 9.2, 5.05, 3.5, 0.78, "RegistrationThrottle &\nValidationUtil Checks\n(Unique Username & CI Email)", fc=SKY_LIGHT, ec=INK, fontsize=8)
    draw_box(ax, 9.2, 3.65, 3.5, 0.75, "Hash Password (jBCrypt cost 12)\nInsert users (role='CUSTOMER')", fc=SKY_LIGHT, ec=INK, fontsize=8)
    draw_box(ax, 9.2, 2.15, 3.5, 0.85, "Render Welcome Email (Thymeleaf)\nWrite to File Outbox (outbox/)\nor Send via Optional SMTP", fc=CREAM, ec=INK, fontsize=8)
    draw_box(ax, 9.2, 0.9, 3.2, 0.65, "Redirect to Login / Session", fc=GREEN_BG, ec=GREEN, tc=INK, bold=True)

    # Arrows Login
    draw_arrow(ax, 2.8, 5.98, 2.8, 5.53)
    draw_arrow(ax, 2.8, 4.57, 2.8, 4.03, "Yes", label_offset=(0.3, 0))
    draw_arrow(ax, 4.4, 5.05, 4.93, 4.5, "No")
    draw_arrow(ax, 4.4, 3.55, 4.93, 4.1, "No")
    draw_arrow(ax, 2.8, 3.07, 2.8, 2.5, "Yes", label_offset=(0.3, 0))
    draw_arrow(ax, 2.2, 1.8, 1.35, 1.28, "ADMIN", label_offset=(-0.45, 0.08))
    draw_arrow(ax, 3.4, 1.8, 4.25, 1.28, "CUSTOMER", label_offset=(0.55, 0.08))

    # Arrows Register
    draw_arrow(ax, 9.2, 5.98, 9.2, 5.46)
    draw_arrow(ax, 9.2, 4.64, 9.2, 4.05)
    draw_arrow(ax, 9.2, 3.25, 9.2, 2.6)
    draw_arrow(ax, 9.2, 1.7, 9.2, 1.25)

    save_fig(fig, "fig12_flow_login.png")


# ==============================================================================
# FIGURE 13: Sequence Diagram (Booking & Payment) - SOLID LINES ONLY
# ==============================================================================
def gen_fig13_sequence_booking():
    fig, ax = plt.subplots(figsize=(12.2, 8.2))
    ax.set_xlim(0, 12.6)
    ax.set_ylim(0, 8.8)
    ax.axis("off")

    ax.text(6.3, 8.45, "Booking and Payment Sequence Diagram",
            fontsize=14, fontweight="bold", color=INK, ha="center")

    actors = [
        ("Customer\nBrowser", 1.1),
        ("WebController", 3.2),
        ("SeatMapService", 5.3),
        ("PricingService", 7.4),
        ("PaymentGateway", 9.5),
        ("PostgreSQL DB", 11.5),
    ]

    # Lifelines (SOLID lines only!)
    for name, x in actors:
        draw_box(ax, x, 7.75, 1.75, 0.62, name, fc=INK, ec=INK, tc=WHITE, fontsize=8.2, bold=True)
        ax.plot([x, x], [7.42, 0.45], color=BORDER, linewidth=1.3, linestyle="solid", zorder=1)
        draw_box(ax, x, 0.3, 1.55, 0.35, name.replace("\n", " "), fc=CREAM, ec=INK, tc=INK, fontsize=7.2)

    msgs = [
        (1.1, 3.2, 6.95, "1. POST /flights/{id}/seats/hold (seatId, csrf)"),
        (3.2, 5.3, 6.45, "2. holdSeat(seatId, userOwner)"),
        (5.3, 11.5, 5.95, "3. Atomic UPDATE flight_seats SET status='HELD', held_until=NOW()+5m"),
        (11.5, 3.2, 5.45, "4. Hold Confirmed (1 row updated)"),
        (1.1, 3.2, 4.85, "5. POST /pay (passenger, card, csrf, idempotencyKey)"),
        (3.2, 7.4, 4.35, "6. quote(flightId, seatClass) [authoritative fare]"),
        (7.4, 3.2, 3.9, "7. PriceQuote (base + demand + timing, 15% VAT incl.)"),
        (3.2, 9.5, 3.35, "8. charge(card, amount, idempotencyKey)"),
        (9.5, 3.2, 2.85, "9. PaymentResult (APPROVED, TXN-ref, card_last4)"),
        (3.2, 11.5, 2.3, "10. BEGIN TX -> INSERT bookings, booking_seats, payments -> UPDATE BOOKED -> COMMIT"),
        (11.5, 3.2, 1.75, "11. BookingRecord (6-char PNR Reference)"),
        (3.2, 1.1, 1.15, "12. HTTP 302 Redirect -> /confirmation/{PNR} (QR + PDF)"),
    ]

    for x1, x2, y, lbl in msgs:
        color = ERD_DARKBLUE if x1 < x2 else GREEN
        arrow = FancyArrowPatch((x1, y), (x2, y), arrowstyle="-|>", mutation_scale=11,
                                color=color, linewidth=1.35, linestyle="solid", zorder=3)
        ax.add_patch(arrow)
        mx = (x1 + x2) / 2
        ax.text(mx, y + 0.16, lbl, fontsize=7.6, color=INK, ha="center", va="bottom",
                bbox=dict(boxstyle="round,pad=0.1", fc=WHITE, ec="none", alpha=0.92), zorder=5)

    save_fig(fig, "fig13_sequence_booking.png")


# ==============================================================================
# FIGURE 14: Annotated Interface Wireframes (4 Main Screens)
# ==============================================================================
def gen_fig14_wireframes():
    fig, ax = plt.subplots(figsize=(12.2, 8.2))
    ax.set_xlim(0, 12.6)
    ax.set_ylim(0, 8.8)
    ax.axis("off")

    ax.text(6.3, 8.45, "Annotated Interface Wireframes (Sky Royal Glass Layout)",
            fontsize=14, fontweight="bold", color=INK, ha="center")

    def frame(x, y, w, h, title):
        f = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.04,rounding_size=0.1",
                           fc="#f8fafc", ec=INK, lw=1.4, zorder=1)
        ax.add_patch(f)
        hdr = Rectangle((x, y + h - 0.42), w, 0.42, fc=INK, ec=INK, zorder=2)
        ax.add_patch(hdr)
        ax.text(x + 0.18, y + h - 0.21, title, fontsize=8.5, fontweight="bold", color=WHITE, va="center", zorder=3)

    # Panel 1: Home & Search (top-left)
    frame(0.3, 4.55, 5.8, 3.55, "1. Home & Flight Search (Web / Desktop)")
    draw_box(ax, 3.2, 7.35, 5.2, 0.36, "Floating Pill Navbar: Logo | Home | Flights | My Bookings | Profile", fc=CREAM, ec=INK, fontsize=7.2)
    draw_box(ax, 2.0, 6.25, 2.8, 1.35, "Hero Headline\n'Fly higher.'\nFull-bleed Aerial Photo", fc=SKY_LIGHT, ec=SKY, fontsize=7.8)
    draw_box(ax, 4.75, 6.25, 2.1, 1.35, "Popular Routes Card\nJNB->CPT  R 1,180\nJNB->DUR  R 950", fc=WHITE, ec=INK, fontsize=7.4)
    draw_box(ax, 3.2, 5.05, 5.2, 0.68, "Bottom Search Bar: [From] [To] [Departure Date] [Cabin] [Search]", fc=WHITE, ec=INK, fontsize=7.5, bold=True)

    # Panel 2: Interactive Cabin Seat Map (top-right)
    frame(6.5, 4.55, 5.8, 3.55, "2. Interactive Cabin Seat Map (Step 2 of 4)")
    draw_box(ax, 9.4, 7.35, 5.2, 0.34, "Progress Stepper: (1) Search  ->  [2] Select Seat  ->  (3) Checkout  ->  (4) Confirmed", fc=CREAM, ec=INK, fontsize=7.1)
    draw_box(ax, 7.65, 5.85, 1.7, 2.1, "Cabin Photo\nPreview\n(Economy /\nFirst Class)", fc=SKY_LIGHT, ec=SKY, fontsize=7.5)
    draw_box(ax, 9.55, 5.85, 1.8, 2.1, "2D Seat Grid\nRows 1-11\nA B C | D E F\nZoom 100-150%", fc=WHITE, ec=INK, fontsize=7.5)
    draw_box(ax, 11.35, 5.85, 1.45, 2.1, "Cream Card\nSeat & Fare\n05:00 Ring\n[Continue]", fc=CREAM, ec=INK, fontsize=7.2, bold=True)

    # Panel 3: Single-Page Checkout (bottom-left)
    frame(0.3, 0.45, 5.8, 3.65, "3. Passenger & Simulated Payment Checkout (Step 3)")
    draw_box(ax, 2.25, 2.65, 3.3, 1.45, "1. Passenger Form\nFull Name, Email,\nPassport, Date of Birth", fc=WHITE, ec=INK, fontsize=7.5)
    draw_box(ax, 2.25, 1.18, 3.3, 1.15, "2. Simulated Card\nCream Card Preview + Inputs\n(4242... approves / 4000... declines)", fc=CREAM, ec=INK, fontsize=7.2)
    draw_box(ax, 4.95, 2.05, 1.8, 2.65, "Order Summary\nRoute & Seat\nBase + Demand\n+ Timing Fare\n15% VAT Incl.\n[Confirm & Pay]", fc=SKY_LIGHT, ec=INK, fontsize=7.2, bold=True)

    # Panel 4: Confirmation & QR Boarding Pass (bottom-right)
    frame(6.5, 0.45, 5.8, 3.65, "4. Confirmation & QR Boarding Pass (Step 4)")
    draw_box(ax, 8.0, 2.05, 2.5, 2.65, "Destination Hero\n'Cape Town is\nwaiting.'\n[Download PDF]\n[My Bookings]\n[Cancel Booking]", fc=SKY_LIGHT, ec=SKY, fontsize=7.5)
    draw_box(ax, 10.75, 2.05, 2.65, 2.65, "White Boarding Pass\nSR 204 | JNB -> CPT\nPassenger & Seat\n6-Char PNR Code\nCode128 Barcode\nHMAC QR Code", fc=WHITE, ec=INK, fontsize=7.5, bold=True)

    save_fig(fig, "fig14_wireframes.png")


# ==============================================================================
# FIGURE 30: Git Contribution Activity Chart (Real git log --all counts)
# ==============================================================================
def gen_fig30_git_activity():
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12.2, 4.8), gridspec_kw={"width_ratios": [1.35, 1.0]})

    # Left: Direct commits + co-authored commits (honest breakdown)
    members = [
        "David Elamenji\n(zennin7)",
        "Sinikiwe Masondo\n(SinikiweMasondo)",
        "Theledi Maluleka\n(ThelediMaluleka)",
        "Ubuntu Mhlongo\n(UAM-IN-TECH)",
        "Luyanda Njolo\n(Yandaaaaa)",
        "Buhle Gumede",
        "Keahana Campbell",
        "Koketso Makweya",
        "Tshepiso Leepile\n(Tshepiso-Sky)",
        "Gaorekwe Petja\n(petjagaorekwe-dot)",
    ]
    direct = [199, 3, 3, 2, 2, 1, 1, 1, 1, 1]
    coauth = [0, 38, 38, 0, 0, 0, 0, 0, 0, 0]

    y = list(range(len(members)))
    ax1.barh(y, direct, color=INK, label="Direct commits", height=0.62)
    ax1.barh(y, coauth, left=direct, color=SKY, label="Co-authored website commits", height=0.62)
    ax1.set_yticks(y)
    ax1.set_yticklabels(members, fontsize=8)
    ax1.invert_yaxis()
    ax1.set_xlabel("Commit Count (all branches, git log --all)", fontsize=9)
    ax1.set_title("Repository Commits per Group Member", fontsize=10.5, fontweight="bold", color=INK)
    ax1.legend(loc="lower right", fontsize=8)
    ax1.grid(axis="x", color="#e0e4ea", linestyle="solid", linewidth=0.7)
    ax1.set_axisbelow(True)
    for i, (d, c) in enumerate(zip(direct, coauth)):
        lbl = f"{d}" if c == 0 else f"{d} (+{c} co-auth)"
        ax1.text(d + c + 2.5, i, lbl, va="center", fontsize=7.5, color=INK)
    ax1.set_xlim(0, 240)

    # Right: Repository summary metrics
    ax2.axis("off")
    ax2.set_title("GitHub Collaboration Summary", fontsize=10.5, fontweight="bold", color=INK)
    summary_lines = [
        ("Total Commits (all branches)", "217"),
        ("Commits on dev branch", "200"),
        ("Merged Pull Requests", "23 (#1 to #23)"),
        ("Remote Feature Branches", "28"),
        ("Local Branches", "19"),
        ("Project Start Date", "15 Sep 2026"),
        ("Latest dev Merge", "25 Nov 2026"),
        ("Contributors (Group 2)", "10 / 10 active"),
    ]
    y0 = 0.93
    for k, v in summary_lines:
        rect = FancyBboxPatch((0.02, y0 - 0.082), 0.96, 0.092,
                              boxstyle="round,pad=0.01,rounding_size=0.02",
                              fc=CREAM if int(y0 * 10) % 2 == 0 else SKY_LIGHT,
                              ec=BORDER, lw=0.8, transform=ax2.transAxes)
        ax2.add_patch(rect)
        ax2.text(0.06, y0 - 0.036, k, fontsize=8.5, color=INK, va="center", transform=ax2.transAxes)
        ax2.text(0.94, y0 - 0.036, v, fontsize=8.8, fontweight="bold", color=INK, ha="right", va="center", transform=ax2.transAxes)
        y0 -= 0.112

    fig.tight_layout()
    save_fig(fig, "fig30_git_activity.png")


if __name__ == "__main__":
    gen_fig01_architecture()
    gen_fig02_use_cases()
    gen_fig03_class_core()
    gen_fig04_class_ui()
    gen_fig05_erd_overview()
    gen_fig06_erd_detail_1()
    gen_fig07_erd_detail_2()
    gen_fig08_erd_detail_3()
    gen_fig09_flow_booking()
    gen_fig10_flow_payment()
    gen_fig11_flow_seathold()
    gen_fig12_flow_login()
    gen_fig13_sequence_booking()
    gen_fig14_wireframes()
    gen_fig30_git_activity()
    print("ALL 15 DIAGRAMS GENERATED.")
