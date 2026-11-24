package com.group2.flightres.util;

import com.group2.flightres.model.SeatClass;
import javafx.scene.image.Image;

import java.net.URL;
import java.util.Optional;

/**
 * Desktop JavaFX adapter for destination, aerial, and cabin photography.
 * Uses core DestinationPhotoResolver and bundled Sky Royal optimized photos (<= 350 KB).
 */
public final class DestinationPhotos {

    public static final String AERIAL_PRIMARY = "/com/group2/flightres/photos/aerial-1.jpg";
    public static final String CABIN_ECONOMY = "/com/group2/flightres/photos/cabin-economy-1.jpg";
    public static final String CABIN_FIRST = "/com/group2/flightres/photos/cabin-first-1.jpg";

    private DestinationPhotos() {
    }

    public static Optional<Image> getImage(String airportCode) {
        Optional<URL> urlOpt = DestinationPhotoResolver.resolveUrl(airportCode);
        if (urlOpt.isPresent()) {
            Optional<Image> loaded = loadUrl(urlOpt.get());
            if (loaded.isPresent()) {
                return loaded;
            }
        }
        return loadResource(AERIAL_PRIMARY);
    }

    public static Optional<Image> getAerialImage() {
        return loadResource(AERIAL_PRIMARY);
    }

    public static Optional<Image> getCabinImage(SeatClass seatClass) {
        String path = (seatClass == SeatClass.FIRST) ? CABIN_FIRST : CABIN_ECONOMY;
        return loadResource(path);
    }

    public static Optional<Image> loadResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return Optional.empty();
        }
        URL url = DestinationPhotos.class.getResource(resourcePath);
        return url != null ? loadUrl(url) : Optional.empty();
    }

    private static Optional<Image> loadUrl(URL url) {
        try {
            return Optional.of(new Image(url.toExternalForm(), false));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
