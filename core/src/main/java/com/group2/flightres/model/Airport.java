package com.group2.flightres.model;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Airport entity representing an operational airport with IATA code and local timezone.
 */
public record Airport(String code, String name, String city, String country, ZoneId zoneId) {

    public Airport {
        Objects.requireNonNull(code, "Airport code cannot be null");
        Objects.requireNonNull(name, "Airport name cannot be null");
        Objects.requireNonNull(city, "Airport city cannot be null");
        Objects.requireNonNull(country, "Airport country cannot be null");
        Objects.requireNonNull(zoneId, "Airport zoneId cannot be null");
    }

    @Override
    public String toString() {
        return city + " (" + code + ")";
    }
}
