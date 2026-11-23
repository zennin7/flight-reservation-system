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

    /**
     * At most 6 crisp landscape aerial and destination photos for Home / Auth rotation.
     */
    public static final List<String> ROTATING_PHOTOS = List.of(
            "/photos/aerial-1.jpg",
            "/photos/aerial-2.jpg",
            "/photos/dest-capetown.jpg",
            "/photos/dest-swiss-alps.jpg",
            "/photos/dest-santorini.jpg",
            "/photos/dest-dubai.jpg"
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
