package com.group2.flightres.controller;

import com.group2.flightres.exception.SeatUnavailableException;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.SimulatedPaymentGateway;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutControllerTest {

    @BeforeAll
    static void initJavaFX() {
        System.setProperty("altitude.testing", "true");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    private BookingContext createTestContext() {
        BookingContext ctx = new BookingContext();
        ctx.setFlightId(1L);
        ctx.setFlightNumber("FL 204");
        ctx.setOrigin("JNB");
        ctx.setDestination("CPT");
        ctx.setDepartureDate("2026-11-14");
        ctx.setDepartureTime("06:30");
        ctx.setArrivalTime("08:40");

        FlightSeat seat = new FlightSeat();
        seat.setFlightSeatId(101L);
        seat.setSeatRow(9);
        seat.setSeatCol('E');
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setStatus(SeatStatus.HELD);
        seat.setCalculatedPrice(new BigDecimal("1200.00"));
        seat.setHeldUntil(Instant.now().plusSeconds(300));

        ctx.setSelectedSeats(new ArrayList<>(List.of(seat)));
        ctx.setBaseFare(new BigDecimal("1200.00"));
        ctx.setDemandPricing(new BigDecimal("180.00"));
        ctx.setTaxesAndFees(new BigDecimal("142.00"));
        ctx.recalculateTotals();
        ctx.setHoldExpiresAt(seat.getHeldUntil());

        return ctx;
    }

    private void fillFormFields(CheckoutController controller) {
        if (controller.getFirstNameField() != null) controller.getFirstNameField().setText("John");
        if (controller.getLastNameField() != null) controller.getLastNameField().setText("Smith");
        if (controller.getPassportField() != null) controller.getPassportField().setText("P12345678");
        if (controller.getDobField() != null) controller.getDobField().setText("1990-05-15");
        if (controller.getEmailField() != null) controller.getEmailField().setText("john.smith@example.com");

        if (controller.getCardNumberField() != null) controller.getCardNumberField().setText("4242 4242 4242 4242");
        if (controller.getExpiryField() != null) controller.getExpiryField().setText("08 / 28");
        if (controller.getCvvField() != null) controller.getCvvField().setText("123");
    }

    @Test
    @DisplayName("SeatUnavailableException after payment approval triggers refund and navigation back")
    void testRefundOnSeatUnavailableException() throws Exception {
        BookingService conflictService = new BookingService() {
            @Override
            public BookingRecord createBooking(BookingContext context) {
                throw new SeatUnavailableException("Seat 9E was taken during checkout.");
            }
        };

        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
        CheckoutController controller = new CheckoutController(conflictService, gateway);

        AtomicBoolean nextCalled = new AtomicBoolean(false);
        AtomicBoolean backCalled = new AtomicBoolean(false);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
        loader.setControllerFactory(t -> controller);
        Parent root = loader.load();

        BookingContext ctx = createTestContext();
        controller.init(ctx, () -> nextCalled.set(true), () -> backCalled.set(true));
        fillFormFields(controller);

        controller.handleConfirm();

        assertFalse(nextCalled.get(), "Should not navigate forward on seat conflict");
        assertTrue(backCalled.get(), "Should navigate back on seat conflict");
        assertTrue(controller.getCardNumberField().getText().isEmpty(), "Card number should be cleared");
        assertTrue(controller.getExpiryField().getText().isEmpty(), "Expiry should be cleared");
        assertTrue(controller.getCvvField().getText().isEmpty(), "CVV should be cleared");

        // Verify refund was called in gateway
        assertNotNullRefund(gateway, ctx);
    }

    @Test
    @DisplayName("Database failure after payment approval triggers refund, error alert, and stays on screen")
    void testRefundOnDatabaseFailure() throws Exception {
        BookingService dbFailService = new BookingService() {
            @Override
            public BookingRecord createBooking(BookingContext context) {
                throw new RuntimeException("Simulated DB connection lost");
            }
        };

        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
        CheckoutController controller = new CheckoutController(dbFailService, gateway);

        AtomicBoolean nextCalled = new AtomicBoolean(false);
        AtomicBoolean backCalled = new AtomicBoolean(false);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
        loader.setControllerFactory(t -> controller);
        Parent root = loader.load();

        BookingContext ctx = createTestContext();
        controller.init(ctx, () -> nextCalled.set(true), () -> backCalled.set(true));
        fillFormFields(controller);

        controller.handleConfirm();

        assertFalse(nextCalled.get(), "Should not navigate forward on DB failure");
        assertFalse(backCalled.get(), "Should stay on screen on DB failure");
        assertTrue(controller.getCardNumberField().getText().isEmpty(), "Card number should be cleared");

        // Verify refund was called in gateway
        assertNotNullRefund(gateway, ctx);
    }

    private void assertNotNullRefund(SimulatedPaymentGateway gateway, BookingContext ctx) {
        assertTrue(ctx.getPaymentResult() != null, "PaymentResult should be present in context");
        String txnRef = ctx.getPaymentResult().getTransactionReference();
        assertTrue(txnRef != null && !txnRef.isBlank(), "Transaction reference should be set");
        assertTrue(gateway.isRefunded(txnRef), "Transaction " + txnRef + " must be marked refunded");
    }

    @Test
    @DisplayName("Destination city is populated from BookingContext destinationCity")
    void testDestinationCityFromContext() throws Exception {
        BookingService dummyService = new BookingService();
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
        CheckoutController controller = new CheckoutController(dummyService, gateway);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
        loader.setControllerFactory(t -> controller);
        loader.load();

        BookingContext ctx = createTestContext();
        ctx.setDestinationCity("Durban");
        controller.init(ctx, () -> {}, () -> {});

        assertEquals("Durban", controller.getTripOriginDestLabel().getText());
    }

    @Test
    @DisplayName("Destination city returns airport code unchanged if destinationCity is blank")
    void testDestinationCityBlankReturnsCode() throws Exception {
        BookingService dummyService = new BookingService();
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
        CheckoutController controller = new CheckoutController(dummyService, gateway);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/group2/flightres/fxml/checkout.fxml"));
        loader.setControllerFactory(t -> controller);
        loader.load();

        BookingContext ctx = createTestContext();
        ctx.setDestination("DUR");
        ctx.setDestinationCity(null);
        controller.init(ctx, () -> {}, () -> {});

        assertEquals("DUR", controller.getTripOriginDestLabel().getText());
    }
}
