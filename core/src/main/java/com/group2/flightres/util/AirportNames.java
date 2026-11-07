package com.group2.flightres.util;

/**
 * Utility for formatting airport display names.
 */
public final class AirportNames {

    private AirportNames() {}

    /**
     * Converts a full airport name to its shortened display version by replacing
     * " International Airport" with " Intl".
     *
     * @param fullName the full airport name from the database
     * @return shortened display name derived from data, or empty string if input is null
     */
    public static String shortName(String fullName) {
        if (fullName == null) {
            return "";
        }
        return fullName.replace(" International Airport", " Intl");
    }
}
