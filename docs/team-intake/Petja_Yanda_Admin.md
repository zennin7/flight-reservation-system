# Team Intake Specification: Admin Dashboard
**Assigned Members:** Petja & Yanda  
**Module Focus:** Platform Analytics, Real-Time KPIs & Management Dashboard

---

## 1. Mapping to Shadow Repository Files

| Shadow Component | File Path |
|---|---|
| **FXML View** | `desktop/src/main/resources/com/group2/flightres/fxml/admin-dashboard.fxml` |
| **JavaFX Controller** | `desktop/src/main/java/com/group2/flightres/controller/AdminDashboardController.java` |
| **Stats Service** | `core/src/main/java/com/group2/flightres/service/DashboardStatsService.java` |
| **Stats DAO** | `core/src/main/java/com/group2/flightres/dao/impl/DashboardStatsDaoImpl.java` |
| **Stats Model** | `core/src/main/java/com/group2/flightres/model/DashboardStats.java` |

---

## 2. What the Teammate Must Provide

1. **Dashboard FXML (`admin-dashboard.fxml`)**:
   - Four KPI stat cards: `totalFlightsLabel`, `totalBookingsLabel`, `revenueLabel`, `seatsSoldLabel`.
   - Dynamic JavaFX `BarChart<String, Number>` (`bookingTrendsChart`) for 7-day booking trends.
   - Interactive `refreshButton` in the header row for immediate live data reloading.
   - No placeholder drawing annotations or hardcoded mockup text.
2. **Controller Implementation (`AdminDashboardController.java`)**:
   - Calls `DashboardStatsService.getStats()` in `initialize()` and `refresh()`.
   - Wires number animations (`Motion.countUp`) and card hover lifts (`Motion.attachHoverLift`).
   - Populates the BarChart dynamically from `stats.getBookingsPerDay()`.
3. **Security Gate**:
   - Admin check: Only users with `UserRole.ADMIN` may view this screen; non-admin attempts must be blocked with an informative alert.

---

## 3. Comparison Criteria & Evaluation Rubric

- **Live Database Aggregation**: Are numbers calculated from live SQL aggregates (`COUNT`, `SUM`, `ROUND`) rather than dummy constants?
- **Real-Time Refresh**: Does clicking `↻ Refresh` update the stats after a booking is made on desktop or web?
- **Chart Presentation**: Is the bar chart clean and legible in both light and dark themes without clipping or axis misalignment?
- **Motion Integration**: Are numeric counters smoothly animated when motion is enabled?

---

## 4. Shadow Baseline (What We Keep If Incoming Is Weaker)

- If incoming implementation contains mock or static data, we keep the shadow's **`DashboardStatsDaoImpl`** SQL aggregate engine.
- If incoming charts lack theming or have overlapping labels, we retain the shadow's **custom-styled JavaFX BarChart** and CSS tokens.
- If the refresh button is missing, we keep the shadow's **`refreshButton` / `handleRefresh()`** workflow.
