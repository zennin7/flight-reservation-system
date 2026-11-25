package com.group2.flightres.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DestinationPhotoResolverTest {

    @Test
    @DisplayName("CPT destination photo resolved via URL and bytes")
    void testCptFound() {
        Optional<URL> urlOpt = DestinationPhotoResolver.resolveUrl("CPT");
        assertTrue(urlOpt.isPresent(), "CPT resource URL must be found");
        assertTrue(urlOpt.get().getPath().toLowerCase().contains("cpt.jpg"));

        Optional<byte[]> bytesOpt = DestinationPhotoResolver.resolveBytes("CPT");
        assertTrue(bytesOpt.isPresent(), "CPT resource bytes must be present");
        assertTrue(bytesOpt.get().length > 10000, "CPT photo bytes should be non-trivial image");

        // Also test case-insensitivity and parenthesized format
        assertTrue(DestinationPhotoResolver.resolveUrl("cpt").isPresent());
        assertTrue(DestinationPhotoResolver.resolveUrl("Cape Town (CPT)").isPresent());
    }

    @Test
    @DisplayName("JNB destination photo resolved via URL and bytes")
    void testJnbFound() {
        Optional<URL> urlOpt = DestinationPhotoResolver.resolveUrl("JNB");
        assertTrue(urlOpt.isPresent(), "JNB resource URL must be found");
        assertTrue(urlOpt.get().getPath().toLowerCase().contains("jnb.jpg"));

        Optional<byte[]> bytesOpt = DestinationPhotoResolver.resolveBytes("JNB");
        assertTrue(bytesOpt.isPresent(), "JNB resource bytes must be present");
        assertTrue(bytesOpt.get().length > 10000, "JNB photo bytes should be non-trivial image");
    }

    @Test
    @DisplayName("Unknown airport code returns Optional.empty()")
    void testUnknownEmpty() {
        Optional<URL> urlOpt = DestinationPhotoResolver.resolveUrl("UNKNOWN");
        assertTrue(urlOpt.isEmpty(), "Unknown airport code must return empty URL");

        Optional<byte[]> bytesOpt = DestinationPhotoResolver.resolveBytes("UNKNOWN");
        assertTrue(bytesOpt.isEmpty(), "Unknown airport code must return empty bytes");
    }

    @Test
    @DisplayName("Null or blank airport code returns Optional.empty()")
    void testNullOrBlankEmpty() {
        assertTrue(DestinationPhotoResolver.resolveUrl(null).isEmpty());
        assertTrue(DestinationPhotoResolver.resolveUrl("").isEmpty());
        assertTrue(DestinationPhotoResolver.resolveUrl("   ").isEmpty());

        assertTrue(DestinationPhotoResolver.resolveBytes(null).isEmpty());
        assertTrue(DestinationPhotoResolver.resolveBytes("").isEmpty());
        assertTrue(DestinationPhotoResolver.resolveBytes("   ").isEmpty());
    }

    @Test
    @DisplayName("All 13 database airports resolve non-empty photo bytes and fallback covers PLZ/GRJ/unknown")
    void testAllDbAirportsAndAerialFallback() {
        assertEquals(13, DestinationPhotoResolver.knownAirportCodes().size());
        assertEquals(java.util.Set.of("GRJ"), DestinationPhotoResolver.airportsWithoutDedicatedPhoto());

        for (String code : DestinationPhotoResolver.knownAirportCodes()) {
            Optional<URL> urlOpt = DestinationPhotoResolver.resolveUrl(code);
            assertTrue(urlOpt.isPresent(), "Expected photo URL for known airport " + code);
            Optional<byte[]> bytesOpt = DestinationPhotoResolver.resolveBytes(code);
            assertTrue(bytesOpt.isPresent() && bytesOpt.get().length > 5000,
                    "Expected valid photo bytes for known airport " + code);
            String webPath = DestinationPhotoResolver.resolveWebPath(code);
            assertTrue(webPath.startsWith("/photos/") && webPath.endsWith(".jpg"),
                    "Invalid web path for " + code + ": " + webPath);
        }

        assertTrue(DestinationPhotoResolver.hasDedicatedPhoto("PLZ"));
        assertEquals("/photos/dest-gqeberha.jpg", DestinationPhotoResolver.resolveWebPath("PLZ"));
        assertTrue(DestinationPhotoResolver.resolveUrl("PLZ").orElseThrow().getPath().contains("plz.jpg"));
        assertFalse(DestinationPhotoResolver.hasDedicatedPhoto("GRJ"));
        assertEquals(DestinationPhotoResolver.WEB_AERIAL_FALLBACK, DestinationPhotoResolver.resolveWebPath("GRJ"));

        assertTrue(DestinationPhotoResolver.resolveUrlOrFallback("UNKNOWN").isPresent());
        assertTrue(DestinationPhotoResolver.resolveBytesOrFallback("UNKNOWN").orElseThrow().length > 5000);
        assertEquals(DestinationPhotoResolver.WEB_AERIAL_FALLBACK, DestinationPhotoResolver.resolveWebPath("UNKNOWN"));
    }
}
