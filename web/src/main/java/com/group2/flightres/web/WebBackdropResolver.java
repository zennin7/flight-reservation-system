package com.group2.flightres.web;

import com.group2.flightres.model.SeatClass;
import com.group2.flightres.util.DestinationPhotoResolver;

import java.util.List;

/**
 * Assigns verified backdrop photography and rotation playlists per web route.
 */
public final class WebBackdropResolver {

    public static final String FIXED_AERIAL = "/photos/aerial-1.jpg";
    public static final String CABIN_ECONOMY = "/photos/cabin-economy-1.jpg";
    public static final String CABIN_FIRST = "/photos/cabin-first-1.jpg";
    public static final String SKY_WING = "/photos/sky-wing-1.jpg";
    public static final String SKY_ENGINE = "/photos/sky-engine-1.jpg";
    public static final String SKY_WINDOW_PEAKS = "/photos/sky-window-peaks.jpg";
    public static final String SKY_JET_CLOUDS = "/photos/sky-jet-clouds.jpg";
    public static final String SKY_JET_OCEAN = "/photos/sky-jet-ocean.jpg";
    public static final String SKY_JET_DARK = "/photos/sky-jet-dark.jpg";
    public static final String TARMAC_JET = "/photos/tarmac-jet-1.jpg";
    public static final String CABIN_MOOD = "/photos/cabin-mood-1.jpg";
    public static final String CABIN_SUITE = "/photos/cabin-suite-1.jpg";

    /**
     * Crisp aviation and destination photos for Home / Auth backdrop rotation.
     */
    public static final List<String> ROTATING_PHOTOS = List.of(
            "/photos/aerial-1.jpg",
            "/photos/sky-jet-clouds.jpg",
            "/photos/sky-wing-1.jpg",
            "/photos/sky-engine-1.jpg",
            "/photos/sky-jet-ocean.jpg",
            "/photos/sky-jet-dark.jpg",
            "/photos/tarmac-jet-1.jpg",
            "/photos/sky-window-peaks.jpg",
            "/photos/cabin-mood-1.jpg",
            "/photos/cabin-suite-1.jpg",
            "/photos/aerial-2.jpg",
            "/photos/dest-capetown.jpg"
    );

    public static final String ROTATING_PHOTOS_CSV = String.join(",", ROTATING_PHOTOS);

    private WebBackdropResolver() {
    }

    public static String destinationBackdrop(String airportCodeOrLabel) {
        return DestinationPhotoResolver.resolveWebPath(airportCodeOrLabel);
    }

    public static String cabinBackdrop(SeatClass seatClass) {
        return seatClass == SeatClass.FIRST ? CABIN_FIRST : CABIN_ECONOMY;
    }
}
