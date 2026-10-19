package com.group2.flightres.web;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.exception.AuthenticationException;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.*;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.SeatMapService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Server-rendered web application for SkyVoyage.
 * Uses Javalin 6 and Thymeleaf with shared core services.
 */
public class WebApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);

    private final FlightService flightService;
    private final SeatMapService seatMapService;
    private final BookingService bookingService;
    private final AuthService authService;
    private final Javalin app;

    public WebApplication() {
        this(new FlightService(), new SeatMapService(), new BookingService(), new AuthService());
    }

    public WebApplication(FlightService flightService, SeatMapService seatMapService,
                          BookingService bookingService, AuthService authService) {
        this.flightService = flightService;
        this.seatMapService = seatMapService;
        this.bookingService = bookingService;
        this.authService = authService;

        // Configure Thymeleaf
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("/templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        // Configure Javalin 6
        this.app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
            config.fileRenderer(new JavalinThymeleaf(templateEngine));
        });

        setupSecurityAndFilters();
        setupRoutes();
        setupErrorHandling();
    }

    public Javalin getApp() {
        return app;
    }

    public void start(int port) {
        app.start(port);
        logger.info("SkyVoyage Web Application started at http://localhost:{}", port);
    }

    public void stop() {
        app.stop();
        logger.info("SkyVoyage Web Application stopped.");
    }

    private void setupSecurityAndFilters() {
        // Security Headers & CSRF Initialization
        app.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("X-XSS-Protection", "1; mode=block");
            ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");

            if (ctx.sessionAttribute("csrf_token") == null) {
                String token = UUID.randomUUID().toString();
                ctx.sessionAttribute("csrf_token", token);
            }

            // CSRF Validation on POST
            if ("POST".equalsIgnoreCase(ctx.method().name())) {
                String sessionToken = ctx.sessionAttribute("csrf_token");
                String formToken = ctx.formParam("csrf_token");
                if (sessionToken == null || !sessionToken.equals(formToken)) {
                    logger.warn("CSRF validation failed for path: {}", ctx.path());
                    ctx.status(403);
                    Map<String, Object> model = baseModel(ctx);
                    model.put("error", "Your session or security token has expired. Please try again.");
                    ctx.render("error.html", model);
                }
            }
        });
    }

    private void setupRoutes() {
        // Home
        app.get("/", this::handleHome);

        // Flights Search & Results
        app.get("/flights", this::handleFlights);

        // Seat Selection & Checkout
        app.get("/flights/{id}/seats", this::handleSeats);

        // Book / Confirm Booking
        app.post("/book", this::handleBook);

        // Booking Confirmation
        app.get("/confirmation/{reference}", this::handleConfirmation);

        // Authentication
        app.get("/login", this::handleLoginGet);
        app.post("/login", this::handleLoginPost);
        app.get("/logout", this::handleLogout);

        // Informational Pages
        app.get("/about", this::handleAbout);
        app.get("/contact", this::handleContactGet);
        app.post("/contact", this::handleContactPost);
    }

    private void setupErrorHandling() {
        app.error(404, ctx -> {
            Map<String, Object> model = baseModel(ctx);
            model.put("error", "The requested page (" + ctx.path() + ") could not be found.");
            ctx.render("error.html", model);
        });

        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Unhandled exception processing request to " + ctx.path(), e);
            ctx.status(500);
            Map<String, Object> model = baseModel(ctx);
            model.put("error", "An unexpected error occurred while processing your request. Please return to Home.");
            ctx.render("error.html", model);
        });
    }

    private Map<String, Object> baseModel(Context ctx) {
        Map<String, Object> model = new HashMap<>();
        model.put("appName", BrandConfig.APP_NAME);
        model.put("logoPrefix", BrandConfig.LOGO_PREFIX);
        model.put("logoSuffix", BrandConfig.LOGO_SUFFIX);
        model.put("supportEmail", BrandConfig.SUPPORT_EMAIL);
        model.put("supportPhone", BrandConfig.SUPPORT_PHONE);
        model.put("csrf_token", ctx.sessionAttribute("csrf_token"));
        model.put("user", ctx.sessionAttribute("user"));
        return model;
    }

    // --- Route Handlers ---

    private void handleHome(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        ctx.render("home.html", model);
    }

    private void handleFlights(Context ctx) {
        Map<String, Object> model = baseModel(ctx);

        String origin = ctx.queryParam("origin");
        String destination = ctx.queryParam("destination");
        String dateStr = ctx.queryParam("date");

        if (origin == null || origin.isBlank()) origin = "Johannesburg (JNB)";
        if (destination == null || destination.isBlank()) destination = "Cape Town (CPT)";
        if (dateStr == null || dateStr.isBlank()) dateStr = "2026-11-02";

        model.put("origin", origin);
        model.put("destination", destination);
        model.put("date", dateStr);

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            date = LocalDate.of(2026, 11, 2);
            model.put("date", "2026-11-02");
        }

        List<FlightSearchResult> flights = Collections.emptyList();
        String searchError = null;

        try {
            flights = flightService.searchFlights(origin, destination, date);
        } catch (Exception e) {
            logger.warn("Flight search error: {}", e.getMessage());
            searchError = e.getMessage();
        }

        model.put("flights", flights);
        model.put("searchError", searchError);
        ctx.render("flights.html", model);
    }

    private void handleSeats(Context ctx) {
        long flightId;
        try {
            flightId = Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.redirect("/flights");
            return;
        }

        Optional<FlightSearchResult> flightOpt = flightService.getFlightById(flightId);
        if (flightOpt.isEmpty()) {
            ctx.redirect("/flights");
            return;
        }

        Map<String, Object> model = baseModel(ctx);
        FlightSearchResult flight = flightOpt.get();
        model.put("flight", flight);

        List<FlightSeat> seats = seatMapService.loadSeatMap(flightId);

        // Group seats by row for clean CSS grid rendering
        Map<Integer, List<FlightSeat>> seatsByRow = new TreeMap<>();
        for (FlightSeat seat : seats) {
            seatsByRow.computeIfAbsent(seat.getSeatRow(), k -> new ArrayList<>()).add(seat);
        }
        for (List<FlightSeat> rowList : seatsByRow.values()) {
            rowList.sort(Comparator.comparing(FlightSeat::getSeatCol));
        }

        model.put("seatsByRow", seatsByRow);
        model.put("firstClassRows", List.of(1, 2));
        model.put("economyRows", List.of(6, 7, 8, 9, 10, 11));

        ctx.render("seats.html", model);
    }

    private void handleBook(Context ctx) {
        long flightId = Long.parseLong(ctx.formParam("flightId"));
        long flightSeatId = Long.parseLong(ctx.formParam("flightSeatId"));
        String passengerName = ctx.formParam("passengerName");
        String passengerEmail = ctx.formParam("passengerEmail");
        String passportNumber = ctx.formParam("passportNumber");

        Optional<FlightSearchResult> flightOpt = flightService.getFlightById(flightId);
        if (flightOpt.isEmpty()) {
            ctx.redirect("/flights");
            return;
        }

        FlightSearchResult flight = flightOpt.get();
        List<FlightSeat> seats = seatMapService.loadSeatMap(flightId);
        FlightSeat targetSeat = seats.stream()
                .filter(s -> s.getFlightSeatId() == flightSeatId)
                .findFirst()
                .orElse(null);

        if (targetSeat == null) {
            renderSeatsWithError(ctx, flightId, "The selected seat does not exist. Please pick an available seat.");
            return;
        }

        // 1. Optimistic seat hold
        try {
            seatMapService.selectSeat(targetSeat);
        } catch (SeatUnavailableException e) {
            renderSeatsWithError(ctx, flightId, e.getMessage());
            return;
        } catch (Exception e) {
            renderSeatsWithError(ctx, flightId, "Seat selection error: " + e.getMessage());
            return;
        }

        // 2. Build BookingContext
        BookingContext bookingContext = new BookingContext();
        bookingContext.setFlightId(flight.getFlightId());
        bookingContext.setFlightNumber(flight.getFlightNumber());
        String origin = ctx.formParam("origin");
        if (origin == null || origin.isBlank()) origin = "Johannesburg (JNB)";
        String destination = ctx.formParam("destination");
        if (destination == null || destination.isBlank()) destination = "Cape Town (CPT)";
        String departureDate = ctx.formParam("departureDate");
        if (departureDate == null || departureDate.isBlank()) departureDate = "2026-11-02";

        bookingContext.setOrigin(origin);
        bookingContext.setDestination(destination);
        bookingContext.setDepartureDate(departureDate);
        bookingContext.setDepartureTime(flight.getDepartureTime());
        bookingContext.setArrivalTime(flight.getArrivalTime());

        bookingContext.setPassengerName(passengerName);
        bookingContext.setPassengerEmail(passengerEmail);
        bookingContext.setPassportNumber(passportNumber);

        bookingContext.setSelectedSeats(List.of(targetSeat));

        BigDecimal basePrice = flight.getBasePrice();
        BigDecimal classMult = targetSeat.getSeatClass() == SeatClass.FIRST ? new BigDecimal("2.5") : BigDecimal.ONE;
        BigDecimal demandPrice = new BigDecimal("180");
        BigDecimal totalPrice = basePrice.multiply(classMult).add(demandPrice);

        bookingContext.setBaseFare(basePrice);
        bookingContext.setClassMultiplier(classMult);
        bookingContext.setDemandPricing(demandPrice);
        bookingContext.setTotalPrice(totalPrice);

        User currentUser = ctx.sessionAttribute("user");
        if (currentUser != null) {
            bookingContext.setCurrentUser(currentUser);
        }

        // 3. Complete transactional booking
        try {
            BookingRecord record = bookingService.createBooking(bookingContext);
            ctx.redirect("/confirmation/" + record.getBookingReference());
        } catch (Exception e) {
            logger.error("Booking creation failed: {}", e.getMessage());
            // Release hold on failure
            seatMapService.deselectSeat(targetSeat);
            renderSeatsWithError(ctx, flightId, "Booking failed: " + e.getMessage());
        }
    }

    private void renderSeatsWithError(Context ctx, long flightId, String error) {
        Map<String, Object> model = baseModel(ctx);
        Optional<FlightSearchResult> flightOpt = flightService.getFlightById(flightId);
        flightOpt.ifPresent(flight -> model.put("flight", flight));

        List<FlightSeat> seats = seatMapService.loadSeatMap(flightId);
        Map<Integer, List<FlightSeat>> seatsByRow = new TreeMap<>();
        for (FlightSeat seat : seats) {
            seatsByRow.computeIfAbsent(seat.getSeatRow(), k -> new ArrayList<>()).add(seat);
        }
        model.put("seatsByRow", seatsByRow);
        model.put("firstClassRows", List.of(1, 2));
        model.put("economyRows", List.of(6, 7, 8, 9, 10, 11));
        model.put("bookingError", error);

        ctx.render("seats.html", model);
    }

    private void handleConfirmation(Context ctx) {
        String reference = ctx.pathParam("reference");
        Optional<BookingRecord> recordOpt = bookingService.getBookingByReference(reference);

        if (recordOpt.isEmpty()) {
            ctx.status(404);
            Map<String, Object> model = baseModel(ctx);
            model.put("error", "No booking found for reference code: " + reference);
            ctx.render("error.html", model);
            return;
        }

        Map<String, Object> model = baseModel(ctx);
        BookingRecord record = recordOpt.get();
        model.put("record", record);
        ctx.render("confirmation.html", model);
    }

    private void handleLoginGet(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        ctx.render("login.html", model);
    }

    private void handleLoginPost(Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        logger.info("Web login attempt for user '{}'", username);

        try {
            User user = authService.authenticate(username, password);
            ctx.sessionAttribute("user", user);
            logger.info("Web login successful for user '{}' (Role: {})", user.getUsername(), user.getRole());
            ctx.redirect("/");
        } catch (AuthenticationException e) {
            Map<String, Object> model = baseModel(ctx);
            model.put("loginError", e.getMessage());
            model.put("username", username);
            ctx.render("login.html", model);
        }
    }

    private void handleLogout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/");
    }

    private void handleAbout(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        ctx.render("about.html", model);
    }

    private void handleContactGet(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        ctx.render("contact.html", model);
    }

    private void handleContactPost(Context ctx) {
        String name = ctx.formParam("name");
        String email = ctx.formParam("email");
        String message = ctx.formParam("message");

        logger.info("Received customer contact inquiry from '{}' <{}>", name, email);

        Map<String, Object> model = baseModel(ctx);
        model.put("contactSuccess", "Thank you, " + name + "! Your inquiry has been received. Our concierge team will respond within 2 hours.");
        ctx.render("contact.html", model);
    }

    public static void main(String[] args) {
        int port = 7070;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException ignored) {}
        }
        WebApplication app = new WebApplication();
        app.start(port);
    }
}
