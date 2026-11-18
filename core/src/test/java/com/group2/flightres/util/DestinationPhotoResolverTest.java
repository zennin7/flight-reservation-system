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
}
