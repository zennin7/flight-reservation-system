package com.group2.flightres.web;

import com.group2.flightres.model.SeatClass;
import com.group2.flightres.util.DestinationPhotoResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Assigns verified backdrop photography, per-section cabin photos (loaded from
 * {@code /cabin-sections.properties}), and full generic rotation playlists per web route.
 */
public final class WebBackdropResolver {

    private static final String PHOTOS_PREFIX = "/photos/";
    private static final String CABIN_CONFIG_RESOURCE = "/cabin-sections.properties";

    private static final Map<Integer, String> SECTION_CABIN_PHOTOS;
    private static final Map<String, String> CLASS_CABIN_PHOTOS;
    private static final String CABIN_FALLBACK;

    static {
        Properties props = new Properties();
        try (InputStream is = WebBackdropResolver.class.getResourceAsStream(CABIN_CONFIG_RESOURCE)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException ignored) {
        }
        String fbFile = props.getProperty("fallback", "cabin-economy-1.jpg").trim();
        CABIN_FALLBACK = toWebPath(fbFile);

        Map<Integer, String> secMap = new LinkedHashMap<>();
        Map<String, String> clsMap = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            String val = props.getProperty(key, "").trim();
            if (val.isEmpty()) continue;
            if (key.startsWith("section.")) {
                try {
                    int idx = Integer.parseInt(key.substring("section.".length()));
                    secMap.put(idx, toWebPath(val));
                } catch (NumberFormatException ignored) {
                }
            } else if (key.startsWith("cabin.")) {
                clsMap.put(key.substring("cabin.".length()).toUpperCase(), toWebPath(val));
            }
        }
        SECTION_CABIN_PHOTOS = Collections.unmodifiableMap(secMap);
        CLASS_CABIN_PHOTOS = Collections.unmodifiableMap(clsMap);
    }

    public static final String FIXED_AERIAL = "/photos/aerial-1.jpg";
    public static final String CABIN_ECONOMY = CLASS_CABIN_PHOTOS.getOrDefault("ECONOMY", CABIN_FALLBACK);
    public static final String CABIN_FIRST = CLASS_CABIN_PHOTOS.getOrDefault("FIRST", CABIN_FALLBACK);
    public static final String CABIN_SECTION_2 = SECTION_CABIN_PHOTOS.getOrDefault(2, CABIN_FALLBACK);

    public static final String HERO_PRIMARY = "/photos/home-hero.jpg";
    public static final String WING_SUNSET = "/photos/tom-barrett-M0AWNxnLaMw-unsplash.jpg";
    public static final String WING_MOUNTAINS = "/photos/sam-mgrdichian-K-xpEUtdtGY-unsplash.jpg";
    public static final String CITY_OVERFLIGHT = "/photos/thanuj-mathew-BWmxrpY9h3M-unsplash.jpg";
    public static final String NIGHT_COAST = "/photos/evgeni-tcherkasski-_-tEJGpBS5w-unsplash.jpg";

    /**
     * Full generic aviation and cabin rotation list used by Home / Auth idle backdrop rotation.
     * Every non-destination photo in /public/photos/ appears here so zero generic photos are unused.
     */
    public static final List<String> ROTATING_PHOTOS = List.of(
            FIXED_AERIAL,
            "/photos/aerial-2.jpg",
            "/photos/aerial-3.jpg",
            HERO_PRIMARY,
            WING_SUNSET,
            WING_MOUNTAINS,
            CITY_OVERFLIGHT,
            NIGHT_COAST,
            CABIN_FIRST,
            CABIN_SECTION_2,
            CABIN_ECONOMY
    );

    public static final String ROTATING_PHOTOS_CSV = String.join(",", ROTATING_PHOTOS);

    private WebBackdropResolver() {
    }

    private static String toWebPath(String fileName) {
        return fileName.startsWith("/") ? fileName : (PHOTOS_PREFIX + fileName);
    }

    public static String destinationBackdrop(String airportCodeOrLabel) {
        return DestinationPhotoResolver.resolveWebPath(airportCodeOrLabel);
    }

    public static String cabinBackdrop(SeatClass seatClass) {
        if (seatClass == null) {
            return CABIN_FALLBACK;
        }
        return CLASS_CABIN_PHOTOS.getOrDefault(seatClass.name(), CABIN_FALLBACK);
    }

    /**
     * Resolves the cabin photo for a 1-based seat map section index (1 = First, 2 = Mid-cabin, 3 = Economy),
     * falling back gracefully to the configured fallback cabin photo if a section has no explicit mapping.
     */
    public static String sectionCabinPhoto(int sectionIndex1Based) {
        String exact = SECTION_CABIN_PHOTOS.get(sectionIndex1Based);
        if (exact != null) {
            return exact;
        }
        if (sectionIndex1Based <= 1) {
            return CABIN_FIRST;
        }
        return CABIN_FALLBACK;
    }

    public static Map<Integer, String> allSectionCabinPhotos() {
        return SECTION_CABIN_PHOTOS;
    }

    /**
     * Returns every web photo path referenced by any rotation, cabin mapping, or destination mapping.
     */
    public static Set<String> allReferencedWebPaths() {
        Set<String> refs = new LinkedHashSet<>(ROTATING_PHOTOS);
        refs.add(FIXED_AERIAL);
        refs.add(CABIN_FALLBACK);
        refs.addAll(SECTION_CABIN_PHOTOS.values());
        refs.addAll(CLASS_CABIN_PHOTOS.values());
        for (String code : DestinationPhotoResolver.knownAirportCodes()) {
            refs.add(DestinationPhotoResolver.resolveWebPath(code));
        }
        return Collections.unmodifiableSet(refs);
    }
}
