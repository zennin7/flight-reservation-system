package com.group2.flightres.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirportNamesTest {

    @Test
    @DisplayName("AirportNames: shortName replaces ' International Airport' with ' Intl'")
    void testShortNameConversion() {
        assertEquals("O. R. Tambo Intl", AirportNames.shortName("O. R. Tambo International Airport"));
        assertEquals("Cape Town Intl", AirportNames.shortName("Cape Town International Airport"));
        assertEquals("King Shaka Intl", AirportNames.shortName("King Shaka International Airport"));
        assertEquals("Dubai Intl", AirportNames.shortName("Dubai International Airport"));
    }

    @Test
    @DisplayName("AirportNames: shortName safely handles unadorned names or null/empty")
    void testShortNameEdgeCases() {
        assertEquals("George Airport", AirportNames.shortName("George Airport"));
        assertEquals("", AirportNames.shortName(null));
        assertEquals("", AirportNames.shortName(""));
    }
}
