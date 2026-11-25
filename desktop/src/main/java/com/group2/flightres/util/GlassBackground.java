package com.group2.flightres.util;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.DefaultProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Atmospheric background helper for the Glass UI theme.
 * Composes a full-bleed photo with GaussianBlur and ColorAdjust, a translucent navy/gradient overlay,
 * radial glow accents, and a top content container. Automatically scales and crops the background
 * image to "cover" the window bounds on resize.
 */
@DefaultProperty("content")
public class GlassBackground extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(GlassBackground.class);

    public static final double DEFAULT_BLUR_RADIUS = 0.0;
    public static final double DEFAULT_DARKNESS = 0.0;
    public static final double DEFAULT_SATURATION = 0.0;
    public static final String DEFAULT_NAVY_OVERLAY_COLOR =
            "linear-gradient(to bottom, rgba(11, 16, 22, 0.24) 0%, rgba(11, 16, 22, 0.30) 48%, rgba(11, 16, 22, 0.48) 100%)";
    public static final String HERO_LEFT_SCRIM =
            "linear-gradient(to right, rgba(11, 16, 22, 0.62) 0%, rgba(11, 16, 22, 0.34) 44%, rgba(11, 16, 22, 0.0) 76%)";
    public static final String DEFAULT_RADIAL_GLOW_GOLD = "transparent";
    public static final String DEFAULT_RADIAL_GLOW_BLUE = "transparent";

    private final ImageView backgroundImageView = new ImageView();
    private final ColorAdjust colorAdjust = new ColorAdjust();
    private final GaussianBlur gaussianBlur = new GaussianBlur();
    private final Region navyOverlay = new Region();
    private final Region glowGold = new Region();
    private final Region glowBlue = new Region();
    private final StackPane contentContainer = new StackPane();
    private final Rectangle clipRect = new Rectangle();

    private String currentPhotoPath;
    private double currentBlurRadius = DEFAULT_BLUR_RADIUS;
    private double currentDarkness = DEFAULT_DARKNESS;
    private double currentSaturation = DEFAULT_SATURATION;
    private String currentLinearOverlay = DEFAULT_NAVY_OVERLAY_COLOR;
    private String currentRadialGlow = DEFAULT_RADIAL_GLOW_GOLD;
    private boolean enableSecondaryGlow = false;
    private boolean heroScrim = false;
    private boolean idleRotation = false;
    private Timeline idleTimeline;
    private List<String> rotationQueue = new ArrayList<>();
    private int rotationIndex = 0;

    public GlassBackground() {
        this("/com/group2/flightres/photos/aerial-1.jpg");
    }

    public GlassBackground(String photoPath) {
        this(photoPath, DEFAULT_BLUR_RADIUS, DEFAULT_DARKNESS, null);
    }

    public GlassBackground(String photoPath, Node content) {
        this(photoPath, DEFAULT_BLUR_RADIUS, DEFAULT_DARKNESS, content);
    }

    public GlassBackground(String photoPath, double blurRadius, double darkness, Node content) {
        this(photoPath, blurRadius, darkness, DEFAULT_SATURATION,
                DEFAULT_NAVY_OVERLAY_COLOR, DEFAULT_RADIAL_GLOW_GOLD, false, content);
    }

    public GlassBackground(String photoPath, double blurRadius, double darkness, double saturation,
                           String linearOverlay, String radialGlow, boolean enableSecondaryGlow, Node content) {
        setAlignment(Pos.CENTER);

        // 1. Configure image & effects (unmanaged so cover-scaled portrait photos never inflate layout bounds)
        backgroundImageView.setPreserveRatio(true);
        backgroundImageView.setSmooth(true);
        backgroundImageView.setManaged(false);

        setDarkness(darkness);
        setSaturation(saturation);
        setBlurRadius(blurRadius);
        updateImageEffects();

        // 2. Configure linear overlay
        setLinearOverlay(linearOverlay != null ? linearOverlay : DEFAULT_NAVY_OVERLAY_COLOR);
        navyOverlay.prefWidthProperty().bind(widthProperty());
        navyOverlay.prefHeightProperty().bind(heightProperty());

        // 2b. Primary radial glow accent (sit above navy overlay, below content)
        glowGold.prefWidthProperty().bind(widthProperty());
        glowGold.prefHeightProperty().bind(heightProperty());
        glowGold.setMouseTransparent(true);
        setRadialGlow(radialGlow != null ? radialGlow : DEFAULT_RADIAL_GLOW_GOLD);

        // 2c. Secondary radial glow accent (optional)
        glowBlue.prefWidthProperty().bind(widthProperty());
        glowBlue.prefHeightProperty().bind(heightProperty());
        glowBlue.setMouseTransparent(true);
        glowBlue.setStyle("-fx-background-color: " + DEFAULT_RADIAL_GLOW_BLUE);
        setEnableSecondaryGlow(enableSecondaryGlow);

        // 3. Configure content container
        contentContainer.setAlignment(Pos.CENTER);
        if (content != null) {
            contentContainer.getChildren().add(content);
        }

        // 4. Assemble stack: ImageView (bottom), Overlay, Primary Glow, Secondary Glow, Content (top)
        getChildren().addAll(backgroundImageView, navyOverlay, glowGold, glowBlue, contentContainer);

        // 5. Constrain blurred bleed using clip
        clipRect.widthProperty().bind(widthProperty());
        clipRect.heightProperty().bind(heightProperty());
        setClip(clipRect);

        // 6. Listen for resizes to scale the image to cover bounds
        widthProperty().addListener((obs, oldV, newV) -> updateImageCover());
        heightProperty().addListener((obs, oldV, newV) -> updateImageCover());

        if (photoPath != null && !photoPath.isBlank()) {
            setPhoto(photoPath);
        }

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopIdleRotation();
            } else if (idleRotation) {
                startIdleRotation();
            }
        });
    }

    /**
     * Loads a photo into the background image view using the bounded 6-image LRU cache.
     * Supports classpath resources starting with "/" or file URLs.
     */
    public void setPhoto(String photoPath) {
        this.currentPhotoPath = photoPath;
        try {
            Image img = loadPhotoImage(photoPath);
            if (img != null) {
                backgroundImageView.setImage(img);
                updateImageCover();
            } else {
                logger.warn("Could not load glass background photo: {}", photoPath);
            }
        } catch (Exception e) {
            logger.error("Error setting glass background photo: {}", photoPath, e);
        }
    }

    /**
     * Smoothly fades (200 ms) to a new background photo without flicker.
     */
    public void setPhotoWithFade(String photoPath) {
        if (photoPath == null || photoPath.isBlank() || photoPath.equals(this.currentPhotoPath)) {
            return;
        }
        Image img = loadPhotoImage(photoPath);
        if (img == null) {
            logger.warn("Could not load glass background photo for fade: {}", photoPath);
            return;
        }
        this.currentPhotoPath = photoPath;
        FadeTransition fadeOut = new FadeTransition(Duration.millis(100), backgroundImageView);
        fadeOut.setFromValue(backgroundImageView.getOpacity());
        fadeOut.setToValue(0.35);
        fadeOut.setOnFinished(e -> {
            backgroundImageView.setImage(img);
            updateImageCover();
            FadeTransition fadeIn = new FadeTransition(Duration.millis(120), backgroundImageView);
            fadeIn.setFromValue(0.35);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        fadeOut.play();
    }

    private Image loadPhotoImage(String photoPath) {
        if (photoPath == null || photoPath.isBlank()) {
            return null;
        }
        if (photoPath.startsWith("/")) {
            Optional<Image> cached = DestinationPhotos.loadResource(photoPath);
            if (cached.isPresent()) {
                return cached.get();
            }
            try (InputStream is = getClass().getResourceAsStream(photoPath)) {
                if (is != null) {
                    return new Image(is);
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        return new Image(photoPath, false);
    }

    public boolean isIdleRotation() {
        return idleRotation;
    }

    public void setIdleRotation(boolean idleRotation) {
        this.idleRotation = idleRotation;
        if (idleRotation && getScene() != null) {
            startIdleRotation();
        } else if (!idleRotation) {
            stopIdleRotation();
        }
    }

    private void rebuildShuffledQueue(String firstPath) {
        List<String> all = new ArrayList<>(DestinationPhotos.ROTATING_PHOTOS);
        all.remove(firstPath);
        Collections.shuffle(all);
        rotationQueue = new ArrayList<>(all.size() + 1);
        if (firstPath != null && !firstPath.isBlank()) {
            rotationQueue.add(firstPath);
        }
        rotationQueue.addAll(all);
        rotationIndex = 0;
    }

    private void preloadNextRotationPhoto() {
        if (rotationQueue.size() <= 1) return;
        int nextIdx = (rotationIndex + 1) % rotationQueue.size();
        DestinationPhotos.loadResource(rotationQueue.get(nextIdx));
    }

    private void startIdleRotation() {
        if (idleTimeline != null) return;
        rebuildShuffledQueue(currentPhotoPath);
        preloadNextRotationPhoto();
        idleTimeline = new Timeline(new KeyFrame(Duration.seconds(12), e -> advanceIdleBackdropNow()));
        idleTimeline.setCycleCount(Timeline.INDEFINITE);
        idleTimeline.play();
    }

    private void stopIdleRotation() {
        if (idleTimeline != null) {
            idleTimeline.stop();
            idleTimeline = null;
        }
    }

    /**
     * Advances to the next photo in the shuffled generic rotation queue (no immediate repeats)
     * and preloads only the subsequent photo.
     */
    public void advanceIdleBackdropNow() {
        if (rotationQueue.isEmpty()) {
            rebuildShuffledQueue(currentPhotoPath);
        }
        if (rotationQueue.size() <= 1) return;
        int nextIdx = rotationIndex + 1;
        if (nextIdx >= rotationQueue.size()) {
            rebuildShuffledQueue(currentPhotoPath);
            nextIdx = 1;
        }
        rotationIndex = nextIdx;
        setPhotoWithFade(rotationQueue.get(rotationIndex));
        preloadNextRotationPhoto();
    }

    public String getPhotoPath() {
        return currentPhotoPath;
    }

    public void setPhotoPath(String photoPath) {
        setPhoto(photoPath);
    }

    public double getBlurRadius() {
        return currentBlurRadius;
    }

    public void setBlurRadius(double radius) {
        this.currentBlurRadius = Math.max(0.0, radius);
        gaussianBlur.setRadius(this.currentBlurRadius);
        updateImageEffects();
    }

    public double getDarkness() {
        return currentDarkness;
    }

    public void setDarkness(double darkness) {
        this.currentDarkness = darkness;
        colorAdjust.setBrightness(Math.max(-1.0, Math.min(1.0, darkness)));
        updateImageEffects();
    }

    public double getSaturation() {
        return currentSaturation;
    }

    public void setSaturation(double saturation) {
        this.currentSaturation = saturation;
        colorAdjust.setSaturation(Math.max(-1.0, Math.min(1.0, saturation)));
        updateImageEffects();
    }

    private void updateImageEffects() {
        boolean needsColor = Math.abs(currentDarkness) > 0.001 || Math.abs(currentSaturation) > 0.001;
        boolean needsBlur = currentBlurRadius > 0.05;
        if (needsBlur && needsColor) {
            gaussianBlur.setInput(colorAdjust);
            backgroundImageView.setEffect(gaussianBlur);
        } else if (needsBlur) {
            gaussianBlur.setInput(null);
            backgroundImageView.setEffect(gaussianBlur);
        } else if (needsColor) {
            backgroundImageView.setEffect(colorAdjust);
        } else {
            backgroundImageView.setEffect(null);
        }
    }

    public boolean isHeroScrim() {
        return heroScrim;
    }

    public void setHeroScrim(boolean heroScrim) {
        this.heroScrim = heroScrim;
        if (heroScrim) {
            setLinearOverlay(HERO_LEFT_SCRIM + ", " + DEFAULT_NAVY_OVERLAY_COLOR);
        } else {
            setLinearOverlay(DEFAULT_NAVY_OVERLAY_COLOR);
        }
    }

    public String getLinearOverlay() {
        return currentLinearOverlay;
    }

    public void setLinearOverlay(String cssBackground) {
        this.currentLinearOverlay = cssBackground;
        if (cssBackground != null && !cssBackground.isBlank()) {
            navyOverlay.setStyle("-fx-background-color: " + cssBackground + ";");
        }
    }

    public String getRadialGlow() {
        return currentRadialGlow;
    }

    public void setRadialGlow(String cssBackground) {
        this.currentRadialGlow = cssBackground;
        if (cssBackground != null && !cssBackground.isBlank()) {
            glowGold.setStyle("-fx-background-color: " + cssBackground + ";");
        }
    }

    public boolean isEnableSecondaryGlow() {
        return enableSecondaryGlow;
    }

    public void setEnableSecondaryGlow(boolean enable) {
        this.enableSecondaryGlow = enable;
        glowBlue.setVisible(enable);
        glowBlue.setManaged(enable);
    }

    /**
     * Replaces or sets the content node displayed on top of the glass backdrop.
     */
    public void setContent(Node content) {
        contentContainer.getChildren().clear();
        if (content != null) {
            contentContainer.getChildren().add(content);
        }
    }

    public Node getContent() {
        if (contentContainer.getChildren().isEmpty()) {
            return null;
        }
        return contentContainer.getChildren().get(0);
    }

    /**
     * Scales the background ImageView to emulate CSS "object-fit: cover".
     */
    private void updateImageCover() {
        Image img = backgroundImageView.getImage();
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
            return;
        }

        double containerW = getWidth();
        double containerH = getHeight();
        if (containerW <= 0 || containerH <= 0) {
            return;
        }

        double imgW = img.getWidth();
        double imgH = img.getHeight();

        double scale = Math.max(containerW / imgW, containerH / imgH);
        double scaledW = imgW * scale;
        double scaledH = imgH * scale;
        backgroundImageView.setFitWidth(scaledW);
        backgroundImageView.setFitHeight(scaledH);
        backgroundImageView.relocate((containerW - scaledW) / 2.0, (containerH - scaledH) / 2.0);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        updateImageCover();
    }
}
