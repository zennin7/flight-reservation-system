package com.group2.flightres.config;

/**
 * Central Brand Configuration.
 * Brand name, tagline, logo text and support metadata are derived from {@link AppInfo#NAME}
 * and referenced across every screen, window title, and web page.
 */
public final class BrandConfig {

    public static final String APP_NAME = AppInfo.NAME;
    public static final String TAGLINE = "Fly in Comfort across South Africa and Beyond";
    public static final String LOGO_TEXT = AppInfo.NAME;
    public static final String LOGO_PREFIX = "Sky ";
    public static final String LOGO_SUFFIX = "Royal";
    public static final String WINDOW_TITLE = AppInfo.NAME + " Flight Reservations";
    public static final String COPYRIGHT = "© 2026 " + AppInfo.NAME + " Airlines. All rights reserved.";
    public static final String SUPPORT_EMAIL = "support@skyroyal.com";
    public static final String SUPPORT_PHONE = "0800 123 456";

    private BrandConfig() {
        // Prevent instantiation
    }
}
