package com.group2.flightres.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable price quote for a single seat on a flight.
 *
 * Calculated in exact order (all scale 2, HALF_UP):
 *   1. classFare = round(baseFare * classMultiplier)
 *   2. afterDemand = round(classFare * demandMultiplier)
 *   3. demandAdjustment = afterDemand - classFare
 *   4. afterTiming = round(afterDemand * timingMultiplier)
 *   5. timingAdjustment = afterTiming - afterDemand
 *   6. total = afterTiming
 *
 * This guarantees: classFare + demandAdjustment + timingAdjustment == total.
 * The quoted total is VAT-inclusive. vatIncluded calculates the informational 15% VAT portion.
 */
public record PriceQuote(
        BigDecimal baseFare,
        BigDecimal classMultiplier,
        BigDecimal classFare,
        BigDecimal demandMultiplier,
        BigDecimal demandAdjustment,
        BigDecimal timingMultiplier,
        BigDecimal timingAdjustment,
        BigDecimal total,
        BigDecimal vatIncluded
) {

    public PriceQuote {
        Objects.requireNonNull(baseFare, "baseFare cannot be null");
        Objects.requireNonNull(classMultiplier, "classMultiplier cannot be null");
        Objects.requireNonNull(classFare, "classFare cannot be null");
        Objects.requireNonNull(demandMultiplier, "demandMultiplier cannot be null");
        Objects.requireNonNull(demandAdjustment, "demandAdjustment cannot be null");
        Objects.requireNonNull(timingMultiplier, "timingMultiplier cannot be null");
        Objects.requireNonNull(timingAdjustment, "timingAdjustment cannot be null");
        Objects.requireNonNull(total, "total cannot be null");
        Objects.requireNonNull(vatIncluded, "vatIncluded cannot be null");
    }

    /**
     * Factory method computing the quote strictly following the transparent pricing rules.
     *
     * @param baseFare raw flight base_price from flights table
     * @param classMultiplier seat class multiplier from seat_class_pricing table
     * @param demandMultiplier occupancy-based multiplier from PricingPolicy
     * @param timingMultiplier departure-proximity multiplier from PricingPolicy
     * @return fully computed, immutable PriceQuote
     */
    public static PriceQuote calculate(
            BigDecimal baseFare,
            BigDecimal classMultiplier,
            BigDecimal demandMultiplier,
            BigDecimal timingMultiplier
    ) {
        BigDecimal base = baseFare.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cMult = classMultiplier.setScale(2, RoundingMode.HALF_UP);
        BigDecimal dMult = demandMultiplier.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tMult = timingMultiplier.setScale(2, RoundingMode.HALF_UP);

        BigDecimal classFare = base.multiply(cMult).setScale(2, RoundingMode.HALF_UP);
        BigDecimal afterDemand = classFare.multiply(dMult).setScale(2, RoundingMode.HALF_UP);
        BigDecimal demandAdjustment = afterDemand.subtract(classFare);

        BigDecimal afterTiming = afterDemand.multiply(tMult).setScale(2, RoundingMode.HALF_UP);
        BigDecimal timingAdjustment = afterTiming.subtract(afterDemand);

        BigDecimal total = afterTiming;

        // VAT is 15% inclusive: portion = total * 15 / 115
        BigDecimal vatIncluded = total.multiply(new BigDecimal("15"))
                .divide(new BigDecimal("115"), 2, RoundingMode.HALF_UP);

        return new PriceQuote(
                base,
                cMult,
                classFare,
                dMult,
                demandAdjustment,
                tMult,
                timingAdjustment,
                total,
                vatIncluded
        );
    }

    public static PriceQuote calculate(
            BigDecimal baseFare,
            BigDecimal classMultiplier,
            double occupancy,
            double hoursToDeparture
    ) {
        return calculate(
                baseFare,
                classMultiplier,
                com.group2.flightres.config.PricingPolicy.getDemandMultiplier(occupancy),
                com.group2.flightres.config.PricingPolicy.getTimingMultiplier(hoursToDeparture)
        );
    }
}