// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.service;

import com.group2.flightres.dao.AdminBookingsDao;
import com.group2.flightres.dao.AdminBookingsDao.AdminBookingsFilter;
import com.group2.flightres.dao.AdminBookingsDao.FlightFilterOption;
import com.group2.flightres.dao.impl.AdminBookingsDaoImpl;
import com.group2.flightres.model.AdminBookingRow;
import com.group2.flightres.model.AdminBookingsPage;
import com.group2.flightres.model.User;
import com.group2.flightres.model.UserRole;
import com.group2.flightres.util.BoardingPassPdfGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Service managing Admin Bookings search, filtering, 25-row pagination, CSV export,
 * and admin boarding pass PDF generation with RBAC enforcement.
 */
public class AdminBookingsService {

    private static final Logger logger = LoggerFactory.getLogger(AdminBookingsService.class);

    public static final int PAGE_SIZE = 25;

    private final AdminBookingsDao dao;

    public AdminBookingsService() {
        this(new AdminBookingsDaoImpl());
    }

    public AdminBookingsService(AdminBookingsDao dao) {
        this.dao = dao != null ? dao : new AdminBookingsDaoImpl();
    }

    public AdminBookingsPage searchBookings(User currentUser, String searchTerm, String statusFilter, int page) {
        requireAdmin(currentUser);
        return dao.searchBookings(searchTerm, statusFilter, page, PAGE_SIZE);
    }

    public AdminBookingsPage searchBookings(User currentUser, String searchTerm, String statusFilter, int page, Connection conn)
            throws SQLException {
        requireAdmin(currentUser);
        return dao.searchBookings(conn, searchTerm, statusFilter, page, PAGE_SIZE);
    }

    public AdminBookingsPage searchBookings(User currentUser, AdminBookingsFilter filter, int page) {
        requireAdmin(currentUser);
        return dao.searchBookings(filter, page, PAGE_SIZE);
    }

    public AdminBookingsPage searchBookings(User currentUser, AdminBookingsFilter filter, int page, Connection conn)
            throws SQLException {
        requireAdmin(currentUser);
        return dao.searchBookings(conn, filter, page, PAGE_SIZE);
    }

    public List<FlightFilterOption> listFlightOptions(User currentUser) {
        requireAdmin(currentUser);
        return dao.listFlightOptions();
    }

    public String exportFilteredCsv(User currentUser, AdminBookingsFilter filter) {
        requireAdmin(currentUser);
        return formatCsv(dao.findAllMatching(filter));
    }

    public String exportFilteredCsv(User currentUser, AdminBookingsFilter filter, Connection conn) throws SQLException {
        requireAdmin(currentUser);
        return formatCsv(dao.findAllMatching(conn, filter));
    }

    public static String formatCsv(List<AdminBookingRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reference,Passenger,Email,Flight,Route,Departure,Seat,Cabin,Total,Status\n");
        if (rows != null) {
            for (AdminBookingRow r : rows) {
                sb.append(csvCell(r.bookingReference())).append(",")
                        .append(csvCell(r.passengerName())).append(",")
                        .append(csvCell(r.passengerEmail())).append(",")
                        .append(csvCell(r.flightNumber())).append(",")
                        .append(csvCell(r.getRoute())).append(",")
                        .append(csvCell(r.getFormattedDepartureDateTime())).append(",")
                        .append(csvCell(r.seatLabels())).append(",")
                        .append(csvCell(r.seatClass() != null ? r.seatClass().name() : "ECONOMY")).append(",")
                        .append(csvCell(String.format(Locale.US, "%.2f", r.totalAmount()))).append(",")
                        .append(csvCell(r.status())).append("\n");
            }
        }
        return sb.toString();
    }

    public static String csvCell(String raw) {
        if (raw == null) return "";
        String s = raw;
        if (!s.isEmpty()) {
            char first = s.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                s = "'" + s;
            }
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public void generateBoardingPassPdf(User currentUser, AdminBookingRow row, File outputFile) throws Exception {
        requireAdmin(currentUser);
        if (row == null || outputFile == null) {
            throw new IllegalArgumentException("Booking row and output file are required.");
        }
        BoardingPassPdfGenerator.generate(row.toTicketData(), outputFile);
    }

    private void requireAdmin(User currentUser) {
        if (currentUser == null || currentUser.getRole() != UserRole.ADMIN) {
            String actor = currentUser != null ? currentUser.getEmail() + " (" + currentUser.getRole() + ")" : "anonymous";
            logger.warn("RBAC violation: {} attempted to access AdminBookingsService", actor);
            throw new SecurityException("Access denied: ADMIN role required to view Admin Bookings.");
        }
    }
}
