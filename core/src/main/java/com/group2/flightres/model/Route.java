package com.group2.flightres.model;

import java.util.Objects;

/**
 * Route entity consisting of origin and destination airports.
 */
public record Route(Airport origin, Airport destination) {

    public Route {
        Objects.requireNonNull(origin, "Origin airport cannot be null");
        Objects.requireNonNull(destination, "Destination airport cannot be null");
    }
}
