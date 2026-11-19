package com.group2.flightres.web;

import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.SeatMapService;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;
import java.util.UUID;

/**
 * Server-rendered web application for Sky Royal.
 * Uses Javalin 6 and Thymeleaf with shared core services.
 */
public class WebApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);

    private final Javalin app;
    private final WebController controller;

    public WebApplication() {
        this(new FlightService(), new SeatMapService(), new BookingService(), new AuthService());
    }

    public WebApplication(FlightService flightService, SeatMapService seatMapService,
                          BookingService bookingService, AuthService authService) {
        this.controller = new WebController(flightService, seatMapService, bookingService, authService);

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

    public void start(int port) {
        app.start(port);
        logger.info("Sky Royal Web Application started at http://localhost:{}", port);
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

        // Book / Confirm Booking
        app.post("/book", controller::handleBook);

        // Booking Confirmation & Lookup
        app.get("/confirmation", controller::handleConfirmationLookup);
        app.get("/confirmation/{reference}", controller::handleConfirmation);
        app.get("/confirmation/{reference}/barcode.png", controller::handleConfirmationBarcode);
        app.get("/confirmation/{reference}/boarding-pass.pdf", controller::handleConfirmationPdf);

        // Authentication
        app.get("/login", controller::handleLoginGet);
        app.post("/login", controller::handleLoginPost);
        app.get("/logout", controller::handleLogout);

        // Informational Pages
        app.get("/about", controller::handleAbout);
        app.get("/contact", controller::handleContactGet);
        app.post("/contact", controller::handleContactPost);
    }

    private void setupErrorHandling() {
        app.error(404, ctx -> {
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
