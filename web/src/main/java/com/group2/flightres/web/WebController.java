package com.group2.flightres.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.config.BrandConfig;
import com.group2.flightres.config.SeatHoldPolicy;
import com.group2.flightres.exception.AuthenticationException;
import com.group2.flightres.exception.DuplicateEmailException;
import com.group2.flightres.exception.PriceChangedException;
import com.group2.flightres.exception.RefundFailedException;
import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.exception.ValidationException;
import com.group2.flightres.model.*;
import com.group2.flightres.service.AdminDashboardService;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BoardingPassVerificationService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.CancellationPolicy;
import com.group2.flightres.service.FlightSalesPolicy;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.LoginThrottle;
import com.group2.flightres.service.PaymentResult;
import com.group2.flightres.service.PaymentService;
import com.group2.flightres.service.PaymentThrottle;
import com.group2.flightres.service.RegistrationService;
import com.group2.flightres.service.RegistrationThrottle;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.service.VerifyThrottle;
import com.group2.flightres.util.BoardingPassPdfGenerator;
import com.group2.flightres.util.DestinationPhotoResolver;
import com.group2.flightres.util.FlightTimes;
import com.group2.flightres.util.MoneyFormatter;
import com.group2.flightres.util.NameInitials;
import com.group2.flightres.util.QrCodeGenerator;
import com.group2.flightres.util.ValidationUtil;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller handling HTTP requests and view rendering for the Sky Royal website.
 */
public class WebController {

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    private final FlightService flightService;
    private final SeatMapService seatMapService;
    private final BookingService bookingService;
    private final AuthService authService;
    private final LoginThrottle loginThrottle;
    private final RegistrationService registrationService;
    private final RegistrationThrottle registrationThrottle;
    private final PaymentService paymentService;
    private final PaymentThrottle paymentThrottle;
    private final BoardingPassVerificationService verificationService;
    private final VerifyThrottle verifyThrottle;
    private final AdminDashboardService adminDashboardService;

    public WebController(FlightService flightService, SeatMapService seatMapService,
                         BookingService bookingService, AuthService authService) {
        this(flightService, seatMapService, bookingService, authService, new LoginThrottle(),
                new RegistrationService(), new RegistrationThrottle(),
                new SimulatedPaymentGateway(), new PaymentThrottle());
    }

    public WebController(FlightService flightService, SeatMapService seatMapService,
                         BookingService bookingService, AuthService authService,
                         LoginThrottle loginThrottle) {
        this(flightService, seatMapService, bookingService, authService, loginThrottle,
                new RegistrationService(), new RegistrationThrottle(),
                new SimulatedPaymentGateway(), new PaymentThrottle());
    }

    public WebController(FlightService flightService, SeatMapService seatMapService,
                         BookingService bookingService, AuthService authService,
                         LoginThrottle loginThrottle, RegistrationService registrationService,
                         RegistrationThrottle registrationThrottle) {
        this(flightService, seatMapService, bookingService, authService, loginThrottle,
                registrationService, registrationThrottle,
                new SimulatedPaymentGateway(), new PaymentThrottle());
    }

    public WebController(FlightService flightService, SeatMapService seatMapService,
                         BookingService bookingService, AuthService authService,
                         LoginThrottle loginThrottle, RegistrationService registrationService,
                         RegistrationThrottle registrationThrottle,
                         PaymentService paymentService, PaymentThrottle paymentThrottle) {
        this(flightService, seatMapService, bookingService, authService, loginThrottle,
                registrationService, registrationThrottle, paymentService, paymentThrottle,
                new BoardingPassVerificationService(bookingService), new VerifyThrottle());
    }

    public WebController(FlightService flightService, SeatMapService seatMapService,
                         BookingService bookingService, AuthService authService,
                         LoginThrottle loginThrottle, RegistrationService registrationService,
                         RegistrationThrottle registrationThrottle,
                         PaymentService paymentService, PaymentThrottle paymentThrottle,
                         BoardingPassVerificationService verificationService, VerifyThrottle verifyThrottle) {
        this(flightService, seatMapService, bookingService, authService, loginThrottle,
                registrationService, registrationThrottle, paymentService, paymentThrottle,
                verificationService, verifyThrottle, new AdminDashboardService());
    }

    public WebController(FlightService flightService, SeatMapService seatMapService,
                         BookingService bookingService, AuthService authService,
                         LoginThrottle loginThrottle, RegistrationService registrationService,
                         RegistrationThrottle registrationThrottle,
                         PaymentService paymentService, PaymentThrottle paymentThrottle,
                         BoardingPassVerificationService verificationService, VerifyThrottle verifyThrottle,
                         AdminDashboardService adminDashboardService) {
        this.flightService = flightService;
        this.seatMapService = seatMapService;
        this.bookingService = bookingService;
        this.authService = authService;
        this.loginThrottle = loginThrottle != null ? loginThrottle : new LoginThrottle();
        this.registrationService = registrationService != null ? registrationService : new RegistrationService();
        this.registrationThrottle = registrationThrottle != null ? registrationThrottle : new RegistrationThrottle();
        this.paymentService = paymentService != null ? paymentService : new SimulatedPaymentGateway();
        this.paymentThrottle = paymentThrottle != null ? paymentThrottle : new PaymentThrottle();
        this.verificationService = verificationService != null
                ? verificationService
                : new BoardingPassVerificationService(bookingService);
        this.verifyThrottle = verifyThrottle != null ? verifyThrottle : new VerifyThrottle();
        this.adminDashboardService = adminDashboardService != null ? adminDashboardService : new AdminDashboardService();
    }

    public Map<String, Object> baseModel(Context ctx) {
        Map<String, Object> model = new HashMap<>();
        User user = ctx.sessionAttribute("user");
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
        String userDisplayName = user != null ? firstNonBlank(user.getFullName(), user.getUsername()) : null;
        String userInitials = user != null ? NameInitials.of(userDisplayName) : null;

        model.put("appName", BrandConfig.APP_NAME);
        model.put("logoPrefix", BrandConfig.LOGO_PREFIX);
        model.put("logoSuffix", BrandConfig.LOGO_SUFFIX);
        model.put("supportEmail", BrandConfig.SUPPORT_EMAIL);
        model.put("supportPhone", BrandConfig.SUPPORT_PHONE);
        model.put("csrf_token", ctx.sessionAttribute("csrf_token"));
        model.put("user", user);
        model.put("isAdmin", isAdmin);
        model.put("userDisplayName", userDisplayName);
        model.put("userInitials", userInitials);
        model.put("salesCloseMinutes", BoardingPolicy.SALES_CLOSE_MINUTES_BEFORE);
        model.put("holdDurationText", (SeatHoldPolicy.HOLD_SECONDS / 60) + " min");
        model.put("holdDurationSeconds", SeatHoldPolicy.HOLD_SECONDS);
        model.put("activeNav", "");
        applyFixedAerialBackdrop(model);
        return model;
    }

    private static void applyFixedAerialBackdrop(Map<String, Object> model) {
        model.put("backdropMode", "fixed");
        model.put("backdropUrl", WebBackdropResolver.FIXED_AERIAL);
        model.put("backdropRotation", "");
    }

    private static void applyRotatingBackdrop(Map<String, Object> model) {
        model.put("backdropMode", "rotate");
        model.put("backdropUrl", WebBackdropResolver.ROTATING_PHOTOS.get(0));
        model.put("backdropRotation", WebBackdropResolver.ROTATING_PHOTOS_CSV);
    }

    private static void applyDestinationBackdrop(Map<String, Object> model, String airportCodeOrLabel, boolean fullBleed) {
        model.put("backdropMode", fullBleed ? "destination-full" : "destination");
        model.put("backdropUrl", WebBackdropResolver.destinationBackdrop(airportCodeOrLabel));
        model.put("backdropRotation", "");
    }

    private static void applyCabinBackdrop(Map<String, Object> model, SeatClass initialCabin) {
        model.put("backdropMode", "cabin");
        model.put("backdropUrl", WebBackdropResolver.cabinBackdrop(initialCabin));
        model.put("backdropRotation", "");
        model.put("cabinEconomyPhoto", WebBackdropResolver.CABIN_ECONOMY);
        model.put("cabinFirstPhoto", WebBackdropResolver.CABIN_FIRST);
    }

    public void handleHome(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        applyRotatingBackdrop(model);
        model.put("activeNav", "home");
        String flashSuccess = ctx.sessionAttribute("flash_success");
        if (flashSuccess != null) {
            ctx.req().getSession().removeAttribute("flash_success");
            model.put("flashSuccess", flashSuccess);
        }
        LocalDate tomorrow = LocalDate.now(bookingService.getClock()).plusDays(1);
        model.put("tomorrowDate", tomorrow.toString());
        model.put("tomorrowDateDisplay", formatFriendlyDate(tomorrow));
        model.put("tomorrowWeekday", tomorrow.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH));
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

        String heroEyebrow = buildHeroEyebrow(airports);

        List<RouteSummary> popularWeekRoutes = new ArrayList<>();
        Set<String> seenPopularRoutes = new HashSet<>();
        List<String[]> preferredPairs = List.of(
                new String[]{"JNB", "CPT"},
                new String[]{"JNB", "DUR"},
                new String[]{"CPT", "JNB"}
        );
        for (String[] pair : preferredPairs) {
            for (RouteSummary r : allRoutes) {
                if (pair[0].equals(r.getOriginCode()) && pair[1].equals(r.getDestCode())
                        && seenPopularRoutes.add(r.getOriginCode() + "->" + r.getDestCode())) {
                    popularWeekRoutes.add(r);
                    break;
                }
            }
        }
        for (RouteSummary r : allRoutes) {
            if (!r.isInternational() && popularWeekRoutes.size() < 3
                    && seenPopularRoutes.add(r.getOriginCode() + "->" + r.getDestCode())) {
                popularWeekRoutes.add(r);
            }
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

        model.put("heroEyebrow", heroEyebrow);
        model.put("airports", airports);
        model.put("popularWeekRoutes", popularWeekRoutes);
        model.put("popularRoutes", popularRoutes);
        model.put("moreRoutes", moreRoutes);
        model.put("internationalRoutes", internationalRoutes);
        model.put("homeError", homeError);
        ctx.render("home.html", model);
    }

    private static String buildHeroEyebrow(List<Airport> airports) {
        if (airports == null || airports.isEmpty()) {
            return "JOHANNESBURG \u00B7 CAPE TOWN \u00B7 DURBAN";
        }
        Map<String, Airport> byCode = new LinkedHashMap<>();
        for (Airport a : airports) {
            if (a.code() != null) {
                byCode.putIfAbsent(a.code().toUpperCase(Locale.ROOT), a);
            }
        }
        List<String> cities = new ArrayList<>();
        for (String hub : List.of("JNB", "CPT", "DUR")) {
            Airport a = byCode.get(hub);
            if (a != null && a.city() != null && !a.city().isBlank()) {
                cities.add(a.city().trim().toUpperCase(Locale.ROOT));
            }
        }
        for (Airport a : airports) {
            if (cities.size() >= 3) break;
            if (a.city() != null && !a.city().isBlank()) {
                String c = a.city().trim().toUpperCase(Locale.ROOT);
                if (!cities.contains(c)) {
                    cities.add(c);
                }
            }
        }
        return cities.isEmpty() ? "JOHANNESBURG \u00B7 CAPE TOWN \u00B7 DURBAN" : String.join(" \u00B7 ", cities);
    }

    private HomeDestinationCard toDestinationCard(RouteSummary route, int gradIdx) {
        String photo = DestinationPhotoResolver.resolveWebPath(route.getDestCode());
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
        model.put("activeNav", "flights");

        String origin = ctx.queryParam("origin");
        String destination = ctx.queryParam("destination");
        String dateStr = ctx.queryParam("date");
        String selectedCabin = SeatClass.FIRST.name().equalsIgnoreCase(ctx.queryParam("cabin"))
                ? SeatClass.FIRST.name() : SeatClass.ECONOMY.name();

        if (origin == null || origin.isBlank()) origin = "Johannesburg (JNB)";
        if (destination == null || destination.isBlank()) destination = "Cape Town (CPT)";
        if (dateStr == null || dateStr.isBlank()) {
            LocalDate today = LocalDate.now(bookingService.getClock());
            dateStr = flightService.nextAvailableDate(origin, destination, today).orElse(today).toString();
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            date = flightService.nextAvailableDate(origin, destination, LocalDate.now(bookingService.getClock()))
                    .orElse(LocalDate.now(bookingService.getClock()));
        }

        List<Airport> airports = Collections.emptyList();
        try {
            airports = flightService.listAirports();
        } catch (Exception e) {
            logger.warn("Failed to list airports on /flights: {}", e.getMessage());
        }

        List<FlightSearchResult> rawFlights = Collections.emptyList();
        String searchError = null;
        try {
            rawFlights = flightService.searchFlights(origin, destination, date);
        } catch (Exception e) {
            logger.warn("Flight search error: {}", e.getMessage());
            searchError = e.getMessage();
        }

        Map<Long, PriceQuote> econQuotes = Map.of();
        Map<Long, PriceQuote> firstQuotes = Map.of();
        if (!rawFlights.isEmpty()) {
            List<Long> fids = rawFlights.stream().map(FlightSearchResult::getFlightId).filter(Objects::nonNull).toList();
            try {
                econQuotes = flightService.quoteCabinBatch(fids, SeatClass.ECONOMY);
                firstQuotes = flightService.quoteCabinBatch(fids, SeatClass.FIRST);
            } catch (Exception e) {
                logger.debug("Fallback to flight search base quotes: {}", e.getMessage());
            }
        }

        List<FlightSearchFilter.FlightCardView> allCards = FlightSearchFilter.buildCardViews(
                rawFlights, econQuotes, firstQuotes, selectedCabin, bookingService.getCancellationPolicy());

        Set<String> depSlots = parseMultiSet(ctx.queryParams("dep"), Set.of(
                FlightSearchFilter.SLOT_BEFORE_6,
                FlightSearchFilter.SLOT_6_TO_12,
                FlightSearchFilter.SLOT_12_TO_18,
                FlightSearchFilter.SLOT_AFTER_18
        ));
        Set<String> cabinFilter = parseMultiSet(ctx.queryParams("cabinFilter"), Set.of(
                SeatClass.ECONOMY.name(), SeatClass.FIRST.name()
        ));
        BigDecimal reqMaxPrice = parseDecimalParam(ctx.queryParam("maxPrice"));
        int reqPage = parseIntParam(ctx.queryParam("page"), 1);

        FlightSearchFilter.FilterResult filterResult = FlightSearchFilter.filterAndPaginate(
                allCards, depSlots, reqMaxPrice, cabinFilter, reqPage, FlightSearchFilter.DEFAULT_PAGE_SIZE);

        String originCode = extractAirportCode(origin, airports);
        String destCode = extractAirportCode(destination, airports);
        String originCity = resolveAirportCityName(origin, originCode, airports);
        String destCity = resolveAirportCityName(destination, destCode, airports);

        String nextAvailDateStr = null;
        if (rawFlights.isEmpty() && searchError == null) {
            nextAvailDateStr = flightService.nextAvailableDate(origin, destination, date.plusDays(1))
                    .map(LocalDate::toString).orElse(null);
        }

        model.put("airports", airports);
        model.put("origin", origin);
        model.put("destination", destination);
        model.put("originCode", originCode);
        model.put("destCode", destCode);
        model.put("originCity", originCity);
        model.put("destCity", destCity);
        model.put("date", date.toString());
        model.put("dateDisplay", formatFriendlyDate(date));
        model.put("dateWeekday", date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH));
        model.put("selectedCabin", selectedCabin);
        model.put("flights", rawFlights);
        model.put("cards", filterResult.getPageCards());
        model.put("filterResult", filterResult);
        boolean hasServerFilters = !depSlots.isEmpty() || !cabinFilter.isEmpty() || reqMaxPrice != null;
        model.put("depSlots", depSlots);
        model.put("cabinFilter", cabinFilter);
        model.put("hasServerFilters", hasServerFilters);
        model.put("nextAvailableDate", nextAvailDateStr);
        model.put("tomorrowDate", LocalDate.now(bookingService.getClock()).plusDays(1).toString());
        model.put("searchError", searchError);
        applyDestinationBackdrop(model, destCode, false);
        ctx.render("flights.html", model);
    }

    private static Set<String> parseMultiSet(List<String> rawVals, Set<String> allowed) {
        if (rawVals == null || rawVals.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> res = new LinkedHashSet<>();
        for (String rv : rawVals) {
            if (rv == null || rv.isBlank()) continue;
            for (String token : rv.split(",")) {
                String clean = token.trim();
                for (String a : allowed) {
                    if (a.equalsIgnoreCase(clean)) {
                        res.add(a);
                        break;
                    }
                }
            }
        }
        return res;
    }

    private static BigDecimal parseDecimalParam(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntParam(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String extractAirportCode(String param, List<Airport> airports) {
        if (param == null) return "";
        String t = param.trim();
        int open = t.lastIndexOf('(');
        int close = t.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return t.substring(open + 1, close).trim().toUpperCase(Locale.ROOT);
        }
        for (Airport a : airports) {
            if (a.code().equalsIgnoreCase(t) || a.city().equalsIgnoreCase(t)) {
                return a.code().toUpperCase(Locale.ROOT);
            }
        }
        return t.toUpperCase(Locale.ROOT);
    }

    private static String resolveAirportCityName(String rawParam, String code, List<Airport> airports) {
        for (Airport a : airports) {
            if (a.code().equalsIgnoreCase(code)) {
                return a.city();
            }
        }
        if (rawParam != null) {
            int open = rawParam.indexOf('(');
            String trimmed = (open > 0 ? rawParam.substring(0, open) : rawParam).trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return code;
    }

    public void handleSeats(Context ctx) {
        long flightId = parseFlightId(ctx.pathParam("id"));
        if (flightId <= 0) {
            ctx.redirect("/flights");
            return;
        }
        String flashError = ctx.sessionAttribute("seat_flash_error");
        if (flashError != null) {
            ctx.req().getSession().removeAttribute("seat_flash_error");
        }
        renderSeatsPage(ctx, flightId, flashError);
    }

    public void handleSeatHoldPost(Context ctx) {
        applyNoStoreHeaders(ctx);
        boolean wantsJson = expectsJson(ctx);
        long flightId = parseFlightId(ctx.pathParam("id"));
        long flightSeatId = parseFlightId(ctx.formParam("flightSeatId"));
        if (flightId <= 0) {
            respondSeatError(ctx, wantsJson, 404, "Flight not found.", "/flights");
            return;
        }

        Optional<FlightSearchResult> flightOpt = flightService.getFlightById(flightId);
        if (flightOpt.isEmpty()) {
            respondSeatError(ctx, wantsJson, 404, "Flight not found.", "/flights");
            return;
        }
        FlightSearchResult flight = flightOpt.get();
        if (flight.getDeparture() != null
                && !FlightSalesPolicy.isOpen(flight.getDeparture(), bookingService.getClock())) {
            respondSeatError(ctx, wantsJson, 409,
                    "Sales for this flight have closed (" + FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE.toMinutes()
                            + " minutes before departure).",
                    "/flights/" + flightId + "/seats");
            return;
        }

        List<FlightSeat> currentSeats = seatMapService.loadSeatMap(flightId);
        FlightSeat targetSeat = currentSeats.stream()
                .filter(s -> s.getFlightSeatId() == flightSeatId)
                .findFirst()
                .orElse(null);
        if (targetSeat == null) {
            respondSeatError(ctx, wantsJson, 404, "Selected seat does not exist.", "/flights/" + flightId + "/seats");
            return;
        }

        String holdOwner = resolveHoldOwner(ctx);
        List<FlightSeat> previousOwned = currentSeats.stream()
                .filter(s -> s.getFlightSeatId() != flightSeatId
                        && s.getStatus() == SeatStatus.HELD
                        && Objects.equals(s.getHeldBy(), holdOwner))
                .toList();

        try {
            seatMapService.selectSeat(targetSeat, holdOwner);
            for (FlightSeat prev : previousOwned) {
                seatMapService.deselectSeat(prev, holdOwner);
            }
            CheckoutSession existingCs = ctx.sessionAttribute("checkout_session");
            if (existingCs != null && existingCs.getFlightSeatId() != targetSeat.getFlightSeatId()) {
                ctx.req().getSession().removeAttribute("checkout_session");
            }
        } catch (SeatUnavailableException | IllegalStateException e) {
            respondSeatError(ctx, wantsJson, 409, e.getMessage(), "/flights/" + flightId + "/seats");
            return;
        }

        Instant now = seatMapService.getClock().instant();
        Instant heldUntil = targetSeat.getHeldUntil() != null
                ? targetSeat.getHeldUntil()
                : now.plusSeconds(SeatHoldPolicy.HOLD_SECONDS);
        long remSecs = Math.max(0L, Duration.between(now, heldUntil).toSeconds());

        PriceQuote quote = computeSeatQuoteSafe(flight, targetSeat.getSeatClass());
        List<FlightSeat> refreshed = seatMapService.loadSeatMap(flightId);
        SeatMapLayoutBuilder.SeatMapView view = SeatMapLayoutBuilder.build(refreshed, holdOwner, now);
        String posTag = SeatMapLayoutBuilder.derivePositionTag(targetSeat, refreshed);

        if (!wantsJson) {
            ctx.redirect("/flights/" + flightId + "/seats");
            return;
        }

        BigDecimal classUpgrade = quote.classFare().subtract(quote.baseFare());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seatId", targetSeat.getFlightSeatId());
        payload.put("seatLabel", targetSeat.getSeatLabel());
        payload.put("seatRow", targetSeat.getSeatRow());
        payload.put("seatCol", String.valueOf(targetSeat.getSeatCol()));
        payload.put("seatClass", targetSeat.getSeatClass().name());
        payload.put("cabinLabel", targetSeat.getSeatClass() == SeatClass.FIRST ? "First Class" : "Economy");
        payload.put("positionTag", posTag);
        payload.put("holdExpiresEpochMs", heldUntil.toEpochMilli());
        payload.put("remainingSeconds", remSecs);
        payload.put("totalHoldSeconds", (long) SeatHoldPolicy.HOLD_SECONDS);
        payload.put("baseFareFormatted", MoneyFormatter.format(quote.baseFare()));
        payload.put("hasClassPremium", classUpgrade.compareTo(BigDecimal.ZERO) > 0);
        payload.put("classUpgradeFormatted", MoneyFormatter.formatSigned(classUpgrade));
        payload.put("demandAdjFormatted", MoneyFormatter.formatSigned(quote.demandAdjustment()));
        payload.put("timingAdjFormatted", MoneyFormatter.formatSigned(quote.timingAdjustment()));
        payload.put("vatFormatted", MoneyFormatter.format(quote.vatIncluded()));
        payload.put("totalFormatted", MoneyFormatter.format(quote.total()));
        payload.put("freeSeats", view.getFreeSeats());
        payload.put("totalSeats", view.getTotalSeats());
        payload.put("cabinPhotoUrl", WebBackdropResolver.cabinBackdrop(targetSeat.getSeatClass()));
        ctx.status(200).contentType("application/json; charset=utf-8").result(toJsonObject(payload));
    }

    public void handleSeatReleasePost(Context ctx) {
        applyNoStoreHeaders(ctx);
        boolean wantsJson = expectsJson(ctx);
        long flightId = parseFlightId(ctx.pathParam("id"));
        long flightSeatId = parseFlightId(ctx.formParam("flightSeatId"));
        if (flightId <= 0 || flightSeatId <= 0) {
            respondSeatError(ctx, wantsJson, 404, "Seat hold not found.", "/flights");
            return;
        }

        Optional<FlightSeat> seatOpt = seatMapService.findSeatById(flightSeatId);
        if (seatOpt.isEmpty() || seatOpt.get().getFlightId() != flightId) {
            respondSeatError(ctx, wantsJson, 404, "Seat not found on this flight.", "/flights/" + flightId + "/seats");
            return;
        }

        FlightSeat seat = seatOpt.get();
        String holdOwner = resolveHoldOwner(ctx);
        if (seat.getStatus() == SeatStatus.BOOKED) {
            respondSeatError(ctx, wantsJson, 409, "This seat has already been booked.", "/flights/" + flightId + "/seats");
            return;
        }
        if (seat.getStatus() == SeatStatus.HELD && seat.getHeldBy() != null
                && !Objects.equals(seat.getHeldBy(), holdOwner)) {
            respondSeatError(ctx, wantsJson, 403,
                    "You do not have permission to release another traveller's seat hold.",
                    "/flights/" + flightId + "/seats");
            return;
        }

        if (seat.getStatus() == SeatStatus.HELD) {
            seatMapService.deselectSeat(seat, holdOwner);
        }
        CheckoutSession cs = ctx.sessionAttribute("checkout_session");
        if (cs != null && cs.getFlightSeatId() == flightSeatId) {
            ctx.req().getSession().removeAttribute("checkout_session");
        }

        if (!wantsJson) {
            ctx.redirect("/flights/" + flightId + "/seats");
            return;
        }

        List<FlightSeat> refreshed = seatMapService.loadSeatMap(flightId);
        SeatMapLayoutBuilder.SeatMapView view = SeatMapLayoutBuilder.build(
                refreshed, holdOwner, seatMapService.getClock().instant());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("released", true);
        payload.put("seatId", flightSeatId);
        payload.put("freeSeats", view.getFreeSeats());
        payload.put("totalSeats", view.getTotalSeats());
        ctx.status(200).contentType("application/json; charset=utf-8").result(toJsonObject(payload));
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
        if (flight.getDeparture() != null
                && !FlightSalesPolicy.isOpen(flight.getDeparture(), bookingService.getClock())) {
            ctx.status(409);
            renderSeatsWithError(ctx, flightId,
                    "Sales for this flight have closed (bookings close "
                            + FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE.toMinutes()
                            + " minutes before departure).");
            return;
        }
        List<FlightSeat> seats = seatMapService.loadSeatMap(flightId);
        FlightSeat targetSeat = seats.stream()
                .filter(s -> s.getFlightSeatId() == flightSeatId)
                .findFirst()
                .orElse(null);

        if (targetSeat == null) {
            renderSeatsWithError(ctx, flightId, "The selected seat does not exist. Please pick an available seat.");
            return;
        }

        // 1. Validate passenger input BEFORE placing any seat hold
        String passengerName = ctx.formParam("passengerName");
        String passengerEmail = ctx.formParam("passengerEmail");
        String passportNumber = normalizePassport(ctx.formParam("passportNumber"));
        LocalDate dob;
        try {
            ValidationUtil.validatePassengerName(passengerName);
            ValidationUtil.validateEmail(passengerEmail);
            ValidationUtil.validatePassportNumber(passportNumber);
            dob = parseAndValidateDob(ctx.formParam("dateOfBirth"));
        } catch (ValidationException e) {
            logger.warn("Booking passenger validation failed: {}", e.getMessage());
            renderSeatsWithError(ctx, flightId, e.getMessage(), resolveErrorField(e.getMessage()));
            return;
        }

        String origin = firstNonBlank(ctx.formParam("origin"),
                flight.getOriginCity() != null ? flight.getOriginCity() + " (" + flight.getOriginCode() + ")" : "Johannesburg (JNB)");
        String destination = firstNonBlank(ctx.formParam("destination"),
                flight.getDestCity() != null ? flight.getDestCity() + " (" + flight.getDestCode() + ")" : "Cape Town (CPT)");
        String departureDate = firstNonBlank(resolveDepartureDate(flight),
                LocalDate.now(bookingService.getClock()).toString());

        // 2. Acquire owner-bound seat hold
        String holdOwner = resolveHoldOwner(ctx);
        try {
            seatMapService.selectSeat(targetSeat, holdOwner);
        } catch (SeatUnavailableException e) {
            renderSeatsWithError(ctx, flightId, e.getMessage());
            return;
        } catch (Exception e) {
            renderSeatsWithError(ctx, flightId, "Seat selection error: " + e.getMessage());
            return;
        }

        // 3. Recompute authoritative fare server-side (ignore any client price input)
        try {
            PriceQuote quote = bookingService.getPricingService()
                    .quote(flightId, targetSeat.getSeatClass(), bookingService.getClock());
            Instant holdExpiresAt = targetSeat.getHeldUntil() != null
                    ? targetSeat.getHeldUntil()
                    : seatMapService.getClock().instant().plusSeconds(SeatHoldPolicy.HOLD_SECONDS);

            CheckoutSession checkoutSession = new CheckoutSession(
                    flight, origin, destination, departureDate, targetSeat,
                    passengerName.trim(), passengerEmail.trim(), passportNumber, dob,
                    quote, UUID.randomUUID().toString(), holdOwner, holdExpiresAt);

            ctx.sessionAttribute("checkout_session", checkoutSession);
            ctx.redirect("/checkout");
        } catch (Exception e) {
            logger.error("Could not prepare checkout session for seat {}: {}", targetSeat.getFlightSeatId(), e.getMessage(), e);
            seatMapService.deselectSeat(targetSeat, holdOwner);
            renderSeatsWithError(ctx, flightId,
                    "We could not prepare checkout right now. Please try selecting your seat again.", null);
        }
    }

    public void handleCheckoutGet(Context ctx) {
        applyNoStoreHeaders(ctx);
        CheckoutSession cs = ctx.sessionAttribute("checkout_session");
        if (cs == null) {
            ctx.redirect("/flights");
            return;
        }
        if (cs.getCompletedBookingRef() != null) {
            ctx.redirect("/confirmation/" + cs.getCompletedBookingRef());
            return;
        }

        String requesterOwner = resolveHoldOwner(ctx);
        if (!Objects.equals(cs.getHoldOwner(), requesterOwner)) {
            ctx.status(403);
            renderCheckoutModel(ctx, cs, "forbidden", "This seat hold belongs to another user or session",
                    "This seat hold belongs to another user or browser session. Please select your own seat.", null);
            return;
        }

        if (cs.getDepartureInstant() != null
                && !FlightSalesPolicy.isOpen(cs.getDepartureInstant(), bookingService.getClock())) {
            seatMapService.deselectSeat(cs.getSeat(), cs.getHoldOwner());
            ctx.status(409);
            renderCheckoutModel(ctx, cs, "expired", "Flight sales have closed",
                    "Sales close " + FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE.toMinutes()
                            + " minutes before departure. This flight can no longer be booked.", null);
            return;
        }

        Optional<FlightSeat> liveSeatOpt = seatMapService.findSeatById(cs.getFlightSeatId());
        Instant now = seatMapService.getClock().instant();
        if (liveSeatOpt.isPresent()) {
            FlightSeat liveSeat = liveSeatOpt.get();
            if (liveSeat.getStatus() == SeatStatus.BOOKED) {
                ctx.status(409);
                renderCheckoutModel(ctx, cs, "taken", "Seat is no longer available",
                        "This seat has already been booked by another traveller. Please choose a different seat.", null);
                return;
            }
            if (liveSeat.getStatus() == SeatStatus.HELD && liveSeat.getHeldBy() != null
                    && !Objects.equals(liveSeat.getHeldBy(), requesterOwner)) {
                ctx.status(403);
                renderCheckoutModel(ctx, cs, "forbidden", "Seat hold owned by another traveller",
                        "Another user or session holds this seat right now. Please pick another seat.", null);
                return;
            }
            if (liveSeat.getStatus() != SeatStatus.HELD
                    || (liveSeat.getHeldUntil() != null && !liveSeat.getHeldUntil().isAfter(now))
                    || !cs.getHoldExpiresAt().isAfter(now)) {
                ctx.status(410);
                renderCheckoutModel(ctx, cs, "expired", "Your 5-minute seat hold has expired",
                        "Your seat hold timed out before payment was completed. Please return to the seat map and select a seat again.", null);
                return;
            }
        } else if (!cs.getHoldExpiresAt().isAfter(now)) {
            ctx.status(410);
            renderCheckoutModel(ctx, cs, "expired", "Your 5-minute seat hold has expired",
                    "Your seat hold timed out before payment was completed. Please return to the seat map and select a seat again.", null);
            return;
        }

        try {
            PriceQuote freshQuote = bookingService.getPricingService()
                    .quote(cs.getFlightId(), cs.getSeat().getSeatClass(), bookingService.getClock());
            cs.setQuote(freshQuote);
        } catch (Exception ignored) {
        }

        renderCheckoutModel(ctx, cs, "active", null, null, null);
    }

    public void handlePayPost(Context ctx) {
        applyNoStoreHeaders(ctx);
        String clientIp = ctx.ip();

        Optional<Duration> lockout = paymentThrottle.remainingLockout(clientIp);
        if (lockout.isPresent()) {
            String waitText = LoginThrottle.formatWait(lockout.get());
            logger.info("Payment throttled for IP wait={}", waitText);
            CheckoutSession cs = ctx.sessionAttribute("checkout_session");
            ctx.status(429);
            if (cs != null) {
                renderCheckoutModel(ctx, cs, "active", null,
                        "Too many payment attempts. Please wait " + waitText + " before trying again.", null);
            } else {
                Map<String, Object> model = baseModel(ctx);
                model.put("error", "Too many payment attempts. Please wait " + waitText + " before trying again.");
                ctx.render("error.html", model);
            }
            return;
        }
        paymentThrottle.recordAttempt(clientIp);

        CheckoutSession cs = ctx.sessionAttribute("checkout_session");
        if (cs == null) {
            ctx.status(410);
            Map<String, Object> model = baseModel(ctx);
            model.put("checkoutState", "expired");
            model.put("stateHeading", "Your checkout session has expired");
            model.put("payError", "Please return to Flights to select a seat and start checkout again.");
            ctx.render("checkout.html", model);
            return;
        }

        synchronized (cs) {
            if (cs.getCompletedBookingRef() != null) {
                markSessionOwnedBooking(ctx, cs.getCompletedBookingRef());
                ctx.redirect("/confirmation/" + cs.getCompletedBookingRef());
                return;
            }
            Optional<BookingRecord> existingByIdem = bookingService.findByIdempotencyKey(cs.getIdempotencyKey());
            if (existingByIdem.isPresent()) {
                String ref = existingByIdem.get().getBookingReference();
                cs.setCompletedBookingRef(ref);
                markSessionOwnedBooking(ctx, ref);
                ctx.redirect("/confirmation/" + ref);
                return;
            }

            String requesterOwner = resolveHoldOwner(ctx);
            if (!Objects.equals(cs.getHoldOwner(), requesterOwner)) {
                ctx.status(403);
                renderCheckoutModel(ctx, cs, "forbidden", "This seat hold belongs to another user or session",
                        "This seat hold belongs to another user or browser session. Please select your own seat.", null);
                return;
            }

            Instant now = seatMapService.getClock().instant();
            Optional<FlightSeat> liveSeatOpt = seatMapService.findSeatById(cs.getFlightSeatId());
            if (liveSeatOpt.isEmpty() || liveSeatOpt.get().getStatus() == SeatStatus.BOOKED) {
                ctx.status(409);
                renderCheckoutModel(ctx, cs, "taken", "Seat is no longer available",
                        "This seat has already been booked. Please return to the seat map to choose another seat.", null);
                return;
            }
            FlightSeat liveSeat = liveSeatOpt.get();
            if (liveSeat.getStatus() == SeatStatus.HELD && liveSeat.getHeldBy() != null
                    && !Objects.equals(liveSeat.getHeldBy(), requesterOwner)) {
                ctx.status(403);
                renderCheckoutModel(ctx, cs, "forbidden", "Seat hold owned by another traveller",
                        "Another user or session holds this seat right now. Please select another seat.", null);
                return;
            }
            if (liveSeat.getStatus() != SeatStatus.HELD
                    || (liveSeat.getHeldUntil() != null && !liveSeat.getHeldUntil().isAfter(now))
                    || !cs.getHoldExpiresAt().isAfter(now)) {
                ctx.status(410);
                renderCheckoutModel(ctx, cs, "expired", "Your 5-minute seat hold has expired",
                        "Your seat hold expired before payment was submitted. Please pick a seat again on the seat map.", null);
                return;
            }

            if (cs.getDepartureInstant() != null
                    && !FlightSalesPolicy.isOpen(cs.getDepartureInstant(), bookingService.getClock())) {
                seatMapService.deselectSeat(cs.getSeat(), cs.getHoldOwner());
                ctx.status(409);
                renderCheckoutModel(ctx, cs, "expired", "Flight sales have closed",
                        "Sales close " + FlightSalesPolicy.SALES_CLOSE_BEFORE_DEPARTURE.toMinutes()
                                + " minutes before departure. This flight can no longer be booked.", null);
                return;
            }

            // Recompute server-side fare (completely ignoring any price/total submitted by client)
            PriceQuote authoritativeQuote;
            try {
                authoritativeQuote = bookingService.getPricingService()
                        .quote(cs.getFlightId(), cs.getSeat().getSeatClass(), bookingService.getClock());
                cs.setQuote(authoritativeQuote);
            } catch (Exception e) {
                logger.error("Pricing recomputation failed at payment: {}", e.getMessage(), e);
                ctx.status(500);
                renderCheckoutModel(ctx, cs, "active", null,
                        "We could not verify the current flight fare right now. Your card was not charged.", null);
                return;
            }

            // Read sensitive card inputs into local variables only — NEVER store in session, DB, or logs
            String rawCardNumber = ctx.formParam("cardNumber");
            String rawExpiry = ctx.formParam("expiry");
            String rawCvv = ctx.formParam("cvv");

            PaymentResult paymentResult;
            try {
                paymentResult = paymentService.processPayment(
                        rawCardNumber, rawExpiry, rawCvv, authoritativeQuote.total());
            } catch (Exception e) {
                logger.error("Payment gateway error during checkout {}: {}", cs.getIdempotencyKey(), e.getMessage());
                ctx.status(502);
                renderCheckoutModel(ctx, cs, "active", null,
                        "The payment gateway encountered a temporary error. Your card was not charged; please try again.", null);
                return;
            }

            if (!paymentResult.isApproved()) {
                int attempts = cs.incrementFailedAttempts();
                if (attempts >= CheckoutSession.MAX_PAYMENT_ATTEMPTS) {
                    seatMapService.deselectSeat(cs.getSeat(), cs.getHoldOwner());
                    ctx.req().getSession().removeAttribute("checkout_session");
                    ctx.sessionAttribute("seat_flash_error",
                            "Payment was declined 3 times, so your seat hold was released. Please choose a seat to start again.");
                    ctx.redirect("/flights/" + cs.getFlightId() + "/seats");
                    return;
                }
                ctx.status(402);
                String reason = firstNonBlank(paymentResult.getErrorMessage(), "Card was declined.");
                renderCheckoutModel(ctx, cs, "active", null,
                        reason + " Please re-enter your card details (" + cs.getRemainingAttempts()
                                + " attempt" + (cs.getRemainingAttempts() == 1 ? "" : "s") + " left while your hold is active).",
                        null);
                return;
            }

            BookingContext bc = buildBookingContext(ctx, cs, authoritativeQuote, paymentResult);
            try {
                BookingRecord record = bookingService.createBooking(bc);
                cs.setCompletedBookingRef(record.getBookingReference());
                markSessionOwnedBooking(ctx, record.getBookingReference());
                ctx.redirect("/confirmation/" + record.getBookingReference());
            } catch (SeatUnavailableException e) {
                refundQuietly(paymentResult.getTransactionReference());
                ctx.status(409);
                renderCheckoutModel(ctx, cs, "expired", "Seat hold expired during checkout",
                        "Your seat hold was lost before booking completed, so any card charge was reversed. Please select a seat again.",
                        null);
            } catch (PriceChangedException e) {
                refundQuietly(paymentResult.getTransactionReference());
                cs.setQuote(bookingService.getPricingService()
                        .quote(cs.getFlightId(), cs.getSeat().getSeatClass(), bookingService.getClock()));
                ctx.status(409);
                renderCheckoutModel(ctx, cs, "active", null,
                        "The flight price updated before confirmation. Your previous charge was reversed — review the updated total and submit again.",
                        null);
            } catch (Exception e) {
                logger.error("Booking persistence failed after approved payment {}: {}",
                        paymentResult.getTransactionReference(), e.getMessage());
                refundQuietly(paymentResult.getTransactionReference());
                ctx.status(500);
                renderCheckoutModel(ctx, cs, "active", null,
                        "We could not complete your booking due to a temporary database error. Your card charge has been reversed; please try again.",
                        null);
            }
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
        model.put("activeNav", "my-bookings");
        BookingRecord record = recordOpt.get();
        boolean owner = isBookingOwner(ctx, record);
        boolean isCancelled = isCancelledStatus(record.getStatus());
        boolean canCancel = owner && !isCancelled
                && bookingService.getCancellationPolicy().evaluate(record).allowed();
        model.put("record", record);
        model.put("totalPaidFormatted", MoneyFormatter.format(record.getTotalAmount()));
        model.put("isBookingOwner", owner);
        model.put("isCancelled", isCancelled);
        model.put("canCancel", canCancel);
        applyDestinationBackdrop(model, record.getDestination(), true);
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

    public void handleConfirmationBarcode(Context ctx) {
        String reference = ctx.pathParam("reference");
        Optional<BookingRecord> recordOpt = bookingService.getBookingByReference(reference);
        if (recordOpt.isEmpty()) {
            ctx.status(404).result("Booking reference not found.");
            return;
        }
        String code = recordOpt.get().getBookingReference();
        try {
            BitMatrix matrix = new Code128Writer().encode(code, BarcodeFormat.CODE_128, 380, 96);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            ctx.contentType("image/png");
            ctx.header("Cache-Control", "private, max-age=300");
            ctx.result(baos.toByteArray());
        } catch (IOException e) {
            logger.error("Failed to render barcode PNG for {}", code, e);
            ctx.status(500).result("Could not generate barcode.");
        }
    }

    public void handleConfirmationQr(Context ctx) {
        String reference = ctx.pathParam("reference");
        Optional<BookingRecord> recordOpt = bookingService.getBookingByReference(reference);
        if (recordOpt.isEmpty()) {
            ctx.status(404).result("Booking reference not found.");
            return;
        }
        BookingRecord record = recordOpt.get();
        if (!isBookingOwner(ctx, record)) {
            ctx.status(403).result("Forbidden.");
            return;
        }
        String verifyUrl = verificationService.buildVerificationUrl(resolveBaseUrl(ctx), record);
        try {
            byte[] qrPng = QrCodeGenerator.generateQrPng(verifyUrl, QrCodeGenerator.MIN_SIZE_PX);
            applyNoStoreHeaders(ctx);
            ctx.contentType("image/png");
            ctx.result(qrPng);
        } catch (IOException e) {
            logger.error("Failed to render QR PNG for {}", record.getBookingReference(), e);
            ctx.status(500).result("Could not generate verification QR code.");
        }
    }

    public void handleConfirmationPdf(Context ctx) {
        String reference = ctx.pathParam("reference");
        Optional<BookingRecord> recordOpt = bookingService.getBookingByReference(reference);
        if (recordOpt.isEmpty()) {
            ctx.status(404).result("Booking reference not found.");
            return;
        }
        BookingRecord record = recordOpt.get();
        if (isCancelledStatus(record.getStatus())) {
            ctx.status(410).result("This booking has been cancelled; the boarding pass PDF is no longer available.");
            return;
        }
        String verifyUrl = verificationService.buildVerificationUrl(resolveBaseUrl(ctx), record);
        String safeRef = record.getBookingReference().replaceAll("[^A-Za-z0-9_-]", "");
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("boarding-pass-" + safeRef + "-", ".pdf");
            BoardingPassPdfGenerator.generate(TicketData.from(record), verifyUrl, tmpFile.toFile());
            byte[] pdfBytes = Files.readAllBytes(tmpFile);
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "inline; filename=\"boarding-pass-" + safeRef + ".pdf\"");
            ctx.header("Cache-Control", "private, max-age=300");
            ctx.result(pdfBytes);
        } catch (Exception e) {
            logger.error("Failed to generate boarding pass PDF for {}", safeRef, e);
            ctx.status(500).result("Could not generate boarding pass PDF.");
        } finally {
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void handleVerifyGet(Context ctx) {
        applyNoStoreHeaders(ctx);
        ctx.header("X-Robots-Tag", "noindex, nofollow");

        String clientIp = ctx.ip();
        Optional<Duration> lockout = verifyThrottle.remainingLockout(clientIp);
        if (lockout.isPresent()) {
            ctx.status(429);
            Map<String, Object> model = baseModel(ctx);
            model.put("verifyState", "THROTTLED");
            model.put("throttleMessage", "Too many verification requests. Please wait a minute and try again.");
            ctx.render("verify.html", model);
            return;
        }
        verifyThrottle.recordRequest(clientIp);

        var result = verificationService.verify(ctx.pathParam("pnr"), ctx.queryParam("sig"));
        if (result.status() == BoardingPassVerificationService.Status.INVALID) {
            ctx.status(404);
            Map<String, Object> invalidModel = new HashMap<>();
            invalidModel.put("appName", BrandConfig.APP_NAME);
            invalidModel.put("verifyState", "INVALID");
            applyFixedAerialBackdrop(invalidModel);
            ctx.render("verify.html", invalidModel);
            return;
        }

        Map<String, Object> model = baseModel(ctx);
        model.put("verifyState", result.status().name());
        model.put("verifyResult", result);
        ctx.render("verify.html", model);
    }

    private boolean isBookingOwner(Context ctx, BookingRecord record) {
        if (record == null) {
            return false;
        }
        User user = ctx.sessionAttribute("user");
        if (user != null && record.getUserId() != null && user.getUserId() == record.getUserId()) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Set<String> owned = ctx.sessionAttribute("owned_booking_refs");
        return owned != null && record.getBookingReference() != null
                && owned.contains(record.getBookingReference().trim().toUpperCase(Locale.ROOT));
    }

    public void handleMyBookingsGet(Context ctx) {
        User user = ctx.sessionAttribute("user");
        if (user == null) {
            ctx.redirect("/login?next=/my-bookings");
            return;
        }

        Map<String, Object> model = baseModel(ctx);
        model.put("activeNav", "my-bookings");
        String flashSuccess = ctx.sessionAttribute("flash_success");
        if (flashSuccess != null) {
            ctx.req().getSession().removeAttribute("flash_success");
            model.put("bookingsFlashSuccess", flashSuccess);
        }

        List<MyBookingCard> upcoming = new ArrayList<>();
        List<MyBookingCard> past = new ArrayList<>();
        List<MyBookingCard> cancelled = new ArrayList<>();
        String bookingsError = null;
        int totalCount = 0;

        try {
            List<BookingSummary> summaries = bookingService.listBookingsForUser(user.getUserId());
            CancellationPolicy policy = bookingService.getCancellationPolicy();
            for (BookingSummary s : summaries) {
                totalCount++;
                String totalFmt = MoneyFormatter.format(s.getTotalPaid());
                boolean isCancelled = isCancelledStatus(s.getStatus()) || isCancelledStatus(s.getStoredStatus());
                if (isCancelled) {
                    cancelled.add(new MyBookingCard(s, totalFmt, false, false));
                } else if ("UPCOMING".equalsIgnoreCase(s.getStatus())) {
                    boolean allowed = policy.evaluate(s.getDepartureInstant(), s.getStoredStatus(), s.getTotalPaid()).allowed();
                    upcoming.add(new MyBookingCard(s, totalFmt, allowed, true));
                } else {
                    past.add(new MyBookingCard(s, totalFmt, false, true));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to list bookings for user {}: {}", user.getUserId(), e.getMessage());
            bookingsError = "We could not load your bookings right now. Please try again in a moment.";
        }

        model.put("upcomingBookings", upcoming);
        model.put("pastBookings", past);
        model.put("cancelledBookings", cancelled);
        model.put("totalCount", totalCount);
        if (bookingsError != null) {
            model.put("bookingsError", bookingsError);
        }
        ctx.render("my-bookings.html", model);
    }

    public void handleCancelGet(Context ctx) {
        String rawRef = ctx.pathParam("reference");
        String safeRef = sanitizeRef(rawRef);
        User user = ctx.sessionAttribute("user");
        if (user == null) {
            ctx.redirect("/login?next=" + (safeRef.isEmpty() ? "/my-bookings" : "/bookings/" + safeRef + "/cancel"));
            return;
        }
        try {
            BookingRecord record = bookingService.getBookingForUser(user.getUserId(), rawRef);
            renderCancelModel(ctx, record, null, null);
        } catch (NoSuchElementException | IllegalArgumentException e) {
            ctx.status(404);
            Map<String, Object> model = baseModel(ctx);
            model.put("error", "No booking found for reference code: " + rawRef);
            ctx.render("error.html", model);
        } catch (SecurityException e) {
            ctx.status(403);
            Map<String, Object> model = baseModel(ctx);
            model.put("error", "You do not have permission to view or cancel this booking.");
            ctx.render("error.html", model);
        }
    }

    public void handleCancelPost(Context ctx) {
        String rawRef = ctx.pathParam("reference");
        User user = ctx.sessionAttribute("user");
        if (user == null) {
            ctx.redirect("/login?next=/my-bookings");
            return;
        }
        try {
            CancelResult result = bookingService.cancelBookingForUser(user.getUserId(), rawRef, paymentService);
            if (result.alreadyCancelled()) {
                BookingRecord record = bookingService.getBookingForUser(user.getUserId(), rawRef);
                renderCancelModel(ctx, record, null, "This booking is already cancelled. No further refund or changes were made.");
                return;
            }
            String refundMsg = result.refundReference() != null && !result.refundReference().isBlank()
                    ? " Refund " + MoneyFormatter.format(result.refundAmount()) + " (" + result.refundReference() + ") processed."
                    : " Refund " + MoneyFormatter.format(result.refundAmount()) + " processed.";
            ctx.sessionAttribute("flash_success",
                    "Booking " + result.bookingReference() + " cancelled." + refundMsg);
            ctx.redirect("/my-bookings");
        } catch (NoSuchElementException | IllegalArgumentException e) {
            ctx.status(404);
            Map<String, Object> model = baseModel(ctx);
            model.put("error", "No booking found for reference code: " + rawRef);
            ctx.render("error.html", model);
        } catch (SecurityException e) {
            ctx.status(403);
            Map<String, Object> model = baseModel(ctx);
            model.put("error", "You do not have permission to cancel this booking.");
            ctx.render("error.html", model);
        } catch (RefundFailedException e) {
            logger.error("Refund failed during cancellation for ref={} user={}: {}", rawRef, user.getUserId(), e.getMessage());
            BookingRecord record = bookingService.getBookingForUser(user.getUserId(), rawRef);
            ctx.status(502);
            renderCancelModel(ctx, record,
                    "The refund could not be completed right now, so your booking and seat remain active. Please try again shortly.",
                    null);
        } catch (IllegalStateException e) {
            BookingRecord record = bookingService.getBookingForUser(user.getUserId(), rawRef);
            ctx.status(409);
            renderCancelModel(ctx, record, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("Cancellation/refund failed for ref={} user={}: {}", rawRef, user.getUserId(), e.getMessage());
            BookingRecord record = bookingService.getBookingForUser(user.getUserId(), rawRef);
            ctx.status(502);
            renderCancelModel(ctx, record,
                    "The refund could not be completed right now, so your booking and seat remain active. Please try again shortly.",
                    null);
        }
    }

    private void renderCancelModel(Context ctx, BookingRecord record, String cancelError, String cancelNotice) {
        Map<String, Object> model = baseModel(ctx);
        model.put("activeNav", "my-bookings");
        CancellationPolicy.Decision decision = bookingService.getCancellationPolicy().evaluate(record);
        model.put("record", record);
        model.put("canConfirm", decision.allowed());
        model.put("alreadyCancelled", decision.alreadyCancelled());
        model.put("cancelBlocked", !decision.allowed() && !decision.alreadyCancelled());
        model.put("policyReason", decision.reason());
        model.put("refundFormatted", MoneyFormatter.format(decision.allowed() ? decision.refundAmount() : record.getTotalAmount()));
        if (cancelError != null) model.put("cancelError", cancelError);
        if (cancelNotice != null) model.put("cancelNotice", cancelNotice);
        ctx.render("cancel.html", model);
    }

    public static final class MyBookingCard {
        private final BookingSummary summary;
        private final String totalFormatted;
        private final boolean cancellable;
        private final boolean showPdf;

        public MyBookingCard(BookingSummary summary, String totalFormatted, boolean cancellable, boolean showPdf) {
            this.summary = summary;
            this.totalFormatted = totalFormatted;
            this.cancellable = cancellable;
            this.showPdf = showPdf;
        }

        public BookingSummary getSummary() { return summary; }
        public String getTotalFormatted() { return totalFormatted; }
        public boolean isCancellable() { return cancellable; }
        public boolean isShowPdf() { return showPdf; }
    }

    private static boolean isCancelledStatus(String status) {
        if (status == null) return false;
        String norm = status.trim().toUpperCase(Locale.ROOT);
        return "CANCELLED".equals(norm) || "REFUNDED".equals(norm);
    }

    private static String sanitizeRef(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
    }

    private static String sanitizeLocalNext(String rawNext) {
        if (rawNext == null) return null;
        String trimmed = rawNext.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("/") || trimmed.startsWith("//")
                || trimmed.contains("\\") || trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0) {
            return null;
        }
        return trimmed;
    }

    public void handleLoginGet(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        applyRotatingBackdrop(model);
        String nextUrl = sanitizeLocalNext(ctx.queryParam("next"));
        if (nextUrl != null) {
            model.put("nextUrl", nextUrl);
        }
        ctx.render("login.html", model);
    }

    public void handleLoginPost(Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        String nextUrl = sanitizeLocalNext(ctx.formParam("next"));
        String clientIp = ctx.ip();

        logger.info("Web login attempt for user '{}'", AuthService.maskIdentifier(username));

        Optional<Duration> activeLockout = loginThrottle.remainingLockout(username, clientIp);
        if (activeLockout.isPresent()) {
            String waitText = LoginThrottle.formatWait(activeLockout.get());
            logger.info("Login throttled: identifier={} wait={}", AuthService.maskIdentifier(username), waitText);
            ctx.status(429);
            Map<String, Object> model = baseModel(ctx);
            applyRotatingBackdrop(model);
            model.put("loginError", "Too many failed login attempts. Please wait " + waitText + " before trying again.");
            model.put("username", username);
            if (nextUrl != null) model.put("nextUrl", nextUrl);
            ctx.render("login.html", model);
            return;
        }

        try {
            User user = authService.authenticate(username, password);
            loginThrottle.recordSuccess(username, clientIp);
            String csrfToken = ctx.sessionAttribute("csrf_token");
            ctx.req().changeSessionId();
            if (csrfToken != null) {
                ctx.sessionAttribute("csrf_token", csrfToken);
            }
            ctx.sessionAttribute("user", user);
            logger.info("Web login successful for user '{}' (Role: {})", user.getUsername(), user.getRole());
            ctx.redirect(nextUrl != null ? nextUrl : "/");
        } catch (AuthenticationException e) {
            loginThrottle.recordFailure(username, clientIp);
            Optional<Duration> triggeredLockout = loginThrottle.remainingLockout(username, clientIp);
            Map<String, Object> model = baseModel(ctx);
            applyRotatingBackdrop(model);
            if (triggeredLockout.isPresent()) {
                String waitText = LoginThrottle.formatWait(triggeredLockout.get());
                logger.info("Login throttled: identifier={} wait={}", AuthService.maskIdentifier(username), waitText);
                ctx.status(429);
                model.put("loginError", "Too many failed login attempts. Please wait " + waitText + " before trying again.");
            } else {
                model.put("loginError", e.getMessage());
            }
            model.put("username", username);
            if (nextUrl != null) model.put("nextUrl", nextUrl);
            ctx.render("login.html", model);
        }
    }

    public void handleLogout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/");
    }

    public void handleRegisterGet(Context ctx) {
        Map<String, Object> model = baseModel(ctx);
        applyRotatingBackdrop(model);
        ctx.render("register.html", model);
    }

    public void handleRegisterPost(Context ctx) {
        String fullName = ctx.formParam("fullName");
        String email = ctx.formParam("email");
        String password = ctx.formParam("password");
        String confirmPassword = ctx.formParam("confirmPassword");
        String consent = ctx.formParam("consent");
        boolean consentChecked = "on".equalsIgnoreCase(consent) || "true".equalsIgnoreCase(consent);
        String honeypot = ctx.formParam("website");
        String clientIp = ctx.ip();

        Optional<Duration> activeLockout = registrationThrottle.remainingLockout(clientIp);
        if (activeLockout.isPresent()) {
            String waitText = LoginThrottle.formatWait(activeLockout.get());
            logger.info("Registration throttled: email={} wait={}",
                    RegistrationService.maskEmail(email), waitText);
            ctx.status(429);
            Map<String, Object> model = baseModel(ctx);
            applyRotatingBackdrop(model);
            model.put("registerError",
                    "Too many registration attempts. Please wait " + waitText + " before trying again.");
            model.put("fullName", fullName);
            model.put("email", email);
            model.put("consentChecked", consentChecked);
            ctx.render("register.html", model);
            return;
        }

        if (honeypot != null && !honeypot.isBlank()) {
            logger.warn("Registration honeypot triggered for email={}", RegistrationService.maskEmail(email));
            Map<String, Object> model = baseModel(ctx);
            applyRotatingBackdrop(model);
            model.put("registerNotice", "Thank you. Your submission has been received.");
            ctx.render("register.html", model);
            return;
        }

        registrationThrottle.recordAttempt(clientIp);

        try {
            com.group2.flightres.util.ValidationUtil.validateFullName(fullName);
            com.group2.flightres.util.ValidationUtil.validateEmail(email);
            String validPassword = com.group2.flightres.util.ValidationUtil.validatePassword(password);
            if (confirmPassword == null || !confirmPassword.equals(validPassword)) {
                throw new ValidationException("confirmPassword", "Passwords do not match.");
            }
            if (!consentChecked) {
                throw new ValidationException("consent",
                        "Please confirm the university project privacy notice to continue.");
            }

            User user = registrationService.register(fullName, email, password, resolveBaseUrl(ctx));
            String csrfToken = ctx.sessionAttribute("csrf_token");
            ctx.req().changeSessionId();
            if (csrfToken != null) {
                ctx.sessionAttribute("csrf_token", csrfToken);
            }
            ctx.sessionAttribute("user", user);
            String firstName = extractFirstName(user.getFullName());
            ctx.sessionAttribute("flash_success", "Welcome to Sky Royal, " + firstName + "!");
            logger.info("Web registration successful: userId={} email={}",
                    user.getUserId(), RegistrationService.maskEmail(user.getEmail()));
            ctx.redirect("/");
        } catch (DuplicateEmailException e) {
            ctx.status(409);
            Map<String, Object> model = baseModel(ctx);
            applyRotatingBackdrop(model);
            model.put("registerError", e.getMessage());
            model.put("errorField", "email");
            model.put("fullName", fullName);
            model.put("email", email);
            model.put("consentChecked", consentChecked);
            ctx.render("register.html", model);
        } catch (ValidationException e) {
            ctx.status(400);
            Map<String, Object> model = baseModel(ctx);
            applyRotatingBackdrop(model);
            model.put("registerError", e.getMessage());
            model.put("errorField", e.getField());
            model.put("fullName", fullName);
            model.put("email", email);
            model.put("consentChecked", consentChecked);
            ctx.render("register.html", model);
        }
    }

    private static String resolveBaseUrl(Context ctx) {
        String configured = com.group2.flightres.config.EmailConfig.fromEnvironmentAndAppConfig().getPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return com.group2.flightres.service.WelcomeEmailRenderer.normalizeBaseUrl(configured, "");
        }
        String scheme = ctx.scheme();
        String host = ctx.host();
        if (scheme == null || host == null || host.isBlank()
                || host.contains("\r") || host.contains("\n") || host.chars().anyMatch(Character::isWhitespace)) {
            return "http://localhost:8080";
        }
        String normScheme = ("https".equalsIgnoreCase(scheme)) ? "https" : "http";
        return normScheme + "://" + host.replaceAll("/+$", "");
    }

    private static String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Traveller";
        }
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
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

    public void handleAdmin(Context ctx) {
        User user = ctx.sessionAttribute("user");
        if (user == null) {
            ctx.redirect("/login?next=/admin");
            return;
        }
        if (user.getRole() != UserRole.ADMIN) {
            ctx.status(403);
            Map<String, Object> model = baseModel(ctx);
            model.put("error", "Administrator access is required to view this page.");
            ctx.render("error.html", model);
            return;
        }

        Map<String, Object> model = baseModel(ctx);
        model.put("activeNav", "admin");
        try {
            AdminDashboardData data = adminDashboardService.getDashboardData(user);
            model.put("dashboardData", data);
            model.put("revenueFormatted", MoneyFormatter.format(data.getTotalRevenue()));
        } catch (Exception e) {
            logger.warn("Failed to load admin dashboard data: {}", e.getMessage());
            model.put("adminError", "Unable to load live admin metrics right now. Please try again.");
        }
        ctx.render("admin.html", model);
    }

    private BookingContext buildBookingContext(Context ctx, CheckoutSession cs, PriceQuote quote, PaymentResult paymentResult) {
        BookingContext bc = new BookingContext();
        bc.setFlightId(cs.getFlightId());
        bc.setFlightNumber(cs.getFlightNumber());
        bc.setOrigin(cs.getOrigin());
        bc.setDestination(cs.getDestination());
        bc.setDepartureDate(cs.getDepartureDate());
        bc.setDepartureTime(cs.getDepartureTime());
        bc.setArrivalTime(cs.getArrivalTime());
        bc.setDepartureInstant(cs.getDepartureInstant());
        bc.setArrivalInstant(cs.getArrivalInstant());
        bc.setPassengerName(cs.getPassengerName());
        bc.setPassengerEmail(cs.getPassengerEmail());
        bc.setPassportNumber(cs.getPassportNumber());
        bc.setDateOfBirth(cs.getDateOfBirth());
        bc.setSelectedSeats(List.of(cs.getSeat()));
        bc.setBaseFare(quote.baseFare());
        bc.setPriceQuotes(List.of(quote));
        bc.setTotalPrice(quote.total());
        bc.setPaymentResult(paymentResult);
        bc.setIdempotencyKey(cs.getIdempotencyKey());
        bc.setHoldOwner(cs.getHoldOwner());

        User currentUser = ctx.sessionAttribute("user");
        if (currentUser != null) {
            bc.setCurrentUser(currentUser);
        }
        return bc;
    }

    private void renderCheckoutModel(Context ctx, CheckoutSession cs, String state,
                                     String stateHeading, String payError, String errorField) {
        Map<String, Object> model = baseModel(ctx);
        applyFixedAerialBackdrop(model);
        model.put("activeNav", "flights");
        model.put("checkoutSession", cs);
        model.put("checkoutState", state);
        if (stateHeading != null) model.put("stateHeading", stateHeading);
        if (payError != null) model.put("payError", payError);
        if (errorField != null) model.put("errorField", errorField);

        PriceQuote q = cs.getQuote();
        BigDecimal classUpgrade = q.classFare().subtract(q.baseFare());
        model.put("baseFareFormatted", MoneyFormatter.format(q.baseFare()));
        model.put("hasClassPremium", classUpgrade.compareTo(BigDecimal.ZERO) > 0);
        model.put("classUpgradeFormatted", MoneyFormatter.formatSigned(classUpgrade));
        model.put("demandAdjFormatted", MoneyFormatter.formatSigned(q.demandAdjustment()));
        model.put("timingAdjFormatted", MoneyFormatter.formatSigned(q.timingAdjustment()));
        model.put("totalFormatted", MoneyFormatter.format(q.total()));
        model.put("vatFormatted", MoneyFormatter.format(q.vatIncluded()));

        Instant now = seatMapService.getClock().instant();
        long remSecs = Math.max(0L, Duration.between(now, cs.getHoldExpiresAt()).toSeconds());
        model.put("remainingHoldSeconds", remSecs);
        model.put("remainingHoldFormatted", String.format(Locale.US, "%02d:%02d", remSecs / 60, remSecs % 60));
        model.put("holdExpiresEpochMs", cs.getHoldExpiresAt().toEpochMilli());
        ctx.render("checkout.html", model);
    }

    private String resolveHoldOwner(Context ctx) {
        User user = ctx.sessionAttribute("user");
        if (user != null && user.getUserId() > 0) {
            return "user:" + user.getUserId();
        }
        return "session:" + ctx.req().getSession(true).getId();
    }

    private void markSessionOwnedBooking(Context ctx, String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) return;
        @SuppressWarnings("unchecked")
        Set<String> owned = ctx.sessionAttribute("owned_booking_refs");
        if (owned == null) {
            owned = ConcurrentHashMap.newKeySet();
            ctx.sessionAttribute("owned_booking_refs", owned);
        }
        owned.add(bookingRef.trim().toUpperCase(Locale.ROOT));
    }

    private static void applyNoStoreHeaders(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate");
        ctx.header("Pragma", "no-cache");
        ctx.header("Expires", "0");
    }

    private void refundQuietly(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) return;
        try {
            paymentService.refund(txnRef);
        } catch (Exception ex) {
            logger.error("Compensating refund failed for {}: {}", txnRef, ex.getMessage());
        }
    }

    private LocalDate parseAndValidateDob(String rawDob) {
        if (rawDob == null || rawDob.isBlank()) {
            throw new ValidationException("dateOfBirth", "Passenger date of birth is required.");
        }
        try {
            LocalDate dob = LocalDate.parse(rawDob.trim());
            ValidationUtil.validateDateOfBirth(dob);
            return dob;
        } catch (DateTimeParseException e) {
            throw new ValidationException("dateOfBirth", "Please enter a valid date of birth (YYYY-MM-DD).");
        }
    }

    private static String firstNonBlank(String candidate, String fallback) {
        return (candidate != null && !candidate.isBlank()) ? candidate.trim() : fallback;
    }

    private static final DateTimeFormatter FRIENDLY_DATE_FMT =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private static String formatFriendlyDate(LocalDate d) {
        return d != null ? FRIENDLY_DATE_FMT.format(d) : "";
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
        if (lower.contains("birth")) return "dateOfBirth";
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

        FlightSearchResult flight = flightOpt.get();
        String holdOwner = resolveHoldOwner(ctx);
        Instant now = seatMapService.getClock().instant();
        List<FlightSeat> seats = seatMapService.loadSeatMap(flightId);
        SeatMapLayoutBuilder.SeatMapView seatMapView = SeatMapLayoutBuilder.build(seats, holdOwner, now);

        FlightSeat heldSeat = seatMapView.getHeldSeat();
        SeatClass initialCabin = heldSeat != null && heldSeat.getSeatClass() != null
                ? heldSeat.getSeatClass()
                : (SeatClass.FIRST.name().equalsIgnoreCase(ctx.queryParam("cabin")) ? SeatClass.FIRST : SeatClass.ECONOMY);

        Map<String, Object> model = baseModel(ctx);
        applyCabinBackdrop(model, initialCabin);
        model.put("activeNav", "flights");
        model.put("flight", flight);

        String defaultOrigin = (flight.getOriginCity() != null ? flight.getOriginCity() : flight.getOriginCode())
                + " (" + flight.getOriginCode() + ")";
        String defaultDest = (flight.getDestCity() != null ? flight.getDestCity() : flight.getDestCode())
                + " (" + flight.getDestCode() + ")";

        String origin = firstNonBlank(ctx.queryParam("origin"),
                firstNonBlank(ctx.formParam("origin"), defaultOrigin));
        String destination = firstNonBlank(ctx.queryParam("destination"),
                firstNonBlank(ctx.formParam("destination"), defaultDest));
        String departureDate = firstNonBlank(ctx.queryParam("departureDate"),
                firstNonBlank(ctx.formParam("departureDate"),
                        firstNonBlank(resolveDepartureDate(flight), LocalDate.now(bookingService.getClock()).toString())));

        LocalDate parsedDep = null;
        try {
            parsedDep = LocalDate.parse(departureDate.trim());
        } catch (Exception ignored) {
        }

        boolean salesOpen = flight.getDeparture() == null
                || FlightSalesPolicy.isOpen(flight.getDeparture(), bookingService.getClock());

        model.put("origin", origin);
        model.put("destination", destination);
        model.put("departureDate", departureDate);
        model.put("departureDateFriendly", parsedDep != null ? formatFriendlyDate(parsedDep) : departureDate);
        model.put("salesClosed", !salesOpen);
        model.put("seatMapView", seatMapView);
        model.put("holdDurationSeconds", SeatHoldPolicy.HOLD_SECONDS);

        PriceQuote previewQuote = heldSeat != null
                ? computeSeatQuoteSafe(flight, heldSeat.getSeatClass())
                : computeSeatQuoteSafe(flight, initialCabin);
        BigDecimal classUpgrade = previewQuote.classFare().subtract(previewQuote.baseFare());
        model.put("selectedQuoteTotalFormatted", MoneyFormatter.format(previewQuote.total()));
        model.put("selectedBaseFareFormatted", MoneyFormatter.format(previewQuote.baseFare()));
        model.put("selectedHasClassPremium", classUpgrade.compareTo(BigDecimal.ZERO) > 0);
        model.put("selectedClassUpgradeFormatted", MoneyFormatter.formatSigned(classUpgrade));
        model.put("selectedDemandAdjFormatted", MoneyFormatter.formatSigned(previewQuote.demandAdjustment()));
        model.put("selectedTimingAdjFormatted", MoneyFormatter.formatSigned(previewQuote.timingAdjustment()));
        model.put("selectedVatFormatted", MoneyFormatter.format(previewQuote.vatIncluded()));

        if (heldSeat != null) {
            Instant heldUntil = heldSeat.getHeldUntil() != null
                    ? heldSeat.getHeldUntil()
                    : now.plusSeconds(SeatHoldPolicy.HOLD_SECONDS);
            long remSecs = Math.max(0L, Duration.between(now, heldUntil).toSeconds());
            model.put("heldSeat", heldSeat);
            model.put("heldSeatPositionTag", seatMapView.getHeldSeatPositionTag());
            model.put("heldExpiresEpochMs", heldUntil.toEpochMilli());
            model.put("heldRemainingFormatted", String.format(Locale.US, "%02d:%02d", remSecs / 60, remSecs % 60));
        }

        if (error != null) {
            model.put("bookingError", error);
        }
        if (errorField != null) {
            model.put("errorField", errorField);
        }
        ctx.render("seats.html", model);
    }

    private PriceQuote computeSeatQuoteSafe(FlightSearchResult flight, SeatClass seatClass) {
        try {
            return bookingService.getPricingService()
                    .quote(flight.getFlightId(), seatClass, bookingService.getClock());
        } catch (Exception e) {
            BigDecimal base = flight.getBasePrice() != null ? flight.getBasePrice() : BigDecimal.ZERO;
            BigDecimal mult = seatClass == SeatClass.FIRST ? new BigDecimal("3.50") : BigDecimal.ONE;
            return PriceQuote.calculate(base, mult, BigDecimal.ONE, BigDecimal.ONE);
        }
    }

    private static boolean expectsJson(Context ctx) {
        String accept = ctx.header("Accept");
        return accept != null && accept.toLowerCase(Locale.ROOT).contains("application/json");
    }

    private void respondSeatError(Context ctx, boolean wantsJson, int status, String message, String redirectTo) {
        ctx.status(status);
        if (wantsJson) {
            ctx.contentType("application/json; charset=utf-8")
                    .result(toJsonObject(Map.of("error", firstNonBlank(message, "Seat request failed."))));
            return;
        }
        ctx.sessionAttribute("seat_flash_error", firstNonBlank(message, "Seat request failed."));
        ctx.redirect(redirectTo);
    }

    private static String toJsonObject(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            Object v = entry.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Boolean || v instanceof Number) {
                sb.append(v);
            } else {
                sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String resolveDepartureDate(FlightSearchResult flight) {
        if (flight == null || flight.getDeparture() == null) {
            return null;
        }
        ZoneId zone = flight.getOriginZone() != null ? flight.getOriginZone() : ZoneId.of("Africa/Johannesburg");
        return FlightTimes.formatLocalDate(flight.getDeparture(), zone);
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
