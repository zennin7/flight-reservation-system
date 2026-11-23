package com.group2.flightres.web;

import com.group2.flightres.dao.impl.UserDaoImpl;
import com.group2.flightres.service.AdminDashboardService;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BoardingPassVerificationService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.EmailServiceFactory;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.LoginThrottle;
import com.group2.flightres.service.PaymentService;
import com.group2.flightres.service.PaymentThrottle;
import com.group2.flightres.service.RegistrationService;
import com.group2.flightres.service.RegistrationThrottle;
import com.group2.flightres.service.SeatMapService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import com.group2.flightres.service.VerifyThrottle;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Map;
import java.util.UUID;

/**
 * Server-rendered web application for Sky Royal.
 * Uses Javalin 6 and Thymeleaf with shared core services.
 */
public class WebApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);

    public static final int DEFAULT_WEB_PORT = 8080;
    public static final int DEFAULT_PORT_PROBE_LIMIT = 20;

    private final Javalin app;
    private final WebController controller;
    private final AuthService authService;
    private volatile int actualPort;
    private volatile String boundBaseUrl = "http://localhost:" + DEFAULT_WEB_PORT;

    public WebApplication() {
        this(new FlightService(), new SeatMapService(), new BookingService(), new AuthService());
    }

    public WebApplication(FlightService flightService, SeatMapService seatMapService,
                          BookingService bookingService, AuthService authService) {
        this(flightService, seatMapService, bookingService, authService, new LoginThrottle());
    }

    public WebApplication(FlightService flightService, SeatMapService seatMapService,
                          BookingService bookingService, AuthService authService,
                          LoginThrottle loginThrottle) {
        this(flightService, seatMapService, bookingService, authService, loginThrottle,
                new RegistrationService(new UserDaoImpl(), EmailServiceFactory.createDefault()),
                new RegistrationThrottle());
    }

    public WebApplication(FlightService flightService, SeatMapService seatMapService,
                          BookingService bookingService, AuthService authService,
                          LoginThrottle loginThrottle, RegistrationService registrationService,
                          RegistrationThrottle registrationThrottle) {
        this(flightService, seatMapService, bookingService, authService, loginThrottle,
                registrationService, registrationThrottle,
                new SimulatedPaymentGateway(), new PaymentThrottle());
    }

    public WebApplication(FlightService flightService, SeatMapService seatMapService,
                          BookingService bookingService, AuthService authService,
                          LoginThrottle loginThrottle, RegistrationService registrationService,
                          RegistrationThrottle registrationThrottle,
                          PaymentService paymentService, PaymentThrottle paymentThrottle) {
        this(flightService, seatMapService, bookingService, authService, loginThrottle,
                registrationService, registrationThrottle, paymentService, paymentThrottle,
                new BoardingPassVerificationService(bookingService), new VerifyThrottle());
    }

    public WebApplication(FlightService flightService, SeatMapService seatMapService,
                          BookingService bookingService, AuthService authService,
                          LoginThrottle loginThrottle, RegistrationService registrationService,
                          RegistrationThrottle registrationThrottle,
                          PaymentService paymentService, PaymentThrottle paymentThrottle,
                          BoardingPassVerificationService verificationService, VerifyThrottle verifyThrottle) {
        this(flightService, seatMapService, bookingService, authService, loginThrottle,
                registrationService, registrationThrottle, paymentService, paymentThrottle,
                verificationService, verifyThrottle, new AdminDashboardService());
    }

    public WebApplication(FlightService flightService, SeatMapService seatMapService,
                          BookingService bookingService, AuthService authService,
                          LoginThrottle loginThrottle, RegistrationService registrationService,
                          RegistrationThrottle registrationThrottle,
                          PaymentService paymentService, PaymentThrottle paymentThrottle,
                          BoardingPassVerificationService verificationService, VerifyThrottle verifyThrottle,
                          AdminDashboardService adminDashboardService) {
        this.authService = authService;
        this.controller = new WebController(flightService, seatMapService, bookingService, authService,
                loginThrottle, registrationService, registrationThrottle,
                paymentService, paymentThrottle, verificationService, verifyThrottle,
                adminDashboardService);

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
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/static";
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

    WebController getController() {
        return controller;
    }

    public void start(int port) {
        app.start(port);
        this.actualPort = app.port();
        this.boundBaseUrl = "http://localhost:" + this.actualPort;
        this.controller.setBoundBaseUrl(this.boundBaseUrl);
        logger.info("Sky Royal Web Application started at {}", this.boundBaseUrl);
        logAuthSeedStatus();
    }

    public int getActualPort() {
        return actualPort;
    }

    public String getBoundBaseUrl() {
        return boundBaseUrl;
    }

    public static int resolvePreferredPort(Map<String, String> env) {
        Integer port = parseValidPort(env != null ? env.get("SKY_ROYAL_WEB_PORT") : null);
        if (port != null) return port;
        port = parseValidPort(env != null ? env.get("PORT") : null);
        if (port != null) return port;
        return DEFAULT_WEB_PORT;
    }

    /**
     * Checks whether a TCP port is free on both 127.0.0.1 and wildcard 0.0.0.0.
     */
    public static boolean isPortAvailable(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        String[] bindAddrs = {"127.0.0.1", "0.0.0.0"};
        for (String addr : bindAddrs) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(InetAddress.getByName(addr), port), 1);
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the first available port starting at preferredPort within maxAttempts.
     */
    public static int findAvailablePort(int preferredPort, int maxAttempts) {
        int start = (preferredPort >= 1 && preferredPort <= 65535) ? preferredPort : DEFAULT_WEB_PORT;
        int attempts = Math.max(1, maxAttempts);
        for (int i = 0; i < attempts; i++) {
            int candidate = start + i;
            if (candidate > 65535) {
                break;
            }
            if (isPortAvailable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No free web server port found starting at " + start
                + " (tried " + attempts + " port(s)). Set SKY_ROYAL_WEB_PORT to a free port.");
    }

    public int startWithFallback(int preferredPort) {
        return startWithFallback(preferredPort, DEFAULT_PORT_PROBE_LIMIT);
    }

    public int startWithFallback(int preferredPort, int maxAttempts) {
        int chosen = findAvailablePort(preferredPort, maxAttempts);
        start(chosen);
        if (chosen != preferredPort) {
            logger.warn("Preferred port {} is busy; started on free port {} instead", preferredPort, chosen);
            System.out.println("[PORT] Port " + preferredPort + " is busy; using free port " + chosen + " instead.");
        }
        System.out.println("Sky Royal Website is running at: " + this.boundBaseUrl);
        return this.actualPort;
    }

    private static Integer parseValidPort(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int p = Integer.parseInt(raw.trim());
            if (p > 0 && p <= 65535) {
                return p;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private void logAuthSeedStatus() {
        try {
            boolean hasAdmin = authService.userExists("testadmin");
            boolean hasCustomer = authService.userExists("customer");
            logger.info("Auth seed check: testadmin={}, customer={}",
                    hasAdmin ? "present" : "missing",
                    hasCustomer ? "present" : "missing");
        } catch (Exception e) {
            logger.warn("Auth seed check skipped: {}", e.getMessage());
        }
    }

    public void stop() {
        app.stop();
        logger.info("Sky Royal Web Application stopped.");
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
                    Map<String, Object> model = controller.baseModel(ctx);
                    model.put("error", "Your session or security token has expired. Please try again.");
                    ctx.render("error.html", model);
                    ctx.skipRemainingHandlers();
                }
            }
        });
    }

    private void setupRoutes() {
        // Home
        app.get("/", controller::handleHome);

        // Flights Search & Results
        app.get("/flights", controller::handleFlights);

        // Seat Selection & Checkout
        app.get("/flights/{id}/seats", controller::handleSeats);

        // Book / Checkout & Pay
        app.post("/book", controller::handleBook);
        app.get("/checkout", controller::handleCheckoutGet);
        app.post("/pay", controller::handlePayPost);

        // Booking Confirmation, QR, Public Verify, My Bookings & Cancel
        app.get("/confirmation", controller::handleConfirmationLookup);
        app.get("/confirmation/{reference}", controller::handleConfirmation);
        app.get("/confirmation/{reference}/barcode.png", controller::handleConfirmationBarcode);
        app.get("/confirmation/{reference}/qr.png", controller::handleConfirmationQr);
        app.get("/confirmation/{reference}/boarding-pass.pdf", controller::handleConfirmationPdf);
        app.get("/verify/{pnr}", controller::handleVerifyGet);
        app.get("/my-bookings", controller::handleMyBookingsGet);
        app.get("/bookings/{reference}/cancel", controller::handleCancelGet);
        app.post("/bookings/{reference}/cancel", controller::handleCancelPost);

        // Authentication & Registration
        app.get("/login", controller::handleLoginGet);
        app.post("/login", controller::handleLoginPost);
        app.get("/register", controller::handleRegisterGet);
        app.post("/register", controller::handleRegisterPost);
        app.get("/logout", controller::handleLogout);

        // Informational Pages & Admin
        app.get("/about", controller::handleAbout);
        app.get("/contact", controller::handleContactGet);
        app.post("/contact", controller::handleContactPost);
        app.get("/admin", controller::handleAdmin);
    }

    private void setupErrorHandling() {
        app.error(404, ctx -> {
            if (ctx.path().startsWith("/verify/")) {
                return;
            }
            Map<String, Object> model = controller.baseModel(ctx);
            model.put("error", "The requested page (" + ctx.path() + ") could not be found.");
            ctx.render("error.html", model);
        });

        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Unhandled exception processing request to " + ctx.path(), e);
            ctx.status(500);
            Map<String, Object> model = controller.baseModel(ctx);
            model.put("error", "An unexpected error occurred while processing your request. Please return to Home.");
            ctx.render("error.html", model);
        });
    }

    public static void main(String[] args) {
        int preferredPort = resolvePreferredPort(System.getenv());
        WebApplication app = new WebApplication();
        app.startWithFallback(preferredPort);
    }
}
