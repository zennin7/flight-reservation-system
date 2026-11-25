package com.group2.flightres.util;

import com.group2.flightres.model.SeatClass;
import javafx.scene.image.Image;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Desktop JavaFX adapter for destination, aerial, and per-section cabin photography.
 * Loads cabin section mappings from {@code /com/group2/flightres/cabin-sections.properties}
 * (shared mapping with the web module) and bounds its in-memory JavaFX image cache to 6 images.
 */
public final class DestinationPhotos {

    public static final String PHOTOS_PREFIX = "/com/group2/flightres/photos/";
    private static final String CABIN_CONFIG_RESOURCE = "/com/group2/flightres/cabin-sections.properties";
    public static final int MAX_IMAGE_CACHE = 6;

    private static final Map<Integer, String> SECTION_CABIN_PHOTOS;
    private static final Map<String, String> CLASS_CABIN_PHOTOS;
    private static final String CABIN_FALLBACK;

    static {
        Properties props = new Properties();
        try (InputStream is = DestinationPhotos.class.getResourceAsStream(CABIN_CONFIG_RESOURCE)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException ignored) {
        }
        String fbFile = props.getProperty("fallback", "cabin-economy-1.jpg").trim();
        CABIN_FALLBACK = toDesktopPath(fbFile);

        Map<Integer, String> secMap = new LinkedHashMap<>();
        Map<String, String> clsMap = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            String val = props.getProperty(key, "").trim();
            if (val.isEmpty()) continue;
            if (key.startsWith("section.")) {
                try {
                    int idx = Integer.parseInt(key.substring("section.".length()));
                    secMap.put(idx, toDesktopPath(val));
                } catch (NumberFormatException ignored) {
                }
            } else if (key.startsWith("cabin.")) {
                clsMap.put(key.substring("cabin.".length()).toUpperCase(), toDesktopPath(val));
            }
        }
        SECTION_CABIN_PHOTOS = Collections.unmodifiableMap(secMap);
        CLASS_CABIN_PHOTOS = Collections.unmodifiableMap(clsMap);
    }

    public static final String AERIAL_PRIMARY = PHOTOS_PREFIX + "aerial-1.jpg";
    public static final String CABIN_ECONOMY = CLASS_CABIN_PHOTOS.getOrDefault("ECONOMY", CABIN_FALLBACK);
    public static final String CABIN_FIRST = CLASS_CABIN_PHOTOS.getOrDefault("FIRST", CABIN_FALLBACK);
    public static final String CABIN_SECTION_2 = SECTION_CABIN_PHOTOS.getOrDefault(2, CABIN_FALLBACK);

    public static final String HERO_PRIMARY = PHOTOS_PREFIX + "home-hero.jpg";
    public static final String WING_SUNSET = PHOTOS_PREFIX + "tom-barrett-M0AWNxnLaMw-unsplash.jpg";
    public static final String WING_MOUNTAINS = PHOTOS_PREFIX + "sam-mgrdichian-K-xpEUtdtGY-unsplash.jpg";
    public static final String CITY_OVERFLIGHT = PHOTOS_PREFIX + "thanuj-mathew-BWmxrpY9h3M-unsplash.jpg";
    public static final String NIGHT_COAST = PHOTOS_PREFIX + "evgeni-tcherkasski-_-tEJGpBS5w-unsplash.jpg";

    /**
     * Shared rotation list containing every generic aviation and cabin photo (matching web).
     */
    public static final List<String> ROTATING_PHOTOS = List.of(
            AERIAL_PRIMARY,
            PHOTOS_PREFIX + "aerial-2.jpg",
            PHOTOS_PREFIX + "aerial-3.jpg",
            HERO_PRIMARY,
            WING_SUNSET,
            WING_MOUNTAINS,
            CITY_OVERFLIGHT,
            NIGHT_COAST,
            CABIN_FIRST,
            CABIN_SECTION_2,
            CABIN_ECONOMY
    );

    private static final Map<String, Image> IMAGE_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_IMAGE_CACHE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > MAX_IMAGE_CACHE;
                }
            }
    );

    private DestinationPhotos() {
    }

    private static String toDesktopPath(String fileName) {
        return fileName.startsWith("/") ? fileName : (PHOTOS_PREFIX + fileName);
    }

    public static String sectionCabinPhotoPath(int sectionIndex1Based) {
        String exact = SECTION_CABIN_PHOTOS.get(sectionIndex1Based);
        if (exact != null) {
            return exact;
        }
        return sectionIndex1Based <= 1 ? CABIN_FIRST : CABIN_FALLBACK;
    }

    public static String cabinPhotoPath(SeatClass seatClass) {
        if (seatClass == null) {
            return CABIN_FALLBACK;
        }
        return CLASS_CABIN_PHOTOS.getOrDefault(seatClass.name(), CABIN_FALLBACK);
    }

    public static Set<String> allReferencedDesktopPaths() {
        Set<String> refs = new LinkedHashSet<>(ROTATING_PHOTOS);
        refs.add(AERIAL_PRIMARY);
        refs.add(CABIN_FALLBACK);
        refs.addAll(SECTION_CABIN_PHOTOS.values());
        refs.addAll(CLASS_CABIN_PHOTOS.values());
        for (String code : DestinationPhotoResolver.knownAirportCodes()) {
            String webPath = DestinationPhotoResolver.resolveWebPath(code);
            String fileName = webPath.substring(webPath.lastIndexOf('/') + 1);
            refs.add(PHOTOS_PREFIX + fileName);
        }
        return Collections.unmodifiableSet(refs);
    }

    public static int getCacheSize() {
        return IMAGE_CACHE.size();
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
        return loadResource(cabinPhotoPath(seatClass));
    }

    public static Optional<Image> getSectionCabinImage(int sectionIndex1Based) {
        return loadResource(sectionCabinPhotoPath(sectionIndex1Based));
    }

    public static Optional<Image> loadResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return Optional.empty();
        }
        Image cached = IMAGE_CACHE.get(resourcePath);
        if (cached != null) {
            return Optional.of(cached);
        }
        URL url = DestinationPhotos.class.getResource(resourcePath);
        if (url == null) {
            return Optional.empty();
        }
        Optional<Image> loaded = loadUrl(url);
        loaded.ifPresent(img -> IMAGE_CACHE.put(resourcePath, img));
        return loaded;
    }

    private static Optional<Image> loadUrl(URL url) {
        try {
            return Optional.of(new Image(url.toExternalForm(), false));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
