package com.group2.flightres.service;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.impl.SeatDaoImpl;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.util.DatabaseAssumption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingServiceProofTest {

    private static final Logger logger = LoggerFactory.getLogger(PricingServiceProofTest.class);

    @BeforeEach
    void checkDb() {
        DatabaseAssumption.assumeDatabaseAvailable();
    }

    @Test
    @DisplayName("PricingService proof: real quotes for 3 ALT flights tomorrow in SAST for ECONOMY and FIRST")
    void testLivePricingProofTomorrowFlights() throws SQLException {
        PricingService pricingService = new PricingService(new SeatDaoImpl(), Clock.systemUTC());

        String sql = """
            SELECT flight_id,
                   flight_number,
                   departure_time AT TIME ZONE 'Africa/Johannesburg' AS dep_sast,
                   departure_time,
                   base_price
            FROM flights
            WHERE (departure_time AT TIME ZONE 'Africa/Johannesburg')::date = (NOW() AT TIME ZONE 'Africa/Johannesburg')::date + 1
              AND flight_number LIKE 'ALT%'
            ORDER BY departure_time
            LIMIT 3;
            """;

        int flightCount = 0;
        Instant now = Instant.now();

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                flightCount++;
                long flightId = rs.getLong("flight_id");
                String flightNo = rs.getString("flight_number");
                String depSast = rs.getString("dep_sast");
                Timestamp depTs = rs.getTimestamp("departure_time");
                Instant depInstant = depTs.toInstant();

                double hoursToDep = Duration.between(now, depInstant).toSeconds() / 3600.0;

                // 1. Cheapest ECONOMY quote
                PriceQuote econQuote = pricingService.quote(flightId, SeatClass.ECONOMY);
                assertNotNull(econQuote, "Economy quote must not be null for flight " + flightNo);
                printQuoteLine(flightNo, depSast, SeatClass.ECONOMY, hoursToDep, econQuote);

                // 2. FIRST seat quote
                PriceQuote firstQuote = pricingService.quote(flightId, SeatClass.FIRST);
                assertNotNull(firstQuote, "First class quote must not be null for flight " + flightNo);
                printQuoteLine(flightNo, depSast, SeatClass.FIRST, hoursToDep, firstQuote);

                // Arithmetic integrity proof
                assertEquals(econQuote.total(),
                        econQuote.classFare().add(econQuote.demandAdjustment()).add(econQuote.timingAdjustment()),
                        "Quote arithmetic must balance for " + flightNo + " ECONOMY");
                assertEquals(firstQuote.total(),
                        firstQuote.classFare().add(firstQuote.demandAdjustment()).add(firstQuote.timingAdjustment()),
                        "Quote arithmetic must balance for " + flightNo + " FIRST");
            }
        }

        assertTrue(flightCount >= 3, "Expected at least 3 ALT flights tomorrow in SAST, found: " + flightCount);
    }

    private void printQuoteLine(String flightNo, String depSast, SeatClass seatClass, double hoursToDep, PriceQuote quote) {
        String demandTierDesc = quote.demandMultiplier().compareTo(BigDecimal.ONE) == 0 ? "<40% occupancy" :
                (quote.demandMultiplier().compareTo(new BigDecimal("1.05")) == 0 ? "40-60% occupancy" :
                (quote.demandMultiplier().compareTo(new BigDecimal("1.12")) == 0 ? "60-80% occupancy" : ">=80% occupancy"));

        String timingTierDesc = quote.timingMultiplier().compareTo(new BigDecimal("0.95")) == 0 ? ">=240h (10+ days)" :
                (quote.timingMultiplier().compareTo(new BigDecimal("1.00")) == 0 ? "120-240h (5-10 days)" :
                (quote.timingMultiplier().compareTo(new BigDecimal("1.07")) == 0 ? "48-120h (2-5 days)" :
                (quote.timingMultiplier().compareTo(new BigDecimal("1.12")) == 0 ? "24-48h (1-2 days)" : "<24h (last minute)")));

        String out = String.format(Locale.US,
                "[PRICING_PROOF] Flight: %-7s | Dep SAST: %-19s | Class: %-7s | HoursToDep: %5.1fh | DemandTier: %s (x%s) | TimingTier: %s (x%s) | Base: R %s | ClassMult: %s | ClassFare: R %s | DemandAdj: +R %s | TimingAdj: +R %s | Total: R %s | VAT(15%% incl): R %s",
                flightNo, depSast, seatClass, hoursToDep,
                demandTierDesc, quote.demandMultiplier(),
                timingTierDesc, quote.timingMultiplier(),
                quote.baseFare(), quote.classMultiplier(), quote.classFare(),
                quote.demandAdjustment(), quote.timingAdjustment(),
                quote.total(), quote.vatIncluded());

        System.out.println(out);
        logger.info(out);
    }
}
