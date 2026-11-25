package com.group2.flightres.web;

import com.group2.flightres.util.DestinationPhotoResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPhotoResourcesTest {

    private static final int MAX_WEB_PHOTO_BYTES = 350 * 1024;

    private static Path webPhotosDir() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("src/main/resources/public/photos"))) {
            return cwd.resolve("src/main/resources/public/photos");
        }
        return cwd.resolve("web/src/main/resources/public/photos");
    }

    @Test
    @DisplayName("Every resolved photo, cabin section photo, and rotation photo exists on classpath and is <= 350 KB")
    void everyResolvedPhotoResourceExistsAndIsWithinSizeLimit() throws IOException {
        Set<String> webPaths = new HashSet<>(WebBackdropResolver.allReferencedWebPaths());
        webPaths.add(DestinationPhotoResolver.WEB_AERIAL_FALLBACK);
        webPaths.add(WebBackdropResolver.sectionCabinPhoto(1));
        webPaths.add(WebBackdropResolver.sectionCabinPhoto(2));
        webPaths.add(WebBackdropResolver.sectionCabinPhoto(3));
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

    @Test
    @DisplayName("Every image file in public/photos is referenced (zero orphan photos) and all 3 cabin sections map to distinct photos")
    void noOrphanPhotosAndDistinctSectionCabinPhotos() throws IOException {
        Set<String> referenced = WebBackdropResolver.allReferencedWebPaths();
        List<String> orphans = new ArrayList<>();
        try (Stream<Path> files = Files.list(webPhotosDir())) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                String lower = name.toLowerCase();
                if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))) {
                    continue;
                }
                String webPath = "/photos/" + name;
                if (!referenced.contains(webPath)) {
                    orphans.add(name);
                }
            }
        }
        assertTrue(orphans.isEmpty(), "Orphan unreferenced web photos found: " + orphans);

        Set<String> sectionPhotos = Set.of(
                WebBackdropResolver.sectionCabinPhoto(1),
                WebBackdropResolver.sectionCabinPhoto(2),
                WebBackdropResolver.sectionCabinPhoto(3)
        );
        assertEquals(3, sectionPhotos.size(), "Sections 1, 2, and 3 must map to 3 distinct cabin photos");
    }
}
