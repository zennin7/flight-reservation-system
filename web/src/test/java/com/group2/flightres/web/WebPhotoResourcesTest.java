package com.group2.flightres.web;

import com.group2.flightres.util.DestinationPhotoResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPhotoResourcesTest {

    private static final int MAX_WEB_PHOTO_BYTES = 350 * 1024;

    @Test
    @DisplayName("Every airport photo path and aerial fallback exists on classpath under /public and is <= 350 KB")
    void everyResolvedPhotoResourceExistsAndIsWithinSizeLimit() throws IOException {
        Set<String> webPaths = new HashSet<>();
        webPaths.add(DestinationPhotoResolver.WEB_AERIAL_FALLBACK);
        webPaths.add("/photos/aerial-1.jpg");
        webPaths.add("/photos/aerial-2.jpg");
        webPaths.add("/photos/aerial-3.jpg");
        webPaths.add("/photos/cabin-economy-1.jpg");
        webPaths.add("/photos/cabin-first-1.jpg");
        webPaths.add("/photos/sky-wing-1.jpg");
        webPaths.add("/photos/cabin-mood-1.jpg");
        webPaths.add("/photos/sky-engine-1.jpg");
        webPaths.add("/photos/sky-window-peaks.jpg");
        webPaths.add("/photos/sky-jet-clouds.jpg");
        webPaths.add("/photos/sky-jet-ocean.jpg");
        webPaths.add("/photos/cabin-suite-1.jpg");
        webPaths.add("/photos/tarmac-jet-1.jpg");
        webPaths.add("/photos/sky-jet-dark.jpg");
        for (String code : DestinationPhotoResolver.knownAirportCodes()) {
            webPaths.add(DestinationPhotoResolver.resolveWebPath(code));
        }

        for (String webPath : webPaths) {
            String cp = "/public" + webPath;
            try (InputStream is = WebPhotoResourcesTest.class.getResourceAsStream(cp)) {
                assertNotNull(is, "Missing web photo resource on classpath: " + cp);
                byte[] bytes = is.readAllBytes();
                assertTrue(bytes.length > 5_000, "Web photo appears too small/corrupt: " + cp + " (" + bytes.length + ")");
                assertTrue(bytes.length <= MAX_WEB_PHOTO_BYTES,
                        "Web photo exceeds 350 KB limit: " + cp + " (" + bytes.length + " bytes)");
            }
        }
    }
}
