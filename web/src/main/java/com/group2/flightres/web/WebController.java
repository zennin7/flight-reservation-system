package com.group2.flightres.web;

import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.exception.AuthenticationException;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.exception.ValidationException;
import com.group2.flightres.model.*;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.SeatMapService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Controller handling HTTP requests and view rendering for the Sky Royal website.
 */
public class WebController {

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    private final FlightService flightService;
    private final SeatMapService seatMapService;
    private final BookingService bookingService;
    private final AuthService authService;

    public WebController(FlightService flightService, SeatMapService seatMapService,
                         BookingService bookingService, AuthService authService) {
        this.flightService = flightService;
        this.seatMapService = seatMapService;
        this.bookingService = bookingService;
        this.authService = authService;
    }

    public Map<String, Object> baseModel(Context ctx) {
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

    public void handleHome(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate returnDate = tomorrow.plusDays(2);
        model.put("tomorrowDate", tomorrow.toString());
        model.put("returnDate", returnDate.toString());
        model.put("holdDurationText", (com.group2.flightres.config.SeatHoldPolicy.HOLD_SECONDS / 60) + " min");

        List<Airport> airports = Collections.emptyList();
        List<RouteSummary> allRoutes = Collections.emptyList();
        String homeError = null;
        try {
            airports = flightService.listAirports();
            allRoutes = flightService.listRouteSummaries();
        } catch (Exception e) {
            logger.warn("Failed to load live home routes: {}", e.getMessage());
            homeError = "Unable to load live schedules right now. Please try searching directly.";
        }

        List<HomeDestinationCard> popularRoutes = new ArrayList<>();
        List<RouteSummary> moreRoutes = new ArrayList<>();
        List<HomeDestinationCard> internationalRoutes = new ArrayList<>();
        Set<String> seenPopularDest = new HashSet<>();
        Set<String> seenIntlDest = new HashSet<>();

        int gradIdx = 1;
        // Prefer flagship domestic routes with photos first (CPT, DUR, JNB), then any remaining domestic routes
        List<String> preferredPopular = List.of("CPT", "DUR", "JNB");
        for (String pref : preferredPopular) {
            for (RouteSummary route : allRoutes) {
                if (!route.isInternational() && pref.equals(route.getDestCode())
                        && seenPopularDest.add(route.getDestCode()) && popularRoutes.size() < 3) {
                    popularRoutes.add(toDestinationCard(route, gradIdx++));
                    break;
                }
            }
        }
        for (RouteSummary route : allRoutes) {
            if (!route.isInternational() && popularRoutes.size() < 3
                    && seenPopularDest.add(route.getDestCode())) {
                popularRoutes.add(toDestinationCard(route, gradIdx++));
            }
            if (moreRoutes.size() < 12) {
                moreRoutes.add(route);
            }
            if (route.isInternational() && seenIntlDest.add(route.getDestCode())) {
                internationalRoutes.add(toDestinationCard(route, gradIdx++));
            }
        }

        // Order international cards to match the Go Further bento layout
        List<String> intlOrder = List.of("ZRH", "MXP", "FCO", "NCE", "JTR", "DXB", "PSA", "GVA");
        internationalRoutes.sort(Comparator.comparingInt(c -> {
            int idx = intlOrder.indexOf(c.getRoute().getDestCode());
            return idx >= 0 ? idx : intlOrder.size();
        }));

        model.put("airports", airports);
        model.put("popularRoutes", popularRoutes);
        model.put("moreRoutes", moreRoutes);
        model.put("internationalRoutes", internationalRoutes);
        model.put("homeError", homeError);
        ctx.render("home.html", model);
    }

    private HomeDestinationCard toDestinationCard(RouteSummary route, int gradIdx) {
        String photo = switch (route.getDestCode()) {
            case "CPT" -> "/photos/dest-capetown.jpg";
            case "DUR" -> "/photos/dest-durban.jpg";
            case "JNB" -> "/photos/dest-johannesburg.jpg";
            case "DXB" -> "/photos/dest-dubai.jpg";
            case "FCO" -> "/photos/dest-rome.jpg";
            case "ZRH" -> "/photos/dest-swiss-alps.jpg";
            case "MXP" -> "/photos/dest-lake-como.jpg";
            case "JTR" -> "/photos/dest-santorini.jpg";
            case "NCE" -> "/photos/dest-monaco.jpg";
            case "PSA" -> "/photos/dest-cinque-terre.jpg";
            case "GVA" -> "/photos/dest-valais.jpg";
            default -> null;
        };
        String gradientClass = "dest-grad-" + (((gradIdx - 1) % 6) + 1);
        return new HomeDestinationCard(route, photo, gradientClass);
    }

    public static class HomeDestinationCard {
        private final RouteSummary route;
        private final String photoPath;
        private final String gradientClass;

        public HomeDestinationCard(RouteSummary route, String photoPath, String gradientClass) {
            this.route = route;
            this.photoPath = photoPath;
            this.gradientClass = gradientClass;
        }

        public RouteSummary getRoute() { return route; }
        public String getPhotoPath() { return photoPath; }
        public String getGradientClass() { return gradientClass; }
    }

    public void handleFlights(Context ctx) {
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
        } catch (Exception e) {
            date = flightService.nextAvailableDate(origin, destination, LocalDate.now()).orElse(LocalDate.now());
            model.put("date", date.toString());
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

    public void handleSeats(Context ctx) {
        long flightId = parseFlightId(ctx.pathParam("id"));
        if (flightId <= 0) {
            ctx.redirect("/flights");
            return;
        }
        renderSeatsPage(ctx, flightId, null);
    }

    public void handleBook(Context ctx) {
        long flightId = parseFlightId(ctx.formParam("flightId"));
        long flightSeatId = parseFlightId(ctx.formParam("flightSeatId"));

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

        // 2. Build BookingContext & complete transactional booking
        try {
            BookingContext bookingContext = buildBookingContext(ctx, flight, targetSeat);
            BookingRecord record = bookingService.createBooking(bookingContext);
            ctx.redirect("/confirmation/" + record.getBookingReference());
        } catch (ValidationException e) {
            logger.warn("Booking validation failed: {}", e.getMessage());
            seatMapService.deselectSeat(targetSeat);
            renderSeatsWithError(ctx, flightId, e.getMessage(), resolveErrorField(e.getMessage()));
        } catch (Exception e) {
            logger.error("Booking creation failed: {}", e.getMessage(), e);
            seatMapService.deselectSeat(targetSeat);
            renderSeatsWithError(ctx, flightId,
                    "We could not complete your booking right now. Please check your details and try again.", null);
        }
    }

    public void handleConfirmation(Context ctx) {
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

    public void handleConfirmationLookup(Context ctx) {
        String ref = ctx.queryParam("reference");
        if (ref != null && !ref.isBlank()) {
            ctx.redirect("/confirmation/" + ref.trim().toUpperCase(Locale.ROOT));
        } else {
            ctx.redirect("/");
        }
    }

    public void handleLoginGet(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        ctx.render("login.html", model);
    }

    public void handleLoginPost(Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        logger.info("Web login attempt for user '{}'", username);

        try {
            User user = authService.authenticate(username, password);
            String csrfToken = ctx.sessionAttribute("csrf_token");
            ctx.req().changeSessionId();
            if (csrfToken != null) {
                ctx.sessionAttribute("csrf_token", csrfToken);
            }
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

    public void handleLogout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/");
    }

    public void handleAbout(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        ctx.render("about.html", model);
    }

    public void handleContactGet(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        ctx.render("contact.html", model);
    }

    public void handleContactPost(Context ctx) {
        String name = ctx.formParam("name");
        String email = ctx.formParam("email");

        logger.info("Received customer contact inquiry from '{}' <{}>", name, email);

        Map<String, Object> model = baseModel(ctx);
        model.put("contactSuccess", "Thank you, " + name + "! Your inquiry has been received. Our concierge team will respond within 2 hours.");
        ctx.render("contact.html", model);
    }

    private BookingContext buildBookingContext(Context ctx, FlightSearchResult flight, FlightSeat targetSeat) {
        BookingContext bc = new BookingContext();
        bc.setFlightId(flight.getFlightId());
        bc.setFlightNumber(flight.getFlightNumber());

        String origin = ctx.formParam("origin");
        bc.setOrigin(origin != null && !origin.isBlank() ? origin : "Johannesburg (JNB)");

        String destination = ctx.formParam("destination");
        bc.setDestination(destination != null && !destination.isBlank() ? destination : "Cape Town (CPT)");

        String departureDate = ctx.formParam("departureDate");
        bc.setDepartureDate(departureDate != null && !departureDate.isBlank() ? departureDate : "2026-11-02");

        bc.setDepartureTime(flight.getDepartureTime());
        bc.setArrivalTime(flight.getArrivalTime());

        bc.setPassengerName(ctx.formParam("passengerName"));
        bc.setPassengerEmail(ctx.formParam("passengerEmail"));
        String rawPassport = ctx.formParam("passportNumber");
        bc.setPassportNumber(normalizePassport(rawPassport));
        bc.setSelectedSeats(List.of(targetSeat));

        BigDecimal basePrice = flight.getBasePrice();
        PriceQuote quote = bookingService.getPricingService().quote(flight.getFlightId(), targetSeat.getSeatClass());

        bc.setBaseFare(basePrice);
        bc.setPriceQuotes(List.of(quote));
        bc.setTotalPrice(quote.total());

        User currentUser = ctx.sessionAttribute("user");
        if (currentUser != null) {
            bc.setCurrentUser(currentUser);
        }
        return bc;
    }

    private static String normalizePassport(String rawPassport) {
        if (rawPassport == null) {
            return null;
        }
        String trimmed = rawPassport.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    private static String resolveErrorField(String message) {
        if (message == null) {
            return null;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("name")) return "passengerName";
        if (lower.contains("email")) return "passengerEmail";
        if (lower.contains("passport")) return "passportNumber";
        if (lower.contains("seat")) return "flightSeatId";
        return null;
    }

    private void renderSeatsWithError(Context ctx, long flightId, String error) {
        renderSeatsPage(ctx, flightId, error, null);
    }

    private void renderSeatsWithError(Context ctx, long flightId, String error, String errorField) {
        renderSeatsPage(ctx, flightId, error, errorField);
    }

    private void renderSeatsPage(Context ctx, long flightId, String error) {
        renderSeatsPage(ctx, flightId, error, null);
    }

    private void renderSeatsPage(Context ctx, long flightId, String error, String errorField) {
        Optional<FlightSearchResult> flightOpt = flightService.getFlightById(flightId);
        if (flightOpt.isEmpty()) {
            ctx.redirect("/flights");
            return;
        }

        Map<String, Object> model = baseModel(ctx);
        model.put("flight", flightOpt.get());

        String origin = ctx.queryParam("origin");
        if (origin == null || origin.isBlank()) origin = ctx.formParam("origin");
        if (origin == null || origin.isBlank()) origin = "Johannesburg (JNB)";

        String destination = ctx.queryParam("destination");
        if (destination == null || destination.isBlank()) destination = ctx.formParam("destination");
        if (destination == null || destination.isBlank()) destination = "Cape Town (CPT)";

        String departureDate = ctx.queryParam("departureDate");
        if (departureDate == null || departureDate.isBlank()) departureDate = ctx.formParam("departureDate");
        if (departureDate == null || departureDate.isBlank()) departureDate = "2026-11-02";

        model.put("origin", origin);
        model.put("destination", destination);
        model.put("departureDate", departureDate);

        List<FlightSeat> seats = seatMapService.loadSeatMap(flightId);
        Map<Integer, List<FlightSeat>> seatsByRow = groupSeatsByRow(seats);

        model.put("seatsByRow", seatsByRow);
        model.put("firstClassRows", List.of(1, 2));
        model.put("economyRows", List.of(6, 7, 8, 9, 10, 11));
        if (error != null) {
            model.put("bookingError", error);
        }
        if (errorField != null) {
            model.put("errorField", errorField);
        }
        String typedName = ctx.formParam("passengerName");
        if (typedName != null) {
            model.put("passengerName", typedName);
        }
        String typedEmail = ctx.formParam("passengerEmail");
        if (typedEmail != null) {
            model.put("passengerEmail", typedEmail);
        }
        String typedPassport = ctx.formParam("passportNumber");
        if (typedPassport != null) {
            model.put("passportNumber", typedPassport);
        }

        ctx.render("seats.html", model);
    }

    private Map<Integer, List<FlightSeat>> groupSeatsByRow(List<FlightSeat> seats) {
        Map<Integer, List<FlightSeat>> seatsByRow = new TreeMap<>();
        for (FlightSeat seat : seats) {
            seatsByRow.computeIfAbsent(seat.getSeatRow(), k -> new ArrayList<>()).add(seat);
        }
        for (List<FlightSeat> rowList : seatsByRow.values()) {
            rowList.sort(Comparator.comparing(FlightSeat::getSeatCol));
        }
        return seatsByRow;
    }

    private long parseFlightId(String str) {
        if (str == null || str.isBlank()) return -1L;
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
