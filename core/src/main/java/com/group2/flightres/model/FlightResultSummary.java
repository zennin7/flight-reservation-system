package com.group2.flightres.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Summary bounds calculated from a flight search result set.
 */
public record FlightResultSummary(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        long minDurationMinutes,
        long maxDurationMinutes
) {

    public static FlightResultSummary fromResults(List<FlightSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return new FlightResultSummary(BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L);
        }
        BigDecimal minPrice = results.stream()
                .map(FlightSearchResult::getBasePrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal maxPrice = results.stream()
                .map(FlightSearchResult::getBasePrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        long minDur = results.stream()
                .mapToLong(f -> Duration.between(f.getDeparture(), f.getArrival()).toMinutes())
                .min()
                .orElse(0L);
        long maxDur = results.stream()
                .mapToLong(f -> Duration.between(f.getDeparture(), f.getArrival()).toMinutes())
                .max()
                .orElse(0L);
        return new FlightResultSummary(minPrice, maxPrice, minDur, maxDur);
    }
}
