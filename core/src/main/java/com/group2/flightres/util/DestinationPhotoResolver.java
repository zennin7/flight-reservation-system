package com.group2.flightres.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;

/**
 * Shared resolver for destination photography across core PDF generation,
 * JavaFX desktop components, and web endpoints.
 * Resolves images from classpath resource path: /images/destinations/<code_lower>.jpg.
 */
public final class DestinationPhotoResolver {

    public static final String RESOURCE_BASE_PATH = "/images/destinations/";

    private DestinationPhotoResolver() {
        // Prevent instantiation
    }

    /**
     * Resolves the classpath resource URL for the given airport code.
     *
     * @param airportCode 3-letter IATA airport code (e.g. "CPT", "JNB", "DUR")
     * @return Optional containing the resource URL, or empty if null/blank or not found.
     */
    public static Optional<URL> resolveUrl(String airportCode) {
        if (airportCode == null || airportCode.isBlank()) {
            return Optional.empty();
        }
        String cleanCode = extractCode(airportCode);
        String path = RESOURCE_BASE_PATH + cleanCode.toLowerCase(Locale.ROOT) + ".jpg";
        URL resource = DestinationPhotoResolver.class.getResource(path);
        return Optional.ofNullable(resource);
    }

    /**
     * Resolves and reads the raw image bytes for the given airport code.
     * Safely closes the underlying stream using try-with-resources to prevent stream leaks.
     *
     * @param airportCode 3-letter IATA airport code
     * @return Optional containing the raw byte array, or empty if null/blank or not found.
     */
    public static Optional<byte[]> resolveBytes(String airportCode) {
        if (airportCode == null || airportCode.isBlank()) {
            return Optional.empty();
        }
        String cleanCode = extractCode(airportCode);
        String path = RESOURCE_BASE_PATH + cleanCode.toLowerCase(Locale.ROOT) + ".jpg";
        try (InputStream is = DestinationPhotoResolver.class.getResourceAsStream(path)) {
            if (is == null) {
                return Optional.empty();
            }
            return Optional.of(is.readAllBytes());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Sanitizes input to extract the 3-letter airport code if an airport string like "Cape Town (CPT)" was provided.
     */
    private static String extractCode(String input) {
        String trimmed = input.trim();
        int openParen = trimmed.lastIndexOf('(');
        int closeParen = trimmed.lastIndexOf(')');
        if (openParen >= 0 && closeParen > openParen) {
            return trimmed.substring(openParen + 1, closeParen).trim();
        }
        return trimmed;
    }
}
