package com.group2.flightres.util;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;

/**
 * Atmospheric background helper for the Glass UI theme.
 * Composes a full-bleed photo with GaussianBlur and ColorAdjust, a translucent navy/gradient overlay,
 * radial glow accents, and a top content container. Automatically scales and crops the background
 * image to "cover" the window bounds on resize.
 */
@DefaultProperty("content")
public class GlassBackground extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(GlassBackground.class);

    public static final double DEFAULT_BLUR_RADIUS = 28.0;
    public static final double DEFAULT_DARKNESS = -0.35; // -35% brightness
    public static final double DEFAULT_SATURATION = 0.0;
    public static final String DEFAULT_NAVY_OVERLAY_COLOR = "rgba(11, 18, 32, 0.60)";
    public static final String DEFAULT_RADIAL_GLOW_GOLD =
            "radial-gradient(center 20% 22%, radius 65%, rgba(212,166,60,0.16) 0%, transparent 100%);";
    public static final String DEFAULT_RADIAL_GLOW_BLUE =
            "radial-gradient(center 80% 78%, radius 60%, rgba(79,124,255,0.12) 0%, transparent 100%);";

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
    private boolean enableSecondaryGlow = true;

    public GlassBackground() {
        this("/images/destinations/cpt.jpg");
    }

    public GlassBackground(String photoPath) {
        this(photoPath, DEFAULT_BLUR_RADIUS, DEFAULT_DARKNESS, null);
    }

    public GlassBackground(String photoPath, Node content) {
        this(photoPath, DEFAULT_BLUR_RADIUS, DEFAULT_DARKNESS, content);
    }

    public GlassBackground(String photoPath, double blurRadius, double darkness, Node content) {
        this(photoPath, blurRadius, darkness, DEFAULT_SATURATION,
                DEFAULT_NAVY_OVERLAY_COLOR, DEFAULT_RADIAL_GLOW_GOLD, true, content);
    }

    public GlassBackground(String photoPath, double blurRadius, double darkness, double saturation,
                           String linearOverlay, String radialGlow, boolean enableSecondaryGlow, Node content) {
        setAlignment(Pos.CENTER);

        // 1. Configure image & effects
        backgroundImageView.setPreserveRatio(true);
        backgroundImageView.setSmooth(true);

        setDarkness(darkness);
        setSaturation(saturation);
        setBlurRadius(blurRadius);
        gaussianBlur.setInput(colorAdjust);
        backgroundImageView.setEffect(gaussianBlur);

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
    }

    /**
     * Loads a photo into the background image view.
     * Supports classpath resources starting with "/" or file URLs.
     */
    public void setPhoto(String photoPath) {
        this.currentPhotoPath = photoPath;
        try {
            Image img = null;
            if (photoPath.startsWith("/")) {
                URL resource = getClass().getResource(photoPath);
                if (resource != null) {
                    img = new Image(resource.toExternalForm(), false);
                } else {
                    try (InputStream is = getClass().getResourceAsStream(photoPath)) {
                        if (is != null) {
                            img = new Image(is);
                        }
                    }
                }
            } else {
                img = new Image(photoPath, false);
            }

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
        this.currentBlurRadius = radius;
        gaussianBlur.setRadius(Math.max(0.0, radius));
    }

    public double getDarkness() {
        return currentDarkness;
    }

    public void setDarkness(double darkness) {
        this.currentDarkness = darkness;
        colorAdjust.setBrightness(Math.max(-1.0, Math.min(1.0, darkness)));
    }

    public double getSaturation() {
        return currentSaturation;
    }

    public void setSaturation(double saturation) {
        this.currentSaturation = saturation;
        colorAdjust.setSaturation(Math.max(-1.0, Math.min(1.0, saturation)));
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
        backgroundImageView.setFitWidth(imgW * scale);
        backgroundImageView.setFitHeight(imgH * scale);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        updateImageCover();
    }
}
