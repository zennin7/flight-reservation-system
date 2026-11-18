package com.group2.flightres.util;

import javafx.scene.image.Image;

import java.net.URL;
import java.util.Optional;

/**
 * Desktop JavaFX adapter for destination photography.
 * Uses core DestinationPhotoResolver and provides cached or loaded JavaFX Image instances.
 */
public final class DestinationPhotos {

    private DestinationPhotos() {
        // Prevent instantiation
    }

    /**
     * Loads a JavaFX Image for the given airport code.
     *
     * @param airportCode 3-letter IATA code (e.g. "CPT", "JNB", "DUR")
     * @return Optional containing the JavaFX Image, or empty if no photo exists for this code.
     */
    public static Optional<Image> getImage(String airportCode) {
        Optional<URL> urlOpt = DestinationPhotoResolver.resolveUrl(airportCode);
        if (urlOpt.isEmpty()) {
            return Optional.empty();
        }
        try {
            Image img = new Image(urlOpt.get().toExternalForm(), false);
            return Optional.of(img);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
