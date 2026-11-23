package com.group2.flightres.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared resolver for destination photography across core PDF generation,
 * JavaFX desktop components, and web endpoints.
 * Covers every airport in the airports table with an explicit generic aerial fallback.
 */
public final class DestinationPhotoResolver {

    public static final String RESOURCE_BASE_PATH = "/images/destinations/";
    public static final String AERIAL_FALLBACK_RESOURCE = RESOURCE_BASE_PATH + "aerial-fallback.jpg";
    public static final String WEB_AERIAL_FALLBACK = "/photos/aerial-1.jpg";

    private static final Map<String, String> DEDICATED_WEB_PATHS = Map.ofEntries(
            Map.entry("JNB", "/photos/dest-johannesburg.jpg"),
            Map.entry("CPT", "/photos/dest-capetown.jpg"),
            Map.entry("DUR", "/photos/dest-durban.jpg"),
            Map.entry("DXB", "/photos/dest-dubai.jpg"),
            Map.entry("FCO", "/photos/dest-rome.jpg"),
            Map.entry("ZRH", "/photos/dest-swiss-alps.jpg"),
            Map.entry("MXP", "/photos/dest-lake-como.jpg"),
            Map.entry("JTR", "/photos/dest-santorini.jpg"),
            Map.entry("NCE", "/photos/dest-monaco.jpg"),
            Map.entry("PSA", "/photos/dest-cinque-terre.jpg"),
            Map.entry("GVA", "/photos/dest-valais.jpg")
    );

    private static final Set<String> FALLBACK_AIRPORT_CODES = Set.of("PLZ", "GRJ");

    private static final Set<String> KNOWN_AIRPORT_CODES = Set.of(
            "JNB", "CPT", "DUR", "PLZ", "GRJ", "DXB", "FCO", "ZRH", "MXP", "JTR", "NCE", "PSA", "GVA"
    );

    private DestinationPhotoResolver() {
        // Prevent instantiation
    }

    /**
     * All airport IATA codes expected in the Sky Royal database airports table.
     */
    public static Set<String> knownAirportCodes() {
        return KNOWN_AIRPORT_CODES;
    }

    /**
     * Airports in the database that currently rely on the shared aerial fallback image.
     */
    public static Set<String> airportsWithoutDedicatedPhoto() {
        return FALLBACK_AIRPORT_CODES;
    }

    /**
     * Returns true if the airport has a dedicated destination photo rather than aerial fallback.
     */
    public static boolean hasDedicatedPhoto(String airportCode) {
        String code = extractUpperCode(airportCode);
        return code != null && DEDICATED_WEB_PATHS.containsKey(code);
    }

    /**
     * Resolves the web public path (/photos/...) for any airport code, falling back to /photos/aerial-1.jpg.
     */
    public static String resolveWebPath(String airportCode) {
        String code = extractUpperCode(airportCode);
        if (code != null && DEDICATED_WEB_PATHS.containsKey(code)) {
            return DEDICATED_WEB_PATHS.get(code);
        }
        return WEB_AERIAL_FALLBACK;
    }

    /**
     * Resolves the classpath resource URL for known airports (using aerial-fallback.jpg for PLZ/GRJ),
     * or empty for null/blank/unknown airport codes.
     */
    public static Optional<URL> resolveUrl(String airportCode) {
        String path = resolveResourcePathForKnown(airportCode);
        if (path == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DestinationPhotoResolver.class.getResource(path));
    }

    /**
     * Resolves the classpath resource URL for any airport code, returning the aerial fallback if unknown.
     */
    public static Optional<URL> resolveUrlOrFallback(String airportCode) {
        Optional<URL> direct = resolveUrl(airportCode);
        if (direct.isPresent()) {
            return direct;
        }
        return Optional.ofNullable(DestinationPhotoResolver.class.getResource(AERIAL_FALLBACK_RESOURCE));
    }

    /**
     * Resolves and reads the raw image bytes for known airport codes (using aerial fallback for PLZ/GRJ),
     * or empty for null/blank/unknown codes.
     */
    public static Optional<byte[]> resolveBytes(String airportCode) {
        String path = resolveResourcePathForKnown(airportCode);
        if (path == null) {
            return Optional.empty();
        }
        return readClasspathBytes(path);
    }

    /**
     * Resolves image bytes for any airport code, returning the aerial fallback if unknown or blank.
     */
    public static Optional<byte[]> resolveBytesOrFallback(String airportCode) {
        Optional<byte[]> direct = resolveBytes(airportCode);
        if (direct.isPresent()) {
            return direct;
        }
        return readClasspathBytes(AERIAL_FALLBACK_RESOURCE);
    }

    private static String resolveResourcePathForKnown(String airportCode) {
        String code = extractUpperCode(airportCode);
        if (code == null || !KNOWN_AIRPORT_CODES.contains(code)) {
            return null;
        }
        if (FALLBACK_AIRPORT_CODES.contains(code)) {
            return AERIAL_FALLBACK_RESOURCE;
        }
        return RESOURCE_BASE_PATH + code.toLowerCase(Locale.ROOT) + ".jpg";
    }

    private static Optional<byte[]> readClasspathBytes(String path) {
        try (InputStream is = DestinationPhotoResolver.class.getResourceAsStream(path)) {
            if (is == null) {
                return Optional.empty();
            }
            return Optional.of(is.readAllBytes());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String extractUpperCode(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        int openParen = trimmed.lastIndexOf('(');
        int closeParen = trimmed.lastIndexOf(')');
        if (openParen >= 0 && closeParen > openParen) {
            trimmed = trimmed.substring(openParen + 1, closeParen).trim();
        }
        return trimmed.isBlank() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
