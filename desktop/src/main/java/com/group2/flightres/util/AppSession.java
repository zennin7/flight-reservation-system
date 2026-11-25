package com.group2.flightres.util;

import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Shared desktop session state so the authenticated user and startup DB diagnostics
 * survive every screen transition and can be cleared cleanly on sign out.
 */
public final class AppSession {

    private static volatile User currentUser;
    private static volatile String dbHost = "";
    private static volatile int dbPort = 0;
    private static volatile String dbName = "";
    private static volatile int upcomingFlights = -1;
    private static final StringProperty dbStatusText = new SimpleStringProperty("");

    private AppSession() {}

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    /**
     * Synchronizes a BookingContext with the session's authenticated user:
     * if context has a user, updates session; otherwise restores session's user onto context.
     */
    public static BookingContext ensureUser(BookingContext context) {
        BookingContext ctx = context != null ? context : new BookingContext();
        if (ctx.getCurrentUser() != null) {
            currentUser = ctx.getCurrentUser();
        } else if (currentUser != null) {
            ctx.setCurrentUser(currentUser);
        }
        return ctx;
    }

    /**
     * Clears all user state and returns a fresh empty BookingContext.
     */
    public static BookingContext signOut(BookingContext existingContext) {
        currentUser = null;
        if (existingContext != null) {
            existingContext.setCurrentUser(null);
            existingContext.setBookingReference(null);
            existingContext.setFlightId(null);
            existingContext.setSelectedSeats(null);
            existingContext.setPassengerName(null);
            existingContext.setPassengerEmail(null);
            existingContext.setPassportNumber(null);
            existingContext.setPaymentResult(null);
            existingContext.setIdempotencyKey(null);
            existingContext.setHoldExpiresAt(null);
            existingContext.setHoldOwner(null);
        }
        return new BookingContext();
    }

    public static void setDbStatus(String host, int port, String name, int upcomingCount) {
        dbHost = host != null ? host : "";
        dbPort = port;
        dbName = name != null ? name : "";
        upcomingFlights = upcomingCount;
        String summary = "DB: " + dbName + " (" + dbHost + ":" + dbPort + ") · " + upcomingFlights + " upcoming flights";
        if (javafx.application.Platform.isFxApplicationThread()) {
            dbStatusText.set(summary);
        } else {
            try {
                javafx.application.Platform.runLater(() -> dbStatusText.set(summary));
            } catch (IllegalStateException ignored) {
                dbStatusText.set(summary);
            }
        }
    }

    public static String getDbStatusText() {
        return dbStatusText.get();
    }

    public static StringProperty dbStatusTextProperty() {
        return dbStatusText;
    }

    public static String getDbHost() { return dbHost; }
    public static int getDbPort() { return dbPort; }
    public static String getDbName() { return dbName; }
    public static int getUpcomingFlights() { return upcomingFlights; }
}
