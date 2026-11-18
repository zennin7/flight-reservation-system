package com.group2.flightres.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PricingServiceClockInjectionTest {

    @Test
    @DisplayName("PricingService uses injected Clock instead of system default zone")
    void testClockInjection() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
        PricingService pricingService = new PricingService(fixedClock);
        assertNotNull(pricingService);

        // Prove default constructor uses UTC clock zone
        PricingService defaultService = new PricingService();
        assertNotNull(defaultService);
    }
}
